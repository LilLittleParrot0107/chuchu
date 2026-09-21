package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Kiểm thử chính sách back phân cấp của màn Queue:
 * Cấp 1: Nuốt cú dội debounce trong cửa sổ swallowUntilMs
 * Cấp 2: Task detail dialog -> DismissTaskDetail
 * Cấp 3: Config dialog -> DismissConfig
 * Cấp 4: Chat full-screen -> CloseChat
 * Cấp 5: Tasks overlay -> CloseTasks
 * Cấp 6: Trang Hội thoại (page 1) -> GoToTimeline (về page 0 Timeline, không thoát app)
 * Cấp 7: Gốc Dòng thời gian (page 0) -> Leave (nhường cho nav / onExitApp / popBackStack)
 */
class QueueBackPolicyTest {

    @Test
    fun `chat dang mo thi back dong chat`() {
        assertEquals(
            QueueBackAction.CloseChat,
            queueBackAction(chatOpen = true, nowMs = 1_000, swallowUntilMs = 0),
        )
        // Kể cả khi đang trong cửa sổ nuốt của lần trước: mở chat rồi back vẫn phải đóng chat.
        assertEquals(
            QueueBackAction.CloseChat,
            queueBackAction(chatOpen = true, nowMs = 1_000, swallowUntilMs = 9_999),
        )
    }

    @Test
    fun `cu back doi trong debounce window bi nuot`() {
        assertEquals(
            QueueBackAction.Swallow,
            queueBackAction(chatOpen = false, nowMs = 1_000, swallowUntilMs = 1_300),
        )
        // Đúng mốc hết hạn là hết nuốt (now < until mới nuốt).
        assertEquals(
            QueueBackAction.Leave,
            queueBackAction(chatOpen = false, isAtRootPage = true, nowMs = 1_300, swallowUntilMs = 1_300),
        )
        assertEquals(
            QueueBackAction.Leave,
            queueBackAction(chatOpen = false, isAtRootPage = true, nowMs = 1_301, swallowUntilMs = 1_300),
        )
    }

    @Test
    fun `khi mo dialog xem chi tiet task thi back dong dialog`() {
        assertEquals(
            QueueBackAction.DismissTaskDetail,
            queueBackAction(chatOpen = false, inspectedTask = true, isAtRootPage = false),
        )
    }

    @Test
    fun `khi mo dialog cau hinh thi back dong cau hinh`() {
        assertEquals(
            QueueBackAction.DismissConfig,
            queueBackAction(chatOpen = false, configOpen = true, isAtRootPage = false),
        )
    }

    @Test
    fun `khi mo bang TASKS thi back dong bang TASKS chu khong thoat app`() {
        // Đang ở Hội thoại mở TASKS
        assertEquals(
            QueueBackAction.CloseTasks,
            queueBackAction(chatOpen = false, tasksOpen = true, isAtRootPage = false),
        )
        // Đang ở Timeline mở TASKS
        assertEquals(
            QueueBackAction.CloseTasks,
            queueBackAction(chatOpen = false, tasksOpen = true, isAtRootPage = true),
        )
    }

    @Test
    fun `khi o Hoi thoai khong co overlay thi back ve Dong thoi gian chu khong thoat app`() {
        assertEquals(
            QueueBackAction.GoToTimeline,
            queueBackAction(chatOpen = false, tasksOpen = false, isAtRootPage = false),
        )
    }

    @Test
    fun `chi khi o goc Dong thoi gian va het overlay moi Leave`() {
        assertEquals(
            QueueBackAction.Leave,
            queueBackAction(
                chatOpen = false,
                tasksOpen = false,
                configOpen = false,
                inspectedTask = false,
                isAtRootPage = true,
                nowMs = 1_000,
                swallowUntilMs = 0,
            ),
        )
    }

    @Test
    fun `do uu tien cao hon duoc xu ly truoc`() {
        // Chat ưu tiên hơn TASKS
        assertEquals(
            QueueBackAction.CloseChat,
            queueBackAction(chatOpen = true, tasksOpen = true, isAtRootPage = false),
        )
        // Task detail ưu tiên hơn Chat
        assertEquals(
            QueueBackAction.DismissTaskDetail,
            queueBackAction(chatOpen = true, inspectedTask = true, isAtRootPage = false),
        )
        // Config ưu tiên hơn Chat
        assertEquals(
            QueueBackAction.DismissConfig,
            queueBackAction(chatOpen = true, configOpen = true, isAtRootPage = false),
        )
        // TASKS ưu tiên hơn GoToTimeline
        assertEquals(
            QueueBackAction.CloseTasks,
            queueBackAction(chatOpen = false, tasksOpen = true, isAtRootPage = false),
        )
    }
}
