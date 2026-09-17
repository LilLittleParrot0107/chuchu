package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.assertEquals
import org.junit.Test

/** Luật dọn "response thừa" tầng đọc (17/9): gộp lượt, ẩn think, dedup chip pending. */
class QueueTranscriptRulesTest {
    private fun msg(role: String, off: Long, text: String = "t", ts: String = "2026-09-17T00:00:0${off}.000Z") =
        ChatMessage(role = role, uuid = "u$off", ts = ts, text = text, offset = off)

    private fun feed(pane: String, off: Long, role: String = "assistant", text: String = "t") =
        FeedMessage(pane = pane, name = pane, agent = null, label = "working", tone = QueueTone.Dim,
            role = role, ts = "2026-09-17T00:00:0$off.000Z", text = text, uuid = "u$off", offset = off)

    private fun task(id: Int, target: String, text: String, state: String) =
        QueueTask(id = id, target = target, text = text, state = state, glyph = "•",
            tone = QueueTone.Dim, stateLabel = state, sub = "", actions = emptyList())

    @Test
    fun mergesConsecutiveAssistantIntoOneBubble() {
        val out = collapseAssistantTurns(
            listOf(msg("user", 1, "làm gì đó"), msg("assistant", 2, "để em xem"), msg("assistant", 3, "đã rõ"), msg("assistant", 4, "xong."))
        )
        assertEquals(listOf("user", "assistant"), out.map { it.role })
        val bubble = out[1]
        assertEquals("xong.", bubble.text)
        assertEquals(listOf("để em xem", "đã rõ"), bubble.paras)
        // key = tin ĐẦU của lượt: bubble không đổi chỗ khi lượt dài thêm giữa hai lần poll
        assertEquals("2:0", bubble.key)
        assertEquals("2026-09-17T00:00:04.000Z", bubble.ts)
    }

    @Test
    fun userAndToolBreakTheRun() {
        val withUser = collapseAssistantTurns(listOf(msg("assistant", 1), msg("user", 2), msg("assistant", 3)))
        assertEquals(3, withUser.size)
        val withTool = collapseAssistantTurns(listOf(msg("assistant", 1), msg("tool", 2, ""), msg("assistant", 3)))
        assertEquals(listOf("assistant", "tool", "assistant"), withTool.map { it.role })
    }

    @Test
    fun thinkIsHiddenAndBridgesTheRun() {
        val out = collapseAssistantTurns(listOf(msg("assistant", 1, "a"), msg("think", 2, "nghĩ vớ vẩn"), msg("assistant", 3, "b")))
        assertEquals(1, out.size)
        assertEquals("b", out[0].text)
        assertEquals(listOf("a"), out[0].paras)
    }

    @Test
    fun emptyAssistantLinesAreDropped() {
        val out = collapseAssistantTurns(listOf(msg("assistant", 1, "a"), msg("assistant", 2, "  "), msg("assistant", 3, "b")))
        assertEquals(1, out.size)
        assertEquals("b", out[0].text)
        assertEquals(listOf("a"), out[0].paras)
    }

    @Test
    fun feedMergesOnlySamePane() {
        val out = collapseFeedTurns(listOf(feed("pA", 1, "user"), feed("pA", 2), feed("pA", 3), feed("pB", 4), feed("pA", 5)))
        assertEquals(4, out.size) // pB chen giữa cắt lượt: pA(5) đứng bubble riêng
        val merged = out[1]
        assertEquals("pA", merged.pane)
        assertEquals(listOf("t"), merged.paras) // gộp 2 đoạn pA liên tiếp, giữ key tin đầu
        assertEquals("pB", out[2].pane)
        assertEquals(5L, out[3].offset)
    }

    @Test
    fun pendingChipsHideOnceEchoedInTranscript() {
        val messages = listOf(msg("user", 1, "  sửa báo cáo  "))
        val tasks = listOf(
            task(10, "wH:p1", "sửa báo cáo", "sent"),      // đã vào transcript → ẩn
            task(11, "wH:p1", "việc khác", "pending"),     // còn xếp hàng → hiện chip
            task(12, "wH:p1", "done rồi", "done"),         // hoàn thành → không phải chip
            task(13, "wH:p2", "lẫn pane", "pending"),      // (QueueScreen đã lọc theo pane trước)
        )
        val chips = pendingChipTasks(tasks, messages)
        assertEquals(listOf(11, 13), chips.map { it.id })
    }
}
