package com.jossephus.chuchu.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Sắc theo phiên (user chốt 22/9): ổn định theo tên, ở trong họ màu ±20°/±20%, sáng kẹp [0.35, 0.90]. */
class SessionShadeTest {
    @Test
    fun `cung ten cung nac, khac ten thuong khac nac`() {
        assertEquals(sessionStep("kohi-maintainer"), sessionStep("  KOHI-maintainer "))
        val steps = listOf("kohi-maintainer", "vbook-maintainer", "OC | Kohi app build", "AGY | aivid-main", "UI extension pop").map { sessionStep(it) }
        assertTrue(steps.all { it in 0 until SESSION_SHADE_STEPS })
        assertTrue(steps.toSet().size >= 3)
        assertEquals(SESSION_SHADE_STEPS / 2, sessionStep(""))
    }

    @Test
    fun `hsl di ve khong doi mau`() {
        for ((r, g, b) in listOf(Triple(0.94f, 0.64f, 0.37f), Triple(0.48f, 0.85f, 0.56f), Triple(0.54f, 0.66f, 0.94f), Triple(0.5f, 0.5f, 0.5f))) {
            val h = rgbToHsl(r, g, b); val back = hslToRgb(h[0], h[1], h[2])
            assertEquals(r, back[0], 0.01f); assertEquals(g, back[1], 0.01f); assertEquals(b, back[2], 0.01f)
        }
    }

    @Test
    fun `sac lech toi da 20 do, sang -20 den +35 phan tram, kep trong khoang`() {
        val base = rgbToHsl(0.94f, 0.64f, 0.37f)   // cam kiểu warning
        for (step in 0 until SESSION_SHADE_STEPS) {
            val rgb = shadeRgb(0.94f, 0.64f, 0.37f, step)
            val h = rgbToHsl(rgb[0], rgb[1], rgb[2])
            assertTrue("hue lệch", hueDistance(h[0], base[0]) <= 20.5f)
            assertTrue("light ${h[2]}", h[2] in SESSION_SHADE_L_MIN..SESSION_SHADE_L_MAX)
            assertTrue(h[2] - base[2] >= -0.205f && h[2] - base[2] <= 0.355f)
        }
        // nấc đầu tối hơn gốc, nấc cuối sáng hơn gốc (lệch về phía sáng nhiều hơn)
        val lo = shadeRgb(0.94f, 0.64f, 0.37f, 0).let { rgbToHsl(it[0], it[1], it[2])[2] }
        val hi = shadeRgb(0.94f, 0.64f, 0.37f, SESSION_SHADE_STEPS - 1).let { rgbToHsl(it[0], it[1], it[2])[2] }
        assertTrue(lo < base[2] && hi > base[2])
    }

    @Test
    fun `tong cua anh xa ca ba ho agent`() {
        val fams = listOf(30f, 220f, 130f)   // cam / xanh dương / xanh lá
        val h = distinctHue(fams)
        assertTrue("h=$h", fams.all { hueDistance(h, it) >= 45f + SESSION_SHADE_HUE_DEG })
        assertEquals(300f, distinctHue(emptyList()), 0f)
    }
}
