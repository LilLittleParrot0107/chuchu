package com.jossephus.chuchu.ui.screens.Dbtop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.data.model.dbtop.CurvePoint
import com.jossephus.chuchu.data.model.dbtop.DailyYield
import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import com.jossephus.chuchu.data.model.dbtop.SpendingState
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.components.chart.YieldComboChart
import com.jossephus.chuchu.ui.components.chart.NetWorthCurveChart
import com.jossephus.chuchu.ui.theme.CHU_HAIRLINE_ALPHA
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import java.util.Locale

@Composable
internal fun WatchlistView(
    items: List<WatchlistTokenItem>,
) {
    if (items.isEmpty()) {
        DashboardEmpty("NO TOKENS IN WATCHLIST")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(items, key = { it.symbol }) { token ->
            WatchlistTokenRow(token = token)
        }
    }
}

@Composable
private fun WatchlistTokenRow(
    token: WatchlistTokenItem,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(colors.accent, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            ChuText(
                text = token.symbol,
                style = type.body.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ChuText(
                text = if (token.price > 0.0) DeFiFormatter.formatTokenPrice(token.price) else "—",
                style = type.body.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.textPrimary,
                maxLines = 1,
            )
            // Cot % 24h (user 27/8) — so voi px24 tu snapshot debank ~24h
            // truoc, KHONG phai pxPrev (gia lan quet truoc, 30 phut).
            val pct = token.changePct24h
            ChuText(
                text = pct?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "—",
                style = type.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                ),
                color = when {
                    pct == null -> colors.textMuted
                    pct >= 0 -> colors.success
                    else -> colors.error
                },
                maxLines = 1,
                modifier = Modifier.width(64.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border.copy(alpha = CHU_HAIRLINE_ALPHA)),
        )
    }
}

@Composable
internal fun ChartsView(
    netWorth: Double,
    currentPerDay: Double?,
    curve: List<CurvePoint>,
    daily: List<DailyYield>,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "net_worth_curve") {
            KohiSectionBand("NET WORTH CURVE", DeFiFormatter.formatUsd(netWorth), containerColor = colors.background)
            ChuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    if (curve.isEmpty()) {
                        ChuText("NO CURVE DATA", style = type.labelSmall, color = colors.textMuted)
                    } else {
                        NetWorthCurveChart(
                            points = curve,
                            lineColor = colors.accent,
                            tooltipBg = colors.surfaceVariant,
                            tooltipText = colors.textPrimary,
                            gridColor = colors.border.copy(alpha = 0.4f),
                            textColor = colors.textSecondary,
                            markerColor = colors.textPrimary,
                            height = 180.dp,
                        )
                    }
                }
            }
        }
        item(key = "daily_yield") {
            KohiSectionBand(
                label = "YIELD · DAILY + ACCUM",
                meta = currentPerDay?.let { "+${DeFiFormatter.formatUsd(it)}/D" } ?: "SCAN OFFLINE",
                containerColor = colors.background,
                accent = if (currentPerDay != null) colors.success else colors.error,
            )
            ChuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    if (daily.isEmpty()) {
                        ChuText("NO DAILY YIELD DATA", style = type.bodySmall, color = colors.textMuted)
                    } else {
                        YieldComboChart(
                            dailyData = daily,
                            barColor = colors.accent,
                            accumColor = colors.success,
                            // accentColor phai KHAC accumColor: no la day duoi
                            // gradient cua bar va dong tooltip thu 3 — trung mau
                            // thi line/bar lan nhau, tooltip 3 dong doc nhu 2.
                            accentColor = colors.accentSecondary,
                            tooltipBg = colors.surfaceVariant,
                            textColor = colors.textSecondary,
                            gridColor = colors.border.copy(alpha = 0.4f),
                            height = 190.dp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab SPENDING — bo cuc LUOI thay list doc (user 27/8: "khong bi dang list
 * dai dang dac"): card THANG NAY / NAM NAY tren cung, duoi la "THEO NGAY"
 * (chi ngay co chi tieu, luoi 2 cot) roi luoi thang cua nam nay (3 cot).
 * Khong all-time (luat 26/8), khong danh sach giao dich (ledger.jsonl giu).
 */
@Composable
internal fun SpendingView(spending: SpendingState?) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    if (spending == null) {
        DashboardEmpty("NO SPENDING DATA (SCAN PENDING)")
        return
    }
    val year = spending.month.substringBefore('-')
    val thisYearMonths = spending.byMonth.entries
        .filter { it.key.startsWith(year) }
        .sortedByDescending { it.key }
    val days = spending.byDay.entries.sortedByDescending { it.key }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        item(key = "summary") {
            ChuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricCell(
                        label = "THIS MONTH",
                        value = "-${DeFiFormatter.formatUsd(spending.monthUsd)}",
                        color = colors.warning,
                    )
                    MetricCell(
                        label = "YEAR $year",
                        value = "-${DeFiFormatter.formatUsd(thisYearMonths.sumOf { it.value })}",
                        color = colors.textPrimary,
                        alignEnd = true,
                    )
                }
            }
        }
        if (days.isNotEmpty()) {
            item(key = "days_band") {
                KohiSectionBand(
                    label = "THEO NGÀY",
                    meta = "THG ${spending.month.substringAfter('-').trimStart('0')}",
                    containerColor = colors.background,
                )
            }
            items(days.chunked(2), key = { row -> "d-" + row.joinToString("|") { it.key } }) { rowDays ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowDays.forEach { (day, usd) ->
                        SpendCell(
                            label = day.substring(8) + "/" + day.substring(5, 7),
                            value = "-${DeFiFormatter.formatUsd(usd)}",
                            highlight = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowDays.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (thisYearMonths.isNotEmpty()) {
            item(key = "year_band") { KohiSectionBand(year, containerColor = colors.background) }
            items(thisYearMonths.chunked(3), key = { row -> "m-" + row.joinToString("|") { it.key } }) { rowMonths ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowMonths.forEach { (month, usd) ->
                        SpendCell(
                            label = "THG " + month.substringAfter('-').trimStart('0'),
                            value = "-${DeFiFormatter.formatUsdCompact(usd)}",
                            highlight = month == spending.month,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - rowMonths.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** O luoi chi tieu: label nho tren, so mono dam duoi, vien hairline. */
@Composable
private fun SpendCell(
    label: String,
    value: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Column(
        modifier = modifier
            .background(colors.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        ChuText(
            label,
            style = type.labelSmall,
            color = if (highlight) colors.warning else colors.textMuted,
            maxLines = 1,
        )
        ChuText(
            value,
            style = type.label.copy(
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.Bold,
            ),
            color = if (highlight) colors.warning else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun DashboardEmpty(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ChuText(text, style = ChuTypography.current.body, color = ChuColors.current.textMuted)
    }
}

@Composable
internal fun DashboardHint(text: String) {
    ChuText(
        text,
        style = ChuTypography.current.labelSmall,
        color = ChuColors.current.textMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .background(ChuColors.current.background)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    )
}
