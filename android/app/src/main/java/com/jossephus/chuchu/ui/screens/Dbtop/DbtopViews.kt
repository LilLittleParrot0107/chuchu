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
 * Tab SPENDING — chi xoay quanh HIEN TAI (user chot 26/8, hai vong gop y):
 * card THANG NAY + tong NAM NAY, duoi la tung thang cua nam nay. KHONG co
 * all-time: 2 vi la dia chi nap san tu 2023, cong don ca lich su ra con so
 * $250k vo nghia voi cau hoi "dang tieu bao nhieu". Khong co danh sach
 * tung giao dich — chi tiet nam o ledger.jsonl tren Legion.
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
    val mono = type.label.copy(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Bold,
    )

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
        if (thisYearMonths.isNotEmpty()) {
            item(key = "year_band") { KohiSectionBand(year, containerColor = colors.background) }
            items(thisYearMonths, key = { "m-${it.key}" }) { (month, usd) ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChuText(
                            "THG ${month.substringAfter('-').trimStart('0')}",
                            style = type.labelSmall,
                            color = if (month == spending.month) colors.textPrimary else colors.textSecondary,
                        )
                        ChuText(
                            "-${DeFiFormatter.formatUsd(usd)}",
                            style = mono,
                            color = if (month == spending.month) colors.warning else colors.textPrimary,
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
        }
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
