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
import androidx.compose.ui.unit.sp
import com.jossephus.chuchu.data.model.dbtop.DataFreshness
import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiCommandBand
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.components.chart.CashflowKpiSummary
import com.jossephus.chuchu.ui.theme.CHU_HAIRLINE_ALPHA
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import java.util.Locale
import kotlin.math.abs

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
        // Không có nút back trong app (user chốt 16/9): phím/cử chỉ back hệ thống đã lo (BackHandler ở DbtopScreen).
        onBack = null,
        // Tan vao nen theme de khop mau voi vung status bar phia tren —
        // truoc day band xam surface con thanh noti mau background, lo seam.
        containerColor = colors.background,
        // Tiêu đề DASHBOARD to hơn chuẩn (user chốt 17/9: "tăng kích thước chữ dashboard").
        titleSize = 18.sp,
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
    perDay: Double?,
    kpis: CashflowKpiSummary,
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
        // phai loi — error do de danh cho SCAN OFFLINE.
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
            // RUN-RATE & APR gop vao bang chinh (user chot 26/9, mock
            // kohi-dashboard-merge-prototype.html): 4 so nay la phan "tai sao"
            // cua NET WORTH / YIELD DAY, khong con the KPI rieng o tab CHART.
            ChuText("RUN-RATE & APR", style = type.labelSmall, color = colors.textMuted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val netApr = kpis.netRunRateApr
                MetricCell(
                    label = "NET RUN-RATE APR",
                    value = netApr?.let { String.format(Locale.US, "%s%.1f%%", if (it >= 0) "+" else "", it) } ?: "--",
                    color = when {
                        netApr == null -> colors.textMuted
                        netApr >= 0 -> colors.success
                        else -> colors.error
                    },
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    label = "GROSS APR",
                    value = kpis.grossApr?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--",
                    color = colors.accent,
                    alignEnd = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricCell(
                    label = "DAILY NET CASHFLOW",
                    value = "${if (kpis.netRunRatePerDay >= 0) "+" else "-"}${DeFiFormatter.formatUsd(abs(kpis.netRunRatePerDay))}/D",
                    color = if (kpis.netRunRatePerDay >= 0) colors.success else colors.error,
                    modifier = Modifier.weight(1f),
                )
                val burn = kpis.burnRatioPct
                MetricCell(
                    label = "BURN RATIO",
                    value = burn?.let { String.format(Locale.US, "%.0f%%", it) } ?: "--",
                    color = when {
                        burn == null -> colors.textMuted
                        burn <= 50.0 -> colors.success
                        burn <= 100.0 -> colors.warning
                        else -> colors.error
                    },
                    alignEnd = true,
                    modifier = Modifier.weight(1f),
                )
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
                    // Tab dashboard to hơn (user chốt 17/9: "tiêu đề + tab").
                    style = type.label.copy(fontWeight = FontWeight.Bold),
                    color = if (active) colors.onAccent else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
