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
        assertEquals(AlertExit.Cancel, alertExit(bubbleOpen = false, hasSnapshot = true))
        assertEquals(AlertExit.Cancel, alertExit(bubbleOpen = false, hasSnapshot = false))
    }

    @Test
    fun `dang mo bubble thi giu notification lam neo`() {
        assertEquals(AlertExit.Anchor, alertExit(bubbleOpen = true, hasSnapshot = true))
    }

    @Test
    fun `mo bubble ma mat noi dung thi de nguyen`() {
        // Process vừa restart: không dựng lại được notification, nhưng vẫn tuyệt đối
        // không được xoá — xoá là cửa sổ đang mở bị dỡ.
        assertEquals(AlertExit.Leave, alertExit(bubbleOpen = true, hasSnapshot = false))
    }
}
