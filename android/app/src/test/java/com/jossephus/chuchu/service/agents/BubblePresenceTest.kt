package com.jossephus.chuchu.service.agents

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sổ cửa sổ bubble là thứ quyết định notification có được xoá hay không, nên nó
 * phải chịu được vòng đời thật của activity: mở lại sau config change (onCreate
 * chạy lần hai mà onDestroy cũ bị bỏ qua) và pane rỗng.
 */
class BubblePresenceTest {

    @After
    fun tearDown() {
        BubblePresence.reset()
    }

    @Test
    fun `cua so mo thi pane duoc giu lai`() {
        BubblePresence.opened("w1:p1")
        assertTrue(BubblePresence.isOpen("w1:p1"))
    }

    @Test
    fun `dong cua so thi thoi giu`() {
        BubblePresence.opened("w1:p1")
        BubblePresence.closed("w1:p1")
        assertFalse(BubblePresence.isOpen("w1:p1"))
    }

    @Test
    fun `mo lai sau config change khong lam mat neo`() {
        BubblePresence.opened("w1:p1")
        // Config change: activity cũ onDestroy bị bỏ qua, activity mới onCreate lại.
        BubblePresence.opened("w1:p1")
        assertTrue(BubblePresence.isOpen("w1:p1"))
        BubblePresence.closed("w1:p1")
        assertFalse(BubblePresence.isOpen("w1:p1"))
    }

    @Test
    fun `pane khac khong anh huong nhau`() {
        BubblePresence.opened("w1:p1")
        BubblePresence.opened("w2:p2")
        BubblePresence.closed("w1:p1")
        assertFalse(BubblePresence.isOpen("w1:p1"))
        assertTrue(BubblePresence.isOpen("w2:p2"))
    }

    @Test
    fun `pane rong khong duoc ghi vao so`() {
        BubblePresence.opened("")
        BubblePresence.opened("   ")
        assertFalse(BubblePresence.isOpen(""))
        assertFalse(BubblePresence.isOpen("   "))
    }

    @Test
    fun `visible chi bat khi cua so dang hien`() {
        BubblePresence.opened("w1:p1")
        assertFalse(BubblePresence.isVisible("w1:p1"))
        BubblePresence.shown("w1:p1")
        assertTrue(BubblePresence.isVisible("w1:p1"))
        BubblePresence.hidden("w1:p1")
        assertFalse(BubblePresence.isVisible("w1:p1"))
        // Thu gọn thành đầu bubble: vẫn mở (neo phải sống) nhưng không còn hiện.
        assertTrue(BubblePresence.isOpen("w1:p1"))
    }

    @Test
    fun `dong cua so thi xoa luon trang thai hien`() {
        BubblePresence.opened("w1:p1")
        BubblePresence.shown("w1:p1")
        BubblePresence.closed("w1:p1")
        assertFalse(BubblePresence.isVisible("w1:p1"))
    }
}
