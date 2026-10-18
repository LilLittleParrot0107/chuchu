package com.jossephus.chuchu.ui.bubble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Luật cửa sổ vào BubbleShell: chỉ đa nhiệm/freeform + thấp mới là bubble.
 * Full-screen thấp (điện thoại xoay ngang) vẫn phải giữ UI đầy đủ.
 */
class BubbleShellTest {

    @Test
    fun `multi-window thap la bubble`() {
        assertTrue(bubbleWindowEligible(screenHeightDp = 520, inMultiWindow = true))
    }

    @Test
    fun `multi-window du cao thi khong`() {
        assertFalse(bubbleWindowEligible(screenHeightDp = 700, inMultiWindow = true))
    }

    @Test
    fun `dien thoai xoay ngang full-screen khong phai bubble`() {
        assertFalse(bubbleWindowEligible(screenHeightDp = 460, inMultiWindow = false))
        assertFalse(bubbleWindowEligible(screenHeightDp = 360, inMultiWindow = false))
    }

    @Test
    fun `toan man binh thuong thi khong`() {
        assertFalse(bubbleWindowEligible(screenHeightDp = 780, inMultiWindow = false))
    }
}
