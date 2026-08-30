package com.jossephus.chuchu.ui.components.chart

import com.jossephus.chuchu.data.model.dbtop.DailyYield
import com.jossephus.chuchu.data.model.dbtop.SpendingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YieldNetChartTest {

    @Test
    fun testCashflowEngine_DateUnion_WithinActiveWindow() {
        val daily = listOf(
            DailyYield(date = "2026-08-20", yieldUsd = 48.9, coverageDays = 0.56),
            DailyYield(date = "2026-08-21", yieldUsd = 64.6, coverageDays = 1.0),
            DailyYield(date = "2026-08-22", yieldUsd = 64.0, coverageDays = 1.0),
        )
        val spend = mapOf(
            "2026-07-31" to 257.24, // Outside window (before 2026-08-20) -> should be excluded
            "2026-08-21" to 416.99, // Inside window -> should be matched
        )

        val points = CashflowEngine.calculatePoints(daily, spend)
        assertEquals(3, points.size)
        assertEquals("2026-08-20", points[0].date)
        assertEquals(48.9, points[0].gross, 0.001)
        assertEquals(0.0, points[0].spend, 0.001)
        assertEquals(48.9, points[0].net, 0.001)

        assertEquals("2026-08-21", points[1].date)
        assertEquals(64.6, points[1].gross, 0.001)
        assertEquals(416.99, points[1].spend, 0.001)
        assertEquals(64.6 - 416.99, points[1].net, 0.001)
    }

    @Test
    fun testCashflowEngine_OutlierSpendKeepsRawNet() {
        val daily = listOf(
            DailyYield(date = "2026-08-20", yieldUsd = 50.0, coverageDays = 1.0),
            DailyYield(date = "2026-08-21", yieldUsd = 50.0, coverageDays = 1.0),
        )
        val spend = mapOf(
            "2026-08-20" to 10.0,     // Net = +$40.0
            "2026-08-21" to 1500.0,   // Net = -$1450.0 (huge outlier spike!)
        )

        val points = CashflowEngine.calculatePoints(daily, spend)
        assertEquals(2, points.size)
        assertEquals(40.0, points[0].net, 0.001)
        // Outlier day keeps its real net — the curve amortizes spend instead of clamping it.
        assertEquals(-1450.0, points[1].net, 0.001)
    }

    @Test
    fun testCashflowEngine_ZeroCapitalDivision() {
        val points = listOf(
            DailyCashflowPoint("2026-08-20", gross = 50.0, spend = 20.0, net = 30.0)
        )
        val kpis = CashflowEngine.computeKpis(
            cap = 0.0,
            currentPerDay = 50.0,
            grossApr = null,
            spending = null,
            points = points
        )

        assertNull("Zero capital must produce null Net APR instead of NaN or Infinity", kpis.netRunRateApr)
        assertEquals(30.0, kpis.netRunRatePerDay, 0.001)
    }

    @Test
    fun testCashflowEngine_ComputeKpis_RunRateAndBurnRatio() {
        val points = listOf(
            DailyCashflowPoint("2026-08-20", gross = 77.90, spend = 0.0, net = 77.90),
            DailyCashflowPoint("2026-08-21", gross = 77.90, spend = 100.0, net = -22.10),
        )
        val spending = SpendingState(monthUsd = 976.66, totalUsd = 12450.0)
        val kpis = CashflowEngine.computeKpis(
            cap = 79338.74,
            currentPerDay = 77.90,
            grossApr = 35.84,
            spending = spending,
            points = points
        )

        // avgDailySpend = 976.66 / 30.416 = 32.1097
        // runRatePerDay = 77.90 - 32.1097 = +45.7903
        // netApr = 45.7903 * 365 / 79338.74 * 100 = 21.066%
        assertEquals(35.84, kpis.grossApr!!, 0.01)
        assertNotNull(kpis.netRunRateApr)
        assertEquals(21.07, kpis.netRunRateApr!!, 0.1)
        assertEquals(45.79, kpis.netRunRatePerDay, 0.1)
        // burnRatio = 32.1097 / 77.90 * 100 = 41.2%
        assertEquals(41.2, kpis.burnRatioPct!!, 0.5)
        assertEquals(55.8, kpis.trailingNetUsd, 0.1)
    }

    @Test
    fun testCashflowEngine_CalculateAprPoints() {
        val daily = listOf(
            DailyYield(date = "2026-08-20", yieldUsd = 77.90, coverageDays = 1.0),
            DailyYield(date = "2026-08-21", yieldUsd = 77.90, coverageDays = 1.0),
            DailyYield(date = "2026-08-22", yieldUsd = 77.90, coverageDays = 1.0),
        )
        val spend = mapOf(
            "2026-08-21" to 416.99,
        )
        val spending = SpendingState(monthUsd = 976.66, totalUsd = 12450.0)

        val aprPoints = CashflowEngine.calculateAprPoints(
            dailyData = daily,
            basePoints = CashflowEngine.calculatePoints(daily, spend),
            cap = 79338.74,
            grossApr = 35.84,
            spending = spending,
        )

        assertEquals(3, aprPoints.size)
        assertEquals("2026-08-20", aprPoints[0].date)
        assertEquals(35.84, aprPoints[0].grossApr, 0.01)

        // Day 1 has spend = 416.99. Over windowLen = 7, effectiveSpend = 416.99 / 7 = 59.57
        // dailyNet = 77.90 - 59.57 = 18.33 -> Net APR = 18.33 * 365 / 79338.74 * 100 = 8.43%
        assertEquals(35.84, aprPoints[1].grossApr, 0.01)
        assertTrue(aprPoints[1].netApr > 0.0)
        assertTrue(aprPoints[1].netApr < aprPoints[1].grossApr)
    }

    @Test
    fun testCashflowEngine_EmptyInputs() {
        val points = CashflowEngine.calculatePoints(emptyList(), emptyMap())
        assertTrue(points.isEmpty())

        val aprPoints = CashflowEngine.calculateAprPoints(emptyList(), emptyList(), 50000.0, null, null)
        assertTrue(aprPoints.isEmpty())

        val kpis = CashflowEngine.computeKpis(
            cap = 50000.0,
            currentPerDay = null,
            grossApr = null,
            spending = null,
            points = points
        )
        assertNull(kpis.grossApr)
        assertEquals(0.0, kpis.netRunRatePerDay, 0.001)
        assertEquals(0.0, kpis.burnRatioPct!!, 0.001)
        assertEquals(0.0, kpis.trailingNetUsd, 0.001)
    }

    @Test
    fun testCashflowEngine_ZeroYieldDayIsNotFabricated() {
        // Ngay khong co yield phai giu gross = 0 — khong suy nguoc tu APR ra so gia.
        val daily = listOf(
            DailyYield(date = "2026-08-20", yieldUsd = 50.0, coverageDays = 1.0),
            DailyYield(date = "2026-08-21", yieldUsd = 0.0, coverageDays = 1.0),
        )
        val aprPoints = CashflowEngine.calculateAprPoints(
            dailyData = daily,
            basePoints = CashflowEngine.calculatePoints(daily, emptyMap()),
            cap = 50000.0,
            grossApr = 30.0,
            spending = null,
        )

        assertEquals(2, aprPoints.size)
        assertEquals(0.0, aprPoints[1].dailyGross, 0.001)
        assertEquals(0.0, aprPoints[1].dailyNet, 0.001)
        assertEquals(0.0, aprPoints[1].netApr, 0.001)
    }

    @Test
    fun testCashflowEngine_NoAprNoDailyData_FallsBackToZeroNotMagicNumber() {
        // Khong co APR va khong co daily data -> gross APR = 0, khong phai hang so dong dinh.
        val spend = mapOf("2026-08-20" to 10.0)
        val aprPoints = CashflowEngine.calculateAprPoints(
            dailyData = emptyList(),
            basePoints = CashflowEngine.calculatePoints(emptyList(), spend),
            cap = 1000.0,
            grossApr = null,
            spending = null,
        )

        assertEquals(1, aprPoints.size)
        assertEquals(0.0, aprPoints[0].grossApr, 0.001)
        assertEquals(0.0, aprPoints[0].dailyGross, 0.001)
    }

    @Test
    fun testCashflowEngine_SpendWithoutYield_BurnRatioIsUndefined() {
        // Tieu tien khi khong co yield = an vao von; ty le "tren yield" khong dinh
        // nghia duoc, phai la null (UI hien "--") thay vi 100% nghe nhu vua du.
        val points = listOf(
            DailyCashflowPoint("2026-08-20", gross = 0.0, spend = 40.0, net = -40.0),
        )
        val kpis = CashflowEngine.computeKpis(
            cap = 50000.0,
            currentPerDay = 0.0,
            grossApr = null,
            spending = null,
            points = points,
        )

        assertNull(kpis.burnRatioPct)
        assertEquals(-40.0, kpis.netRunRatePerDay, 0.001)
    }

    @Test
    fun testCashflowEngine_AmortizedSpendMatchesRunningSum() {
        // Chi tieu duoc dan deu theo cua so >= 7 ngay: cong don chay phai cho ket
        // qua y het cach quet lai tien to cu.
        val daily = (1..10).map { d ->
            DailyYield(date = String.format("2026-08-%02d", d), yieldUsd = 20.0, coverageDays = 1.0)
        }
        val spend = mapOf("2026-08-03" to 70.0, "2026-08-08" to 140.0)
        val basePoints = CashflowEngine.calculatePoints(daily, spend)

        val aprPoints = CashflowEngine.calculateAprPoints(daily, basePoints, 10000.0, 30.0, null)

        assertEquals(10, aprPoints.size)
        aprPoints.forEachIndexed { i, p ->
            val cumSpend = basePoints.take(i + 1).sumOf { it.spend }
            val expected = if (cumSpend > 0) cumSpend / maxOf(i + 1, 7) else 0.0
            assertEquals(expected, p.dailySpend, 0.0001)
        }
    }
}

