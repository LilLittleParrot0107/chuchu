package com.jossephus.chuchu.ui.components.chart

import com.jossephus.chuchu.data.model.dbtop.DailyYield
import com.jossephus.chuchu.data.model.dbtop.SpendingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetRateChartTest {

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
        assertEquals(0.56, points[0].coverage, 0.001)

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
        // Outlier day keeps its real net — the chart amortizes spend on the trailing
        // line while the bar still shows the real spike.
        assertEquals(-1450.0, points[1].net, 0.001)
    }

    @Test
    fun testCashflowEngine_SpendOnlyDayHasNoCoverage() {
        // Ngay chi co chi tieu, khong co ban ghi yield: coverage = 0 ("khong do
        // duoc"), khac han mot ngay do duoc va yield bang 0.
        val daily = listOf(DailyYield(date = "2026-08-20", yieldUsd = 20.0, coverageDays = 1.0))
        val points = CashflowEngine.calculatePoints(daily, mapOf("2026-08-20" to 5.0))
        assertEquals(1.0, points[0].coverage, 0.001)

        val points2 = CashflowEngine.calculatePoints(emptyList(), mapOf("2026-08-20" to 5.0))
        assertEquals(1, points2.size)
        assertEquals(0.0, points2[0].coverage, 0.001)
        assertEquals(0.0, points2[0].gross, 0.001)
    }

    @Test
    fun testCashflowEngine_TrailingGrossNormalizesByCoverage() {
        // Ngay scan chi phu duoc nua ngay khong duoc keo tut toc do xuong mot nua:
        // chia tong USD cho tong ngay THUC SU do duoc.
        val daily = listOf(
            DailyYield(date = "2026-08-20", yieldUsd = 20.0, coverageDays = 1.0),
            DailyYield(date = "2026-08-21", yieldUsd = 10.0, coverageDays = 0.5),
        )
        val rate = CashflowEngine.calculateRatePoints(CashflowEngine.calculatePoints(daily, emptyMap()))

        assertEquals(2, rate.size)
        assertEquals(20.0, rate[0].trailGross, 0.001)
        assertEquals(20.0, rate[1].trailGross, 0.001)
        // Cot van ve so do that, con grossRate la muc le ra phai toi neu do du ngay.
        assertEquals(10.0, rate[1].gross, 0.001)
        assertEquals(20.0, rate[1].grossRate, 0.001)
    }

    @Test
    fun testCashflowEngine_MeasuredZeroDragsRateButUnmeasuredDayDoesNot() {
        val measuredZero = listOf(
            DailyYield(date = "2026-08-20", yieldUsd = 20.0, coverageDays = 1.0),
            DailyYield(date = "2026-08-21", yieldUsd = 0.0, coverageDays = 1.0),
        )
        val r1 = CashflowEngine.calculateRatePoints(CashflowEngine.calculatePoints(measuredZero, emptyMap()))
        assertEquals(10.0, r1[1].trailGross, 0.001)

        // Ngay khong co ban ghi yield (chi co chi tieu, nam giua cua so do) khong
        // duoc coi la ngay kiem duoc 0$ — no chi la ngay khong do.
        val unmeasured = listOf(
            DailyYield(date = "2026-08-20", yieldUsd = 20.0, coverageDays = 1.0),
            DailyYield(date = "2026-08-22", yieldUsd = 20.0, coverageDays = 1.0),
        )
        val r2 = CashflowEngine.calculateRatePoints(
            CashflowEngine.calculatePoints(unmeasured, mapOf("2026-08-21" to 5.0))
        )
        assertEquals(3, r2.size)
        assertEquals("2026-08-21", r2[1].date)
        assertEquals(0.0, r2[1].coverage, 0.001)
        assertEquals(20.0, r2[1].trailGross, 0.001)
        assertEquals(20.0, r2[2].trailGross, 0.001)
    }

    @Test
    fun testCashflowEngine_TrailingSpendUsesFullWindowAndRollsOff() {
        val daily = (1..10).map { d ->
            DailyYield(date = String.format("2026-08-%02d", d), yieldUsd = 20.0, coverageDays = 1.0)
        }
        val spend = mapOf("2026-08-03" to 70.0)
        val rate = CashflowEngine.calculateRatePoints(
            CashflowEngine.calculatePoints(daily, spend),
            window = 7,
            fallbackSpendPerDay = 99.0, // co du lieu that -> KHONG duoc dung fallback
        )

        assertEquals(10, rate.size)
        assertEquals(0.0, rate[0].trailSpend, 0.001)
        // Chia cho tron cua so 7 ngay, khong phai cho 3 ngay da thay.
        assertEquals(10.0, rate[2].trailSpend, 0.001)
        // Van con trong cua so o ngay thu 9 (index 8: 2..8).
        assertEquals(10.0, rate[8].trailSpend, 0.001)
        // Da truot ra khoi cua so o index 9 (3..9).
        assertEquals(0.0, rate[9].trailSpend, 0.001)
        assertEquals(20.0 - 10.0, rate[2].trailNet, 0.001)
    }

    @Test
    fun testCashflowEngine_FallbackSpendOnlyWhenNoDailySpendData() {
        val daily = listOf(
            DailyYield(date = "2026-08-20", yieldUsd = 50.0, coverageDays = 1.0),
            DailyYield(date = "2026-08-21", yieldUsd = 50.0, coverageDays = 1.0),
        )
        val rate = CashflowEngine.calculateRatePoints(
            CashflowEngine.calculatePoints(daily, emptyMap()),
            fallbackSpendPerDay = 32.11,
        )
        assertEquals(32.11, rate[0].trailSpend, 0.001)
        assertEquals(50.0 - 32.11, rate[1].trailNet, 0.001)
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
    fun testCashflowEngine_KpiFallbackGrossUsesMeasuredDays() {
        // Thieu perday tuc thoi: suy tu do do, chia cho so ngay DO DUOC (1.5),
        // khong phai so ngay lich (2) — neu khong se bao cao thap hon thuc te.
        val points = listOf(
            DailyCashflowPoint("2026-08-20", gross = 20.0, spend = 0.0, net = 20.0, coverage = 0.5),
            DailyCashflowPoint("2026-08-21", gross = 20.0, spend = 0.0, net = 20.0, coverage = 1.0),
        )
        val kpis = CashflowEngine.computeKpis(
            cap = 10000.0,
            currentPerDay = null,
            grossApr = null,
            spending = null,
            points = points,
        )
        assertEquals(40.0 / 1.5, kpis.netRunRatePerDay, 0.001)
    }

    @Test
    fun testCashflowEngine_EmptyInputs() {
        val points = CashflowEngine.calculatePoints(emptyList(), emptyMap())
        assertTrue(points.isEmpty())
        assertTrue(CashflowEngine.calculateRatePoints(points).isEmpty())

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
}
