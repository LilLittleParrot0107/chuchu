package com.jossephus.chuchu.data.model.dbtop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** flow.json (22/9): đọc + lệnh theo ngày cho tấm chi tiết FLOW. */
class FlowModelsTest {

    /** Dạng thật flow-scan xuất ngày 18/10 (rút gọn). */
    private val flowJson = """{"ts":1792328227,"wallet":"DXJp…","tokens":["USDC","USDT","USDG"],"month":"2026-09",
        "month_in":8719.42,"month_out":15419.23,"month_net":-6699.81,"month_count":116,
        "by_day":{"2026-09-19":{"in":0.0,"out":2005.0},"2026-09-20":{"in":2875.18,"out":983.0}},
        "days":{"2026-09-20":[{"ts":1789916314,"token":"USDC","amount":359.0},{"ts":1789885000,"token":"USDC","amount":-240.0},{"ts":1789916213,"token":"USDG","amount":269.25}]},
        "by_month":{"2026-09":{"in":8719.42,"out":15419.23,"net":-6699.81,"count":116}},
        "not_counted_month":{"spending":800.04,"protocol":278555.35,"swap":0.0},"backfill":{"from_ts":1769176193,"done":false}}"""

    @Test
    fun `doc duoc flow json ke ca khoa in la tu khoa`() {
        val f = DbtopJson.decodeFromString(FlowState.serializer(), flowJson)
        assertEquals("2026-09", f.month)
        assertEquals(-6699.81, f.monthNet, 1e-9)
        assertEquals(116, f.monthCount)
        assertEquals(2875.18, f.byDay.getValue("2026-09-20").inUsd, 1e-9)
        assertEquals(2005.0, f.byDay.getValue("2026-09-19").out, 1e-9)
        assertEquals(3, f.days.getValue("2026-09-20").size)
        // by_month (18/10) — tổng đủ tháng, không phụ thuộc cửa sổ days.
        assertEquals(-6699.81, f.byMonth.getValue("2026-09").net, 1e-9)
        assertEquals(8719.42, f.byMonth.getValue("2026-09").inUsd, 1e-9)
        assertEquals(116, f.byMonth.getValue("2026-09").count)
    }

    @Test
    fun `flow thang uu tien by_month du days chi co vai ngay`() {
        val f = DbtopJson.decodeFromString(FlowState.serializer(), flowJson)
        val sep = flowMonthTotals(f, "2026").getValue("2026-09")
        assertEquals(8719.42, sep.inUsd, 1e-9)
        assertEquals(15419.23, sep.out, 1e-9)
        assertEquals(-6699.81, sep.net, 1e-9)
        assertEquals(116, sep.count)
    }

    @Test
    fun `flow thang tu choi fallback khi cua so days bat dau giua thang`() {
        val f = FlowState(
            days = mapOf(
                "2026-09-03" to listOf(FlowTx(1, "USDC", 500.0), FlowTx(2, "USDC", -100.0)),
                "2026-09-20" to listOf(FlowTx(3, "USDC", 1000.0)),
            ),
        )
        // Ngày đầu cửa sổ là 3/9 → tháng 9 thiếu ngày 1–2, không được coi là đủ tháng.
        assertTrue(flowMonthTotals(f, "2026").isEmpty())
    }

    @Test
    fun `flow thang fallback days khi cua so phu tu mung 1`() {
        val f = FlowState(
            days = mapOf(
                "2026-09-01" to listOf(FlowTx(1, "USDC", 500.0), FlowTx(2, "USDC", -100.0)),
                "2026-09-20" to listOf(FlowTx(3, "USDC", 1000.0)),
            ),
        )
        val sep = flowMonthTotals(f, "2026").getValue("2026-09")
        assertEquals(1500.0, sep.inUsd, 1e-9)
        assertEquals(100.0, sep.out, 1e-9)
        assertEquals(1400.0, sep.net, 1e-9)
        assertEquals(3, sep.count)
    }

    @Test
    fun `flow thang fallback by_day khi flow json doi dau chua co days`() {
        val f = FlowState(
            byDay = mapOf(
                "2026-09-01" to FlowDay(inUsd = 50.0, out = 0.0),
                "2026-09-02" to FlowDay(inUsd = 0.0, out = 20.0),
            ),
        )
        val sep = flowMonthTotals(f, "2026").getValue("2026-09")
        assertEquals(50.0, sep.inUsd, 1e-9)
        assertEquals(20.0, sep.out, 1e-9)
        assertEquals(30.0, sep.net, 1e-9)
        assertEquals(0, sep.count)
    }

    @Test
    fun `lenh trong ngay moi nhat truoc, giu dau, thieu ngay thi rong`() {
        val f = DbtopJson.decodeFromString(FlowState.serializer(), flowJson)
        val rows = flowDayRows("2026-09-20", f)
        assertEquals(listOf(359.0, 269.25, -240.0), rows.map { it.usd })
        assertEquals(listOf("USDC", "USDG", "USDC"), rows.map { it.token })
        assertTrue(flowDayRows("2026-09-19", f).isEmpty())
        assertTrue(flowDayRows("2026-09-20", null).isEmpty())
    }
}
