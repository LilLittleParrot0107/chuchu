package com.jossephus.chuchu.service.agents

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Nhịp trực thích ứng (17/10 — user: "chủ yếu là trễ thông báo"): có phiên đang
 * chạy/bị chặn thì 15s, mọi phiên rảnh thì 60s. Nhãn lạ/"" xem như rảnh.
 */
class WatchIntervalTest {

    private fun snap(pane: String, label: String) =
        AgentSnap(pane = pane, name = "agent-$pane", label = label, preview = "")

    @Test
    fun `dang working thi 15s`() {
        assertEquals(WATCH_INTERVAL_ACTIVE_MS, watchIntervalMs(listOf(snap("w1:p1", "working"))))
    }

    @Test
    fun `blocked busy sent unsure deu tinh la nhip ngan`() {
        for (label in listOf("blocked", "busy", "sent", "unsure")) {
            assertEquals(WATCH_INTERVAL_ACTIVE_MS, watchIntervalMs(listOf(snap("w1:p1", label))))
        }
    }

    @Test
    fun `cai nhap hoa khac nhau van nhan ra`() {
        assertEquals(WATCH_INTERVAL_ACTIVE_MS, watchIntervalMs(listOf(snap("w1:p1", "Working"))))
    }

    @Test
    fun `moi phien ranh thi 60s`() {
        assertEquals(
            WATCH_INTERVAL_IDLE_MS,
            watchIntervalMs(listOf(snap("w1:p1", "idle"), snap("w1:p2", "done"))),
        )
    }

    @Test
    fun `trong hoac nhan la thi 60s`() {
        assertEquals(WATCH_INTERVAL_IDLE_MS, watchIntervalMs(emptyList()))
        assertEquals(WATCH_INTERVAL_IDLE_MS, watchIntervalMs(listOf(snap("w1:p1", "unknown"))))
        assertEquals(WATCH_INTERVAL_IDLE_MS, watchIntervalMs(listOf(snap("w1:p1", ""))))
    }

    @Test
    fun `mot phien risk la du de rut ngan ca chuoi`() {
        assertEquals(
            WATCH_INTERVAL_ACTIVE_MS,
            watchIntervalMs(listOf(snap("w1:p1", "idle"), snap("w1:p2", "working"))),
        )
    }
}
