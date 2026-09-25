package com.jossephus.chuchu.ui.screens.Dbtop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.jossephus.chuchu.ui.components.KohiCompactAction
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.data.model.dbtop.DailyYield
import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import com.jossephus.chuchu.data.model.dbtop.DayTx
import com.jossephus.chuchu.data.model.dbtop.FlowDay
import com.jossephus.chuchu.data.model.dbtop.flowDayRows
import com.jossephus.chuchu.data.model.dbtop.FlowState
import com.jossephus.chuchu.data.model.dbtop.SpendingState
import com.jossephus.chuchu.ui.components.KohiBottomSheet
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.components.noRippleClickable
import com.jossephus.chuchu.ui.components.chart.CashflowEngine
import com.jossephus.chuchu.ui.components.chart.CashflowKpiSummary
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

/**
 * Phân vùng NET RATE — từ 26/9 nằm trong tab SPEND (user chốt: "gộp chart vào
 * spend"), không còn tab CHART riêng. Bảng KPI + curve NET WORTH đã lên bảng
 * summary đầu màn nên ở đây chỉ còn chart.
 */
@Composable
internal fun NetRateSection(
    currentPerDay: Double?,
    daily: List<DailyYield>,
    spending: SpendingState? = null,
    spendByDay: Map<String, Double> = spending?.byDay ?: emptyMap(),
    cap: Double = 0.0,
    kpis: CashflowKpiSummary,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    val cashflowPoints = remember(daily, spendByDay) {
        CashflowEngine.calculatePoints(daily, spendByDay)
    }
    // Chi dung tong thang lam chi tieu/ngay khi KHONG co du lieu theo ngay nao.
    val fallbackSpendPerDay = spending?.monthUsd?.takeIf { it > 0.0 }?.div(30.416) ?: 0.0
    val ratePoints = remember(cashflowPoints, fallbackSpendPerDay) {
        CashflowEngine.calculateRatePoints(cashflowPoints, fallbackSpendPerDay = fallbackSpendPerDay)
    }
    // %APR ung voi moi 1 USD/ngay — chinh he so bien truc USD thanh truc APR.
    val aprFactor = remember(cap) { if (cap > 0.0) 365.0 / cap * 100.0 else null }

    val netAprVal = kpis.netRunRateApr
    val perDay = kpis.netRunRatePerDay
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

/**
 * Tab SPENDING — bố cục lưới, không all-time (luật 26/8), không danh sách giao dịch.
 * 22/9 (user chốt "kiểu 2" sau 8 vòng prototype): phần chi tiêu y bản 27/8 — card, lưới BY DAY
 * hai cột, lưới tháng — chỉ đổi ô phải của card thành FLOW · <tháng> (ròng USDC/USDT/USDG của ví
 * chính, dòng nhỏ vào · ra) và tổng năm dời xuống meta dải năm. FLOW là PHÂN VÙNG RIÊNG ở cuối,
 * dải GẤP (user 22/9 tối gọi lại: "có ẩn hiện như lúc trước"); xổ ra là BẢNG ngày · in · out như
 * bản 1.62.3, chạm hàng mở tấm chi tiết ngày cùng khung với tấm vị thế.
 * Chạm ô ngày chi tiêu không làm gì. Chưa có flow.json → ô phải hiện tổng năm, không có phân vùng.
 */
@Composable
internal fun SpendingView(
    spending: SpendingState?,
    flow: FlowState? = null,
    moneyDisplay: MoneyDisplay = MoneyDisplay.USD,
    currentPerDay: Double? = null,
    daily: List<DailyYield> = emptyList(),
    cap: Double = 0.0,
    kpis: CashflowKpiSummary,
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
    val flowDays = remember(flowMonth) {
        flowMonth?.byDay?.entries.orEmpty()
            .filter { it.key.startsWith(spending.month) }
            .sortedByDescending { it.key }
            .map { it.key to it.value }
    }
    var flowOpen by rememberSaveable { mutableStateOf(false) }
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
        // Chart NET RATE lên đầu tab SPEND (user chốt 26/9: "gộp chart vào spend").
        item(key = "net_rate") {
            NetRateSection(
                currentPerDay = currentPerDay,
                daily = daily,
                spending = spending,
                spendByDay = spending.byDay,
                cap = cap,
                kpis = kpis,
            )
        }
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
            // Phân vùng FLOW: dải gấp, trạng thái nhớ trong phiên (rememberSaveable); xổ ra là bảng ngày.
            item(key = "flow_fold") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(top = 8.dp)
                        .border(1.dp, colors.border)
                        .noRippleClickable { flowOpen = !flowOpen }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChuText(
                        (if (flowOpen) "▾ " else "▸ ") + "FLOW · ${monthAbbr(flowMonth.month)}",
                        style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.accent,
                    )
                    Spacer(Modifier.width(10.dp))
                    ChuText(pos + money(flowMonth.monthIn), style = type.labelSmall, color = colors.success)
                    ChuText(" · ", style = type.labelSmall, color = colors.textMuted)
                    ChuText(neg + money(flowMonth.monthOut), style = type.labelSmall, color = colors.warning)
                }
            }
            if (flowOpen) {
                if (flowDays.isEmpty()) {
                    item(key = "flow_empty") { DashboardHint("no transfers this month yet") }
                } else {
                    item(key = "flow_table") {
                        FlowDayTable(
                            days = flowDays,
                            selected = openDay,
                            hidden = hidden,
                            money = { money(it) },
                            onSelect = { openDay = it },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bảng FLOW theo ngày (khung bảng 1.62.3, user gọi lại 22/9 tối): một hàng một ngày, IN · OUT căn
 * phải theo cột để quét dọc được; thiếu số ghi "0" mờ giữ cột thẳng; đầu bảng nền surfaceVariant.
 * Chạm hàng mở tấm chi tiết ngày, hàng đang mở nền nhấn nhẹ.
 */
@Composable
private fun FlowDayTable(
    days: List<Pair<String, FlowDay>>,
    selected: String?,
    hidden: Boolean,
    money: (Double) -> String,
    onSelect: (String) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val numStyle = type.label.copy(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Bold,
    )

    @Composable
    fun RowScope.Head(text: String) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            ChuText(text, style = type.labelSmall, color = colors.textMuted, maxLines = 1)
        }
    }

    @Composable
    fun RowScope.Num(value: Double, prefix: String, color: Color) {
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
            Head("IN")
            Head("OUT")
        }
        days.forEachIndexed { i, (day, fd) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (day == selected) colors.accent.copy(alpha = 0.08f) else Color.Transparent)
                    .noRippleClickable { onSelect(day) }
                    // hàng cao hơn bản 1.62.3 (4dp) — user 22/9 tối: "tăng kích thước mỗi hàng lên xíu"
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText(
                    day.substring(8) + "/" + day.substring(5, 7),
                    style = type.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    modifier = Modifier.width(44.dp),
                )
                Num(fd.inUsd, "+", colors.success)
                Num(fd.out, "-", colors.warning)
            }
            if (i < days.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = CHU_HAIRLINE_ALPHA)))
            }
        }
    }
}

