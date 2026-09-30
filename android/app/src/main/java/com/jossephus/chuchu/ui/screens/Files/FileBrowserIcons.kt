package com.jossephus.chuchu.ui.screens.Files

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// ── Toolbar & action icons ──────────────────────────────────────────────


/**
 * Refresh / circular arrow icon.
 */
@Composable
fun RefreshIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.12f
        val cx = w / 2f
        val cy = h / 2f
        val radius = w * 0.34f

        // Arc: 300-degree sweep, open at top-right
        val arcPath = Path().apply {
            arcTo(
                androidx.compose.ui.geometry.Rect(
                    left = cx - radius,
                    top = cy - radius,
                    right = cx + radius,
                    bottom = cy + radius,
                ),
                startAngleDegrees = 135f,
                sweepAngleDegrees = 300f,
                forceMoveTo = true,
            )
        }
        drawPath(arcPath, color, style = Stroke(width = stroke))

        // Arrowhead at the end of the arc (at ~75° direction)
        val endAngleRad = (75.0 * Math.PI / 180.0)
        val tipX = cx + radius * cos(endAngleRad).toFloat()
        val tipY = cy + radius * sin(endAngleRad).toFloat()
        val headLen = w * 0.18f
        drawLine(
            color = color,
            start = Offset(tipX, tipY),
            end = Offset(
                (tipX - headLen * cos(endAngleRad + 0.7)).toFloat(),
                (tipY - headLen * sin(endAngleRad + 0.7)).toFloat(),
            ),
            strokeWidth = stroke,
        )
        drawLine(
            color = color,
            start = Offset(tipX, tipY),
            end = Offset(
                (tipX - headLen * cos(endAngleRad - 1.3)).toFloat(),
                (tipY - headLen * sin(endAngleRad - 1.3)).toFloat(),
            ),
            strokeWidth = stroke,
        )
    }
}

/**
 * Copy / duplicate icon: two overlapping rectangles.
 */
@Composable
fun CopyIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.1f

        // Back rectangle (offset right and up)
        val backRect = androidx.compose.ui.geometry.Rect(
            left = w * 0.35f,
            top = h * 0.0f,
            right = w * 1.0f,
            bottom = h * 0.65f,
        )
        drawRect(
            color = color.copy(alpha = 0.35f),
            topLeft = backRect.topLeft,
            size = backRect.size,
        )
        drawRect(
            color = color,
            topLeft = backRect.topLeft,
            size = backRect.size,
            style = Stroke(width = stroke),
        )

        // Front rectangle (offset left and down)
        val frontRect = androidx.compose.ui.geometry.Rect(
            left = w * 0.0f,
            top = h * 0.35f,
            right = w * 0.65f,
            bottom = h * 1.0f,
        )
        drawRect(
            color = color,
            topLeft = frontRect.topLeft,
            size = frontRect.size,
        )
        drawRect(
            color = color,
            topLeft = frontRect.topLeft,
            size = frontRect.size,
            style = Stroke(width = stroke),
        )
    }
}

// ── File type icons ───────────────────────────────────────────────────────

/**
 * Vector-drawn file type icons that look like classic folder/document/symlink
 * shapes, rendered in a single color to match the TUI aesthetic.
 */
@Composable
fun FileTypeIcon(
    type: FileEntryType,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        when (type) {
            FileEntryType.Directory -> drawFolderIcon(w, h, color)
            FileEntryType.File -> drawFileIcon(w, h, color)
            FileEntryType.Symlink -> drawSymlinkIcon(w, h, color)
            FileEntryType.Other -> drawOtherIcon(w, h, color)
        }
    }
}

/**
 * Classic folder shape: tab on the top-left, rectangular body.
 */
private fun DrawScope.drawFolderIcon(w: Float, h: Float, color: Color) {
    val tabH = h * 0.18f
    val tabW = w * 0.45f
    val bodyTop = tabH + h * 0.1f

    val path = Path().apply {
        moveTo(0f, h)
        lineTo(0f, tabH)
        lineTo(0f, 0f)
        lineTo(tabW, 0f)
        lineTo(tabW + w * 0.08f, bodyTop)
        lineTo(w, bodyTop)
        lineTo(w, h)
        close()
    }
    drawPath(path, color)
}

