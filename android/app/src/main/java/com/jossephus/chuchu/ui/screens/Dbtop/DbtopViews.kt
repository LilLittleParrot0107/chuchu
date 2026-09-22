package com.jossephus.chuchu.ui.screens.Dbtop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.jossephus.chuchu.data.model.dbtop.DayTx
import com.jossephus.chuchu.data.model.dbtop.flowDayRows
import com.jossephus.chuchu.data.model.dbtop.FlowState
import com.jossephus.chuchu.data.model.dbtop.SpendingState
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
 * 22/9 (user chốt "kiểu 2" sau 8 vòng prototype): phần chi tiêu y bản 27/8 — card, lưới BY DAY
 * hai cột, lưới tháng — chỉ đổi ô phải của card thành FLOW · <tháng> (ròng USDC/USDT/USDG của ví
 * chính, dòng nhỏ vào · ra) và tổng năm dời xuống meta dải năm. FLOW là PHÂN VÙNG RIÊNG ở cuối, luôn hiện (user bỏ dải
 * gấp 22/9 tối): dải FLOW + lưới ngày ô đôi +in · −out, số không lẻ, chữ nhỏ cho khỏi lẹm;
 * chạm ô ngày FLOW mở tấm liệt kê từng lệnh.
 * Chạm ô ngày chi tiêu không làm gì. Chưa có flow.json → ô phải hiện tổng năm, không có phân vùng.
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
    val dayRows = remember(spending) {
        spending.byDay.entries
            .filter { it.key.startsWith(spending.month) }
            .sortedByDescending { it.key }
            .chunked(2)
    }
    // flow chỉ dùng khi cùng tháng với spending — lệch tháng là server chưa quét tới.
    val flowMonth = flow?.takeIf { it.month == spending.month }
    val flowRows = remember(flowMonth) {
        flowMonth?.byDay?.entries.orEmpty()
            .filter { it.key.startsWith(spending.month) }
            .sortedByDescending { it.key }
            .chunked(2)
    }
    // UI toan tieng Anh (nguyen tac app) — thang hien dang JAN..DEC.
    fun monthAbbr(m: String): String =
        MONTH_ABBR.getOrElse((m.substringAfter('-').toIntOrNull() ?: 1) - 1) { m }
    val rate = spending.usdVnd
    val hidden = moneyDisplay == MoneyDisplay.HIDDEN
    val neg = if (hidden) "" else "-"
    val pos = if (hidden) "" else "+"
    fun money(v: Double, compact: Boolean = false, decimals: Int = 2) = formatMoney(v, moneyDisplay, rate, compact, decimals)
    var openDay by remember { mutableStateOf<String?>(null) }
    openDay?.let { day ->
        FlowDaySheet(
            day = day,
            rows = remember(day, flowMonth) { flowDayRows(day, flowMonth) },
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
                                ChuText(pos + money(flowMonth.monthIn), style = type.labelSmall, color = colors.success)
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
                            value = neg + money(usd),
                            highlight = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowDays.size == 1) Spacer(Modifier.weight(1f))
                }
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
        if (flowMonth != null) {
            // Phân vùng FLOW luôn hiện: dải chuẩn như BY DAY, rồi lưới ngày ô đôi.
            item(key = "flow_band") {
                KohiSectionBand(
                    label = "FLOW",
                    meta = monthAbbr(flowMonth.month) + " · in / out",
                    containerColor = colors.background,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (flowRows.isEmpty()) {
                item(key = "flow_empty") { DashboardHint("no transfers this month yet") }
            }
            items(flowRows, key = { row -> "f-" + row.joinToString("|") { it.key } }) { rowDays ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowDays.forEach { (day, fd) ->
                        FlowCell(
                            label = day.substring(8) + "/" + day.substring(5, 7),
                            // số không lẻ + chữ nhỏ: ô 2 cột ở 360dp từng lẹm "-$983.00" thành "-$98" (ảnh 22/9 19:40)
                            inText = if (fd.inUsd > 0) pos + money(fd.inUsd, decimals = 0) else null,
                            outText = if (fd.out > 0) neg + money(fd.out, decimals = 0) else null,
                            selected = openDay == day,
                            onClick = { openDay = day },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowDays.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Ô ngày của phân vùng FLOW: ngày trên, dưới là +in · −out cùng một dòng; thiếu số ghi "0" mờ. Chạm mở tấm chi tiết. */
@Composable
private fun FlowCell(
    label: String,
    inText: String?,
    outText: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val num = type.labelSmall.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)
    Column(
        modifier = modifier
            .background(if (selected) colors.accent.copy(alpha = 0.08f) else colors.surface)
            .border(1.dp, if (selected) colors.accent.copy(alpha = 0.6f) else colors.border)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        ChuText(label, style = type.labelSmall, color = colors.textMuted, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (inText != null) ChuText(inText, style = num, color = colors.success, maxLines = 1)
            else ChuText("0", style = type.labelSmall, color = colors.textMuted, maxLines = 1)
            ChuText(" · ", style = type.labelSmall, color = colors.textMuted)
            if (outText != null) ChuText(outText, style = num, color = colors.warning, maxLines = 1)
            else ChuText("0", style = type.labelSmall, color = colors.textMuted, maxLines = 1)
        }
    }
}

/**
 * Tấm chi tiết một ngày FLOW (prototype kohi-spend-v2-detail-prototype.html F, user chốt 22/9):
 * đầu là ngày + tổng vào · ra, dưới là từng lệnh giờ · số tiền có dấu · token. Không mũi tên
 * (user bỏ), không đối tác, không chỉ dẫn.
 */
@Composable
private fun FlowDaySheet(
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
    val sumIn = rows.filter { it.usd > 0 }.sumOf { it.usd }
    val sumOut = rows.filter { it.usd < 0 }.sumOf { -it.usd }
    ChuBottomSheet(onDismiss = onDismiss) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ChuText(
                day.substring(8) + "/" + day.substring(5, 7) + " · FLOW",
                style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.accent,
            )
            Spacer(Modifier.weight(1f))
            ChuText((if (hidden) "" else "+") + money(sumIn), style = type.labelSmall, color = colors.success)
            ChuText(" · ", style = type.labelSmall, color = colors.textMuted)
            ChuText((if (hidden) "" else "-") + money(sumOut), style = type.labelSmall, color = colors.warning)
        }
        if (rows.isEmpty()) {
            ChuText("no transfers", style = type.labelSmall, color = colors.textMuted)
        }
        rows.forEachIndexed { i, tx ->
            val inbound = tx.usd >= 0
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
                Spacer(Modifier.weight(1f))
                ChuText(
                    (if (hidden) "" else if (inbound) "+" else "-") + money(kotlin.math.abs(tx.usd)),
                    style = numStyle,
                    color = if (inbound) colors.success else colors.warning,
                )
                ChuText(
                    tx.token,
                    style = type.labelSmall,
                    color = colors.textMuted,
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
