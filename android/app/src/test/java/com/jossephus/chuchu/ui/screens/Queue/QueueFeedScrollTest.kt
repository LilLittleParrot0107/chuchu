package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vị trí cuộn của dòng thời gian khi quay lại màn (user báo 17/9: "quay đi quay
 * lại thì phải dừng ở chỗ gần nhất t scroll, không thì phải là điểm mới nhất").
 * Neo theo KEY vì feed cắt tin cũ ở đầu làm index trôi.
 */
class QueueFeedScrollTest {

    private val keys = listOf("a", "b", "c", "d")

    @Test
    fun `co neo thi dung dung tin cuoi da thay`() {
        assertEquals(2, feedRestoreIndex(keys, pinned = false, anchorKey = "c"))
        assertEquals(0, feedRestoreIndex(keys, pinned = false, anchorKey = "a"))
    }

    @Test
    fun `dang o day thi ve tin moi nhat`() {
        assertEquals(keys.size, feedRestoreIndex(keys, pinned = true, anchorKey = "b"))
    }

    @Test
    fun `chua tung cuon thi ve tin moi nhat`() {
        assertEquals(keys.size, feedRestoreIndex(keys, pinned = false, anchorKey = null))
    }

    @Test
    fun `neo roi khoi cua so feed thi ve tin moi nhat`() {
        assertEquals(keys.size, feedRestoreIndex(keys, pinned = false, anchorKey = "x"))
    }

    @Test
    fun `feed rong thi khong cuon dau`() {
        assertEquals(0, feedRestoreIndex(emptyList(), pinned = false, anchorKey = "a"))
    }
}
