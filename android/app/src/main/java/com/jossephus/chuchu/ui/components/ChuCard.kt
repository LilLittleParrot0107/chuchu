package com.jossephus.chuchu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.theme.ChuColors

/** Bật R1 (gờ sáng) cho các card trong Dashboard, không đổi card toàn app. */
val LocalDashboardRefined = compositionLocalOf { false }

@Composable
fun ChuCard(
    modifier: Modifier = Modifier,
    background: Color = ChuColors.current.surface,
    border: Color = ChuColors.current.border,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RectangleShape
    val refined = LocalDashboardRefined.current
    // R1: trung bình vẫn là surface; nửa trên +1,5% trắng, nửa dưới -1%.
    val bg: Brush = if (refined) {
        Brush.verticalGradient(
            listOf(
                lerp(background, Color.White, 0.015f),
                background,
                lerp(background, Color.Black, 0.010f),
            ),
        )
    } else {
        Brush.verticalGradient(listOf(background, background))
    }
    Column(
        modifier = modifier
            .background(bg, shape)
            .border(BorderStroke(1.dp, border), shape)
            .then(
                if (refined) {
                    Modifier.drawBehind {
                        drawLine(
                            color = Color.White.copy(alpha = 0.05f),
                            start = Offset.Zero,
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}
