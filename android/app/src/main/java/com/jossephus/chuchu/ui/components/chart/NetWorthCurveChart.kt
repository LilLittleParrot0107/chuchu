package com.jossephus.chuchu.ui.components.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jossephus.chuchu.data.model.dbtop.CurvePoint
import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NetWorthCurveChart(
    points: List<CurvePoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF38BDF8),
    tooltipBg: Color = Color(0xFF181825),
    tooltipText: Color = Color(0xFFCDD6F4),
    gridColor: Color = Color(0xFF45475A).copy(alpha = 0.4f),
    textColor: Color = Color(0xFFA6ADC8),
    height: Dp = 200.dp,
) {
    if (points.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(points.size) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        )
    }

    var isDragging by remember { mutableStateOf(false) }
    val touchX = remember { mutableFloatStateOf(-1f) }
    val lastHapticIndex = remember { mutableIntStateOf(-1) }

    val curvePath = remember { Path() }
    val fillPath = remember { Path() }
    val tooltipPath = remember { Path() }

    val minVal = remember(points) { points.minOfOrNull { it.nw } ?: 0.0 }
    val maxVal = remember(points) { points.maxOfOrNull { it.nw } ?: 1.0 }
    val valueRange = if (maxVal == minVal) 1.0 else (maxVal - minVal)

    val labelTextStyle = remember(textColor) {
        TextStyle(
            color = textColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
        )
    }
    val tooltipTitleStyle = remember(tooltipText) {
        TextStyle(
            color = tooltipText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }

    val timeFormatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        touchX.floatValue = offset.x
                    },
                    onDragEnd = {
                        isDragging = false
                        touchX.floatValue = -1f
                        lastHapticIndex.intValue = -1
                    },
                    onDragCancel = {
                        isDragging = false
                        touchX.floatValue = -1f
                        lastHapticIndex.intValue = -1
                    },
                    onDrag = { change, _ ->
                        touchX.floatValue = change.position.x
                    },
                )
            }
            .pointerInput(points) {
                detectTapGestures(
                    onPress = { offset ->
                        isDragging = true
                        touchX.floatValue = offset.x
                        tryAwaitRelease()
                        isDragging = false
                        touchX.floatValue = -1f
                        lastHapticIndex.intValue = -1
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val topPadding = 20.dp.toPx()
            val bottomPadding = 24.dp.toPx()
            val leftPadding = 8.dp.toPx()
            val rightPadding = 8.dp.toPx()

            val plotWidth = canvasWidth - leftPadding - rightPadding
            val plotHeight = canvasHeight - topPadding - bottomPadding

            if (plotWidth <= 0 || plotHeight <= 0) return@Canvas

            // 1. Gridlines
            val gridLinesCount = 3
            val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

            for (i in 0..gridLinesCount) {
                val ratio = i.toFloat() / gridLinesCount
                val y = topPadding + plotHeight * (1f - ratio)
                val lineVal = minVal + valueRange * ratio

                drawLine(
                    color = gridColor,
                    start = Offset(leftPadding, y),
                    end = Offset(canvasWidth - rightPadding, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashedEffect,
                )

                val labelText = DeFiFormatter.formatUsdCompact(lineVal)
                val textLayout = textMeasurer.measure(labelText, labelTextStyle)
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(canvasWidth - rightPadding - textLayout.size.width, y - textLayout.size.height - 2f),
                )
            }

            // 2. Data points
            val pointCount = points.size
            val xCoords = FloatArray(pointCount)
            val yCoords = FloatArray(pointCount)

            for (i in 0 until pointCount) {
                val normX = if (pointCount > 1) i.toFloat() / (pointCount - 1) else 0.5f
                val normY = (if (maxVal == minVal) 0.5 else (points[i].nw - minVal) / valueRange).toFloat().coerceIn(0f, 1f)

                xCoords[i] = leftPadding + normX * plotWidth
                yCoords[i] = topPadding + (1f - normY) * plotHeight
            }

            // 3. Monotone Cubic Bézier Spline
            curvePath.rewind()
            fillPath.rewind()

            if (pointCount == 1) {
                curvePath.moveTo(leftPadding, yCoords[0])
                curvePath.lineTo(canvasWidth - rightPadding, yCoords[0])
            } else {
                curvePath.moveTo(xCoords[0], yCoords[0])
                for (i in 0 until pointCount - 1) {
                    val x0 = if (i > 0) xCoords[i - 1] else xCoords[i]
                    val y0 = if (i > 0) yCoords[i - 1] else yCoords[i]
                    val x1 = xCoords[i]
                    val y1 = yCoords[i]
                    val x2 = xCoords[i + 1]
                    val y2 = yCoords[i + 1]
                    val x3 = if (i + 2 < pointCount) xCoords[i + 2] else x2
                    val y3 = if (i + 2 < pointCount) yCoords[i + 2] else y2

                    val c1x = x1 + (x2 - x0) / 6f
                    val c1y = y1 + (y2 - y0) / 6f
                    val c2x = x2 - (x3 - x1) / 6f
                    val c2y = y2 - (y3 - y1) / 6f

                    curvePath.cubicTo(c1x, c1y, c2x, c2y, x2, y2)
                }
            }

            fillPath.addPath(curvePath)
            fillPath.lineTo(xCoords.last(), topPadding + plotHeight)
            fillPath.lineTo(xCoords.first(), topPadding + plotHeight)
            fillPath.close()

            // 4. Draw Gradient Fill & Line
            val progress = animationProgress.value
            if (progress > 0f) {
                val fillGradient = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.30f * progress),
                        lineColor.copy(alpha = 0.05f * progress),
                        Color.Transparent,
                    ),
                    startY = topPadding,
                    endY = topPadding + plotHeight,
                )

                drawPath(path = fillPath, brush = fillGradient, style = Fill)
                drawPath(
                    path = curvePath,
                    color = lineColor.copy(alpha = progress),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }

            // 5. Touch Scrubbing Marker & Tooltip
            val currentTouchX = touchX.floatValue
            if (isDragging && currentTouchX >= leftPadding && currentTouchX <= canvasWidth - rightPadding && pointCount > 1) {
                val rawRatio = ((currentTouchX - leftPadding) / plotWidth).coerceIn(0f, 1f)
                val exactIndexFloat = rawRatio * (pointCount - 1)
                val nearestIndex = exactIndexFloat.toInt().coerceIn(0, pointCount - 1)
                val nextIndex = (nearestIndex + 1).coerceAtMost(pointCount - 1)
                val subFraction = exactIndexFloat - nearestIndex

                val markerX = xCoords[nearestIndex] + (xCoords[nextIndex] - xCoords[nearestIndex]) * subFraction
                val markerY = yCoords[nearestIndex] + (yCoords[nextIndex] - yCoords[nearestIndex]) * subFraction
                val selectedPoint = points[nearestIndex]

                if (lastHapticIndex.intValue != nearestIndex) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastHapticIndex.intValue = nearestIndex
                }

                // Crosshair
                drawLine(
                    color = lineColor.copy(alpha = 0.6f),
                    start = Offset(markerX, topPadding),
                    end = Offset(markerX, topPadding + plotHeight),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f),
                )

                // Pulse Glow Marker
                drawCircle(
                    color = lineColor.copy(alpha = 0.25f),
                    radius = 10.dp.toPx(),
                    center = Offset(markerX, markerY),
                )
                drawCircle(
                    color = lineColor,
                    radius = 4.5.dp.toPx(),
                    center = Offset(markerX, markerY),
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = Offset(markerX, markerY),
                )

                // Tooltip
                val usdFormatted = DeFiFormatter.formatUsd(selectedPoint.nw)
                val timeFormatted = timeFormatter.format(Date(selectedPoint.ts * 1000L))
                val tooltipText = "$usdFormatted · $timeFormatted"

                val titleLayout = textMeasurer.measure(tooltipText, tooltipTitleStyle)

                val tooltipPaddingH = 8.dp.toPx()
                val tooltipPaddingV = 4.dp.toPx()
                val boxWidth = titleLayout.size.width + tooltipPaddingH * 2
                val boxHeight = titleLayout.size.height + tooltipPaddingV * 2

                var boxLeft = markerX - boxWidth / 2f
                if (boxLeft < leftPadding) boxLeft = leftPadding
                if (boxLeft + boxWidth > canvasWidth - rightPadding) boxLeft = canvasWidth - rightPadding - boxWidth
                if (boxLeft < leftPadding) boxLeft = leftPadding
                if (boxLeft < leftPadding) boxLeft = leftPadding

                var boxTop = markerY - boxHeight - 10.dp.toPx()
                if (boxTop < 4.dp.toPx()) boxTop = markerY + 10.dp.toPx()

                tooltipPath.rewind()
                tooltipPath.addRoundRect(
                    RoundRect(
                        left = boxLeft,
                        top = boxTop,
                        right = boxLeft + boxWidth,
                        bottom = boxTop + boxHeight,
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    ),
                )

                drawPath(
                    path = tooltipPath,
                    color = tooltipBg.copy(alpha = 0.95f),
                    style = Fill,
                )
                drawPath(
                    path = tooltipPath,
                    color = lineColor.copy(alpha = 0.8f),
                    style = Stroke(width = 1.dp.toPx()),
                )

                drawText(
                    textLayoutResult = titleLayout,
                    topLeft = Offset(boxLeft + tooltipPaddingH, boxTop + tooltipPaddingV),
                )
            }
        }
    }
}
