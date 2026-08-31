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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
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
 * Dong tien mot ngay: yield do duoc, chi tieu thuc, phan ngay do duoc.
 *
 * [coverage] = 1.0 nghia la do tron ngay; 0.56 nghia la scan chi phu duoc 56%
 * cua ngay do — [gross] khi do la so THUC DO, chua phai toc do mot ngay day.
 */
data class DailyCashflowPoint(
    val date: String,          // "YYYY-MM-DD"
    val gross: Double,         // Daily yield in USD
    val spend: Double,         // Daily spend in USD
    val net: Double,           // gross - spend
    val coverage: Double = 1.0,
)

/**
 * Mot ngay tren chart NET RATE: cot len = yield thuc, cot xuong = chi tieu thuc,
 * hai duong trailing = toc do USD/ngay cua gross va cua net.
 *
 * Tat ca deu la USD/ngay — %APR chi la chinh cai truc do nhan voi 365/von,
 * nen mot hinh ve mang duoc ca hai don vi ma khong can hai thang.
 */
data class NetRatePoint(
    val date: String,          // "YYYY-MM-DD"
    val gross: Double,         // USD do duoc trong ngay (cot len)
    val spend: Double,         // USD chi ra trong ngay (cot xuong)
    val coverage: Double,      // Phan cua ngay ma scan phu duoc
    val grossRate: Double,     // gross quy ve mot ngay day du
    val trailGross: Double,    // USD/ngay — trung binh truot cua so
    val trailSpend: Double,    // USD/ngay — chi tieu dan deu tron cua so
    val trailNet: Double,      // trailGross - trailSpend
)

/**
 * KPI tuc thoi cho card RUN-RATE & APR (khong phai chuoi thoi gian).
 */
data class CashflowKpiSummary(
    val grossApr: Double?,
    val netRunRateApr: Double?,
    val netRunRatePerDay: Double,
    val burnRatioPct: Double?,
    val trailingNetUsd: Double,
    val windowDays: Int,
)

object CashflowEngine {
    /** Cua so trailing mac dinh — mot tuan, du de nuot mot lan chi to. */
    const val TRAIL_WINDOW = 7

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