/**
 * Tấm chi tiết một ngày FLOW — cùng khung với [PositionDetailPane] (user 22/9 tối: "vibe phải giống
 * cái position"): ngày lớn màu accent + dòng phụ, nút CLOSE, section TOTAL (in / out / net) và
 * TRANSFERS (giờ · số có dấu · token) bằng đúng DetailSection/SpecRow của tấm vị thế. Không mũi tên,
 * không đối tác, không chỉ dẫn. Nền tảng: [KohiBottomSheet] (neo đáy, inset đo từ cửa sổ gốc).
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
    val clock = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val sumIn = rows.filter { it.usd > 0 }.sumOf { it.usd }
    val sumOut = rows.filter { it.usd < 0 }.sumOf { -it.usd }
    val net = sumIn - sumOut
    val pos = if (hidden) "" else "+"
    val neg = if (hidden) "" else "-"
    KohiBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(colors.surface)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    ChuText(
                        day.substring(8) + "/" + day.substring(5, 7),
                        style = type.title.copy(fontWeight = FontWeight.Bold),
                        color = colors.accent,
                        maxLines = 1,
                    )
                    ChuText(
                        "FLOW · ${rows.size} TRANSFER" + if (rows.size == 1) "" else "S",
                        style = type.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                KohiCompactAction(label = "CLOSE", onClick = onDismiss)
            }

            DetailSection("TOTAL")
            SpecRow("IN", pos + money(sumIn), colors.success)
            SpecRow("OUT", neg + money(sumOut), colors.warning)
            SpecRow(
                "NET",
                (if (net >= 0) pos else neg) + money(kotlin.math.abs(net)),
                if (net >= 0) colors.accent else colors.warning,
            )

            DetailSection("TRANSFERS")
            if (rows.isEmpty()) {
                ChuText("no transfers", style = type.bodySmall, color = colors.textSecondary)
            }
            // Mỗi lệnh cách nhau một vạch mờ và cao hơn dòng spec thường (user 22/9 tối: "cần có vạch
            // phân giữa các mục tiền in out, nhìn cho dễ"). Vạch lấy xám chữ, không lấy border — border
            // trên surface gần như tàng hình (đo 28/8, xem DetailSection).
            rows.forEachIndexed { i, tx ->
                val inbound = tx.usd >= 0
                Box(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    SpecRow(
                        label = remember(tx.ts) { clock.format(Date(tx.ts * 1000)) },
                        value = (if (inbound) pos else neg) + money(kotlin.math.abs(tx.usd)) + " " + tx.token,
                        valueColor = if (inbound) colors.success else colors.warning,
                    )
                }
                if (i < rows.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.textMuted.copy(alpha = 0.25f)))
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
