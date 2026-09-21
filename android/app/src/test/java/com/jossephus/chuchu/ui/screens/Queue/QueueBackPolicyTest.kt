package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Luật back của màn Queue bóc từng lớp: chat → bảng VIỆC → về DÒNG THỜI GIAN → nhường nav.
 * Dialog (task/CFG) là cửa sổ riêng tự ăn back nên không có trong luật.
 */
class QueueBackPolicyTest {

    @Test
    fun `chat dang mo thi back dong chat truoc moi thu`() {
        assertEquals(QueueBackAction.CloseChat, queueBackAction(chatOpen = true, tasksOpen = false, mode = QueueMode.Threads))
        assertEquals(QueueBackAction.CloseChat, queueBackAction(chatOpen = true, tasksOpen = true, mode = QueueMode.Timeline))
    }

    @Test
    fun `bang VIEC mo thi back dong bang, khong thoat app du dang o goc`() {
        assertEquals(QueueBackAction.CloseTasks, queueBackAction(chatOpen = false, tasksOpen = true, mode = QueueMode.Timeline))
        assertEquals(QueueBackAction.CloseTasks, queueBackAction(chatOpen = false, tasksOpen = true, mode = QueueMode.Threads))
    }

    @Test
    fun `o HOI THOAI sach lop thi back ve DONG THOI GIAN`() {
        assertEquals(QueueBackAction.GoToTimeline, queueBackAction(chatOpen = false, tasksOpen = false, mode = QueueMode.Threads))
    }

    @Test
    fun `chi o goc DONG THOI GIAN sach lop moi nhuong cho nav`() {
        assertEquals(QueueBackAction.Leave, queueBackAction(chatOpen = false, tasksOpen = false, mode = QueueMode.Timeline))
    }
}
