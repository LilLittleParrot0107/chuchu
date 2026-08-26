package com.jossephus.chuchu.ui.screens.Dbtop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.data.model.dbtop.DappRow
import com.jossephus.chuchu.data.model.dbtop.DbtopState
import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import com.jossephus.chuchu.data.model.dbtop.OptionDetail
import com.jossephus.chuchu.data.model.dbtop.RiskEvaluator
import com.jossephus.chuchu.data.model.dbtop.TokenPosition
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.KohiSelectableRow
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import java.util.Locale

@Composable
internal fun PositionsView(
    rows: List<DappRow>,
    selectedKey: String?,
    showYield: Boolean,
    nowSec: Long,
    onSelect: (DappRow) -> Unit,
) {
    if (rows.isEmpty()) {
        DashboardEmpty("NO POSITIONS")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(rows, key = DappRow::positionKey) { row ->
            DappPositionRow(
                row = row,
                selected = selectedKey == row.positionKey(),
                showYield = showYield && (row.expiry == null || row.expiry > nowSec),
                onClick = { onSelect(row) },
            )
        }
    }
}

@Composable
private fun DappPositionRow(
    row: DappRow,
    selected: Boolean,
    showYield: Boolean,
    onClick: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val option = row.detail?.option != null || row.expiry != null
    val lending = row.health != null || row.debt != null
    val glyph = when {
        option -> "◈"
        lending -> "⬡"
        row.proto.contains("balancer", true) -> "⟡"
        else -> "◆"
    }
    val tone = when {
        row.health != null && row.health < DBTOP_HIGH_RISK_HEALTH_FACTOR -> colors.error
        option -> colors.warning
        lending -> colors.accentSecondary
        else -> colors.accent
    }
    // HF khong con nam trong metrics text: lending row co han thanh health
    // truc quan ben duoi (user yeu cau 26/8).
    val metrics = remember(row, showYield) {
        buildList {
            if (showYield && row.perday > 0) add("+${DeFiFormatter.formatUsd(row.perday)}/D")
            if (showYield && row.apr != null && row.apr > 0) add("${DeFiFormatter.formatPercent(row.apr, false)} APR")
        }.joinToString(" · ").ifBlank { row.proto.uppercase() }
    }

    KohiSelectableRow(
        selected = selected,
        tone = tone,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        ChuText(glyph, style = type.labelSmall, color = tone)
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            ChuText(
                row.name,
                style = type.label.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ChuText(
                metrics,
                style = type.labelSmall,
                color = if (row.health != null && row.health < RiskEvaluator.HP_BAD) colors.error else colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // padding(end): thanh la khoi dac full-bleed, khong co khoang tho
            // tu nhien nhu chu — de sat so cap ben phai nhin rat bi.
            row.detail?.option?.let { opt ->
                OptionProgressBar(
                    option = opt,
                    compact = true,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            row.health?.takeIf { it > 0.0 }?.let { hf ->
                LendingHealthBar(
                    health = hf,
                    liqDrop = row.liqDrop,
                    compact = true,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        ChuText(
            DeFiFormatter.formatUsdCompact(row.cap),
            style = type.label.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun OptionProgressBar(
    option: OptionDetail,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    nowSec: Long = System.currentTimeMillis() / 1000L,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    val expiry = option.expiry ?: 0L
    // option.dte trong state.json la TONG KY HAN cua option (expiry - sold),
    // KHONG phai so ngay con lai — tung bi do thang vao "D LEFT" nen option
    // con 2 ngay van hien 13.9D. Ngay con lai LUON tinh tu expiry vs now;
    // dte chi con la fallback cho tong ky han khi thieu sold.
    val daysLeft = if (expiry > nowSec) (expiry - nowSec) / 86400.0 else 0.0
    val sold = option.sold ?: 0L

    val totalSec = if (sold in 1 until expiry) (expiry - sold).toDouble() else (option.dte ?: 0.0) * 86400.0
    val elapsedSec = if (sold > 0 && totalSec > 0) (nowSec - sold).toDouble().coerceIn(0.0, totalSec) else 0.0
    val progressPct = if (totalSec > 0) (elapsedSec / totalSec).toFloat().coerceIn(0f, 1f) else 0f

    val prem = option.prem ?: 0.0
    val harvestedPrem = prem * progressPct.toDouble()

    // Safety buffer: khoang cach spot toi strike. KHONG hien "ITM lo $X" nua
    // (user chot 26/8): ban covered call/put co the chap luon itmUsd > 0 ma
    // khong co khoan lo hien thuc nao — premium da thu, giao hang tai strike
    // la ket qua tinh truoc. Chi khi nao co so LO THAT tu server thi moi
    // duoc phep hien lai.
    val spotPx = option.underlying?.px ?: 0.0
    val strikePx = option.strike ?: 0.0
    val isPut = option.type.equals("Put", ignoreCase = true)
    val bufferPct = if (spotPx > 0 && strikePx > 0) {
        if (isPut) ((spotPx - strikePx) / spotPx) * 100.0
        else ((strikePx - spotPx) / strikePx) * 100.0
    } else 0.0

    val barColor = if (daysLeft <= 1.0) colors.warning else colors.accent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (compact) 3.dp else 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChuText(
                text = "${(progressPct * 100).toInt()}% ELAPSED",
                style = type.labelSmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = barColor,
            )
            ChuText(
                text = if (daysLeft > 0) String.format(Locale.US, "%.1fD LEFT", daysLeft) else "EXPIRED",
                style = type.labelSmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = if (daysLeft <= 1.0) colors.warning else colors.textSecondary,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 3.dp else 4.dp)
                .background(colors.surfaceVariant, shape = RoundedCornerShape(2.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progressPct)
                    .fillMaxHeight()
                    .background(barColor, shape = RoundedCornerShape(2.dp)),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChuText(
                text = if (prem > 0) "+${DeFiFormatter.formatUsd(harvestedPrem)} / ${DeFiFormatter.formatUsd(prem)}" else "THETA DECAY",
                style = type.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )

            if (bufferPct > 0) {
                ChuText(
                    text = "🛡 BUFFER +${String.format(Locale.US, "%.1f%%", bufferPct)}",
                    style = type.labelSmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                    color = colors.accentSecondary,
                )
            }
        }
    }
}

/**
 * Thanh health truc quan cho vi the lending, cung ngon ngu voi
 * OptionProgressBar: HF 1.0 = thanh ly (thanh can), HF >= 2.0 = day thanh.
 * Mau theo nguong that cua qd/dbtop: <1.15 do, <1.25 cam, con lai xanh.
 */
@Composable
internal fun LendingHealthBar(
    health: Double,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    liqDrop: Double? = null,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    val fillPct = ((health - 1.0).toFloat()).coerceIn(0f, 1f)
    val tone = when {
        health < RiskEvaluator.HP_BAD -> colors.error
        health < RiskEvaluator.HP_WARN -> colors.warning
        else -> colors.success
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (compact) 3.dp else 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChuText(
                text = "HF ${String.format(Locale.US, "%.2fx", health)}",
                style = type.labelSmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = tone,
            )
            if (liqDrop != null && liqDrop > 0) {
                ChuText(
                    text = String.format(Locale.US, "-%.1f%% TO LIQ", liqDrop),
                    style = type.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = colors.textSecondary,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 3.dp else 4.dp)
                .background(colors.surfaceVariant, shape = RoundedCornerShape(2.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = fillPct)
                    .fillMaxHeight()
                    .background(tone, shape = RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
internal fun PositionDetailPane(
    row: DappRow,
    showYield: Boolean,
    onClose: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val positions = buildList {
        row.detail?.collateral?.forEach { add("COLLATERAL" to it) }
        row.detail?.supply?.forEach { add("SUPPLY" to it) }
        row.detail?.borrow?.forEach { add("BORROW" to it) }
        row.detail?.reward?.forEach { add("REWARD" to it) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChuText(
                row.name.uppercase(),
                style = type.label.copy(fontWeight = FontWeight.Bold),
                color = colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            KohiCompactAction(label = "CLOSE", onClick = onClose)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ChuText(
                "PROTOCOL ${row.proto.ifBlank { "—" }.uppercase()}",
                style = type.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            ChuText(
                "CAP ${DeFiFormatter.formatUsdCompact(row.cap)}",
                style = type.labelSmall,
                color = colors.textPrimary,
            )
        }
        val metricLine = remember(row, showYield) {
            buildList {
                if (showYield && row.perday > 0) add("YIELD +${DeFiFormatter.formatUsd(row.perday)}/D")
                if (showYield && row.apr != null) add("APR ${DeFiFormatter.formatPercent(row.apr, false)}")
                if (!showYield && (row.perday != 0.0 || row.apr != null)) add("YIELD HIDDEN (SCAN OFFLINE)")
                row.health?.let { add("HF ${String.format(Locale.US, "%.2fx", it)}") }
                row.debt?.let { add("DEBT ${DeFiFormatter.formatUsd(it)}") }
                row.coll?.let { add("COLLATERAL ${DeFiFormatter.formatUsd(it)}") }
            }.joinToString(" · ")
        }
        if (metricLine.isNotBlank()) {
            ChuText(metricLine, style = type.labelSmall, color = colors.textSecondary)
        }

        // Cảnh báo giá kích hoạt thanh lý cụ thể chuẩn dbtop
        if (row.liqDrop != null && row.liqAt != null && row.liqAt > 0) {
            val baseToken = row.liqBase ?: "ASSET"
            val liqText = "⚠ LIQUIDATION: Drops -${String.format(Locale.US, "%.1f", row.liqDrop)}% to ${DeFiFormatter.formatUsd(row.liqAt)} ($baseToken)"
            val liqTone = if ((row.health ?: 9.0) < RiskEvaluator.HP_BAD) colors.error else colors.warning
            ChuText(
                liqText,
                style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = liqTone,
            )
        }
        row.health?.takeIf { it > 0.0 }?.let { hf ->
            LendingHealthBar(health = hf, liqDrop = row.liqDrop)
        }

        row.detail?.option?.let { OptionDetailLines(it) }

        // Bóc tách cơ cấu lợi suất và haircut thưởng nếu có
        row.detail?.asLendingBreakdown()?.let { bd ->
            val bdText = buildList {
                if (bd.stake != 0.0) add("Stake: ${DeFiFormatter.formatPercent(bd.stake, true)}")
                if (bd.dust != 0.0) add("DUST: ${DeFiFormatter.formatPercent(bd.dust, true)} (Keep ${(bd.keep * 100).toInt()}%)")
                if (bd.borrow_base != 0.0) add("Borrow: -${DeFiFormatter.formatPercent(Math.abs(bd.borrow_base), false)}")
            }.joinToString(" · ")
            if (bdText.isNotBlank()) {
                ChuText("YIELD BREAKDOWN: $bdText", style = type.labelSmall, color = colors.accent)
            }
        }

        positions.forEach { (kind, token) -> TokenPositionLine(kind, token) }
        if (row.detail?.option == null && positions.isEmpty() && row.detail?.breakdown == null) {
            ChuText("BREAKDOWN UNAVAILABLE IN THIS SNAPSHOT", style = type.labelSmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun OptionDetailLines(option: OptionDetail) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val line = buildList {
        add("TYPE ${option.type.uppercase()}")
        option.strike?.let { add("STRIKE ${DeFiFormatter.formatUsd(it)}") }
        option.prem?.let { add("PREM +${DeFiFormatter.formatUsd(it)}") }
        option.apr?.let { add("APR ${DeFiFormatter.formatPercent(it, false)}") }
    }.joinToString(" · ")
    ChuText(line, style = type.labelSmall, color = colors.warning)

    OptionProgressBar(option = option, compact = false)
}

@Composable
private fun TokenPositionLine(kind: String, token: TokenPosition) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ChuText(
            "$kind · ${String.format(Locale.US, "%.4f", token.amt)} ${token.sym}",
            style = type.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ChuText(
            DeFiFormatter.formatUsd(token.usd),
            style = type.labelSmall,
            color = colors.textPrimary,
        )
    }
}

@Composable
internal fun YieldInsightPane(
    state: DbtopState,
    currentPerDay: Double?,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current

    val perday = currentPerDay ?: state.perday
    val monthlyProjectedRaw = state.mtd?.proj ?: (perday * 30.416)
    val monthlyProjected = if (monthlyProjectedRaw.isFinite() && !monthlyProjectedRaw.isNaN()) monthlyProjectedRaw else 0.0
    val mtdUsd = state.mtd?.usd
    val mtdDays = state.mtd?.days
    val totalCapital = state.cap.takeIf { it > 0 } ?: state.rows.sumOf { it.cap }.takeIf { it > 0 } ?: state.netWorth
    val avgAprRaw = state.apr ?: if (totalCapital > 0 && perday > 0) (perday * 365.0 / totalCapital) * 100.0 else null
    val avgApr = if (avgAprRaw != null && avgAprRaw.isFinite() && !avgAprRaw.isNaN()) avgAprRaw else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Tiêu đề
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChuText(
                "YIELD & INCOME INSIGHTS",
                style = typography.label.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
            ChuText(
                "OVERVIEW",
                style = typography.labelSmall,
                color = colors.textMuted,
            )
        }

        // Card 1: 4 Chỉ số Lợi suất Cốt lõi (Grid 2x2 trong ChuCard)
        ChuCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Daily Income
                    Column(modifier = Modifier.weight(1f)) {
                        ChuText("DAILY INCOME", style = typography.labelSmall, color = colors.textMuted)
                        ChuText(
                            "+${String.format(Locale.US, "$%,.2f", perday)} / D",
                            style = typography.title.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = colors.accent,
                        )
                    }
                    // Projected Monthly
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        ChuText("PROJECTED / MO", style = typography.labelSmall, color = colors.textMuted)
                        ChuText(
                            "~${String.format(Locale.US, "$%,.2f", monthlyProjected)}",
                            style = typography.title.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = colors.textPrimary,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // MTD Realized (Tháng này)
                    Column(modifier = Modifier.weight(1f)) {
                        ChuText(
                            if (mtdDays != null && mtdDays > 0) "THIS MONTH (${String.format(Locale.US, "%.1fd", mtdDays)})" else "THIS MONTH (MTD)",
                            style = typography.labelSmall,
                            color = colors.textMuted,
                        )
                        ChuText(
                            if (mtdUsd != null) "+${String.format(Locale.US, "$%,.2f", mtdUsd)}" else "+${String.format(Locale.US, "$%,.2f", perday * 25.0)}",
                            style = typography.body.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = colors.textPrimary,
                        )
                    }
                    // Average APR
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        ChuText("AVG PORTFOLIO APR", style = typography.labelSmall, color = colors.textMuted)
                        ChuText(
                            if (avgApr != null) String.format(Locale.US, "%.2f%% APR", avgApr) else "--",
                            style = typography.body.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = colors.accentSecondary,
                        )
                    }
                }
            }
        }

        // Card 2: Phân bổ Lợi suất theo Vị thế / Giao thức (Top Yield Contributors)
        val topYieldRows = remember(state.rows) {
            state.rows
                .filter { it.perday > 0 }
                .sortedByDescending { it.perday }
        }

        if (topYieldRows.isNotEmpty()) {
            ChuCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChuText(
                        "TOP YIELD CONTRIBUTORS",
                        style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.textSecondary,
                    )
                    topYieldRows.take(5).forEach { row ->
                        val sharePctRaw = if (perday > 0) (row.perday / perday) * 100.0 else 0.0
                        val sharePct = if (sharePctRaw.isFinite() && !sharePctRaw.isNaN()) sharePctRaw else 0.0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                ChuText(
                                    row.name.ifBlank { row.proto },
                                    style = typography.body,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                ChuText(
                                    "${row.proto.uppercase()} · ${String.format(Locale.US, "%.1f%% yield share", sharePct)}",
                                    style = typography.labelSmall,
                                    color = colors.textMuted,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                ChuText(
                                    "+${String.format(Locale.US, "$%,.2f", row.perday)}/D",
                                    style = typography.body.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = colors.accent,
                                )
                                if (row.apr != null && row.apr > 0) {
                                    ChuText(
                                        String.format(Locale.US, "%.1f%% APR", row.apr),
                                        style = typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        color = colors.textMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Card 3: Thống kê & Tiến trình Option Lifecycle (Active Option Orders)
        val optionRows = remember(state.rows) {
            state.rows.filter { it.detail?.option != null }
        }
        val prem = state.prem
        val realized = state.realized

        if (optionRows.isNotEmpty() || prem != null || realized != null) {
            ChuCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChuText(
                            if (optionRows.isNotEmpty()) "ACTIVE OPTIONS (${optionRows.size})" else "OPTION PREMIUM & REALIZED",
                            style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.textSecondary,
                        )
                        if (optionRows.isNotEmpty()) {
                            val totalOptionCap = optionRows.sumOf { it.cap }
                            ChuText(
                                "CAP ${DeFiFormatter.formatUsdCompact(totalOptionCap)}",
                                style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.warning,
                            )
                        }
                    }

                    if (optionRows.isNotEmpty()) {
                        val totalOptionPerday = optionRows.sumOf { it.perday }
                        val totalOptionPrem = optionRows.sumOf { it.detail?.option?.prem ?: 0.0 }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                ChuText("DAILY THETA DECAY", style = typography.labelSmall, color = colors.textMuted)
                                ChuText(
                                    "+${String.format(Locale.US, "$%,.2f", totalOptionPerday)}/D",
                                    style = typography.body.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                                    color = colors.accent,
                                )
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                ChuText("PENDING PREMIUM", style = typography.labelSmall, color = colors.textMuted)
                                ChuText(
                                    "+${String.format(Locale.US, "$%,.2f", totalOptionPrem)}",
                                    style = typography.body.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                                    color = colors.accentSecondary,
                                )
                            }
                        }

                        // Danh sách tiến trình từng lệnh Option
                        optionRows.forEach { optRow ->
                            val opt = optRow.detail?.option
                            if (opt != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.surfaceVariant, shape = RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ChuText(
                                            optRow.name,
                                            style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        ChuText(
                                            DeFiFormatter.formatUsdCompact(optRow.cap),
                                            style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = colors.textPrimary,
                                        )
                                    }
                                    OptionProgressBar(option = opt, compact = true)
                                }
                            }
                        }
                    }

                    // Thống kê Realized / 7D / 28D Premium lịch sử nếu có
                    if (prem != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            ChuText("7D Premium", style = typography.labelSmall, color = colors.textMuted)
                            ChuText(
                                String.format(Locale.US, "$%,.2f", prem.d7),
                                style = typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                color = colors.textPrimary,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            ChuText("28D Premium", style = typography.labelSmall, color = colors.textMuted)
                            ChuText(
                                String.format(Locale.US, "$%,.2f", prem.d28),
                                style = typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                color = colors.textPrimary,
                            )
                        }
                    }
                    if (realized != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            ChuText("Realized Total (${realized.orders} orders)", style = typography.labelSmall, color = colors.textMuted)
                            ChuText(
                                String.format(Locale.US, "$%,.2f", realized.premium),
                                style = typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                color = colors.accent,
                            )
                        }
                    }
                }
            }
        }
    }
}
