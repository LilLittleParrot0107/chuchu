package com.jossephus.chuchu.service.agents

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Luật "alert hết hiệu lực thì notification đi đâu". Sai luật này là bubble user
 * đang mở bị dỡ ngay trước mặt — đúng lúc họ vừa gửi câu trả lời (Blocked→Working).
 */
class AlertExitTest {

    @Test
    fun `khong co cua so bubble thi xoa han`() {
        assertEquals(
            AlertExit.Cancel,
            alertExit(bubbleOpen = false, notificationLive = true, hasSnapshot = true),
        )
        assertEquals(
            AlertExit.Cancel,
            alertExit(bubbleOpen = false, notificationLive = true, hasSnapshot = false),
        )
    }

    @Test
    fun `dang mo bubble thi giu notification lam neo`() {
        assertEquals(
            AlertExit.Anchor,
            alertExit(bubbleOpen = true, notificationLive = true, hasSnapshot = true),
        )
    }

    @Test
    fun `mo bubble ma mat noi dung thi de nguyen`() {
        // Process vừa restart: không dựng lại được notification, nhưng vẫn tuyệt đối
        // không được xoá — xoá là cửa sổ đang mở bị dỡ.
        assertEquals(
            AlertExit.Leave,
            alertExit(bubbleOpen = true, notificationLive = true, hasSnapshot = false),
        )
    }

    @Test
    fun `user da vuot dau bubble thi khong hoi sinh notification`() {
        // Cửa sổ còn sống nhưng notification đã biến mất khỏi shade: SystemUI đã dỡ
        // bubble theo nó. Dựng lại neo lúc này là hồi sinh thứ user vừa dọn.
        assertEquals(
            AlertExit.Cancel,
            alertExit(bubbleOpen = true, notificationLive = false, hasSnapshot = true),
        )
        assertEquals(
            AlertExit.Cancel,
            alertExit(bubbleOpen = true, notificationLive = false, hasSnapshot = false),
        )
    }
}
