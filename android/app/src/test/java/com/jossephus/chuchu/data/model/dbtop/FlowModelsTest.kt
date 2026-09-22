package com.jossephus.chuchu.data.model.dbtop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** flow.json (22/9) + ghép với spending.json thành hàng bảng BY DAY. */
class FlowModelsTest {

    /** Dạng thật flow-scan xuất ngày 22/9 (rút gọn). */
    private val flowJson = """{"ts":1790070538,"wallet":"DXJp…","tokens":["USDC","USDT"],"month":"2026-09",
        "month_in":9627.52,"month_out":10323.54,"month_net":-696.03,"month_count":55,
        "by_day":{"2026-09-19":{"in":2000.0,"out":2005.0},"2026-09-21":{"in":2016.36,"out":0.0}},
        "not_counted_month":{"spending":800.04,"swap":5615.49},"backfill":{"from_ts":1783609703,"done":false}}"""

    @Test
    fun `doc duoc flow json ke ca khoa in la tu khoa`() {
        val f = DbtopJson.decodeFromString(FlowState.serializer(), flowJson)
        assertEquals("2026-09", f.month)
        assertEquals(-696.03, f.monthNet, 1e-9)
        assertEquals(55, f.monthCount)
        assertEquals(2016.36, f.byDay.getValue("2026-09-21").inUsd, 1e-9)
        assertEquals(2005.0, f.byDay.getValue("2026-09-19").out, 1e-9)
    }

    @Test
    fun `hang BY DAY gop chi tieu va flow, moi nhat truoc, thieu thi 0`() {
        val spending = SpendingState(month = "2026-09", byDay = mapOf("2026-09-20" to 300.07, "2026-09-04" to 499.97, "2026-08-29" to 100.0))
        val flow = DbtopJson.decodeFromString(FlowState.serializer(), flowJson)
        val rows = dayFlowRows(spending, flow)
        assertEquals(listOf("2026-09-21", "2026-09-20", "2026-09-19", "2026-09-04"), rows.map { it.day })
        assertEquals(DayFlowRow("2026-09-21", 0.0, 2016.36, 0.0), rows[0])
        assertEquals(DayFlowRow("2026-09-20", 300.07, 0.0, 0.0), rows[1])
        assertTrue(rows.none { it.day.startsWith("2026-08") })
    }

    @Test
    fun `chi tiet ngay gop flow va spending, moi nhat truoc, so luon duong`() {
        val flow = DbtopJson.decodeFromString(FlowState.serializer(), """{"month":"2026-09","days":{
            "2026-09-20":[{"ts":1789916314,"token":"USDC","amount":359.0},{"ts":1789885000,"token":"USDC","amount":-240.0}]}}""")
        val spending = SpendingState(month = "2026-09", days = mapOf("2026-09-20" to listOf(SpendingEntry(ts = 1789871879, token = "USDT", amount = 300.07, usd = 300.07))))
        val rows = dayTransactions("2026-09-20", spending, flow)
        assertEquals(listOf("in", "out", "spend"), rows.map { it.kind })
        assertEquals(listOf(359.0, 240.0, 300.07), rows.map { it.usd })
        assertEquals("USDT", rows[2].token)
        assertTrue(dayTransactions("2026-09-19", spending, flow).isEmpty())
        assertEquals(listOf("spend"), dayTransactions("2026-09-20", spending, null).map { it.kind })
    }

    @Test
    fun `flow lech thang thi khong tron vao`() {
        val spending = SpendingState(month = "2026-10", byDay = mapOf("2026-10-01" to 12.0))
        val flow = DbtopJson.decodeFromString(FlowState.serializer(), flowJson)   // tháng 9
        val rows = dayFlowRows(spending, flow)
        assertEquals(listOf(DayFlowRow("2026-10-01", 12.0, 0.0, 0.0)), rows)
        assertEquals(rows, dayFlowRows(spending, null))
    }
}
