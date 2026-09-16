package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Preview hàng HỘI THOẠI là chữ trơn một dòng (học từ prototype queue-sleek-terminal,
 * user 17/9): markdown thô (** và `) chỉ làm rối mắt trong danh sách.
 */
class QueuePreviewTextTest {

    @Test
    fun `lot dau sao kep va backtick`() {
        assertEquals(
            "Build xong. APK mới: /home/a/vbook.apk",
            stripPreviewMarkdown("Build xong. APK mới: **`/home/a/vbook.apk`**"),
        )
    }

    @Test
    fun `giu nguyen cau khong markdown`() {
        assertEquals("RAM 14GB đã dùng 13GB", stripPreviewMarkdown("RAM 14GB đã dùng 13GB"))
    }

    @Test
    fun `dong xuong gop thanh mot dong`() {
        assertEquals("A soi xong, apk build ok", stripPreviewMarkdown("A soi xong,\napk build ok"))
    }

    @Test
    fun `text rong van rong`() {
        assertEquals("", stripPreviewMarkdown(""))
    }
}
