package com.jossephus.chuchu.ui.screens.Dbtop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.data.model.dbtop.DataFreshness
import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiCommandBand
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

@Composable
internal fun DbtopTopBar(
    freshness: DataFreshness,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = ChuColors.current
    val ageText = when (freshness) {
        is DataFreshness.Fresh ->
            if (freshness.ageSeconds < 60) "JUST NOW"
            else "UPDATED ${freshness.ageSeconds / 60}M AGO"
        is DataFreshness.Warning -> "STALE ${freshness.ageSeconds / 60}M AGO"
        is DataFreshness.Dead -> "OFFLINE ${freshness.ageSeconds / 3600}H"
    }
    val tone = when (freshness) {
        is DataFreshness.Fresh -> colors.success
        is DataFreshness.Warning -> colors.warning
        is DataFreshness.Dead -> colors.error
    }
    KohiCommandBand(
        title = "DBTOP",
        status = ageText,
        statusColor = tone,
        onBack = onClose,
    ) {
        KohiCompactAction(
            label = if (isRefreshing) "SCANNING" else "↻",
            onClick = onRefresh,
            enabled = !isRefreshing,
        )
    }
}

@Composable
internal fun DashboardSummary(
    netWorth: Double,
    wallet: Double,
    perDay: Double?,
    debt: Double,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    KohiSectionBand(
        label = "OVERVIEW",
        meta = if (perDay != null) "LIVE YIELD" else "YIELD HIDDEN",
        accent = if (perDay != null) colors.success else colors.error,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetricCell(
            label = "NET WORTH",
            value = DeFiFormatter.formatUsd(netWorth),
            color = colors.accent,
            modifier = Modifier.weight(1.45f),
        )
        MetricCell(
            label = "YIELD / DAY",
            value = perDay?.let { (if (it >= 0) "+" else "") + DeFiFormatter.formatUsd(it) } ?: "—",
            color = if (perDay != null) colors.success else colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        Column(
            modifier = Modifier.weight(1f).heightIn(min = 50.dp),
            horizontalAlignment = Alignment.End,
        ) {
            ChuText("WALLET", style = type.labelSmall, color = colors.textMuted)
            ChuText(
                DeFiFormatter.formatUsd(wallet),
                style = type.label.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (debt > 0.0) {
                ChuText(
                    "DEBT ${DeFiFormatter.formatUsd(debt)}",
                    style = type.labelSmall,
                    color = colors.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val type = ChuTypography.current
    val colors = ChuColors.current
    Column(modifier = modifier) {
        ChuText(label, style = type.labelSmall, color = colors.textMuted, maxLines = 1)
        ChuText(
            value,
            style = type.title.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun DashboardViewBand(
    selected: DbtopView,
    positionCount: Int,
    onSelect: (DbtopView) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val counts = mapOf(
        DbtopView.POSITIONS to positionCount,
        DbtopView.CHARTS to 2,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DbtopView.entries.forEach { view ->
            val active = selected == view
            ChuButton(
                onClick = { onSelect(view) },
                variant = if (active) ChuButtonVariant.Filled else ChuButtonVariant.Ghost,
                bracketed = true,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                minHeight = 24.dp,
                modifier = Modifier.weight(1f),
            ) {
                ChuText(
                    "${view.label} ${counts.getValue(view)}",
                    style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (active) colors.onAccent else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
