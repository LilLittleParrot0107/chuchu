package com.jossephus.chuchu.service.agents

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Nhịp alarm từ 18/10 ("tiết kiệm pin như Collie"): alarm chỉ là lưới dự phòng.
 * Guard long-poll còn sống → 5 phút/lần, bất kể trạng thái (long-poll đã lo tức thì).
 * Guard chết (process bị dọn) → 2 phút nếu có agent bận, 5 phút nếu rảnh.
 */
class WatchIntervalTest {

    private fun snap(pane: String, label: String) =
        AgentSnap(pane = pane, name = "agent-$pane", label = label, preview = "")

    @Test
    fun `guard con song thi alarm 5 phut ke ca khi ban`() {
        assertEquals(
            WATCH_ALARM_MS,
            alarmIntervalMs(listOf(snap("w1:p1", "working")), guardAlive = true),
        )
        assertEquals(
            WATCH_ALARM_MS,
            alarmIntervalMs(listOf(snap("w1:p1", "blocked")), guardAlive = true),
        )
    }

    @Test
    fun `guard chet va co agent ban thi 2 phut`() {
        // Nhãn thật qsrv gửi: blocked → "needs approval", unsure → "unknown".
        for (label in listOf("working", "blocked", "busy", "sent", "unsure", "needs approval", "unknown")) {
            assertEquals(
                "label=$label",
                WATCH_ALARM_ACTIVE_MS,
                alarmIntervalMs(listOf(snap("w1:p1", label)), guardAlive = false),
            )
        }
    }

    @Test
    fun `guard chet nhung ranh thi 5 phut`() {
        assertEquals(
            WATCH_ALARM_MS,
            alarmIntervalMs(listOf(snap("w1:p1", "idle"), snap("w1:p2", "done")), guardAlive = false),
        )
    }

    @Test
    fun `nhan nhap hoa khac nhau van nhan ra`() {
        assertEquals(
            WATCH_ALARM_ACTIVE_MS,
            alarmIntervalMs(listOf(snap("w1:p1", "Working")), guardAlive = false),
        )
    }

    @Test
    fun `trong hoac nhan la that su thi 5 phut`() {
        assertEquals(WATCH_ALARM_MS, alarmIntervalMs(emptyList(), guardAlive = false))
        assertEquals(WATCH_ALARM_MS, alarmIntervalMs(listOf(snap("w1:p1", "idle")), guardAlive = false))
        assertEquals(WATCH_ALARM_MS, alarmIntervalMs(listOf(snap("w1:p1", "")), guardAlive = false))
    }

    @Test
    fun `mot phien ban la du de rut ngan chuoi khi guard chet`() {
        assertEquals(
            WATCH_ALARM_ACTIVE_MS,
            alarmIntervalMs(listOf(snap("w1:p1", "idle"), snap("w1:p2", "working")), guardAlive = false),
        )
    }
}
