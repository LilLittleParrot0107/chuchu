package com.jossephus.chuchu.ui.screens.Dbtop

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jossephus.chuchu.data.model.dbtop.DappRow
import com.jossephus.chuchu.data.model.dbtop.DataFreshness
import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import com.jossephus.chuchu.data.model.dbtop.OptionPosition
import com.jossephus.chuchu.data.model.dbtop.WalletToken
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.TuiBadge
import com.jossephus.chuchu.ui.components.chart.DailyYieldBarChart
import com.jossephus.chuchu.ui.components.chart.NetWorthCurveChart
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import java.util.Locale

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

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        onClose()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onScreenVisible()
        onPauseOrDispose {
            viewModel.onScreenHidden()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // ==================== TOP BAR ====================
            DbtopTopBar(
                freshness = uiState.freshness,
                isRefreshing = uiState.isRefreshing,
                onRefresh = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.refresh()
                },
                onClose = onClose,
            )

            // Error Banner
            if (uiState.errorMessage != null && !uiState.state.hasData) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.error.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    ChuText(
                        uiState.errorMessage ?: "",
                        style = typography.labelSmall,
                        color = colors.error,
                    )
                }
            }

            // ==================== MAIN SCROLLABLE CONTENT ====================
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // SUMMARY OVERVIEW CARD
                item(key = "overview_card") {
                    SummaryOverviewCard(
                        netWorth = uiState.state.netWorth,
                        wallet = uiState.state.wallet,
                        perday = uiState.state.perday,
                        debt = uiState.state.debt,
                    )
                }

                // CATEGORY FILTER CHIPS
                item(key = "filter_chips") {
                    DbtopFilterChipsBar(
                        selected = uiState.selectedFilter,
                        counts = uiState.filterCounts,
                        onSelect = { filter ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.setFilter(filter)
                        },
                    )
                }

                // VIEW: CHARTS
                if (uiState.selectedFilter == DbtopFilter.CHARTS) {
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
                                    ChuText("NET WORTH CURVE", style = typography.labelSmall, color = colors.textMuted)
                                    ChuText(DeFiFormatter.formatUsd(uiState.state.netWorth), style = typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = colors.accent)
                                }
                                NetWorthCurveChart(
                                    points = uiState.state.curve,
                                    lineColor = colors.accent,
                                    height = 180.dp,
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
                                    ChuText("DAILY YIELD", style = typography.labelSmall, color = colors.textMuted)
                                    ChuText("+${DeFiFormatter.formatUsd(uiState.state.perday)}/d", style = typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = colors.success)
                                }
                                DailyYieldBarChart(
                                    dailyData = uiState.state.daily,
                                    primaryColor = colors.accent,
                                    accentColor = colors.success,
                                    height = 160.dp,
                                )
                            }
                        }
                    }
                } else if (uiState.selectedFilter == DbtopFilter.WALLET) {
                    // VIEW: WALLET TOKENS
                    items(uiState.state.walletTokens, key = { it.sym + it.amt.toString() }) { token ->
                        WalletTokenCard(token = token)
                    }
                } else {
                    // VIEW: DAPP POSITION CARDS
                    if (uiState.filteredRows.isEmpty()) {
                        item(key = "empty_state") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                ChuText("no positions in this category", style = typography.body, color = colors.textMuted)
                            }
                        }
                    } else {
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
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

            ChuText("dbtop", style = typography.title.copy(fontWeight = FontWeight.Bold))

            val dotColor = when (freshness) {
                is DataFreshness.Fresh -> colors.success
                is DataFreshness.Warning -> colors.warning
                is DataFreshness.Dead -> colors.error
            }
            val ageText = when (freshness) {
                is DataFreshness.Fresh -> "${freshness.ageSeconds / 60}m"
                is DataFreshness.Warning -> "${freshness.ageSeconds / 60}m old"
                is DataFreshness.Dead -> "${freshness.ageSeconds / 3600}h dead"
            }
            TuiBadge(ageText, dotColor)
        }

        ChuButton(
            onClick = onRefresh,
            variant = ChuButtonVariant.Ghost,
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        ) {
            ChuText(if (isRefreshing) "⟳…" else "⟳", style = typography.label, color = colors.textSecondary)
        }
    }
}

