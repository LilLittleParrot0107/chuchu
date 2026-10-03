package com.jossephus.chuchu.ui.screens.Queue

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Khoá semantics "yêu cầu về HỘI THOẠI" — sự kiện một lần (thay tick Int, bài học 3/10):
 *  - request trước khi có collector vẫn tới (bấm tab từ màn khác);
 *  - double-tap conflate còn một;
 *  - sau khi tiêu thụ, collect lại (remount/xoay màn) KHÔNG nhận gì.
 */
class QueueHomeRequestsTest {
    @Test
    fun `request truoc collect van toi dung mot lan`() = runBlocking {
        val h = QueueHomeRequests()
        h.request()
        assertEquals(Unit, withTimeoutOrNull(1_000) { h.events.first() })
    }

    @Test
    fun `double request conflate con mot`() = runBlocking {
        val h = QueueHomeRequests()
        h.request()
        h.request()
        assertEquals(Unit, withTimeoutOrNull(1_000) { h.events.first() })
        assertNull(withTimeoutOrNull(150) { h.events.first() })
    }

    @Test
    fun `khong replay sau khi tieu thu`() = runBlocking {
        val h = QueueHomeRequests()
        h.request()
        assertEquals(Unit, withTimeoutOrNull(1_000) { h.events.first() })
        assertNull(withTimeoutOrNull(150) { h.events.first() })
    }

    @Test
    fun `request sau do collect nhan duoc`() = runBlocking {
        val h = QueueHomeRequests()
        assertNull(withTimeoutOrNull(150) { h.events.first() })
        h.request()
        assertEquals(Unit, withTimeoutOrNull(1_000) { h.events.first() })
    }
}
