package com.jossephus.chuchu.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

data class GhosttyTheme(
    val name: String,
    val background: Color,
    val foreground: Color,
    val cursorColor: Color,
    val cursorText: Color,
    val selectionBackground: Color,
    val selectionForeground: Color,
    val palette: List<Color>,
) {
    companion object {
        fun parse(name: String, content: String): GhosttyTheme {
            val props = mutableMapOf<String, String>()
            val paletteColors = arrayOfNulls<Color>(16)

            content.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { line ->
                    val eqIndex = line.indexOf('=')
                    if (eqIndex < 0) return@forEach
                    val key = line.substring(0, eqIndex).trim()
                    val value = line.substring(eqIndex + 1).trim()

                    if (key == "palette") {
                        val sepIndex = value.indexOf('=')
                        if (sepIndex >= 0) {
                            val idx = value.substring(0, sepIndex).toIntOrNull() ?: return@forEach
                            if (idx in 0..15) {
                                paletteColors[idx] = parseHexColor(value.substring(sepIndex + 1))
                            }
                        }
                    } else {
                        props[key] = value
                    }
            }

            val bg = parseHexColor(props["background"] ?: "#000000")
            val fg = parseHexColor(props["foreground"] ?: "#ffffff")

            return GhosttyTheme(
                name = name,
                background = bg,
                foreground = fg,
                cursorColor = parseHexColor(props["cursor-color"] ?: props["foreground"] ?: "#ffffff"),
                cursorText = parseHexColor(props["cursor-text"] ?: props["background"] ?: "#000000"),
                selectionBackground = parseHexColor(props["selection-background"] ?: props["foreground"] ?: "#ffffff"),
                selectionForeground = parseHexColor(props["selection-foreground"] ?: props["background"] ?: "#000000"),
                palette = List(16) { paletteColors[it] ?: defaultTerminalPaletteColor(it) },
            )
        }

        private fun parseHexColor(hex: String): Color {
            val h = hex.trimStart('#')
            val argb = when (h.length) {
                6 -> h.toLongOrNull(16)?.let { 0xFF000000 or it }
                8 -> h.toLongOrNull(16)
                else -> null
            }
            return Color((argb ?: 0xFF000000).toInt())
        }
    }
}

fun GhosttyTheme.toChuColorPalette(): ChuColorPalette {
    val isDark = !background.isLightColor()
    val contrast = if (isDark) Color.White else Color.Black

    // Panels: lighter touch on light themes so pale sage/cream backgrounds
    // don't get muddied; keep the existing weight on dark themes.
    val surface = background.mix(contrast, if (isDark) 0.15f else 0.06f)
    // surfaceVariant phai tach duoc khoi background bang mat thuong —
    // 0.04 cu chi ~1.1:1, track/inset gan nhu tang hinh tren OLED.
    val surfaceVariant = background.mix(contrast, if (isDark) 0.08f else 0.05f)
    // Borders need more punch on light themes — the eye is less forgiving
    // of low-contrast outlines on bright backgrounds.
    val border = background.mix(contrast, if (isDark) 0.22f else 0.28f)

    val textPrimary = foreground
    // secondary mix IT hon muted nen luon gan foreground hon (truoc day
    // 0.28/0.22 bi nguoc bac). ensureContrast keo ca hai ve muc san khi
    // theme von it tuong phan; san 5.5 vs 4.5 giu khoang cach giua hai bac
    // ngay ca khi phai keo.
    val textSecondary = ensureContrast(foreground.mix(background, 0.22f), surface, 5.5f)
    val textMuted = ensureContrast(foreground.mix(background, 0.40f), surface, 4.5f)

    // Brand accent: prefer the theme's own cursor-color — it is the one
    // slot where theme authors put their identity color (Adventure Time's
    // yellow, Dracula's pink…) and it harmonises by construction. Fall back
    // to the "inverted text" foreground derivation when the cursor is just
    // a copy of fg/bg or too close to the background to read.
    val cursorAccent = cursorColor.takeIf {
        it != foreground && it != background && contrastRatio(it, background) >= 2.5f
    }
    val accent = ensureContrast(
        cursorAccent ?: foreground.mix(background, if (isDark) 0.15f else 0.08f),
        surface,
        4.5f,
    )
    // Nut Filled GIU cong thuc "inverted text" cu (fg pha bg) — khong an theo
    // cursor-color nhu accent: nut to ban nen vang identity bi choi (user
    // chot 26/8, task #136). onAccent = background van doc tot tren nen nay.
    val buttonFill = ensureContrast(
        foreground.mix(background, if (isDark) 0.15f else 0.08f),
        surface,
        4.5f,
    )
    // Nhieu theme de ANSI blue rat toi (Adventure Time #0f4ac6 ~1.2:1 tren
    // surface) — dung tho la chu tang hinh. Keo ve AA, giu hue.
    val accentSecondary = ensureContrast(
        palette[4].mix(background, if (isDark) 0.15f else 0.35f),
        surface,
        4.5f,
    )
    // onAccent di cap chu yeu voi buttonFill (chu tren nut Filled). Da so
    // theme background dat san 4.5 va giu nguyen; vai theme mid-tone (Blue
    // Dolphin, Grass, Hot Dog Stand…) can day nhe ve phia toi/sang.
    val onAccent = ensureContrast(background, buttonFill, 4.5f)

    val disabledSurface = surface.mix(background, 0.5f)
    // Phai TOI hon textMuted mot bac — bang nhau thi control disabled
    // khong phan biet duoc voi metadata thuong.
    val disabledText = foreground.mix(background, 0.55f)

    return ChuColorPalette(
        name = this.name,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        border = border,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        accent = accent,
        buttonFill = buttonFill,
        accentSecondary = accentSecondary,
        // ANSI 1/2/3 tho co the qua toi hoac qua choi so voi nen (DEBT do
        // #BD0013 tren card chi 1.5:1) — moi mau deu duoc keo ve toi thieu
        // AA tren surface, hue giu nguyen.
        error = ensureContrast(palette[1], surface, 4.5f),
        success = ensureContrast(palette[2], surface, 4.5f),
        warning = ensureContrast(palette[3], surface, 4.5f),
        onAccent = onAccent,
        disabledSurface = disabledSurface,
        disabledText = disabledText,
    )
}

