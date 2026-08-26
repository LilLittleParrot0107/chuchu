package com.jossephus.chuchu.ui.components.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
import com.jossephus.chuchu.data.model.dbtop.DailyYield
import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import kotlin.math.max
import kotlin.math.min

/**
 * Σ YIELD VS SPEND — hai đường CÙNG MỘT THANG (đơn vị USD cộng dồn trong cửa
 * sổ): Σ yield gộp và Σ net sau khi trừ chi tiêu; khoảng hở giữa hai đường tô
 * mờ = đúng phần bị spend ăn mất. Sinh ra 27/8 thay cho line Σ trong chart
 * bar (hai-trục làm user thấy "tỉ lệ sai" mãi).
 */
@Composable
fun YieldNetChart(
    dailyData: List<DailyYield>,
    spendByDay: Map<String, Double>,
    grossColor: Color,
    netColor: Color,
    gridColor: Color,
    textColor: Color,
    tooltipBg: Color,
    tooltipText: Color,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
) {
    if (dailyData.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()

    var isTouching by remember { mutableStateOf(false) }
    val touchX = remember { mutableFloatStateOf(-1f) }
    val selectedIndexState = remember { mutableIntStateOf(-1) }

    data class NetPoint(val date: String, val gross: Double, val spend: Double, val net: Double)

    val points = remember(dailyData, spendByDay) {
        var g = 0.0
        var sp = 0.0
        dailyData.map { d ->
            g += d.yieldUsd
            sp += spendByDay[d.date] ?: 0.0
            NetPoint(d.date, g, sp, g - sp)
        }
    }
    val yMax = remember(points) { max(points.maxOf { it.gross }, 1.0) }
    val yMin = remember(points) { min(points.minOf { it.net }, 0.0) }
    val range = (yMax - yMin).takeIf { it > 0 } ?: 1.0

    val labelStyle = remember(textColor) {
        TextStyle(
            color = textColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
        )
    }
    val gridLabels = remember(yMin, yMax, labelStyle) {
        (0..2).map { i ->
            val v = yMin + range * i / 2.0
            textMeasurer.measure(DeFiFormatter.formatUsdCompact(v), labelStyle)
        }
    }
    val tooltipStyles = remember(grossColor, netColor, textColor) {
        Triple(
            TextStyle(color = grossColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
            TextStyle(color = netColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
            TextStyle(color = textColor, fontSize = 9.sp, fontFamily = FontFamily.Monospace),
        )
    }
    val tooltipTitleStyle = remember(tooltipText) {
        TextStyle(color = tooltipText, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }

    val grossPath = remember { Path() }
    val netPath = remember { Path() }
    val gapPath = remember { Path() }
    val tooltipPath = remember { Path() }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) }
    val xs = remember(points) { FloatArray(points.size) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(points) {
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
                    onDrag = { change, _ -> touchX.floatValue = change.position.x },
                )
            }
            .pointerInput(points) {
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
            val w = size.width
            val h = size.height
            val topPad = 20.dp.toPx()
            val bottomPad = 8.dp.toPx()
            val sidePad = 8.dp.toPx()
            val plotW = w - sidePad * 2
            val plotH = h - topPad - bottomPad
            if (plotW <= 0 || plotH <= 0) return@Canvas

            fun yOf(v: Double): Float = (topPad + (1.0 - (v - yMin) / range) * plotH).toFloat()

            // Grid + nhan (mot thang duy nhat)
            for (i in 0..2) {
                val v = yMin + range * i / 2.0
                val y = yOf(v)
                drawLine(
                    color = gridColor,
                    start = Offset(sidePad, y),
                    end = Offset(w - sidePad, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect,
                )
                val label = gridLabels[i]
                drawText(label, topLeft = Offset(w - sidePad - label.size.width, y - label.size.height - 2f))
            }
            // Vach 0 khi net am — moc "hoa von" phai nhin thay ro.
            if (yMin < 0.0 && yMax > 0.0) {
                drawLine(
                    color = textColor.copy(alpha = 0.5f),
                    start = Offset(sidePad, yOf(0.0)),
                    end = Offset(w - sidePad, yOf(0.0)),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val n = points.size
            for (i in 0 until n) {
                xs[i] = if (n > 1) sidePad + plotW * i / (n - 1) else sidePad + plotW / 2
            }

            grossPath.rewind()
            netPath.rewind()
            gapPath.rewind()
            points.forEachIndexed { i, p ->
                if (i == 0) {
                    grossPath.moveTo(xs[i], yOf(p.gross))
                    netPath.moveTo(xs[i], yOf(p.net))
                    gapPath.moveTo(xs[i], yOf(p.gross))
                } else {
                    grossPath.lineTo(xs[i], yOf(p.gross))
                    netPath.lineTo(xs[i], yOf(p.net))
                    gapPath.lineTo(xs[i], yOf(p.gross))
                }
            }
            for (i in n - 1 downTo 0) gapPath.lineTo(xs[i], yOf(points[i].net))
            gapPath.close()

            // Khoang ho = tien bi spend an — to mo mau net.
            drawPath(gapPath, color = netColor.copy(alpha = 0.16f), style = Fill)
            drawPath(grossPath, color = grossColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawPath(netPath, color = netColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

            // Scrub tooltip
            val tx = touchX.floatValue
            if (isTouching && tx >= sidePad && tx <= w - sidePad && n > 0) {
                val idx = (((tx - sidePad) / plotW) * (n - 1)).toInt().coerceIn(0, n - 1)
                if (selectedIndexState.intValue != idx) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedIndexState.intValue = idx
                }
                val p = points[idx]
                drawLine(
                    color = textColor.copy(alpha = 0.6f),
                    start = Offset(xs[idx], topPad),
                    end = Offset(xs[idx], topPad + plotH),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect,
                )
                drawCircle(grossColor, 3.dp.toPx(), Offset(xs[idx], yOf(p.gross)))
                drawCircle(netColor, 3.dp.toPx(), Offset(xs[idx], yOf(p.net)))

                val (grossStyle, netStyle, spendStyle) = tooltipStyles
                val l0 = textMeasurer.measure(p.date.takeLast(5), tooltipTitleStyle)
                val l1 = textMeasurer.measure("Σ +${DeFiFormatter.formatUsd(p.gross)}", grossStyle)
                val l2 = textMeasurer.measure("SPEND -${DeFiFormatter.formatUsd(p.spend)}", spendStyle)
                val l3 = textMeasurer.measure("NET ${if (p.net >= 0) "+" else "-"}${DeFiFormatter.formatUsd(kotlin.math.abs(p.net))}", netStyle)
                val padH = 8.dp.toPx()
                val padV = 4.dp.toPx()
                val ttW = maxOf(l0.size.width, l1.size.width, l2.size.width, l3.size.width) + padH * 2
                val ttH = l0.size.height + l1.size.height + l2.size.height + l3.size.height + padV * 2 + 6.dp.toPx()
                var left = xs[idx] - ttW / 2
                if (left + ttW > w - sidePad) left = w - sidePad - ttW
                if (left < sidePad) left = sidePad
                val top = topPad + 2.dp.toPx()
                tooltipPath.rewind()
                tooltipPath.addRoundRect(
                    RoundRect(left, top, left + ttW, top + ttH, CornerRadius(4.dp.toPx(), 4.dp.toPx())),
                )
                drawPath(tooltipPath, color = tooltipBg)
                var ty = top + padV
                drawText(l0, topLeft = Offset(left + padH, ty)); ty += l0.size.height + 2.dp.toPx()
                drawText(l1, topLeft = Offset(left + padH, ty)); ty += l1.size.height + 2.dp.toPx()
                drawText(l2, topLeft = Offset(left + padH, ty)); ty += l2.size.height + 2.dp.toPx()
                drawText(l3, topLeft = Offset(left + padH, ty))
            }
        }
    }
}
