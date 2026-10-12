package com.jossephus.chuchu.ui.bubble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Luật cửa sổ vào BubbleShell (12/10): đa nhiệm + thấp = bubble; cửa sổ rất thấp
 * luôn bubble dù không đa nhiệm. Tách khỏi Compose để kiểm được bằng JVM thuần.
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
    fun `cua so rat thap luon la bubble`() {
        assertTrue(bubbleWindowEligible(screenHeightDp = 460, inMultiWindow = false))
    }

    @Test
    fun `toan man binh thuong thi khong`() {
        assertFalse(bubbleWindowEligible(screenHeightDp = 780, inMultiWindow = false))
    }
}
