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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import com.jossephus.chuchu.data.model.dbtop.SpendingState
import com.jossephus.chuchu.ui.theme.ChuTypography
import java.util.Locale
import kotlin.math.abs

/**
 * Data model representing daily cashflow delta:
 * Gross yield, Spend outflow, Net remainder, and soft-clamped outlier info.
 */
data class DailyCashflowPoint(
    val date: String,          // "YYYY-MM-DD"
    val gross: Double,         // Daily yield in USD
    val spend: Double,         // Daily spend in USD
    val net: Double,           // gross - spend
    val clampedNet: Double,    // Soft-clamped value for bar height
    val isClamped: Boolean,    // true if spend was an outlier clamped to floor
)

/**
 * Data point for the NET APR vs GROSS APR trend curve.
 * Amortizes lumpy spending over the capital base and window to prevent single-day distortion.
 */
data class NetAprPoint(
    val date: String,          // "YYYY-MM-DD"
    val grossApr: Double,      // Nominal Gross APR % (e.g. 35.84)
    val netApr: Double,        // Realized Net APR % after living expenses (e.g. 21.07)
    val dailyGross: Double,    // Daily gross yield in USD
    val dailySpend: Double,    // Effective amortized daily spend in USD
    val dailyNet: Double,      // dailyGross - dailySpend in USD
)

/**
 * Summary metrics of portfolio cashflow & compounding trajectory.
 */
data class CashflowKpiSummary(
    val grossApr: Double?,
    val netRunRateApr: Double?,
    val netRunRatePerDay: Double,
    val burnRatioPct: Double?,
    val trailingNetUsd: Double,
    val windowDays: Int,
)

/**
 * Pure calculation engine for cashflow points, Net APR curves, and financial KPIs.
 */
object CashflowEngine {
    fun calculatePoints(
        dailyData: List<DailyYield>,
        spendByDay: Map<String, Double>,
    ): List<DailyCashflowPoint> {
        if (dailyData.isEmpty() && spendByDay.isEmpty()) return emptyList()

        val dailyMap = dailyData.associateBy { it.date }
        val minDate = dailyData.minOfOrNull { it.date } ?: spendByDay.keys.minOrNull() ?: ""
        val maxDate = dailyData.maxOfOrNull { it.date } ?: spendByDay.keys.maxOrNull() ?: ""

        val unionDates = (dailyMap.keys + spendByDay.keys.filter { it in minDate..maxDate })
            .distinct()
            .sorted()

        val rawList = unionDates.map { date ->
            val gross = dailyMap[date]?.yieldUsd ?: 0.0
            val spend = spendByDay[date] ?: 0.0
            val net = gross - spend
            Triple(date, gross, spend) to net
        }

        val maxGross = rawList.maxOfOrNull { it.first.second }?.takeIf { it > 0.0 } ?: 10.0
        val clampFloor = -maxOf(maxGross * 2.5, 50.0)

        return rawList.map { (info, net) ->
            val (date, gross, spend) = info
            val isClamped = net < clampFloor
            val clampedNet = if (isClamped) clampFloor else net
            DailyCashflowPoint(
                date = date,
                gross = gross,
                spend = spend,
                net = net,
                clampedNet = clampedNet,
                isClamped = isClamped,
            )
        }
    }

