package com.jossephus.chuchu.ui.screens.Dbtop

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jossephus.chuchu.data.model.dbtop.DappRow
import com.jossephus.chuchu.data.model.dbtop.DataFreshness
import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import com.jossephus.chuchu.data.model.dbtop.RiskEvaluator
import com.jossephus.chuchu.data.model.dbtop.RiskTier
import com.jossephus.chuchu.data.model.dbtop.TokenPosition
import com.jossephus.chuchu.data.model.dbtop.WalletToken
import java.util.Locale
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.TuiBadge
import com.jossephus.chuchu.ui.components.chart.DailyYieldBarChart
import com.jossephus.chuchu.ui.components.chart.NetWorthCurveChart
import com.jossephus.chuchu.ui.screens.Queue.QueuePalette
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

object DbtopPalette {
    val Gold = Color(0xFFFACC15)
    val YieldCyan = Color(0xFF38BDF8)
    val Emerald = Color(0xFF4ADE80)
    val AmberWarn = Color(0xFFFB923C)
    val CrimsonCrit = Color(0xFFFF5252)
    val PurpleOption = Color(0xFFC084FC)
    val BlueLending = Color(0xFF60A5FA)
    val TealPool = Color(0xFF2DD4BF)
    val SurfaceCard = Color(0xFF1E293B)
    val SurfaceTray = Color(0xFF0F172A)
    val BorderMuted = Color(0xFF334155)
}

