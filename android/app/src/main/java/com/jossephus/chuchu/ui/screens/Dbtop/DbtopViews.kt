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
import com.jossephus.chuchu.ui.components.chart.CashflowEngine
import com.jossephus.chuchu.ui.components.chart.CashflowKpiSummary
import com.jossephus.chuchu.ui.components.chart.NetWorthCurveChart
import com.jossephus.chuchu.ui.components.chart.YieldComboChart
import com.jossephus.chuchu.ui.components.chart.YieldNetChart
import com.jossephus.chuchu.ui.theme.CHU_HAIRLINE_ALPHA
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import java.util.Locale
import kotlin.math.abs

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
internal fun PerformanceKpiCard(
    kpis: CashflowKpiSummary,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    ChuCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Row 1: NET RUN-RATE APR & GROSS APR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val netAprText = kpis.netRunRateApr?.let {
                    val sign = if (it >= 0) "+" else ""
                    String.format(Locale.US, "%s%.1f%% APR", sign, it)
                } ?: "--"
                val netColor = when {
                    kpis.netRunRateApr == null -> colors.textMuted
                    kpis.netRunRateApr >= 0 -> colors.success
                    else -> colors.error
                }
                MetricCell(
                    label = "NET RUN-RATE APR",
                    value = netAprText,
                    color = netColor,
                    modifier = Modifier.weight(1f),
                )
                val grossAprText = kpis.grossApr?.let {
                    String.format(Locale.US, "%.1f%% APR", it)
                } ?: "--"
                MetricCell(
                    label = "GROSS APR",
                    value = grossAprText,
                    color = colors.accent,
                    alignEnd = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border.copy(alpha = CHU_HAIRLINE_ALPHA)),
            )

            // Row 2: DAILY NET CASHFLOW & BURN RATIO (RUNWAY)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val netPerDayText = "${if (kpis.netRunRatePerDay >= 0) "+" else "-"}${DeFiFormatter.formatUsd(abs(kpis.netRunRatePerDay))}/D"
                val netPerDayColor = if (kpis.netRunRatePerDay >= 0) colors.success else colors.error
                MetricCell(
                    label = "DAILY NET CASHFLOW",
                    value = netPerDayText,
                    color = netPerDayColor,
                    modifier = Modifier.weight(1f),
                )
                // Bo hau to "(∞)/(DEFICIT)": surplus hay thieu hut da nam o band
                // va o o DAILY NET CASHFLOW ngay ben canh — o nay chi tra loi
                // "chi tieu an bao nhieu phan yield".
                val burnRatioText = kpis.burnRatioPct?.let {
                    String.format(Locale.US, "%.0f%% OF YIELD", it)
                } ?: "--"
                val burnColor = when {
                    kpis.burnRatioPct == null -> colors.textMuted
                    kpis.burnRatioPct <= 50.0 -> colors.success
                    kpis.burnRatioPct <= 100.0 -> colors.warning
                    else -> colors.error
                }
                MetricCell(
                    label = "BURN RATIO",
                    value = burnRatioText,
                    color = burnColor,
                    alignEnd = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun ChartsView(
    netWorth: Double,
    currentPerDay: Double?,
    curve: List<CurvePoint>,
    daily: List<DailyYield>,
    spending: SpendingState? = null,
    spendByDay: Map<String, Double> = spending?.byDay ?: emptyMap(),
    cap: Double = 0.0,
    apr: Double? = null,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    // Mot lan tinh dong tien theo ngay, dung chung cho ca the KPI lan duong Net APR.
    val cashflowPoints = remember(daily, spendByDay) {
        CashflowEngine.calculatePoints(daily, spendByDay)
    }
    val kpiSummary = remember(cap, currentPerDay, apr, spending, cashflowPoints) {
        CashflowEngine.computeKpis(cap, currentPerDay, apr, spending, cashflowPoints)
    }
    val aprPoints = remember(daily, cashflowPoints, cap, apr, spending) {
        CashflowEngine.calculateAprPoints(daily, cashflowPoints, cap, apr, spending)
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "performance_kpis") {
            KohiSectionBand(
                label = "RUN-RATE & APR",
                meta = if (kpiSummary.netRunRatePerDay >= 0) "NET SURPLUS" else "NET DEFICIT",
                containerColor = colors.background,
                accent = if (kpiSummary.netRunRatePerDay >= 0) colors.success else colors.error,
            )
            PerformanceKpiCard(
                kpis = kpiSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
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
                label = "YIELD · DAILY",
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
                            accentColor = colors.accentSecondary,
                            tooltipBg = colors.surfaceVariant,
                            textColor = colors.textSecondary,
                            gridColor = colors.border.copy(alpha = 0.4f),
                            height = 170.dp,
                        )
                    }
                }
            }
        }
        item(key = "net_vs_spend") {
            val netAprVal = kpiSummary.netRunRateApr ?: 0.0
            val netMeta = "${if (netAprVal >= 0) "+" else ""}${String.format(Locale.US, "%.1f%%", netAprVal)} NET APR"
            KohiSectionBand(
                label = "NET APR · TRAILING",
                meta = netMeta,
                containerColor = colors.background,
                accent = if (netAprVal >= 0) colors.success else colors.error,
            )
            ChuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    if (daily.isEmpty()) {
                        ChuText("NO DAILY DATA", style = type.bodySmall, color = colors.textMuted)
                    } else {
                        YieldNetChart(
                            points = aprPoints,
                            grossColor = colors.accent,
                            netColor = if (netAprVal >= 0) colors.success else colors.error,
                            gridColor = colors.border.copy(alpha = 0.4f),
                            textColor = colors.textSecondary,
                            tooltipBg = colors.surfaceVariant,
                            tooltipText = colors.textPrimary,
                            height = 180.dp,
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
internal fun SpendingView(spending: SpendingState?, moneyDisplay: MoneyDisplay = MoneyDisplay.USD) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    if (spending == null) {
        DashboardEmpty("NO SPENDING DATA (SCAN PENDING)")
        return
    }
    val year = spending.month.substringBefore('-')
    // Loc + sap xep + chunk deu nho theo spending: view nay recompose moi lan
    // xoay che do tien (USD -> VND -> AN), khoi lam lai phan viec danh sach.
    val monthRows = remember(spending) {
        spending.byMonth.entries
            .filter { it.key.startsWith(year) }
            .sortedByDescending { it.key }
            .chunked(3)
    }
    val yearTotal = remember(monthRows) { monthRows.sumOf { row -> row.sumOf { it.value } } }
    val dayRows = remember(spending) {
        spending.byDay.entries
            .filter { it.key.startsWith(spending.month) }
            .sortedByDescending { it.key }
            .chunked(2)
    }
    // UI toan tieng Anh (nguyen tac app) — thang hien dang JAN..DEC.
    fun monthAbbr(m: String): String =
        MONTH_ABBR.getOrElse((m.substringAfter('-').toIntOrNull() ?: 1) - 1) { m }

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
                    val rate = spending.usdVnd
                    val neg = if (moneyDisplay == MoneyDisplay.HIDDEN) "" else "-"
                    MetricCell(
                        label = "THIS MONTH",
                        value = neg + formatMoney(spending.monthUsd, moneyDisplay, rate),
                        color = colors.warning,
                    )
                    MetricCell(
                        label = "YEAR $year",
                        value = neg + formatMoney(yearTotal, moneyDisplay, rate),
                        color = colors.textPrimary,
                        alignEnd = true,
                    )
                }
            }
        }
        if (dayRows.isNotEmpty()) {
            item(key = "days_band") {
                KohiSectionBand(
                    label = "BY DAY",
                    meta = monthAbbr(spending.month),
                    containerColor = colors.background,
                )
            }
            items(dayRows, key = { row -> "d-" + row.joinToString("|") { it.key } }) { rowDays ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowDays.forEach { (day, usd) ->
                        SpendCell(
                            label = day.substring(8) + "/" + day.substring(5, 7),
                            value = (if (moneyDisplay == MoneyDisplay.HIDDEN) "" else "-") +
                                formatMoney(usd, moneyDisplay, spending.usdVnd),
                            highlight = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowDays.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (monthRows.isNotEmpty()) {
            item(key = "year_band") { KohiSectionBand(year, containerColor = colors.background) }
            items(monthRows, key = { row -> "m-" + row.joinToString("|") { it.key } }) { rowMonths ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowMonths.forEach { (month, usd) ->
                        SpendCell(
                            label = monthAbbr(month),
                            value = (if (moneyDisplay == MoneyDisplay.HIDDEN) "" else "-") +
                                formatMoney(usd, moneyDisplay, spending.usdVnd, compact = true),
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

private val MONTH_ABBR = arrayOf(
    "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
    "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
)

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
