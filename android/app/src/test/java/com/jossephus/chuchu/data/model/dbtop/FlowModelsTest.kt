package com.jossephus.chuchu.data.model.dbtop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** flow.json (22/9): đọc + lệnh theo ngày cho tấm chi tiết FLOW. */
class FlowModelsTest {

    /** Dạng thật flow-scan xuất ngày 22/9 (rút gọn). */
    private val flowJson = """{"ts":1790070538,"wallet":"DXJp…","tokens":["USDC","USDT","USDG"],"month":"2026-09",
        "month_in":8719.42,"month_out":9434.55,"month_net":-715.13,"month_count":98,
        "by_day":{"2026-09-19":{"in":0.0,"out":2005.0},"2026-09-20":{"in":2875.18,"out":983.0}},
        "days":{"2026-09-20":[{"ts":1789916314,"token":"USDC","amount":359.0},{"ts":1789885000,"token":"USDC","amount":-240.0},{"ts":1789916213,"token":"USDG","amount":269.25}]},
        "not_counted_month":{"spending":800.04,"protocol":265525.2,"swap":0.0},"backfill":{"from_ts":1769176193,"done":false}}"""

    @Test
    fun `doc duoc flow json ke ca khoa in la tu khoa`() {
        val f = DbtopJson.decodeFromString(FlowState.serializer(), flowJson)
        assertEquals("2026-09", f.month)
        assertEquals(-715.13, f.monthNet, 1e-9)
        assertEquals(98, f.monthCount)
        assertEquals(2875.18, f.byDay.getValue("2026-09-20").inUsd, 1e-9)
        assertEquals(2005.0, f.byDay.getValue("2026-09-19").out, 1e-9)
        assertEquals(3, f.days.getValue("2026-09-20").size)
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
