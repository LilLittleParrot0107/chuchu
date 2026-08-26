package com.jossephus.chuchu.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Chay derive palette tren TOAN BO theme trong assets/themes va do contrast
 * WCAG that. Chan tai phat vu 20xx: derive cu tra ve error 1.5:1 (DEBT vo
 * hinh tren Adventure Time), muted sang hon secondary, accentSecondary 1.2:1.
 */
class ThemeContrastTest {

    private fun themesDir(): File {
        var dir = File(System.getProperty("user.dir"))
        repeat(5) {
            val candidate = File(dir, "src/main/assets/themes")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("khong tim thay src/main/assets/themes tu ${System.getProperty("user.dir")}")
    }

    @Test
    fun derivedPalettesMeetMinimumContrast() {
        val themeFiles = themesDir().listFiles()!!.filter { it.isFile }.sortedBy { it.name }
        assertTrue("themes directory rong?", themeFiles.size > 100)

        val failures = mutableListOf<String>()
        for (file in themeFiles) {
            val palette = GhosttyTheme.parse(file.name, file.readText()).toChuColorPalette()
            val surface = palette.surface
            // Tran contrast dat duoc tren surface nay: nen mid-gray khong the
            // cham 4.5 voi bat ky mau nao — chi doi hoi den muc kha thi.
            val cap = maxOf(
                contrastRatio(Color.Black, surface),
                contrastRatio(Color.White, surface),
            ) - 0.05f

            fun check(label: String, color: Color, min: Float) {
                val target = minOf(min, cap)
                val ratio = contrastRatio(color, surface)
                if (ratio < target) {
                    failures += "%s: %s %.2f:1 < %.2f".format(Locale.US, file.name, label, ratio, target)
                }
            }
            check("textSecondary", palette.textSecondary, 5.4f)
            check("textMuted", palette.textMuted, 4.4f)
            check("accent", palette.accent, 4.4f)
            check("buttonFill", palette.buttonFill, 4.4f)
            // Chu onAccent (= background) tren nut Filled phai doc duoc.
            if (contrastRatio(palette.onAccent, palette.buttonFill) < 4.4f) {
                failures += "%s: onAccent tren buttonFill chi %.2f:1".format(
                    Locale.US, file.name, contrastRatio(palette.onAccent, palette.buttonFill),
                )
            }
            check("accentSecondary", palette.accentSecondary, 4.4f)
            check("error", palette.error, 4.4f)
            check("success", palette.success, 4.4f)
            check("warning", palette.warning, 4.4f)

            // Bac chu khong duoc dao: muted phai gan surface hon secondary.
            val secondaryRatio = contrastRatio(palette.textSecondary, surface)
            val mutedRatio = contrastRatio(palette.textMuted, surface)
            if (mutedRatio > secondaryRatio + 0.01f) {
                failures += "%s: muted %.2f:1 SANG hon secondary %.2f:1".format(
                    Locale.US, file.name, mutedRatio, secondaryRatio,
                )
            }
        }

        assertTrue(
            "${failures.size} vi pham contrast:\n" + failures.take(40).joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun adventureTimeUsesItsIdentityAccent() {
        val file = File(themesDir(), "Adventure Time")
        assertTrue(file.isFile)
        val palette = GhosttyTheme.parse(file.name, file.readText()).toChuColorPalette()
        // cursor-color #efbf38 (vang) phai thanh accent, khong bi vut nhu truoc.
        assertTrue("accent phai am vang: ${palette.accent}", palette.accent.red > palette.accent.blue)
    }
}