@Composable
fun DbtopScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DbtopViewModel = viewModel(
        factory = DbtopViewModel.factory(LocalContext.current.applicationContext as Application),
    ),
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current
    val haptics = LocalHapticFeedback.current
    val uiState by viewModel.ui.collectAsStateWithLifecycle()

    BackHandler { onClose() }

    LifecycleResumeEffect(Unit) {
        viewModel.startPolling()
        onPauseOrDispose {
            viewModel.stopPolling()
        }
    }

    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // --- 1. TOP BAR ---
        DbtopTopBar(
            freshness = uiState.freshness,
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.pullToRefresh()
            },
            onClose = onClose,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // --- 2. HEADER KPI BAND ---
            item(key = "kpi_band") {
                DbtopKpiBand(
                    netWorth = uiState.state.netWorth,
                    dailyYield = uiState.state.perday,
                    netApr = uiState.state.apr ?: 0.0,
                    delta24hPct = uiState.state.delta["h24"]?.pct ?: 0.0,
                    delta24hUsd = uiState.state.delta["h24"]?.d ?: 0.0,
                    defiCap = uiState.state.defi,
                    walletCash = uiState.state.wallet,
                )
            }

            // --- 3. DYNAMIC RISK BANNER ---
            val criticalRow = uiState.criticalLendingRow
            if (criticalRow != null) {
                item(key = "risk_banner") {
                    DbtopRiskBanner(
                        row = criticalRow,
                        onClick = {
                            viewModel.setFilter(DbtopFilter.LENDING)
                            viewModel.toggleRowExpanded(criticalRow.name)
                        },
                    )
                }
            }

            // --- 4. FILTER CHIPS BAR ---
            item(key = "filter_chips") {
                DbtopFilterChipsBar(
                    selected = uiState.selectedFilter,
                    counts = uiState.categoryCounts,
                    onSelect = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.setFilter(it)
                    },
                )
            }

            // --- 5. MAIN CONTENT BASED ON FILTER ---
            when (uiState.selectedFilter) {
                DbtopFilter.CHARTS -> {
                    item(key = "charts_curve") {
                        ChuCard(
                            background = colors.surfaceVariant,
                            border = colors.border,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ChuText("ĐỒ THỊ TÀI SẢN RÒNG (NET WORTH)", style = typography.labelSmall, color = colors.textMuted)
                                    ChuText(DeFiFormatter.formatUsd(uiState.state.netWorth), style = typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DbtopPalette.Gold)
                                }
                                NetWorthCurveChart(
                                    points = uiState.state.curve,
                                    lineColor = DbtopPalette.YieldCyan,
                                    height = 190.dp,
                                )
                            }
                        }
                    }

                    item(key = "charts_daily") {
                        ChuCard(
                            background = colors.surfaceVariant,
                            border = colors.border,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ChuText("LỢI NHUẬN THEO NGÀY (DAILY YIELD)", style = typography.labelSmall, color = colors.textMuted)
                                    ChuText("+${DeFiFormatter.formatUsd(uiState.state.perday)}/ngày", style = typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DbtopPalette.YieldCyan)
                                }
                                DailyYieldBarChart(
                                    dailyData = uiState.state.daily,
                                    primaryColor = DbtopPalette.YieldCyan,
                                    accentColor = DbtopPalette.Emerald,
                                    height = 170.dp,
                                )
                            }
                        }
                    }
                }

                DbtopFilter.WALLET -> {
                    item(key = "wallet_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChuText("TIỀN MẶT TRONG VÍ (${uiState.state.walletTokens.size} TOKENS)", style = typography.labelSmall, color = colors.textMuted)
                            ChuText(DeFiFormatter.formatUsd(uiState.state.wallet), style = typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DbtopPalette.Gold)
                        }
                    }

                    items(uiState.state.walletTokens, key = { it.sym + it.amt.toString() }) { token ->
                        WalletTokenCard(token = token)
                    }
                }

                else -> {
                    // DApp Position Cards
                    items(uiState.filteredRows, key = { it.name + it.proto }) { row ->
                        val isExpanded = uiState.expandedRowName == row.name
                        DappPositionCard(
                            row = row,
                            isExpanded = isExpanded,
                            onToggle = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.toggleRowExpanded(row.name)
                            },
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-COMPONENTS
// =============================================================================

@Composable
private fun DbtopTopBar(
    freshness: DataFreshness,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.border))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChuButton(
                onClick = onClose,
                variant = ChuButtonVariant.Ghost,
                bracketed = true,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            ) {
                ChuText("←", style = typography.label, color = colors.textSecondary)
            }

            ChuText("dbtop", style = typography.title.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)

            // Freshness Dot
            val dotColor = when (freshness) {
                is DataFreshness.Fresh -> DbtopPalette.Emerald
                is DataFreshness.Warning -> DbtopPalette.AmberWarn
                is DataFreshness.Dead -> DbtopPalette.CrimsonCrit
            }
            val ageText = when (freshness) {
                is DataFreshness.Fresh -> "${freshness.ageSeconds / 60}m trước"
                is DataFreshness.Warning -> "${freshness.ageSeconds / 60}m (cũ)"
                is DataFreshness.Dead -> "${freshness.ageSeconds / 3600}h (chết)"
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(dotColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                ChuText("●", style = typography.labelSmall, color = dotColor)
                ChuText(ageText, style = typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp), color = dotColor)
            }
        }

        ChuButton(
            onClick = onRefresh,
            variant = ChuButtonVariant.Ghost,
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        ) {
            ChuText(
                if (isRefreshing) "↻ ĐANG ĐỒNG BỘ" else "↻ ĐỒNG BỘ",
                style = typography.labelSmall,
                color = if (isRefreshing) DbtopPalette.YieldCyan else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun DbtopKpiBand(
    netWorth: Double,
    dailyYield: Double,
    netApr: Double,
    delta24hPct: Double,
    delta24hUsd: Double,
    defiCap: Double,
    walletCash: Double,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(6.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChuText("TỔNG TÀI SẢN (NET WORTH)", style = typography.labelSmall.copy(letterSpacing = 1.sp), color = colors.textMuted)
            TuiBadge("DeFi: ${DeFiFormatter.formatUsdCompact(defiCap)} · Ví: ${DeFiFormatter.formatUsdCompact(walletCash)}", colors.textSecondary)
        }

        BasicText(
            text = DeFiFormatter.formatUsd(netWorth),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = DbtopPalette.Gold,
            ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DbtopPalette.BorderMuted),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cột 1: Thu nhập / ngày
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ChuText("THU NHẬP / NGÀY", style = typography.labelSmall.copy(fontSize = 10.sp), color = colors.textMuted)
                ChuText("+${DeFiFormatter.formatUsd(dailyYield)}/d", style = typography.body.copy(fontWeight = FontWeight.Bold), color = DbtopPalette.YieldCyan)
                ChuText("≈ ${DeFiFormatter.formatUsdCompact(dailyYield * 30)}/tháng", style = typography.labelSmall.copy(fontSize = 10.sp), color = colors.textSecondary)
            }

            // Cột 2: Net APR
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ChuText("NET APR", style = typography.labelSmall.copy(fontSize = 10.sp), color = colors.textMuted)
                ChuText(DeFiFormatter.formatPercent(netApr, showPlusSign = false), style = typography.body.copy(fontWeight = FontWeight.Bold), color = DbtopPalette.Emerald)
                ChuText("lãi danh mục", style = typography.labelSmall.copy(fontSize = 10.sp), color = colors.textSecondary)
            }

            // Cột 3: Biến động 24h
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ChuText("BIẾN ĐỘNG 24H", style = typography.labelSmall.copy(fontSize = 10.sp), color = colors.textMuted)
                val signStr = if (delta24hPct >= 0) "▲ " else "▼ "
                val chgColor = if (delta24hPct >= 0) DbtopPalette.Emerald else DbtopPalette.CrimsonCrit
                ChuText("$signStr${DeFiFormatter.formatPercent(delta24hPct)}", style = typography.body.copy(fontWeight = FontWeight.Bold), color = chgColor)
                ChuText(DeFiFormatter.formatUsd(delta24hUsd), style = typography.labelSmall.copy(fontSize = 10.sp), color = chgColor)
            }
        }
    }
}