@Composable
private fun SummaryOverviewCard(
    netWorth: Double,
    wallet: Double,
    perday: Double,
    debt: Double,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current

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
            // Net Worth Top
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    ChuText("NET WORTH", style = typography.labelSmall, color = colors.textMuted)
                    ChuText(
                        DeFiFormatter.formatUsd(netWorth),
                        style = typography.headline.copy(fontWeight = FontWeight.Bold),
                        color = colors.accent,
                    )
                }

                if (perday > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        ChuText("YIELD / DAY", style = typography.labelSmall, color = colors.textMuted)
                        ChuText(
                            "+${DeFiFormatter.formatUsd(perday)}",
                            style = typography.title.copy(fontWeight = FontWeight.Bold),
                            color = colors.success,
                        )
                    }
                }
            }

            // Wallet & Debt Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText(
                    "wallet: ${DeFiFormatter.formatUsd(wallet)}",
                    style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textSecondary,
                )

                if (debt > 0) {
                    ChuText(
                        "debt: ${DeFiFormatter.formatUsd(debt)}",
                        style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun DbtopFilterChipsBar(
    selected: DbtopFilter,
    counts: Map<DbtopFilter, Int>,
    onSelect: (DbtopFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DbtopFilter.entries.forEach { filter ->
            val count = counts[filter] ?: 0
            val isSelected = filter == selected
            val labelText = if (filter == DbtopFilter.CHARTS) "${filter.label} 📈" else "${filter.label} ($count)"

            ChuButton(
                onClick = { onSelect(filter) },
                variant = if (isSelected) ChuButtonVariant.Filled else ChuButtonVariant.Outlined,
                bracketed = true,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            ) {
                ChuText(
                    labelText,
                    style = ChuTypography.current.labelSmall,
                    color = if (isSelected) ChuColors.current.onAccent else ChuColors.current.textSecondary,
                )
            }
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

    val glyph = when {
        isOption -> "◈"
        isLending -> "⬡"
        row.proto.contains("balancer", true) -> "⟡"
        else -> "◆"
    }

    ChuCard(
        background = colors.surfaceVariant,
        border = if (isExpanded) colors.accent else colors.border,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // TẦNG 1: PROTOCOL NAME & TOTAL CAP (FULL WIDTH RESPONSIVE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    ChuText(glyph, style = typography.title, color = colors.accentSecondary)
                    ChuText(
                        row.name,
                        style = typography.title.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(8.dp))

                ChuText(
                    DeFiFormatter.formatUsd(row.cap),
                    style = typography.title.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    color = colors.textPrimary,
                )
            }

            // TẦNG 2: METRICS LINE (MONOSPACE WITH DOT SEPARATORS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val metrics = buildString {
                    if (row.perday > 0) {
                        append("+${DeFiFormatter.formatUsd(row.perday)}/d")
                    }
                    if (row.apr != null && row.apr > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${DeFiFormatter.formatPercent(row.apr, showPlusSign = false)} APR")
                    }
                    if (row.health != null) {
                        if (isNotEmpty()) append(" · ")
                        append("HF ${String.format(Locale.US, "%.2fx", row.health)}")
                    }
                }

                ChuText(
                    text = if (metrics.isEmpty()) row.proto else metrics,
                    style = typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (row.health != null && row.health < 1.15) colors.error else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                ChuText(
                    if (isExpanded) "[▲]" else "[▼]",
                    style = typography.labelSmall,
                    color = colors.textMuted,
                )
            }

            // TẦNG 3: EXPANDED DETAILS
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val opt = row.detail?.option
                    if (opt != null) {
                        OptionDetailView(opt = opt)
                    }

                    if (row.tokens.isNotEmpty()) {
                        ChuText("tokens:", style = typography.labelSmall, color = colors.textMuted)
                        row.tokens.forEach { tok ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ChuText(
                                    "${String.format(Locale.US, "%.4f", tok.amt)} ${tok.sym}",
                                    style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = colors.textSecondary,
                                )
                                ChuText(
                                    DeFiFormatter.formatUsd(tok.valUsd),
                                    style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionDetailView(opt: OptionPosition) {
    val colors = ChuColors.current
    val typography = ChuTypography.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ChuText("type: ${opt.type.uppercase()}", style = typography.labelSmall, color = colors.accent)
            ChuText("strike: ${DeFiFormatter.formatUsd(opt.strike)}", style = typography.labelSmall, color = colors.textSecondary)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ChuText("expiry: ${opt.expiry}", style = typography.labelSmall, color = colors.textMuted)
            if (opt.iv != null) {
                ChuText("IV: ${String.format(Locale.US, "%.1f%%", opt.iv * 100)}", style = typography.labelSmall, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun WalletTokenCard(token: WalletToken) {
    val colors = ChuColors.current
    val typography = ChuTypography.current

    ChuCard(
        background = colors.surfaceVariant,
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChuText(token.sym, style = typography.title.copy(fontWeight = FontWeight.Bold))
                    if (token.chain.isNotBlank()) {
                        TuiBadge(token.chain, colors.accentSecondary)
                    }
                }
                ChuText(
                    "${String.format(Locale.US, "%.4f", token.amt)} @ ${DeFiFormatter.formatUsd(token.price)}",
                    style = typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textMuted,
                )
            }

            ChuText(
                DeFiFormatter.formatUsd(token.valUsd),
                style = typography.title.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = colors.textPrimary,
            )
        }
    }
}
