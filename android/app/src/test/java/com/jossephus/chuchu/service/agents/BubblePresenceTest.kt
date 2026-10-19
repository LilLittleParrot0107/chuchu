package com.jossephus.chuchu.service.agents

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sổ cửa sổ bubble là thứ quyết định notification có được xoá hay không, nên nó
 * phải chịu được vòng đời thật của activity: mở lại sau config change (cùng token,
 * không thành "cửa sổ thứ hai") và hai instance cùng pane (`documentLaunchMode=always`
 * tạo task mới mỗi lần launch — instance này chết không được kéo neo của instance kia).
 */
class BubblePresenceTest {

    @After
    fun tearDown() {
        BubblePresence.reset()
    }

    @Test
    fun `cua so mo thi pane duoc giu lai`() {
        BubblePresence.opened("w1:p1", "t1")
        assertTrue(BubblePresence.isOpen("w1:p1"))
    }

    @Test
    fun `dong cua so thi thoi giu`() {
        BubblePresence.opened("w1:p1", "t1")
        BubblePresence.closed("w1:p1", "t1")
        assertFalse(BubblePresence.isOpen("w1:p1"))
    }

    @Test
    fun `mo lai sau config change khong lam mat neo`() {
        val token = "t1"
        BubblePresence.opened("w1:p1", token)
        // Config change: onDestroy cũ KHÔNG báo đóng, activity mới onCreate lại với
        // ĐÚNG token đó (token nằm trong ViewModel, sống qua config change).
        BubblePresence.opened("w1:p1", token)
        assertTrue(BubblePresence.isOpen("w1:p1"))
        BubblePresence.closed("w1:p1", token)
        assertFalse(BubblePresence.isOpen("w1:p1"))
    }

    @Test
    fun `hai instance cung pane thi instance chet khong keo theo cua so kia`() {
        BubblePresence.opened("w1:p1", "t1")
        BubblePresence.opened("w1:p1", "t2")
        BubblePresence.closed("w1:p1", "t1")
        // Cửa sổ của t2 vẫn đang mở → notification neo của nó phải được giữ.
        assertTrue(BubblePresence.isOpen("w1:p1"))
        BubblePresence.closed("w1:p1", "t2")
        assertFalse(BubblePresence.isOpen("w1:p1"))
    }

    @Test
    fun `pane khac khong anh huong nhau`() {
        BubblePresence.opened("w1:p1", "t1")
        BubblePresence.opened("w2:p2", "t2")
        BubblePresence.closed("w1:p1", "t1")
        assertFalse(BubblePresence.isOpen("w1:p1"))
        assertTrue(BubblePresence.isOpen("w2:p2"))
    }

    @Test
    fun `pane rong khong duoc ghi vao so`() {
        BubblePresence.opened("", "t1")
        BubblePresence.opened("   ", "t1")
        assertFalse(BubblePresence.isOpen(""))
        assertFalse(BubblePresence.isOpen("   "))
    }

    @Test
    fun `visible chi bat khi cua so dang hien`() {
        BubblePresence.opened("w1:p1", "t1")
        assertFalse(BubblePresence.isVisible("w1:p1"))
        BubblePresence.shown("w1:p1", "t1")
        assertTrue(BubblePresence.isVisible("w1:p1"))
        BubblePresence.hidden("w1:p1", "t1")
        assertFalse(BubblePresence.isVisible("w1:p1"))
        // Thu gọn thành đầu bubble: vẫn mở (neo phải sống) nhưng không còn hiện.
        assertTrue(BubblePresence.isOpen("w1:p1"))
    }

    @Test
    fun `dong cua so thi xoa luon trang thai hien`() {
        BubblePresence.opened("w1:p1", "t1")
        BubblePresence.shown("w1:p1", "t1")
        BubblePresence.closed("w1:p1", "t1")
        assertFalse(BubblePresence.isVisible("w1:p1"))
    }

    @Test
    fun `instance cu dong khong keo mat trang thai hien cua instance con lai`() {
        BubblePresence.opened("w1:p1", "t1")
        BubblePresence.opened("w1:p1", "t2")
        BubblePresence.shown("w1:p1", "t2")
        BubblePresence.closed("w1:p1", "t1")
        // t2 vẫn đang mở VÀ đang hiện: alert mới của pane này phải im tiếng.
        assertTrue(BubblePresence.isVisible("w1:p1"))
    }
}