    fun calculateAprPoints(
        dailyData: List<DailyYield>,
        spendByDay: Map<String, Double>,
        cap: Double,
        grossApr: Double?,
        spending: SpendingState?,
    ): List<NetAprPoint> {
        val basePoints = calculatePoints(dailyData, spendByDay)
        if (basePoints.isEmpty()) return emptyList()

        val safeCap = if (cap > 0.0) cap else 1.0
        val baseGrossApr = grossApr ?: if (dailyData.isNotEmpty()) {
            val avgDaily = dailyData.sumOf { it.yieldUsd } / dailyData.size.coerceAtLeast(1)
            (avgDaily * 365.0 / safeCap) * 100.0
        } else 35.84

        return basePoints.mapIndexed { index, point ->
            val cumSpend = basePoints.take(index + 1).sumOf { it.spend }
            val windowLen = maxOf(index + 1, 7)
            val effectiveSpendPerDay = if (cumSpend > 0) {
                cumSpend / windowLen
            } else if (spending != null && spending.monthUsd > 0) {
                spending.monthUsd / 30.416
            } else 0.0

            val currentGross = point.gross.takeIf { it > 0 } ?: (baseGrossApr * safeCap / 36500.0)
            val dailyNet = currentGross - effectiveSpendPerDay
            val netApr = (dailyNet * 365.0 / safeCap) * 100.0

            NetAprPoint(
                date = point.date,
                grossApr = baseGrossApr,
                netApr = netApr,
                dailyGross = currentGross,
                dailySpend = effectiveSpendPerDay,
                dailyNet = dailyNet,
            )
        }
    }

    fun computeKpis(
        cap: Double,
        currentPerDay: Double?,
        grossApr: Double?,
        spending: SpendingState?,
        points: List<DailyCashflowPoint>,
    ): CashflowKpiSummary {
        val safeCap = if (cap > 0.0) cap else 1.0
        val n = points.size.coerceAtLeast(1)
        val totalGross = points.sumOf { it.gross }
        val totalSpend = points.sumOf { it.spend }
        val trailingNet = totalGross - totalSpend

        val avgDailySpend = if (spending != null && spending.monthUsd > 0.0) {
            spending.monthUsd / 30.416
        } else {
            totalSpend / n
        }

        val dailyGross = currentPerDay ?: if (totalGross > 0.0) totalGross / n else 0.0
        val runRatePerDay = dailyGross - avgDailySpend

        val netApr = if (cap > 0.0) (runRatePerDay * 365.0 / safeCap) * 100.0 else null

        val burnRatio = when {
            dailyGross > 0.0 -> (avgDailySpend / dailyGross) * 100.0
            avgDailySpend > 0.0 -> 100.0
            else -> 0.0
        }

        return CashflowKpiSummary(
            grossApr = grossApr,
            netRunRateApr = netApr,
            netRunRatePerDay = runRatePerDay,
            burnRatioPct = burnRatio,
            trailingNetUsd = trailingNet,
            windowDays = points.size,
        )
    }
}

