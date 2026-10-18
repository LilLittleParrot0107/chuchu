package com.jossephus.chuchu.data.model.dbtop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Join spending + flow cho tab SPEND — tách khỏi Compose để khoá các bug tháng. */
class SpendOverviewTest {

    private fun spending(
        month: String = "2026-10",
        byMonth: Map<String, Double> = mapOf("2026-09" to 800.04, "2026-10" to 791.69),
        byDay: Map<String, Double> = mapOf(
            "2026-10-01" to 100.0,
            "2026-10-15" to 691.69,
            "2026-09-30" to 50.0,
        ),
    ) = SpendingState(month = month, monthUsd = byMonth[month] ?: 0.0, byMonth = byMonth, byDay = byDay)

    @Test
    fun `thang rong hoac thieu spending thi khong co model`() {
        assertNull(buildSpendOverview(null, null, "2026-10"))
        assertNull(buildSpendOverview(SpendingState(month = ""), null, "2026-10"))
    }

    @Test
    fun `luoi thang la hop spending va flow, thang chi co flow co spend null`() {
        val flow = FlowState(
            byMonth = mapOf(
                "2026-09" to FlowMonth(8719.42, 15419.23, -6699.81, 116),
                "2026-05" to FlowMonth(3478.83, 2680.0, 798.83, 11),
            ),
        )
        val o = buildSpendOverview(spending(), flow, "2026-10")!!
        assertEquals(listOf("2026-10", "2026-09", "2026-05"), o.months.map { it.month })
        assertEquals(791.69, o.months[0].spendUsd!!, 1e-9)
        assertEquals(-6699.81, o.months[1].flow!!.net, 1e-9)
        assertNull(o.months[2].spendUsd)
        assertEquals(798.83, o.months[2].flow!!.net, 1e-9)
        assertEquals(1591.73, o.yearSpendUsd, 1e-9)
    }

    @Test
    fun `flow thang fallback chi khi cua so phu tu mung 1`() {
        val partial = FlowState(
            days = mapOf(
                "2026-09-03" to listOf(FlowTx(1, "USDC", 500.0)),
                "2026-10-01" to listOf(FlowTx(2, "USDC", 20.0)),
            ),
        )
        val o = buildSpendOverview(spending(), partial, "2026-10")!!
        // Sep trong by_month của spending nhưng flow chỉ có từ 3/9 → không bịa flow Sep.
        assertNull(o.months.first { it.month == "2026-09" }.flow)
        assertEquals(20.0, o.currentFlow!!.net, 1e-9)
    }

    @Test
    fun `thang cu khong bi danh dau la thang hien tai`() {
        val o = buildSpendOverview(
            spending(month = "2026-09"),
            null,
            nowMonth = "2026-10",
        )!!
        assertEquals(false, o.isCurrentMonth)
        assertEquals("2026", o.year)
    }

    @Test
    fun `ngay chi tieu va flow chi giu dung thang, moi nhat truoc`() {
        val flow = FlowState(
            byDay = mapOf(
                "2026-10-01" to FlowDay(inUsd = 0.0, out = 10.0),
                "2026-10-15" to FlowDay(inUsd = 5.0, out = 0.0),
                "2026-09-30" to FlowDay(inUsd = 99.0, out = 0.0),
            ),
        )
        val o = buildSpendOverview(spending(), flow, "2026-10")!!
        assertEquals(listOf("2026-10-15", "2026-10-01"), o.days.map { it.day })
        assertEquals(listOf("2026-10-15", "2026-10-01"), o.flowDays.map { it.first })
        assertTrue(o.flowDays.none { it.first.startsWith("2026-09") })
    }
}