/**
 * Document/page shape with a folded dog-ear corner.
 */
private fun DrawScope.drawFileIcon(w: Float, h: Float, color: Color) {
    val foldSize = w * 0.3f

    val path = Path().apply {
        moveTo(0f, 0f)
        lineTo(w - foldSize, 0f)
        lineTo(w, foldSize)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }

    drawPath(path, color)

    drawLine(
        color = color,
        start = Offset(w - foldSize, 0f),
        end = Offset(w - foldSize, foldSize),
        strokeWidth = w * 0.04f,
    )
    drawLine(
        color = color,
        start = Offset(w - foldSize, foldSize),
        end = Offset(w, foldSize),
        strokeWidth = w * 0.04f,
    )
}

/**
 * Symlink: document shape with a curved arrow overlay.
 */
private fun DrawScope.drawSymlinkIcon(w: Float, h: Float, color: Color) {
    val bgColor = color.copy(alpha = 0.35f)
    val foldSize = w * 0.3f

    val docPath = Path().apply {
        moveTo(0f, 0f)
        lineTo(w - foldSize, 0f)
        lineTo(w, foldSize)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(docPath, bgColor)

    drawLine(
        color = bgColor,
        start = Offset(w - foldSize, 0f),
        end = Offset(w - foldSize, foldSize),
        strokeWidth = w * 0.04f,
    )
    drawLine(
        color = bgColor,
        start = Offset(w - foldSize, foldSize),
        end = Offset(w, foldSize),
        strokeWidth = w * 0.04f,
    )

    // Curved arrow overlay
    val cx = w * 0.45f
    val cy = h * 0.55f
    val arrowR = w * 0.25f
    val arrowStroke = w * 0.08f

    val arcPath = Path().apply {
        arcTo(
            androidx.compose.ui.geometry.Rect(
                left = cx - arrowR,
                top = cy - arrowR,
                right = cx + arrowR,
                bottom = cy + arrowR,
            ),
            startAngleDegrees = 210f,
            sweepAngleDegrees = -240f,
            forceMoveTo = true,
        )
    }
    drawPath(arcPath, color, style = Stroke(width = arrowStroke))

    val endAngleRad = (330.0 * Math.PI / 180.0)
    val tipX = cx + arrowR * cos(endAngleRad).toFloat()
    val tipY = cy + arrowR * sin(endAngleRad).toFloat()
    val headLen = w * 0.18f
    val barbAngle1 = endAngleRad + 0.6
    val barbAngle2 = endAngleRad - 1.4
    drawLine(
        color = color,
        start = Offset(tipX, tipY),
        end = Offset(
            (tipX - headLen * cos(barbAngle1)).toFloat(),
            (tipY - headLen * sin(barbAngle1)).toFloat(),
        ),
        strokeWidth = arrowStroke,
    )
    drawLine(
        color = color,
        start = Offset(tipX, tipY),
        end = Offset(
            (tipX - headLen * cos(barbAngle2)).toFloat(),
            (tipY - headLen * sin(barbAngle2)).toFloat(),
        ),
        strokeWidth = arrowStroke,
    )
}

/**
 * Unknown type: rounded rectangle with a ? inside.
 */
private fun DrawScope.drawOtherIcon(w: Float, h: Float, color: Color) {
    val inset = w * 0.1f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(w - inset * 2, h - inset * 2),
        cornerRadius = CornerRadius(w * 0.1f),
        style = Stroke(width = w * 0.08f),
    )
    val cx = w / 2f
    drawLine(
        color = color,
        start = Offset(cx, h * 0.25f),
        end = Offset(cx, h * 0.45f),
        strokeWidth = w * 0.08f,
    )
    drawCircle(
        color = color,
        radius = w * 0.06f,
        center = Offset(cx, h * 0.6f),
    )
}