@Composable
private fun DbtopRiskBanner(
    row: DappRow,
    onClick: () -> Unit,
) {
    val typography = ChuTypography.current
    val hf = row.health ?: 1.0
    val isCritical = hf < 1.15
    val primaryColor = if (isCritical) DbtopPalette.CrimsonCrit else DbtopPalette.AmberWarn

    val infiniteTransition = rememberInfiniteTransition(label = "risk_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isCritical) 600 else 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(primaryColor.copy(alpha = 0.08f))
            .border(BorderStroke(1.2.dp, primaryColor.copy(alpha = pulseAlpha)), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ChuText(if (isCritical) "⚠ [NGUY HIỂM]" else "▲ [CẢNH BÁO]", style = typography.label.copy(fontWeight = FontWeight.Bold), color = primaryColor)
                ChuText("@${row.name}", style = typography.label.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(primaryColor.copy(alpha = 0.2f))
                    .border(BorderStroke(1.dp, primaryColor.copy(alpha = 0.8f)), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                ChuText("HF ${String.format(Locale.US, "%.2fx", hf)}", style = typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = primaryColor)
            }
        }

        val dropMsg = if (row.liqDrop != null) "Chỉ cần ${row.liqBase ?: "tài sản"} giảm -${String.format(Locale.US, "%.2f%%", row.liqDrop)} (về \$${String.format(Locale.US, "%.4f", row.liqAt ?: 0.0)}) là bị thanh lý!" else "Vị thế cận kề rủi ro thanh lý, hãy theo dõi sát sao."
        ChuText(dropMsg, style = typography.bodySmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DbtopFilterChipsBar(
    selected: DbtopFilter,
    counts: Map<DbtopFilter, Int>,
    onSelect: (DbtopFilter) -> Unit,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DbtopFilter.entries.forEach { filter ->
            val count = counts[filter] ?: 0
            val isSelected = filter == selected
            val labelText = if (filter == DbtopFilter.CHARTS) "${filter.label} 📈" else "${filter.label} ($count)"

            DbtopFilterChip(
                label = labelText,
                isSelected = isSelected,
                onClick = { onSelect(filter) },
            )
        }
    }
}

@Composable
private fun DbtopFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current

    val borderColor = if (isSelected) DbtopPalette.YieldCyan else colors.border
    val bgColor = if (isSelected) DbtopPalette.YieldCyan.copy(alpha = 0.15f) else colors.surfaceVariant
    val textColor = if (isSelected) DbtopPalette.YieldCyan else colors.textSecondary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ChuText("[", style = typography.labelSmall, color = borderColor)
            ChuText(label, style = typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal), color = textColor)
            ChuText("]", style = typography.labelSmall, color = borderColor)
        }
    }
}

