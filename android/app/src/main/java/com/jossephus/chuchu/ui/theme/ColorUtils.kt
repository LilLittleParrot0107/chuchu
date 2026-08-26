package com.jossephus.chuchu.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Blend this color toward [other] by [fraction] (0 = this, 1 = other). */
internal fun Color.mix(other: Color, fraction: Float): Color {
    val inv = 1f - fraction
    return Color(
        red = red * inv + other.red * fraction,
        green = green * inv + other.green * fraction,
        blue = blue * inv + other.blue * fraction,
        alpha = alpha * inv + other.alpha * fraction,
    )
}

/** WCAG contrast ratio (1..21) between two opaque colors. */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

/**
 * True when content drawn over this color should be dark. Judged by which
 * pole (black/white) contrasts more, not by a raw luminance threshold —
 * mid-tone backgrounds classify correctly either way.
 */
internal fun Color.isLightColor(): Boolean =
    contrastRatio(this, Color.Black) >= contrastRatio(this, Color.White)

/**
 * Nudge [color] toward white or black — away from [on] — until it reaches at
 * least [min] contrast against [on]. Hue is preserved; a color that already
 * passes is returned untouched.
 */
internal fun ensureContrast(color: Color, on: Color, min: Float): Color {
    if (contrastRatio(color, on) >= min) return color
    val pole = if (on.isLightColor()) Color.Black else Color.White
    if (contrastRatio(pole, on) < min) return pole
    var lo = 0f
    var hi = 1f
    repeat(8) {
        val mid = (lo + hi) / 2f
        if (contrastRatio(color.mix(pole, mid), on) >= min) hi = mid else lo = mid
    }
    return color.mix(pole, hi)
}
