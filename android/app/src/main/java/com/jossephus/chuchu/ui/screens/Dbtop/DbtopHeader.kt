package com.jossephus.chuchu.ui.screens.Dbtop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiCommandBand
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.theme.CHU_HAIRLINE_ALPHA
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
        title = "DASHBOARD",
        status = ageText,
        statusColor = tone,
        onBack = onClose,
        // Tan vao nen theme de khop mau voi vung status bar phia tren —
        // truoc day band xam surface con thanh noti mau background, lo seam.
        containerColor = colors.background,
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
    moneyDisplay: MoneyDisplay,
    vndRate: Double,
    onCycleMoney: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    KohiSectionBand(
        label = "OVERVIEW",
        meta = if (perDay != null) "LIVE YIELD" else "YIELD HIDDEN",
        containerColor = colors.background,
        // Yield an di vi snapshot cu/chet la trang thai "canh giac", khong
        // phai loi — error do de danh cho DEBT va SCAN OFFLINE.
        accent = if (perDay != null) colors.success else colors.warning,
    )
    ChuCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricCell(
                    label = "NET WORTH",
                    value = formatMoney(netWorth, moneyDisplay, vndRate),
                    color = colors.accent,
                    // Tap = xoay vong USD -> VND -> AN (user chot 27/8) —
                    // ap cho ca Overview + tab Spending.
                    modifier = Modifier.clickable(onClick = onCycleMoney),
                )
                MetricCell(
                    label = "YIELD / DAY",
                    value = perDay?.let { (if (it >= 0 && moneyDisplay != MoneyDisplay.HIDDEN) "+" else "") + formatMoney(it, moneyDisplay, vndRate) } ?: "—",
                    color = if (perDay != null) colors.success else colors.textMuted,
                    alignEnd = true,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border.copy(alpha = CHU_HAIRLINE_ALPHA)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChuText("WALLET  ", style = type.labelSmall, color = colors.textMuted)
                    ChuText(
                        formatMoney(wallet, moneyDisplay, vndRate),
                        style = type.label.copy(
                            fontFamily = FontFamily.Monospace,
                            fontFeatureSettings = "tnum",
                            fontWeight = FontWeight.Bold,
                        ),
                        color = colors.textPrimary,
                    )
                }
                if (debt > 0.0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChuText("DEBT  ", style = type.labelSmall, color = colors.error)
                        ChuText(
                            formatMoney(debt, moneyDisplay, vndRate),
                            style = type.label.copy(
                                fontFamily = FontFamily.Monospace,
                                fontFeatureSettings = "tnum",
                                fontWeight = FontWeight.Bold,
                            ),
                            color = colors.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MetricCell(
    label: String,
    value: String,
    color: Color,
    alignEnd: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val type = ChuTypography.current
    val colors = ChuColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        ChuText(label, style = type.labelSmall, color = colors.textMuted, maxLines = 1)
        ChuText(
            value,
            style = type.title.copy(
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.Bold,
            ),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun DashboardViewBand(
    selected: DbtopView,
    onSelect: (DbtopView) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
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
                    view.tab,
                    style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (active) colors.onAccent else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
