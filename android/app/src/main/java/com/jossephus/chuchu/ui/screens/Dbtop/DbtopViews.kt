package com.jossephus.chuchu.ui.screens.Dbtop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.jossephus.chuchu.data.model.dbtop.DayFlowRow
import com.jossephus.chuchu.data.model.dbtop.DayTx
import com.jossephus.chuchu.data.model.dbtop.dayTransactions
import com.jossephus.chuchu.data.model.dbtop.FlowState
import com.jossephus.chuchu.data.model.dbtop.SpendingState
import com.jossephus.chuchu.data.model.dbtop.dayFlowRows
import com.jossephus.chuchu.ui.components.ChuBottomSheet
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.components.noRippleClickable
import com.jossephus.chuchu.ui.components.chart.CashflowEngine
import com.jossephus.chuchu.ui.components.chart.CashflowKpiSummary
import com.jossephus.chuchu.ui.components.chart.NetWorthCurveChart
import com.jossephus.chuchu.ui.components.chart.NetRateChart
import com.jossephus.chuchu.ui.theme.CHU_HAIRLINE_ALPHA
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import java.text.SimpleDateFormat
import java.util.Date
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
            val isZero = pct == null || kotlin.math.abs(pct) < 0.05
            ChuText(
                text = pct?.let {
                    if (kotlin.math.abs(it) < 0.05) "0.0%" else String.format(Locale.US, "%+.1f%%", it)
                } ?: "—",
                style = type.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                ),
                color = when {
                    isZero -> colors.textMuted
                    pct >= 0.05 -> colors.success
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
                    // Nhãn ô đã ghi "APR" rồi, lặp lại trong giá trị là thừa (user 3/9).
                    String.format(Locale.US, "%s%.1f%%", sign, it)
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
                    String.format(Locale.US, "%.1f%%", it)
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
                // Đuôi "(∞)/(DEFICIT)" bỏ từ 30/8, đuôi "OF YIELD" bỏ 3/9: nhãn
                // BURN RATIO đã nói nó là tỉ lệ trên yield, viết lại là thừa.
                val burnRatioText = kpis.burnRatioPct?.let {
                    String.format(Locale.US, "%.0f%%", it)
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

    // Mot lan tinh dong tien theo ngay, dung chung cho ca the KPI lan chart NET RATE.
    val cashflowPoints = remember(daily, spendByDay) {
        CashflowEngine.calculatePoints(daily, spendByDay)
    }
    // Chi dung tong thang lam chi tieu/ngay khi KHONG co du lieu theo ngay nao.
    val fallbackSpendPerDay = spending?.monthUsd?.takeIf { it > 0.0 }?.div(30.416) ?: 0.0
    val ratePoints = remember(cashflowPoints, fallbackSpendPerDay) {
        CashflowEngine.calculateRatePoints(cashflowPoints, fallbackSpendPerDay = fallbackSpendPerDay)
    }
    // Card KPI lay dung muc chi cua diem cuoi chart -> card va duong ve cung mot so.
    val kpiSummary = remember(cap, currentPerDay, apr, ratePoints, cashflowPoints) {
        CashflowEngine.computeKpis(cap, currentPerDay, apr, ratePoints.lastOrNull()?.trailSpend, cashflowPoints)
    }
    // %APR ung voi moi 1 USD/ngay — chinh he so bien truc USD thanh truc APR.
    val aprFactor = remember(cap) { if (cap > 0.0) 365.0 / cap * 100.0 else null }

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
        item(key = "net_rate") {
            val netAprVal = kpiSummary.netRunRateApr
            val perDay = kpiSummary.netRunRatePerDay
            val meta = when {
                currentPerDay == null && daily.isEmpty() -> "SCAN OFFLINE"
                netAprVal != null -> "${if (netAprVal >= 0) "+" else ""}${String.format(Locale.US, "%.1f%% NET APR", netAprVal)}"
                else -> "${if (perDay >= 0) "+" else "-"}${DeFiFormatter.formatUsd(abs(perDay))}/D NET"
            }
            KohiSectionBand(
                label = "NET RATE · TRAILING",
                meta = meta,
                containerColor = colors.background,
                accent = if (perDay >= 0) colors.success else colors.error,
            )
            ChuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    if (ratePoints.isEmpty()) {
                        ChuText("NO DAILY YIELD DATA", style = type.bodySmall, color = colors.textMuted)
                    } else {
                        NetRateChart(
                            points = ratePoints,
                            grossColor = colors.accent,
                            netColor = if (perDay >= 0) colors.success else colors.error,
                            // KHÔNG dùng warning: cam cạnh vàng (yield) nhìn lẫn (user 5/9).
                            // Cũng không dùng error: lúc chi vượt yield, đường NET đỏ sẽ
                            // chìm vào cột đỏ — đúng lúc cần đọc nhất.
                            spendColor = colors.accentSecondary,
                            gridColor = colors.border.copy(alpha = 0.4f),
                            textColor = colors.textSecondary,
                            tooltipBg = colors.surfaceVariant,
                            tooltipText = colors.textPrimary,
                            aprFactor = aprFactor,
                            height = 200.dp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab SPENDING — bố cục lưới, không all-time (luật 26/8), không danh sách giao dịch.
 * 22/9 (user chốt V3 sau 4 vòng prototype): card đầu = THIS MONTH (chi tiêu) · FLOW · <tháng>
 * (ròng USDC/USDT của ví chính, dòng nhỏ vào · ra); BY DAY là BẢNG bốn cột day · spend · in ·
 * out, mọi hàng cùng cỡ, thiếu số ghi "0" mờ chứ không bỏ trống; tổng năm là meta của dải năm.
 * Chưa có flow.json (server cũ) → ô phải hiện tổng năm như trước, bảng chỉ còn cột spend.
 */
@Composable
internal fun SpendingView(
    spending: SpendingState?,
    flow: FlowState? = null,
    moneyDisplay: MoneyDisplay = MoneyDisplay.USD,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    if (spending == null) {
        DashboardEmpty("NO SPENDING DATA (SCAN PENDING)")
        return
    }
    val year = spending.month.substringBefore('-')
    val monthRows = remember(spending) {
        spending.byMonth.entries
            .filter { it.key.startsWith(year) }
            .sortedByDescending { it.key }
            .chunked(3)
    }
    val yearTotal = remember(monthRows) { monthRows.sumOf { row -> row.sumOf { it.value } } }
    // flow chỉ dùng khi cùng tháng với spending — lệch tháng là server chưa quét tới.
    val flowMonth = flow?.takeIf { it.month == spending.month }
    val dayRows = remember(spending, flowMonth) { dayFlowRows(spending, flowMonth) }
    // UI toan tieng Anh (nguyen tac app) — thang hien dang JAN..DEC.
    fun monthAbbr(m: String): String =
        MONTH_ABBR.getOrElse((m.substringAfter('-').toIntOrNull() ?: 1) - 1) { m }
    val rate = spending.usdVnd
    val hidden = moneyDisplay == MoneyDisplay.HIDDEN
    val neg = if (hidden) "" else "-"
    fun money(v: Double, compact: Boolean = false) = formatMoney(v, moneyDisplay, rate, compact)
    // Chạm một ngày trên bảng → tấm trượt từ đáy liệt kê từng lệnh của ngày đó (user chốt B, 22/9).
    var openDay by remember { mutableStateOf<String?>(null) }
    openDay?.let { day ->
        DayDetailSheet(
            day = day,
            rows = remember(day, spending, flowMonth) { dayTransactions(day, spending, flowMonth) },
            hidden = hidden,
            money = { money(it) },
            onDismiss = { openDay = null },
        )
    }

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
                        value = neg + money(spending.monthUsd),
                        color = colors.warning,
                    )
                    if (flowMonth != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            MetricCell(
                                label = "FLOW · ${monthAbbr(flowMonth.month)}",
                                value = if (!hidden && flowMonth.monthNet > 0) "+" + money(flowMonth.monthNet) else money(flowMonth.monthNet),
                                color = if (flowMonth.monthNet >= 0) colors.accent else colors.warning,
                                alignEnd = true,
                            )
                            Row {
                                ChuText((if (hidden) "" else "+") + money(flowMonth.monthIn), style = type.labelSmall, color = colors.success)
                                ChuText(" · ", style = type.labelSmall, color = colors.textMuted)
                                ChuText(neg + money(flowMonth.monthOut), style = type.labelSmall, color = colors.warning)
                            }
                        }
                    } else {
                        MetricCell(
                            label = "YEAR $year",
                            value = neg + money(yearTotal),
                            color = colors.textPrimary,
                            alignEnd = true,
                        )
                    }
                }
            }
        }
        if (dayRows.isNotEmpty()) {
            item(key = "days_band") {
                KohiSectionBand(
                    label = "BY DAY",
                    meta = monthAbbr(spending.month) + if (flowMonth != null) " · spend · in · out" else "",
                    containerColor = colors.background,
                )
            }
            item(key = "days_table") {
                DayFlowTable(
                    rows = dayRows,
                    showFlow = flowMonth != null,
                    hidden = hidden,
                    money = { money(it) },
                    onDayClick = { openDay = it },
                )
            }
        }
        if (monthRows.isNotEmpty()) {
            item(key = "year_band") {
                KohiSectionBand(year, meta = neg + money(yearTotal), containerColor = colors.background)
            }
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
                            value = neg + money(usd, compact = true),
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

/**
 * Bảng BY DAY (user chốt V3, 22/9): một hàng một ngày, số căn phải theo cột để quét dọc được;
 * mọi hàng cùng cao, thiếu số ghi "0" mờ giữ cột thẳng. Đầu bảng nền surfaceVariant.
 */
@Composable
private fun DayFlowTable(
    rows: List<DayFlowRow>,
    showFlow: Boolean,
    hidden: Boolean,
    money: (Double) -> String,
    onDayClick: (String) -> Unit = {},
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val numStyle = type.label.copy(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Bold,
    )

    @Composable
    fun RowScope.Head(text: String, end: Boolean = true) {
        Box(Modifier.weight(1f), contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart) {
            ChuText(text, style = type.labelSmall, color = colors.textMuted, maxLines = 1)
        }
    }

    @Composable
    fun RowScope.Num(value: Double, prefix: String, color: androidx.compose.ui.graphics.Color) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (value <= 0.0) {
                ChuText("0", style = type.labelSmall, color = colors.textMuted, maxLines = 1)
            } else {
                ChuText((if (hidden) "" else prefix) + money(value), style = numStyle, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .border(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChuText("DAY", style = type.labelSmall, color = colors.textMuted, maxLines = 1, modifier = Modifier.width(44.dp))
            Head("SPEND")
            if (showFlow) {
                Head("IN")
                Head("OUT")
            }
        }
        rows.forEachIndexed { i, r ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable { onDayClick(r.day) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText(
                    r.day.substring(8) + "/" + r.day.substring(5, 7),
                    style = type.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    modifier = Modifier.width(44.dp),
                )
                Num(r.spend, "-", colors.warning)
                if (showFlow) {
                    Num(r.inUsd, "+", colors.success)
                    Num(r.out, "-", colors.warning)
                }
            }
            if (i < rows.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = CHU_HAIRLINE_ALPHA)))
            }
        }
    }
}

