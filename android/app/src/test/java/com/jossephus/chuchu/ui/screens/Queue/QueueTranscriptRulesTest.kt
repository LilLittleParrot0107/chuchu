package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.assertEquals
import org.junit.Test

/** Luật dọn "response thừa" tầng đọc (17/9): gộp lượt, ẩn think. */
class QueueTranscriptRulesTest {
    private fun msg(role: String, off: Long, text: String = "t", ts: String = "2026-09-17T00:00:0${off}.000Z") =
        ChatMessage(role = role, uuid = "u$off", ts = ts, text = text, offset = off)


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


    /** Mở chat neo vào tin anh gửi cuối cùng (25/9), không phải đáy tuyệt đối. */
    @Test
    fun lastUserIndexFindsTheUsersLatestMessage() {
        val msgs = listOf(msg("user", 1, "a"), msg("assistant", 2), msg("user", 3, "b"), msg("assistant", 4))
        // Gộp lượt assistant không làm đổi chỗ tin user → neo đọc trên danh sách đã gộp vẫn đúng.
        val collapsed = collapseAssistantTurns(msgs)
        assertEquals(2, lastUserMessageIndex(collapsed))
        assertEquals("b", collapsed[lastUserMessageIndex(collapsed)].text)
        // Chưa có tin của anh / rỗng → −1, caller giữ nhảy đáy.
        assertEquals(-1, lastUserMessageIndex(emptyList()))
        assertEquals(-1, lastUserMessageIndex(listOf(msg("assistant", 1))))
    }
}
