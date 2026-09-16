package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Luật back của màn Queue. Bản 1.61.8 dùng hai BackHandler bật/tắt so le và hở
 * một khe: ngay sau khi back đóng chat, cả hai đều tắt trong lúc chờ recompose
 * → cú back dội xuyên ra NavController, pop thẳng về home (user báo 17/9).
 * Từ đây luật là hàm thuần, một handler luôn bật chỉ việc hỏi hàm này.
 */
class QueueBackPolicyTest {

    @Test
    fun `chat dang mo thi back dong chat`() {
        assertEquals(QueueBackAction.CloseChat, queueBackAction(chatOpen = true, nowMs = 1_000, swallowUntilMs = 0))
        // Kể cả khi đang trong cửa sổ nuốt của lần trước: mở chat rồi back vẫn phải đóng chat.
        assertEquals(QueueBackAction.CloseChat, queueBackAction(chatOpen = true, nowMs = 1_000, swallowUntilMs = 9_999))
    }

    @Test
    fun `cu back doi ngay sau khi dong chat bi nuot`() {
        assertEquals(QueueBackAction.Swallow, queueBackAction(chatOpen = false, nowMs = 1_000, swallowUntilMs = 1_450))
        // Mép trên: đúng mốc hết hạn là đã nhường (now < until mới nuốt).
        assertEquals(QueueBackAction.Leave, queueBackAction(chatOpen = false, nowMs = 1_450, swallowUntilMs = 1_450))
        assertEquals(QueueBackAction.Leave, queueBackAction(chatOpen = false, nowMs = 1_451, swallowUntilMs = 1_450))
    }

    @Test
    fun `back thuong khi chua tung dong chat thi nhuong cho nav`() {
        assertEquals(QueueBackAction.Leave, queueBackAction(chatOpen = false, nowMs = 1_000, swallowUntilMs = 0))
    }
}