/**
 * NET APR VS GROSS APR — Compounding Yield Horizon Curve.
 *
 * Visualizes the true capital compounding rate net of living expenses:
 * - Dotted/dashed ceiling line for Gross APR (nominal earning power).
 * - Smooth dynamic curve for Net APR (actual compounding power).
 * - Shaded area between Gross and Net representing lifestyle drag.
 * - Solid break-even baseline at 0% APR.
 * - Draw-phase isolation for 120Hz smooth scrubbing.
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
    cap: Double = 0.0,
    grossApr: Double? = null,
    spending: SpendingState? = null,
) {
    val points = remember(dailyData, spendByDay, cap, grossApr, spending) {
        CashflowEngine.calculateAprPoints(dailyData, spendByDay, cap, grossApr, spending)
    }
    if (points.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    val typography = ChuTypography.current
    val density = LocalDensity.current

    val selectedIndexState = remember { mutableIntStateOf(-1) }

    val rawMaxApr = remember(points) {
        maxOf(points.maxOfOrNull { maxOf(it.grossApr, it.netApr) } ?: 40.0, 30.0)
    }
    val rawMinApr = remember(points) {
        minOf(points.minOfOrNull { it.netApr } ?: 0.0, 0.0)
    }

    val yMax = remember(rawMaxApr) { rawMaxApr * 1.15 }
    val yMin = remember(rawMinApr) { if (rawMinApr < 0) rawMinApr * 1.2 else 0.0 }
    val range = remember(yMax, yMin) { (yMax - yMin).takeIf { it > 0 } ?: 1.0 }

    val labelStyle = remember(textColor, typography) {
        typography.labelSmall.copy(
            color = textColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontFeatureSettings = "tnum",
            fontWeight = FontWeight.Normal,
        )
    }

    val gridLabels = remember(yMin, yMax, labelStyle) {
        (0..3).map { i ->
            val v = yMin + (yMax - yMin) * (i.toDouble() / 3.0)
            textMeasurer.measure(String.format(Locale.US, "%.0f%%", v), labelStyle)
        }
    }

    val tooltipStyles = remember(grossColor, netColor, textColor, typography) {
        Triple(
            typography.labelSmall.copy(color = grossColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold),
            typography.labelSmall.copy(color = netColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold),
            typography.labelSmall.copy(color = textColor, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"),
        )
    }
    val tooltipTitleStyle = remember(tooltipText, typography) {
        typography.labelSmall.copy(color = tooltipText, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)
    }

    val curvePath = remember { Path() }
    val fillPath = remember { Path() }
    val tooltipPath = remember { Path() }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f) }

    val sidePadDp = 8.dp
    val sidePadPx = with(density) { sidePadDp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val plotW = (size.width - sidePadPx * 2).coerceAtLeast(1f)
                        val slotW = plotW / points.size
                        val idx = ((offset.x - sidePadPx) / slotW).toInt().coerceIn(0, points.size - 1)
                        if (selectedIndexState.intValue != idx) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedIndexState.intValue = idx
                        }
                    },
                    onDragEnd = { selectedIndexState.intValue = -1 },
                    onDragCancel = { selectedIndexState.intValue = -1 },
                    onDrag = { change, _ ->
                        val plotW = (size.width - sidePadPx * 2).coerceAtLeast(1f)
                        val slotW = plotW / points.size
                        val idx = ((change.position.x - sidePadPx) / slotW).toInt().coerceIn(0, points.size - 1)
                        if (selectedIndexState.intValue != idx) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedIndexState.intValue = idx
                        }
                    },
                )
            }
            .pointerInput(points) {
                detectTapGestures(
                    onPress = { offset ->
                        val plotW = (size.width - sidePadPx * 2).coerceAtLeast(1f)
                        val slotW = plotW / points.size
                        val idx = ((offset.x - sidePadPx) / slotW).toInt().coerceIn(0, points.size - 1)
                        if (selectedIndexState.intValue != idx) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedIndexState.intValue = idx
                        }
                        tryAwaitRelease()
                        selectedIndexState.intValue = -1
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val topPad = 16.dp.toPx()
            val bottomPad = 18.dp.toPx()
            val sidePad = sidePadPx
            val plotW = w - sidePad * 2
            val plotH = h - topPad - bottomPad
            if (plotW <= 0 || plotH <= 0) return@Canvas

            fun yOf(v: Double): Float = (topPad + (1.0 - (v - yMin) / range) * plotH).toFloat()
            val yZero = yOf(0.0)

            // Draw Horizontal Gridlines
            (0..3).forEach { i ->
                val v = yMin + (yMax - yMin) * (i.toDouble() / 3.0)
                val yLine = yOf(v)
                drawLine(
                    color = gridColor,
                    start = Offset(sidePad, yLine),
                    end = Offset(w - sidePad, yLine),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect,
                )
                val lbl = gridLabels[i]
                drawText(lbl, topLeft = Offset(w - sidePad - lbl.size.width - 2.dp.toPx(), yLine - lbl.size.height / 2f))
            }

            // Prominent break-even baseline at Y = 0%
            drawLine(
                color = textColor.copy(alpha = 0.55f),
                start = Offset(sidePad, yZero),
                end = Offset(w - sidePad, yZero),
                strokeWidth = 1.2.dp.toPx(),
            )

            val n = points.size
            val slotW = plotW / n
            val activeIdx = selectedIndexState.intValue

            // Calculate point coordinates
            val xCoords = FloatArray(n)
            val yCoords = FloatArray(n)
            val yGrossCoords = FloatArray(n)

            for (i in 0 until n) {
                xCoords[i] = sidePad + (i + 0.5f) * slotW
                yCoords[i] = yOf(points[i].netApr)
                yGrossCoords[i] = yOf(points[i].grossApr)
            }

            // 1. Draw Gross APR Benchmark Line (Dashed Line)
            val grossPath = curvePath
            grossPath.rewind()
            grossPath.moveTo(xCoords[0], yGrossCoords[0])
            for (i in 1 until n) {
                val cx = (xCoords[i] + xCoords[i - 1]) / 2f
                grossPath.cubicTo(cx, yGrossCoords[i - 1], cx, yGrossCoords[i], xCoords[i], yGrossCoords[i])
            }
            drawPath(
                path = grossPath,
                color = grossColor.copy(alpha = 0.55f),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = dashEffect,
                    cap = StrokeCap.Round,
                ),
            )

            // 2. Draw Lifestyle Drag / Net Growth Gradient Fill
            fillPath.rewind()
            fillPath.moveTo(xCoords[0], yZero)
            fillPath.lineTo(xCoords[0], yCoords[0])
            for (i in 1 until n) {
                val cx = (xCoords[i] + xCoords[i - 1]) / 2f
                fillPath.cubicTo(cx, yCoords[i - 1], cx, yCoords[i], xCoords[i], yCoords[i])
            }
            fillPath.lineTo(xCoords[n - 1], yZero)
            fillPath.close()

            val latestNetApr = points.lastOrNull()?.netApr ?: 0.0
            val curveColor = if (latestNetApr >= 0) netColor else Color(0xFFF38BA8)

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        curveColor.copy(alpha = 0.25f),
                        curveColor.copy(alpha = 0.03f),
                    ),
                    startY = minOf(yCoords.minOrNull() ?: topPad, topPad),
                    endY = yZero,
                ),
            )

            // 3. Draw Net APR Curve (Solid Cubic Bezier)
            curvePath.rewind()
            curvePath.moveTo(xCoords[0], yCoords[0])
            for (i in 1 until n) {
                val cx = (xCoords[i] + xCoords[i - 1]) / 2f
                curvePath.cubicTo(cx, yCoords[i - 1], cx, yCoords[i], xCoords[i], yCoords[i])
            }
            drawPath(
                path = curvePath,
                color = curveColor,
                style = Stroke(
                    width = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            // 4. Draw Data Point Dots
            val dotRadius = 2.5.dp.toPx()
            for (i in 0 until n) {
                val isSelected = activeIdx == i
                val dotAlpha = if (activeIdx >= 0 && !isSelected) 0.4f else 1.0f
                drawCircle(
                    color = curveColor.copy(alpha = dotAlpha),
                    radius = if (isSelected) dotRadius * 1.6f else dotRadius,
                    center = Offset(xCoords[i], yCoords[i]),
                )
            }

            // 5. Draw Interactive Scrubbing HUD
            if (activeIdx in 0 until n) {
                val p = points[activeIdx]
                val cx = xCoords[activeIdx]
                val cy = yCoords[activeIdx]

                // Vertical scrub cursor
                drawLine(
                    color = textColor.copy(alpha = 0.5f),
                    start = Offset(cx, topPad),
                    end = Offset(cx, topPad + plotH),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect,
                )

                val (grossStyle, netStyle, detailStyle) = tooltipStyles
                val netSign = if (p.netApr >= 0) "+" else ""
                val l0 = textMeasurer.measure(p.date.takeLast(5), tooltipTitleStyle)
                val l1 = textMeasurer.measure("GROSS ${String.format(Locale.US, "%.1f%% APR", p.grossApr)}", grossStyle)
                val l2 = textMeasurer.measure("NET ${String.format(Locale.US, "%s%.1f%% APR", netSign, p.netApr)}", netStyle)
                val l3 = textMeasurer.measure("DAILY NET ${if (p.dailyNet >= 0) "+" else "-"}${DeFiFormatter.formatUsd(abs(p.dailyNet))}/D", detailStyle)

                val padH = 8.dp.toPx()
                val padV = 4.dp.toPx()
                val ttW = maxOf(l0.size.width, l1.size.width, l2.size.width, l3.size.width) + padH * 2
                val ttH = l0.size.height + l1.size.height + l2.size.height + l3.size.height + padV * 2 + 6.dp.toPx()

                var left = cx - ttW / 2f
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