@Composable
private fun DappPositionCard(
    row: DappRow,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current

    val isOption = row.detail?.option != null || row.name.startsWith("Call", true) || row.name.startsWith("Put", true)
    val isLending = row.health != null || row.debt != null

    val cardBorderColor = when {
        row.health != null && row.health < 1.15 -> DbtopPalette.CrimsonCrit
        row.health != null && row.health < 1.25 -> DbtopPalette.AmberWarn
        isExpanded -> DbtopPalette.YieldCyan.copy(alpha = 0.6f)
        else -> colors.border
    }

    val glyph = when {
        isOption -> "◈"
        isLending -> "⬡"
        row.proto.contains("balancer", true) -> "⟡"
        row.proto.contains("mento", true) -> "⊞"
        else -> "◆"
    }

    val glyphColor = when {
        isOption -> DbtopPalette.PurpleOption
        isLending -> DbtopPalette.BlueLending
        else -> DbtopPalette.TealPool
    }

    ChuCard(
        background = colors.surface,
        border = cardBorderColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = 600f)),
        ) {
            // TẦNG 1: OVERVIEW ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    ChuText(glyph, style = typography.title, color = glyphColor)

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ChuText(row.name, style = typography.body.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (row.apr != null && row.apr > 0) {
                                TuiBadge("${DeFiFormatter.formatPercent(row.apr, showPlusSign = false)} APR", DbtopPalette.Emerald)
                            }
                            if (row.health != null) {
                                val hfColor = when {
                                    row.health < 1.15 -> DbtopPalette.CrimsonCrit
                                    row.health < 1.25 -> DbtopPalette.AmberWarn
                                    else -> DbtopPalette.Emerald
                                }
                                TuiBadge("HF ${String.format(Locale.US, "%.2fx", row.health)}", hfColor)
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ChuText(DeFiFormatter.formatUsd(row.cap), style = typography.body.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                        if (row.perday > 0) {
                            ChuText("+${DeFiFormatter.formatUsd(row.perday)}/d", style = typography.labelSmall, color = DbtopPalette.YieldCyan)
                        }
                    }
                    ChuText(if (isExpanded) "[▲]" else "[▼]", style = typography.labelSmall, color = if (isExpanded) DbtopPalette.YieldCyan else colors.textMuted)
                }
            }

            // TẦNG 2: EXPANDED ACTION TRAY
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DbtopPalette.SurfaceTray)
                        .border(BorderStroke(1.dp, DbtopPalette.BorderMuted))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Option details
                    val opt = row.detail?.option
                    if (opt != null) {
                        OptionDetailView(opt = opt)
                    }

                    // Lending details
                    if (row.health != null || row.debt != null) {
                        LendingDetailView(row = row)
                    }

                    // Supply tokens
                    val supplies = row.detail?.supply.orEmpty()
                    if (supplies.isNotEmpty()) {
                        ChuText("TÀI SẢN THẾ CHẤP (SUPPLY):", style = typography.labelSmall, color = colors.textMuted)
                        supplies.forEach { t -> TokenRowView(t) }
                    }

                    // Borrow tokens
                    val borrows = row.detail?.borrow.orEmpty()
                    if (borrows.isNotEmpty()) {
                        ChuText("NỢ VAY (BORROW DEBT):", style = typography.labelSmall, color = colors.textMuted)
                        borrows.forEach { t -> TokenRowView(t, isDebt = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionDetailView(opt: com.jossephus.chuchu.data.model.dbtop.OptionDetail) {
    val typography = ChuTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ChuText("Loại quyền chọn:", style = typography.labelSmall, color = Color.Gray)
            ChuText("${opt.type} (Strike: \$${String.format(Locale.US, "%,.0f", opt.strike ?: 0.0)})", style = typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DbtopPalette.PurpleOption)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ChuText("Thời gian đáo hạn (DTE):", style = typography.labelSmall, color = Color.Gray)
            ChuText(DeFiFormatter.formatDteDays(opt.dte), style = typography.labelSmall, color = Color.White)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ChuText("Premium đã thu:", style = typography.labelSmall, color = Color.Gray)
            ChuText("+${DeFiFormatter.formatUsd(opt.prem)} (${DeFiFormatter.formatPercent(opt.apr, showPlusSign = false)} APR)", style = typography.labelSmall, color = DbtopPalette.Emerald)
        }
        if (opt.itmUsd != null && opt.itmUsd > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ChuText("Trạng thái ITM:", style = typography.labelSmall, color = DbtopPalette.CrimsonCrit)
                ChuText("+${DeFiFormatter.formatUsd(opt.itmUsd)}", style = typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DbtopPalette.CrimsonCrit)
            }
        }
    }
}

@Composable
private fun LendingDetailView(row: DappRow) {
    val typography = ChuTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ChuText("Tổng thế chấp / Nợ:", style = typography.labelSmall, color = Color.Gray)
            ChuText("${DeFiFormatter.formatUsdCompact(row.coll)} / ${DeFiFormatter.formatUsdCompact(row.debt)} (Lev ${String.format(Locale.US, "%.2fx", row.lev ?: 1.0)})", style = typography.labelSmall, color = Color.White)
        }
        if (row.liqDrop != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ChuText("Đệm thanh lý:", style = typography.labelSmall, color = Color.Gray)
                ChuText("-${String.format(Locale.US, "%.2f%%", row.liqDrop)} (về \$${String.format(Locale.US, "%.4f", row.liqAt ?: 0.0)})", style = typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DbtopPalette.AmberWarn)
            }
        }
    }
}

@Composable
private fun TokenRowView(t: TokenPosition, isDebt: Boolean = false) {
    val typography = ChuTypography.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuText("  • ${t.amt} ${t.sym}", style = typography.labelSmall, color = if (isDebt) DbtopPalette.CrimsonCrit else Color.White)
        ChuText(DeFiFormatter.formatUsd(t.usd), style = typography.labelSmall, color = if (isDebt) DbtopPalette.CrimsonCrit else Color.Gray)
    }
}

@Composable
private fun WalletTokenCard(token: WalletToken) {
    val colors = ChuColors.current
    val typography = ChuTypography.current

    ChuCard(
        background = colors.surface,
        border = colors.border,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChuText("⚡", style = typography.label, color = DbtopPalette.Gold)
                Column {
                    ChuText(token.sym, style = typography.body.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    ChuText("${token.amt} ${token.sym} @ ${DeFiFormatter.formatTokenPrice(token.px)}", style = typography.labelSmall, color = colors.textSecondary)
                }
            }
            ChuText(DeFiFormatter.formatUsd(token.usd), style = typography.body.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
        }
    }
}
