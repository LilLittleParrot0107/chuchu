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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.jossephus.chuchu.ui.theme.CHU_HAIRLINE_ALPHA
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
                nowSec = nowSec,
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
    nowSec: Long,
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
    // So cu the nam O DAY vi thanh do gio la fill nen row (am hieu, khong
    // phai do thi chinh xac) — user chot 27/8.
    val metrics = remember(row, showYield, nowSec) {
        buildList {
            row.detail?.option?.let { opt ->
                val expiry = opt.expiry ?: 0L
                val daysLeft = if (expiry > nowSec) (expiry - nowSec) / 86400.0 else 0.0
                add(if (daysLeft > 0) String.format(Locale.US, "%.1fD LEFT", daysLeft) else "EXPIRED")
            }
            row.health?.takeIf { it > 0 }?.let { add("HF ${String.format(Locale.US, "%.2fx", it)}") }
            row.liqDrop?.takeIf { it > 0 && row.health != null }?.let {
                add(String.format(Locale.US, "-%.0f%% LIQ", it))
            }
            if (showYield && row.perday > 0) add("+${DeFiFormatter.formatUsd(row.perday)}/D")
            if (showYield && row.apr != null && row.apr > 0) add("${DeFiFormatter.formatPercent(row.apr, false)} APR")
        }.joinToString(" · ").ifBlank { row.proto.uppercase() }
    }
    // Fill nen row: health uu tien (rui ro truoc), khong thi tien trinh option.
    val health = row.health
    val rowFill: Pair<Float, Color>? = when {
        health != null && health > 0 -> {
            val tier = when {
                health < RiskEvaluator.HP_BAD -> colors.error
                health < RiskEvaluator.HP_WARN -> colors.warning
                else -> colors.success
            }
            ((health - 1.0).toFloat().coerceIn(0f, 1f)) to tier
        }
        row.detail?.option != null ->
            optionElapsedFraction(row.detail.option, nowSec) to colors.warning
        else -> null
    }

    KohiSelectableRow(
        selected = selected,
        tone = tone,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        fillFraction = rowFill?.first,
        fillColor = rowFill?.second,
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

/** Phan tram ky han da troi qua cua option (0..1) — dung chung row fill va bar detail. */
internal fun optionElapsedFraction(option: OptionDetail, nowSec: Long): Float {
    val expiry = option.expiry ?: 0L
    val sold = option.sold ?: 0L
    val totalSec = if (sold in 1 until expiry) (expiry - sold).toDouble() else (option.dte ?: 0.0) * 86400.0
    val elapsedSec = if (sold > 0 && totalSec > 0) (nowSec - sold).toDouble().coerceIn(0.0, totalSec) else 0.0
    return if (totalSec > 0) (elapsedSec / totalSec).toFloat().coerceIn(0f, 1f) else 0f
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

            // Badge buffer CHI o dang compact (row list): trong detail pane
            // MoneynessGauge da noi dieu nay roi — hien ca hai la trung lap.
            if (compact && bufferPct > 0) {
                ChuText(
                    text = "BUFFER +${String.format(Locale.US, "%.1f%%", bufferPct)}",
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

/**
 * Detail pane kieu "the spec TUI" (de xuat 26/8, user duyet): label trai ngan
 * gon, gia tri phai mono thang cot, chia section RISK / YIELD / OPTION /
 * TOKENS, kem thanh co cau von va thuoc moneyness. Ky luat man hep: label
 * <= 8 ky tu, so dung dang compact, gia tri dai duoc wrap dong 2.
 */
@Composable
internal fun PositionDetailPane(
    row: DappRow,
    showYield: Boolean,
    onClose: () -> Unit,
    maxHeight: androidx.compose.ui.unit.Dp = 220.dp,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val tokens = buildList {
        row.detail?.collateral?.forEach { add(Triple("COLL", colors.accentSecondary, it)) }
        row.detail?.supply?.forEach { add(Triple("SUP", colors.accent, it)) }
        row.detail?.borrow?.forEach { add(Triple("BOR", colors.error, it)) }
        row.detail?.reward?.forEach { add(Triple("RWD", colors.success, it)) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
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
                    row.name.uppercase(),
                    style = type.label.copy(fontWeight = FontWeight.Bold),
                    color = colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ChuText(
                    "${row.proto.ifBlank { "—" }.uppercase()} · ${DeFiFormatter.formatUsdCompact(row.cap)}",
                    style = type.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            KohiCompactAction(label = "CLOSE", onClick = onClose)
        }

        val hasRisk = row.health != null || row.debt != null || row.liqAt != null
        if (hasRisk) {
            DetailSection("RISK")
            row.health?.takeIf { it > 0.0 }?.let { hf ->
                LendingHealthBar(health = hf, liqDrop = row.liqDrop)
            }
            if (row.liqAt != null && row.liqAt > 0) {
                SpecRow(
                    label = "LIQ",
                    value = "${DeFiFormatter.formatTokenPrice(row.liqAt)} (${row.liqBase ?: "ASSET"})",
                    valueColor = if ((row.health ?: 9.0) < RiskEvaluator.HP_BAD) colors.error else colors.warning,
                )
            }
            row.debt?.takeIf { it > 0 }?.let { SpecRow("DEBT", DeFiFormatter.formatUsd(it), colors.error) }
            row.coll?.takeIf { it > 0 }?.let { SpecRow("COLL", DeFiFormatter.formatUsd(it)) }
        }

        DetailSection("YIELD")
        if (showYield && row.perday > 0) {
            SpecRow("PER DAY", "+${DeFiFormatter.formatUsd(row.perday)}", colors.success)
        }
        if (showYield && row.apr != null) {
            SpecRow("APR", DeFiFormatter.formatPercent(row.apr, false))
        }
        if (!showYield && (row.perday != 0.0 || row.apr != null)) {
            SpecRow("YIELD", "HIDDEN (SCAN OFFLINE)", colors.textMuted)
        }
        row.detail?.asLendingBreakdown()?.let { bd ->
            val mix = buildList {
                if (bd.stake != 0.0) add("stake ${DeFiFormatter.formatPercent(bd.stake, false)}")
                if (bd.dust != 0.0) add("dust ${DeFiFormatter.formatPercent(bd.dust, false)}·keep ${(bd.keep * 100).toInt()}%")
                if (bd.borrow_base != 0.0) add("borrow -${DeFiFormatter.formatPercent(Math.abs(bd.borrow_base), false)}")
            }.joinToString(" + ")
            if (mix.isNotBlank()) SpecRow("MIX", mix, colors.textSecondary)
        }

        row.detail?.option?.let { option ->
            DetailSection("OPTION")
            SpecRow("TYPE", option.type.uppercase(), colors.warning)
            option.strike?.let { SpecRow("STRIKE", DeFiFormatter.formatTokenPrice(it)) }
            option.prem?.let { SpecRow("PREM", "+${DeFiFormatter.formatUsd(it)}", colors.success) }
            MoneynessGauge(option)
            OptionProgressBar(option = option, compact = false)
        }

        if (tokens.isNotEmpty()) {
            DetailSection("TOKENS")
            CompositionBar(tokens.map { (_, color, t) -> color to Math.abs(t.usd) })
            tokens.forEach { (kind, color, t) -> TokenRow(kind, color, t) }
        }
        if (row.detail?.option == null && tokens.isEmpty() && row.detail?.breakdown == null) {
            ChuText("BREAKDOWN UNAVAILABLE IN THIS SNAPSHOT", style = type.labelSmall, color = colors.textMuted)
        }
    }
}

/** Tieu de section gon: chu nho + duong ke keo het hang. */
@Composable
private fun DetailSection(label: String) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ChuText(label, style = type.labelSmall.copy(fontWeight = FontWeight.Bold), color = colors.textMuted)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.border.copy(alpha = CHU_HAIRLINE_ALPHA)),
        )
    }
}

/** Hang spec 2 cot: label trai co dinh, gia tri phai mono thang cot, cho wrap. */
@Composable
private fun SpecRow(
    label: String,
    value: String,
    valueColor: Color? = null,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        ChuText(
            label,
            style = type.labelSmall,
            color = colors.textMuted,
            maxLines = 1,
            modifier = Modifier.width(74.dp),
        )
        ChuText(
            value,
            style = type.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            ),
            color = valueColor ?: colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Thanh co cau von: moi mau mot phan doan theo ti trong USD — nhin mot giay
 * biet vi the nang o dau (COLL lam / SUP vang / BOR do / RWD xanh).
 */
@Composable
private fun CompositionBar(segments: List<Pair<Color, Double>>) {
    val total = segments.sumOf { it.second }
    if (total <= 0.0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        segments.filter { it.second > 0 }.forEach { (color, usd) ->
            Box(
                modifier = Modifier
                    .weight(usd.toFloat().coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .background(color, shape = RoundedCornerShape(1.dp)),
            )
        }
    }
}

/**
 * Thuoc moneyness: khoang dem tu SPOT ve STRIKE. Day thanh = dem >= 30%,
 * can thanh = cham strike. KHONG hien so lo ITM (user chot 26/8).
 */
@Composable
private fun MoneynessGauge(option: OptionDetail) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val spot = option.underlying?.px ?: return
    val strike = option.strike ?: return
    if (spot <= 0 || strike <= 0) return
    val isPut = option.type.equals("Put", ignoreCase = true)
    val bufferPct = if (isPut) ((spot - strike) / spot) * 100.0 else ((strike - spot) / strike) * 100.0
    val fill = (bufferPct / 30.0).toFloat().coerceIn(0f, 1f)
    val tone = when {
        bufferPct <= 5.0 -> colors.warning
        else -> colors.success
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ChuText(
                "STRIKE ${DeFiFormatter.formatTokenPrice(strike)}",
                style = type.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = colors.textMuted,
            )
            ChuText(
                if (bufferPct > 0) "BUFFER +${String.format(Locale.US, "%.1f%%", bufferPct)}" else "AT STRIKE",
                style = type.labelSmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = tone,
            )
            ChuText(
                "SPOT ${DeFiFormatter.formatTokenPrice(spot)}",
                style = type.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = colors.textSecondary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(colors.surfaceVariant, shape = RoundedCornerShape(2.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = fill)
                    .fillMaxHeight()
                    .background(tone, shape = RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** Hang token 3 cot: cham mau + loai + symbol | so luong | USD compact. */
@Composable
private fun TokenRow(kind: String, dotColor: Color, token: TokenPosition) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val mono = type.labelSmall.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuText("●", style = type.labelSmall, color = dotColor)
        Spacer(Modifier.width(5.dp))
        ChuText(
            "$kind ${token.sym}",
            style = type.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ChuText(
            formatAmountCompact(token.amt),
            style = mono,
            color = colors.textMuted,
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        ChuText(
            DeFiFormatter.formatUsdCompact(token.usd),
            style = mono.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary,
            maxLines = 1,
        )
    }
}

/** So luong token gon cho man hep: 1.11M / 483.8k / 50,000 / 0.2000. */
private fun formatAmountCompact(amount: Double): String {
    val a = Math.abs(amount)
    return when {
        a >= 1_000_000 -> String.format(Locale.US, "%.2fM", amount / 1_000_000.0)
        a >= 10_000 -> String.format(Locale.US, "%.1fk", amount / 1_000.0)
        a >= 100 -> String.format(Locale.US, "%,.0f", amount)
        a >= 1 -> String.format(Locale.US, "%,.2f", amount)
        else -> String.format(Locale.US, "%.4f", amount)
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