fun GhosttyTheme.toTerminalPaletteBytes(): ByteArray {
    val colors = MutableList(256, ::defaultTerminalPaletteColor)
    for (index in palette.indices) {
        colors[index] = palette[index]
    }

    return ByteArray(colors.size * 3).also { bytes ->
        colors.forEachIndexed { index, color ->
            val offset = index * 3
            bytes[offset] = colorChannelToByte(color.red)
            bytes[offset + 1] = colorChannelToByte(color.green)
            bytes[offset + 2] = colorChannelToByte(color.blue)
        }
    }
}

fun Color.toRgbIntArray(): IntArray = intArrayOf(
    (red * 255f).roundToInt().coerceIn(0, 255),
    (green * 255f).roundToInt().coerceIn(0, 255),
    (blue * 255f).roundToInt().coerceIn(0, 255),
)

private fun colorChannelToByte(value: Float): Byte =
    (value * 255f).roundToInt().coerceIn(0, 255).toByte()

private fun defaultTerminalPaletteColor(index: Int): Color {
    if (index in DEFAULT_ANSI_COLORS.indices) {
        return DEFAULT_ANSI_COLORS[index]
    }

    return when (index) {
        in 16..231 -> {
            val cubeIndex = index - 16
            val red = XTERM_CUBE_LEVELS[cubeIndex / 36]
            val green = XTERM_CUBE_LEVELS[(cubeIndex / 6) % 6]
            val blue = XTERM_CUBE_LEVELS[cubeIndex % 6]
            Color(red = red / 255f, green = green / 255f, blue = blue / 255f)
        }

        in 232..255 -> {
            val level = 8 + (index - 232) * 10
            Color(red = level / 255f, green = level / 255f, blue = level / 255f)
        }

        else -> Color(0xFF000000)
    }
}

private val DEFAULT_ANSI_COLORS = listOf(
    Color(0xFF000000),
    Color(0xFF800000),
    Color(0xFF008000),
    Color(0xFF808000),
    Color(0xFF000080),
    Color(0xFF800080),
    Color(0xFF008080),
    Color(0xFFC0C0C0),
    Color(0xFF808080),
    Color(0xFFFF0000),
    Color(0xFF00FF00),
    Color(0xFFFFFF00),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFF00FFFF),
    Color(0xFFFFFFFF),
)

private val XTERM_CUBE_LEVELS = intArrayOf(0, 95, 135, 175, 215, 255)
