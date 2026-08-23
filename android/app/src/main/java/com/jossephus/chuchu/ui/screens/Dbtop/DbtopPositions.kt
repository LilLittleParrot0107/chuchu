package com.jossephus.chuchu.ui.screens.Dbtop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.data.model.dbtop.DappRow
import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import com.jossephus.chuchu.data.model.dbtop.OptionDetail
import com.jossephus.chuchu.data.model.dbtop.RiskEvaluator
import com.jossephus.chuchu.data.model.dbtop.TokenPosition
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
    val metrics = buildList {
        if (showYield && row.perday > 0) add("+${DeFiFormatter.formatUsd(row.perday)}/D")
        if (showYield && row.apr != null && row.apr > 0) add("${DeFiFormatter.formatPercent(row.apr, false)} APR")
        row.health?.let { add("HF ${String.format(Locale.US, "%.2fx", it)}") }
    }.joinToString(" · ").ifBlank { row.proto.uppercase() }

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
        }
        Spacer(Modifier.width(8.dp))
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
        val metricLine = buildList {
            if (showYield && row.perday > 0) add("YIELD +${DeFiFormatter.formatUsd(row.perday)}/D")
            if (showYield && row.apr != null) add("APR ${DeFiFormatter.formatPercent(row.apr, false)}")
            if (!showYield && (row.perday != 0.0 || row.apr != null)) add("YIELD HIDDEN (SCAN OFFLINE)")
            row.health?.let { add("HF ${String.format(Locale.US, "%.2fx", it)}") }
            row.debt?.let { add("DEBT ${DeFiFormatter.formatUsd(it)}") }
            row.coll?.let { add("COLLATERAL ${DeFiFormatter.formatUsd(it)}") }
        }.joinToString(" · ")
        if (metricLine.isNotBlank()) {
            ChuText(metricLine, style = type.labelSmall, color = colors.textSecondary)
        }
        row.detail?.option?.let { OptionDetailLines(it) }
        positions.forEach { (kind, token) -> TokenPositionLine(kind, token) }
        if (row.detail?.option == null && positions.isEmpty()) {
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
        option.dte?.let { add(if (it <= 0.0) "DTE EXPIRED" else "DTE ${String.format(Locale.US, "%.1fD", it)}") }
        option.apr?.let { add("APR ${DeFiFormatter.formatPercent(it, false)}") }
    }.joinToString(" · ")
    ChuText(line, style = type.labelSmall, color = colors.warning)
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
