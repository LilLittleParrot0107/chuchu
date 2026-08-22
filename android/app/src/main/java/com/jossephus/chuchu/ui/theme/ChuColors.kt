package com.jossephus.chuchu.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ChuColorPalette(
    val name: String,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSecondary: Color,
    val error: Color,
    val success: Color,
    val warning: Color,
    val onAccent: Color,
    val disabledSurface: Color,
    val disabledText: Color,
)

val ChuDarkColors: ChuColorPalette = ChuColorPalette(
    name = "mocha",
    background = Color(0xFF1E1E2E),
    surface = Color(0xFF313244),
    surfaceVariant = Color(0xFF181825),
    border = Color(0xFF45475A),
    // Ba cap text phan biet ro: primary gan trang-lavender cho noi dung chinh,
    // secondary xam sang cho metadata, muted tim-xam TOI nhat chi cho trang thai
    // vo hieu hoa. Truoc day secondary (#A6ADC8) va muted (#6C7086) qua giong nhau
    // nen khong biet dau la thong tin chinh.
    textPrimary = Color(0xFFE4E8FC),
    textSecondary = Color(0xFFAEB4D6),
    textMuted = Color(0xFF666C92),
    accent = Color(0xFFB4BEFE),
    accentSecondary = Color(0xFF89B4FA),
    error = Color(0xFFF38BA8),
    success = Color(0xFFA6E3A1),
    warning = Color(0xFFFAB387),
    onAccent = Color(0xFF1E1E2E),
    disabledSurface = Color(0xFF585B70),
    disabledText = Color(0xFF585D80),
)

val LocalChuColors = staticCompositionLocalOf { ChuDarkColors }

object ChuColors {
    val current: ChuColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalChuColors.current
}