/**
 * Tấm chi tiết một ngày (prototype kohi-spend-day-detail-prototype.html B, user chốt 22/9): đầu là
 * ngày + tổng vào / ra / spend, dưới là từng lệnh giờ · chiều · số tiền · token. Tiền tới ví
 * spending in cam kèm chữ "spend". Không đối tác, không chỉ dẫn.
 */
@Composable
private fun DayDetailSheet(
    day: String,
    rows: List<DayTx>,
    hidden: Boolean,
    money: (Double) -> String,
    onDismiss: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val numStyle = type.label.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)
    val clock = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val sumIn = rows.filter { it.kind == "in" }.sumOf { it.usd }
    val sumOut = rows.filter { it.kind == "out" }.sumOf { it.usd }
    val sumSpend = rows.filter { it.kind == "spend" }.sumOf { it.usd }
    ChuBottomSheet(onDismiss = onDismiss) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ChuText(
                day.substring(8) + "/" + day.substring(5, 7),
                style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.accent,
            )
            Spacer(Modifier.weight(1f))
            if (sumIn > 0) ChuText((if (hidden) "" else "+") + money(sumIn), style = type.labelSmall, color = colors.success)
            if (sumIn > 0 && (sumOut > 0 || sumSpend > 0)) ChuText(" · ", style = type.labelSmall, color = colors.textMuted)
            if (sumOut > 0) ChuText((if (hidden) "" else "-") + money(sumOut), style = type.labelSmall, color = colors.warning)
            if (sumOut > 0 && sumSpend > 0) ChuText(" · ", style = type.labelSmall, color = colors.textMuted)
            if (sumSpend > 0) ChuText("spend " + (if (hidden) "" else "-") + money(sumSpend), style = type.labelSmall, color = colors.warning)
        }
        if (rows.isEmpty()) {
            ChuText("no transactions", style = type.labelSmall, color = colors.textMuted)
        }
        rows.forEachIndexed { i, tx ->
            val inbound = tx.kind == "in"
            val color = if (inbound) colors.success else colors.warning
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText(
                    remember(tx.ts) { clock.format(Date(tx.ts * 1000)) },
                    style = type.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.width(44.dp),
                )
                ChuText(if (inbound) "←" else "→", style = type.label.copy(fontWeight = FontWeight.Bold), color = color, modifier = Modifier.width(18.dp))
                Spacer(Modifier.weight(1f))
                ChuText((if (hidden) "" else if (inbound) "+" else "-") + money(tx.usd), style = numStyle, color = color)
                ChuText(
                    if (tx.kind == "spend") "spend" else tx.token,
                    style = type.labelSmall,
                    color = if (tx.kind == "spend") colors.warning else colors.textMuted,
                    modifier = Modifier.width(52.dp).padding(start = 8.dp),
                )
            }
            if (i < rows.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = CHU_HAIRLINE_ALPHA)))
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
