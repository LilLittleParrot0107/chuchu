package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.assertEquals
import org.junit.Test

/** Luật back của màn Queue (25/9: ba trang HỘI THOẠI ↔ FILES ↔ MACHINE). */
class QueueBackPolicyTest {
    @Test
    fun `chat toan man dong truoc moi thu`() {
        assertEquals(QueueBackAction.CloseChat, queueBackAction(chatOpen = true, tasksOpen = true, mode = QueueMode.Threads))
        assertEquals(QueueBackAction.CloseChat, queueBackAction(chatOpen = true, tasksOpen = false, mode = QueueMode.Files))
    }

    @Test
    fun `bang viec dong sau chat`() {
        assertEquals(QueueBackAction.CloseTasks, queueBackAction(chatOpen = false, tasksOpen = true, mode = QueueMode.Threads))
    }

    @Test
    fun `trang FILES lui ve HOI THOAI`() {
        assertEquals(QueueBackAction.GoToThreads, queueBackAction(chatOpen = false, tasksOpen = false, mode = QueueMode.Files))
    }

    @Test
    fun `trang MACHINE cung lui ve HOI THOAI`() {
        assertEquals(QueueBackAction.GoToThreads, queueBackAction(chatOpen = false, tasksOpen = false, mode = QueueMode.Machine))
    }

    @Test
    fun `o goc HOI THOAI thi nhuong cho nav`() {
        assertEquals(QueueBackAction.Leave, queueBackAction(chatOpen = false, tasksOpen = false, mode = QueueMode.Threads))
    }
}
