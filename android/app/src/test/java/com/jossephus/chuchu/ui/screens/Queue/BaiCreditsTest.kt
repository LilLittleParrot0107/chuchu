package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Số dư B.AI hiện nguyên Credit kèm dấu phẩy nghìn — "14,275,165 cr" (17/9).
 * Không rút gọn kiểu 14.3M: đây là số tiền thật, mỗi lần nạp/tiêu là một lần
 * đối chiếu được.
 */
class BaiCreditsTest {

    @Test
    fun `chen dau phay nghin`() {
        assertEquals("14,275,165", credits(14275165L))
    }

    @Test
    fun `bon chu so tro xuong`() {
        assertEquals("999", credits(999L))
        assertEquals("1,000", credits(1000L))
    }

    @Test
    fun `so 0 khong rong`() {
        assertEquals("0", credits(0L))
    }
}
