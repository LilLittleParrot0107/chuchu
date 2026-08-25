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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.StrokeCap
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
import com.jossephus.chuchu.data.model.dbtop.DailyYield
import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import kotlin.math.max

/**
 * 1 chart HAI lớp: bar = daily yield theo ngày, line = accumulated yield
 * (cộng dồn) với thang riêng bên phải. Chạm/kéo để xem tooltip cả hai giá.
 */
@Composable
fun YieldComboChart(
    dailyData: List<DailyYield>,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF38BDF8),
    accumColor: Color = Color(0xFFA6E3A1),
    accentColor: Color = Color(0xFF4ADE80),
    tooltipBg: Color = Color(0xFF11111B),
    gridColor: Color = Color(0xFF45475A).copy(alpha = 0.35f),
    textColor: Color = Color(0xFFA6ADC8),
    height: Dp = 190.dp,
) {
    if (dailyData.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(dailyData.size) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        )
    }

    var isTouching by remember { mutableStateOf(false) }
    val touchX = remember { mutableFloatStateOf(-1f) }
    val selectedIndexState = remember { mutableIntStateOf(-1) }

    // Thang riêng cho từng lớp: bar theo max daily, line theo max cộng dồn.
    val maxYield = remember(dailyData) {
        max(dailyData.maxOfOrNull { it.yieldUsd } ?: 1.0, 1.0)
    }
    val cumulative = remember(dailyData) {
        var s = 0.0
        dailyData.map { s += it.yieldUsd; s }
    }
    val maxCum = remember(cumulative) {
        max(cumulative.lastOrNull() ?: 1.0, 1.0)
    }

    val labelStyle = remember(textColor) {
        TextStyle(
            color = textColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
        )
    }
    val tooltipMainStyle = remember(barColor) {
        TextStyle(
            color = barColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
    val tooltipAccStyle = remember(accumColor) {
        TextStyle(
            color = accumColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
    val tooltipSecStyle = remember(accentColor) {
        TextStyle(
            color = accentColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }

    val tooltipPath = remember { Path() }
    val linePath = remember { Path() }
    // Hoist khoi draw: allocation moi frame khi keo tooltip la lang phi.
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f) }

    // Cache text layout: measure trong draw chay moi frame khi keo tooltip.
    val dateLayouts = remember(dailyData, labelStyle) {
        dailyData.map { textMeasurer.measure(it.date.takeLast(5), labelStyle) }
    }
    val gridLabels = remember(dailyData, labelStyle) {
        val maxYieldV = maxYield
        (0..2).map { i -> textMeasurer.measure(DeFiFormatter.formatUsdCompact(maxYieldV * i / 2.0), labelStyle) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(dailyData) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isTouching = true
                        touchX.floatValue = offset.x
                    },
                    onDragEnd = {
                        isTouching = false
                        touchX.floatValue = -1f
                        selectedIndexState.intValue = -1
                    },
                    onDragCancel = {
                        isTouching = false
                        touchX.floatValue = -1f
                        selectedIndexState.intValue = -1
                    },
                    onDrag = { change, _ ->
                        touchX.floatValue = change.position.x
                    },
                )
            }
            .pointerInput(dailyData) {
                detectTapGestures(
                    onPress = { offset ->
                        isTouching = true
                        touchX.floatValue = offset.x
                        tryAwaitRelease()
                        isTouching = false
                        touchX.floatValue = -1f
                        selectedIndexState.intValue = -1
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val topPadding = 20.dp.toPx()
            val bottomPadding = 22.dp.toPx()
            val leftPadding = 8.dp.toPx()
            val rightPadding = 8.dp.toPx()

            val plotWidth = canvasWidth - leftPadding - rightPadding
            val plotHeight = canvasHeight - topPadding - bottomPadding

            if (plotWidth <= 0 || plotHeight <= 0) return@Canvas

            // 1. Gridlines theo thang DAILY (nhãn bên phải)
            val gridSteps = 2
            for (i in 0..gridSteps) {
                val ratio = i.toFloat() / gridSteps
                val y = topPadding + plotHeight * (1f - ratio)
                val yieldVal = maxYield * ratio

                drawLine(
                    color = gridColor,
                    start = Offset(leftPadding, y),
                    end = Offset(canvasWidth - rightPadding, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect,
                )

                val labelResult = gridLabels[i]
                drawText(
                    textLayoutResult = labelResult,
                    topLeft = Offset(canvasWidth - rightPadding - labelResult.size.width, y - labelResult.size.height - 2f),
                )
            }

            // 2. Bars — daily yield
            val count = dailyData.size
            val slotWidth = plotWidth / count
            val barSpacing = slotWidth * 0.25f
            val barWidth = slotWidth - barSpacing
            val cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())

            val currentTouch = touchX.floatValue
            var activeIndex = -1
            if (isTouching && currentTouch >= leftPadding && currentTouch <= canvasWidth - rightPadding) {
                activeIndex = ((currentTouch - leftPadding) / slotWidth).toInt().coerceIn(0, count - 1)
                if (selectedIndexState.intValue != activeIndex) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedIndexState.intValue = activeIndex
                }
            }

            for (i in 0 until count) {
                val item = dailyData[i]
                val barLeft = leftPadding + i * slotWidth + barSpacing / 2f
                val normalizedHeight = (item.yieldUsd / maxYield).toFloat().coerceIn(0f, 1f)
                val animatedBarHeight = normalizedHeight * plotHeight * animProgress.value
                val barTop = topPadding + plotHeight - animatedBarHeight

                val isSelected = (i == activeIndex)
                val isAnyActive = (activeIndex != -1)
                val alpha = when {
                    !isAnyActive -> 1.0f
                    isSelected -> 1.0f
                    else -> 0.35f
                }

                val barBrush = Brush.verticalGradient(
                    colors = listOf(
                        (if (isSelected) accentColor else barColor).copy(alpha = alpha),
                        accentColor.copy(alpha = alpha * 0.7f),
                    ),
                    startY = barTop,
                    endY = topPadding + plotHeight,
                )

                drawRoundRect(
                    brush = barBrush,
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, animatedBarHeight),
                    cornerRadius = cornerRadius,
                )

                val dateLayout = dateLayouts[i]
                if (dateLayout.size.width <= slotWidth * 0.9f) {
                    drawText(
                        textLayoutResult = dateLayout,
                        topLeft = Offset(barLeft + (barWidth - dateLayout.size.width) / 2f, canvasHeight - bottomPadding + 3.dp.toPx()),
                    )
                }
            }

            // 3. Line — accumulated yield, thang RIENG (maxCum), vẽ đè lên bars
            linePath.rewind()
            for (i in 0 until count) {
                val cx = leftPadding + i * slotWidth + slotWidth / 2f
                val normY = (cumulative[i] / maxCum).toFloat().coerceIn(0f, 1f)
                val cy = topPadding + (1f - normY) * plotHeight * animProgress.value
                if (i == 0) linePath.moveTo(cx, cy) else linePath.lineTo(cx, cy)
            }
            drawPath(
                path = linePath,
                color = accumColor.copy(alpha = if (activeIndex != -1) 0.9f else 1f),
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
            )

            // 4. Tooltip: daily + accum + ngày
            if (activeIndex in 0 until count) {
                val selected = dailyData[activeIndex]
                val barCenterX = leftPadding + activeIndex * slotWidth + slotWidth / 2f

                val yieldStr = "+${DeFiFormatter.formatUsd(selected.yieldUsd)}/D"
                val accumStr = "Σ +${DeFiFormatter.formatUsd(cumulative[activeIndex])}"
                val subStr = "${selected.date} (${String.format(java.util.Locale.US, "%.1fd", selected.coverageDays)})"

                val yieldLayout = textMeasurer.measure(yieldStr, tooltipMainStyle)
                val accumLayout = textMeasurer.measure(accumStr, tooltipAccStyle)
                val subLayout = textMeasurer.measure(subStr, tooltipSecStyle)

                val padH = 8.dp.toPx()
                val padV = 4.dp.toPx()
                val ttWidth = maxOf(yieldLayout.size.width, accumLayout.size.width, subLayout.size.width) + padH * 2
                val ttHeight = yieldLayout.size.height + accumLayout.size.height + subLayout.size.height + padV * 2 + 4.dp.toPx()

                var ttLeft = barCenterX - ttWidth / 2f
                if (ttLeft + ttWidth > canvasWidth - rightPadding) ttLeft = canvasWidth - rightPadding - ttWidth
                if (ttLeft < leftPadding) ttLeft = leftPadding

                val ttTop = topPadding + 2.dp.toPx()

                tooltipPath.rewind()
                tooltipPath.addRoundRect(
                    RoundRect(
                        left = ttLeft,
                        top = ttTop,
                        right = ttLeft + ttWidth,
                        bottom = ttTop + ttHeight,
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
                    color = accumColor.copy(alpha = 0.7f),
                    style = Stroke(width = 1.dp.toPx()),
                )

                drawText(textLayoutResult = yieldLayout, topLeft = Offset(ttLeft + padH, ttTop + padV))
                drawText(textLayoutResult = accumLayout, topLeft = Offset(ttLeft + padH, ttTop + padV + yieldLayout.size.height + 2.dp.toPx()))
                drawText(textLayoutResult = subLayout, topLeft = Offset(ttLeft + padH, ttTop + padV + yieldLayout.size.height + accumLayout.size.height + 4.dp.toPx()))
            }
        }
    }
}
