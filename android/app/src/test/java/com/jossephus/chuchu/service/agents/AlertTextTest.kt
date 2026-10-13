package com.jossephus.chuchu.service.agents

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Làm gọn tin cuối cho notification (13/10): markdown/quote/bullet rụng, khoảng trắng
 * dồn 1, cắt ở RANH GIỚI TỪ — không cắt giữa chữ.
 */
class AlertTextTest {

    @Test
    fun `markdown va xuong dong duoc don`() {
        assertEquals(
            "Da sua xong bug phan trang.",
            refineAlertText("**Da sua xong**\nbug `phan trang`."),
        )
    }

    @Test
    fun `heading va bullet dau dong bi bo`() {
        assertEquals("Ket qua: build xanh", refineAlertText("## Ket qua:\n- build xanh"))
    }

    @Test
    fun `cat o ranh gioi tu khong cat giua chu`() {
        val long = "a".repeat(60) + " " + "b".repeat(200)
        val out = refineAlertText(long, limit = 100)
        assertEquals("a".repeat(60) + "…", out)
    }

    @Test
    fun `ngan thi giu nguyen`() {
        assertEquals("chao anh", refineAlertText("  chao   anh  "))
    }
}