        return unionDates.map { date ->
            val entry = dailyMap[date]
            val gross = entry?.yieldUsd ?: 0.0
            val spend = spendByDay[date] ?: 0.0
            // Ngay chi co chi tieu, khong co ban ghi yield -> coverage = 0:
            // "khong do duoc", khac han voi "do duoc va bang 0".
            DailyCashflowPoint(
                date = date,
                gross = gross,
                spend = spend,
                net = gross - spend,
                coverage = entry?.coverageDays ?: 0.0,
            )
        }
    }

    /**
     * Chuoi cho chart NET RATE. Nhan [points] tu ngoai vao — caller tinh mot lan
     * roi dung chung cho ca card KPI lan chart.
     *
     * Hai quy uoc quan trong:
     * - Gross trailing = tong USD kiem duoc / tong ngay THUC SU do duoc. Ngay scan
     *   thieu khong bi tinh nhu mot ngay day roi keo tut trung binh.
     * - Spend trailing luon chia cho tron cua so. Mot ngay tieu to khong duoc phep
     *   tu lam mau so cho chinh no roi hoa thanh "muc song thuong ngay".
     *
     * [fallbackSpendPerDay] chi dung khi KHONG co du lieu chi tieu theo ngay nao
     * (vi du chi co tong thang) — co du lieu that thi tin du lieu that, ke ca khi
     * ca tuan bang 0.
     */
    fun calculateRatePoints(
        points: List<DailyCashflowPoint>,
        window: Int = TRAIL_WINDOW,
        fallbackSpendPerDay: Double = 0.0,
    ): List<NetRatePoint> {
        if (points.isEmpty()) return emptyList()
        val w = window.coerceAtLeast(1)
        val hasSpendData = points.any { it.spend > 0.0 }

        // Cong don chay theo cua so truot — O(n).
        var sumGross = 0.0
        var sumSpend = 0.0
        var sumCov = 0.0
        return points.mapIndexed { i, p ->
            sumGross += p.gross
            sumSpend += p.spend
            sumCov += p.coverage
            if (i >= w) {
                val out = points[i - w]
                sumGross -= out.gross
                sumSpend -= out.spend
                sumCov -= out.coverage
            }
            val daysInWindow = minOf(i + 1, w)
            val trailGross = when {
                sumCov > MIN_COVERAGE -> sumGross / sumCov
                else -> sumGross / daysInWindow
            }
            val trailSpend = if (hasSpendData) sumSpend / w else fallbackSpendPerDay
            // Do qua it thi khong suy dien toc do ca ngay tu mot mau be xiu.
            val grossRate = if (p.coverage > MIN_COVERAGE) p.gross / p.coverage else p.gross

            NetRatePoint(
                date = p.date,
                gross = p.gross,
                spend = p.spend,
                coverage = p.coverage,
                grossRate = grossRate,
                trailGross = trailGross,
                trailSpend = trailSpend,
                trailNet = trailGross - trailSpend,
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
        val totalCov = points.sumOf { it.coverage }
        val trailingNet = totalGross - totalSpend

        val avgDailySpend = if (spending != null && spending.monthUsd > 0.0) {
            spending.monthUsd / 30.416
        } else {
            totalSpend / n
        }

        // Thieu perday tuc thoi thi suy tu do do: chia cho so ngay DO DUOC.
        val dailyGross = currentPerDay ?: when {
            totalGross <= 0.0 -> 0.0
            totalCov > MIN_COVERAGE -> totalGross / totalCov
            else -> totalGross / n
        }
        val runRatePerDay = dailyGross - avgDailySpend

        val netApr = if (cap > 0.0) (runRatePerDay * 365.0 / safeCap) * 100.0 else null

        // Tieu ma khong co yield thi ty le "tren yield" khong dinh nghia duoc —
        // tra null de UI hien "--", dung bao 100% (nghe nhu vua du, thuc ra dang an vao von).
        val burnRatio = when {
            dailyGross > 0.0 -> (avgDailySpend / dailyGross) * 100.0
            avgDailySpend > 0.0 -> null
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

    /** Duoi nguong nay coi nhu khong do duoc gi — chia cho no chi ra so rac. */
    private const val MIN_COVERAGE = 0.05
}

/**
 * NET RATE — gop "YIELD · DAILY" va "NET APR · TRAILING" lam mot.
 *
 * Hai chart cu ve cung mot hinh: APR ngay = yield ngay x 365/von, tuc la cot
 * yield nhan mot hang so. Chinh cai hang so do cho phep MOT khung ve mang hai
 * don vi — truc trai %APR, truc phai USD/ngay — thay vi hai the.
 *
 * - Cot len  = yield thuc tung ngay (ngay scan thieu: mo hon + khung dut net
 *              chi muc le ra phai toi neu do du ngay).
 * - Cot xuong = chi tieu thuc ngay do. Ngay tieu to hien thanh cai gai chu khong
 *              bi dan phang di mat. Vach 0 vi the co nghia: tren = vao, duoi = ra.
 * - Duong dut = gross trailing, duong lien = net trailing. Khoang ho giua hai
 *              duong chinh la phan chi tieu an mat. Net cat xuong duoi 0 = ngay
 *              run-rate am.
 *
 * [aprFactor] = 365/von*100, tuc %APR ung voi moi 1 USD/ngay. Khong biet von thi
 * truyen null — chart giau truc APR di thay vi bia mot con so.
 */
@Composable
fun NetRateChart(
    points: List<NetRatePoint>,
    grossColor: Color,
    netColor: Color,
    spendColor: Color,
    gridColor: Color,
    textColor: Color,
    tooltipBg: Color,
    tooltipText: Color,
    modifier: Modifier = Modifier,
    aprFactor: Double? = null,
    height: Dp = 200.dp,
) {
    if (points.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    val typography = ChuTypography.current
    val density = LocalDensity.current

    val selectedIndexState = remember { mutableIntStateOf(-1) }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(points.size) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }

    // Thang do: nua tren theo yield, nua duoi theo chi tieu NHUNG bi chan o
    // 1.5 lan nua tren — mot ngay tieu 400$ khong duoc phep ep dep het cot
    // yield 78$. Ngay vuot chan van ve het co va danh dau mui nhon o day.
    val scale = remember(points) {
        val posMax = maxOf(
            points.maxOf { maxOf(it.gross, it.trailGross) },
            0.01,
        )
        val spendMax = points.maxOf { maxOf(it.spend, it.trailSpend) }
        val lineMin = points.minOf { it.trailNet }
        // Cot co the bi cat, duong thi khong bao gio.
        val negNeed = maxOf(minOf(spendMax, posMax * 1.5), if (lineMin < 0) -lineMin else 0.0)
        val yMax = posMax * 1.18
        val yMin = if (negNeed > 0.0) -(negNeed * 1.18) else 0.0
        Triple(yMax, yMin, (yMax - yMin).takeIf { it > 0.0 } ?: 1.0)
    }
    val (yMax, yMin, yRange) = scale

    val labelStyle = remember(textColor, typography) {
        typography.labelSmall.copy(
            color = textColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontFeatureSettings = "tnum",
            fontWeight = FontWeight.Normal,
        )
    }
    val gridValues = remember(yMin, yMax) {
        (0..3).map { i -> yMin + (yMax - yMin) * (i.toDouble() / 3.0) }
    }
    val usdLabels = remember(gridValues, labelStyle) {
        gridValues.map { textMeasurer.measure(DeFiFormatter.formatUsdCompact(it), labelStyle) }
    }
    val aprLabels = remember(gridValues, labelStyle, aprFactor) {
        aprFactor?.let { f -> gridValues.map { textMeasurer.measure(String.format(Locale.US, "%.0f%%", it * f), labelStyle) } }
    }
    // Ba moc ngay: dau — giua — cuoi. Ve nhan cho ca 30 cot thi chi thanh vet mo.
    val dateTicks = remember(points, labelStyle) {
        val idx = listOf(0, points.size / 2, points.size - 1).distinct()
        idx.map { it to textMeasurer.measure(points[it].date.takeLast(5), labelStyle) }
    }

    val grossStyle = remember(grossColor, typography) {
        typography.labelSmall.copy(color = grossColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)
    }
    val spendStyle = remember(spendColor, typography) {
        typography.labelSmall.copy(color = spendColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)
    }
    val netStyle = remember(netColor, typography) {
        typography.labelSmall.copy(color = netColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)
    }
    val titleStyle = remember(tooltipText, typography) {
        typography.labelSmall.copy(color = tooltipText, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)
    }

    val curvePath = remember { Path() }
    val fillPath = remember { Path() }
    val tooltipPath = remember { Path() }
    val markPath = remember { Path() }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f) }
    val barBrush = remember(grossColor) { GradientBrushHolder() }

    val sidePadDp = 8.dp
    val sidePadPx = with(density) { sidePadDp.toPx() }

    fun indexAt(x: Float, width: Int): Int {
        val plotW = (width - sidePadPx * 2).coerceAtLeast(1f)
        val slotW = plotW / points.size
        return ((x - sidePadPx) / slotW).toInt().coerceIn(0, points.size - 1)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val idx = indexAt(offset.x, size.width)
                        if (selectedIndexState.intValue != idx) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedIndexState.intValue = idx
                        }
                    },
                    onDragEnd = { selectedIndexState.intValue = -1 },
                    onDragCancel = { selectedIndexState.intValue = -1 },
                    onDrag = { change, _ ->
                        val idx = indexAt(change.position.x, size.width)
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
                        val idx = indexAt(offset.x, size.width)
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

            val anim = animProgress.value
            fun yOf(v: Double): Float = (topPad + (1.0 - (v - yMin) / yRange) * plotH).toFloat()
            val yZero = yOf(0.0)
            val plotBottom = topPad + plotH

            // 1. Luoi + hai truc: %APR ben trai, USD/ngay ben phai.
            gridValues.forEachIndexed { i, v ->
                val yLine = yOf(v)
                drawLine(
                    color = gridColor,
                    start = Offset(sidePad, yLine),
                    end = Offset(w - sidePad, yLine),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect,
                )
                val usd = usdLabels[i]
                drawText(usd, topLeft = Offset(w - sidePad - usd.size.width - 2.dp.toPx(), yLine - usd.size.height / 2f))
                aprLabels?.get(i)?.let { apr ->
                    drawText(apr, topLeft = Offset(sidePad + 2.dp.toPx(), yLine - apr.size.height / 2f))
                }
            }

            // 2. Vach 0 — ranh gioi tien vao / tien ra.
            drawLine(
                color = textColor.copy(alpha = 0.55f),
                start = Offset(sidePad, yZero),
                end = Offset(w - sidePad, yZero),
                strokeWidth = 1.2.dp.toPx(),
            )

            val n = points.size
            val slotW = plotW / n
            val barW = (slotW * 0.62f).coerceAtLeast(1f)
            val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            val activeIdx = selectedIndexState.intValue

            if (barBrush.geometry != plotH || barBrush.brush == null) {
                barBrush.geometry = plotH
                barBrush.brush = Brush.verticalGradient(
                    colors = listOf(grossColor, grossColor.copy(alpha = 0.45f)),
                    startY = topPad,
                    endY = yZero,
                )
            }

            // 3. Cot: yield len tren, chi tieu xuong duoi.
            for (i in 0 until n) {
                val p = points[i]
                val cx = sidePad + (i + 0.5f) * slotW
                val left = cx - barW / 2f
                val alpha = if (activeIdx < 0 || activeIdx == i) 1f else 0.3f

                if (p.gross > 0.0) {
                    val top = yOf(p.gross * anim)
                    val partial = p.coverage in 0.05..0.95
                    drawRoundRect(
                        brush = barBrush.brush!!,
                        topLeft = Offset(left, top),
                        size = Size(barW, (yZero - top).coerceAtLeast(0f)),
                        cornerRadius = corner,
                        alpha = if (partial) alpha * 0.5f else alpha,
                    )
                    // Ngay scan khong tron: khung dut cho toi muc le ra phai toi.
                    if (partial && p.grossRate > p.gross) {
                        val projTop = yOf(minOf(p.grossRate, yMax) * anim)
                        drawRoundRect(
                            color = grossColor.copy(alpha = alpha * 0.55f),
                            topLeft = Offset(left, projTop),
                            size = Size(barW, (yZero - projTop).coerceAtLeast(0f)),
                            cornerRadius = corner,
                            style = Stroke(width = 1.dp.toPx(), pathEffect = dashEffect),
                        )
                    }
                }

                if (p.spend > 0.0) {
                    val bottomRaw = yOf(-p.spend * anim)
                    val bottom = minOf(bottomRaw, plotBottom)
                    drawRoundRect(
                        color = spendColor.copy(alpha = alpha * 0.85f),
                        topLeft = Offset(left, yZero),
                        size = Size(barW, (bottom - yZero).coerceAtLeast(0f)),
                        cornerRadius = corner,
                    )
                    // Vuot khung: mui nhon o day de biet cot con dai nua.
                    if (bottomRaw > plotBottom + 1f) {
                        markPath.rewind()
                        markPath.moveTo(cx - barW / 2f, plotBottom)
                        markPath.lineTo(cx + barW / 2f, plotBottom)
                        markPath.lineTo(cx, plotBottom + 4.dp.toPx())
                        markPath.close()
                        drawPath(markPath, color = spendColor.copy(alpha = alpha))
                    }
                }
            }

            // 4. Hai duong trailing (USD/ngay — cung thang voi cot).
            val xs = FloatArray(n)
            val yNet = FloatArray(n)
            val yGross = FloatArray(n)
            for (i in 0 until n) {
                xs[i] = sidePad + (i + 0.5f) * slotW
                yNet[i] = yOf(points[i].trailNet * anim)
                yGross[i] = yOf(points[i].trailGross * anim)
            }

            fun buildCurve(path: Path, ys: FloatArray) {
                path.rewind()
                path.moveTo(xs[0], ys[0])
                for (i in 1 until n) {
                    val cx = (xs[i] + xs[i - 1]) / 2f
                    path.cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i])
                }
            }

            buildCurve(curvePath, yGross)
            drawPath(
                path = curvePath,
                color = grossColor.copy(alpha = 0.8f),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashEffect, cap = StrokeCap.Round),
            )

            buildCurve(fillPath, yNet)
            fillPath.lineTo(xs[n - 1], yZero)
            fillPath.lineTo(xs[0], yZero)
            fillPath.close()
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(netColor.copy(alpha = 0.22f), netColor.copy(alpha = 0.02f)),
                    startY = minOf(yNet.minOrNull() ?: topPad, topPad),
                    endY = yZero,
                ),
            )

            buildCurve(curvePath, yNet)
            drawPath(
                path = curvePath,
                color = netColor,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // 5. Moc ngay: dau — giua — cuoi.
            if (activeIdx < 0) {
                dateTicks.forEach { (i, lbl) ->
                    val cx = sidePad + (i + 0.5f) * slotW
                    var lx = cx - lbl.size.width / 2f
                    if (lx < sidePad) lx = sidePad
                    if (lx + lbl.size.width > w - sidePad) lx = w - sidePad - lbl.size.width
                    drawText(lbl, topLeft = Offset(lx, plotBottom + 4.dp.toPx()))
                }
            }

            // 6. HUD khi keo.
            if (activeIdx in 0 until n) {
                val p = points[activeIdx]
                val cx = xs[activeIdx]

                drawLine(
                    color = textColor.copy(alpha = 0.5f),
                    start = Offset(cx, topPad),
                    end = Offset(cx, plotBottom),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect,
                )
                drawCircle(color = netColor, radius = 3.5.dp.toPx(), center = Offset(cx, yNet[activeIdx]))

                val covNote = if (p.coverage in 0.0..0.95) String.format(Locale.US, " %.1fD SCAN", p.coverage) else ""
                val aprNote = aprFactor?.let { String.format(Locale.US, " (%s%.1f%%)", if (p.trailNet >= 0) "+" else "-", abs(p.trailNet * it)) } ?: ""
                val lines = buildList {
                    add(textMeasurer.measure(p.date.takeLast(5) + covNote, titleStyle))
                    add(textMeasurer.measure("YIELD +${DeFiFormatter.formatUsd(p.gross)}", grossStyle))
                    if (p.spend > 0.0) add(textMeasurer.measure("SPEND -${DeFiFormatter.formatUsd(p.spend)}", spendStyle))
                    add(textMeasurer.measure(
                        "NET ${if (p.trailNet >= 0) "+" else "-"}${DeFiFormatter.formatUsd(abs(p.trailNet))}/D$aprNote",
                        netStyle,
                    ))
                }

                val padH = 8.dp.toPx()
                val padV = 4.dp.toPx()
                val gap = 2.dp.toPx()
                val ttW = (lines.maxOf { it.size.width }) + padH * 2
                val ttH = lines.sumOf { it.size.height }.toFloat() + gap * (lines.size - 1) + padV * 2

                var left = cx - ttW / 2f
                if (left + ttW > w - sidePad) left = w - sidePad - ttW
                if (left < sidePad) left = sidePad
                val top = topPad + 2.dp.toPx()

                tooltipPath.rewind()
                tooltipPath.addRoundRect(RoundRect(left, top, left + ttW, top + ttH, CornerRadius(4.dp.toPx(), 4.dp.toPx())))
                drawPath(tooltipPath, color = tooltipBg)

                var ty = top + padV
                lines.forEach { l ->
                    drawText(l, topLeft = Offset(left + padH, ty))
                    ty += l.size.height + gap
                }
            }
        }
    }
}
