package com.jossephus.chuchu.ui.screens.Dbtop

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jossephus.chuchu.data.model.dbtop.DataFreshness
import com.jossephus.chuchu.ui.components.KohiNoticeBand
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.theme.ChuColors
import java.util.Locale

/** dbtop's stacked mobile hierarchy with one selection rail and detail pane. */
@Composable
fun DbtopScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DbtopViewModel = viewModel(
        factory = DbtopViewModel.factory(LocalContext.current.applicationContext as Application),
    ),
) {
    val colors = ChuColors.current
    val haptics = LocalHapticFeedback.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val nowSec = System.currentTimeMillis() / 1_000L
    val currentPerDay = ui.currentPerDay(nowSec)
    val selectedRow = ui.state.rows.firstOrNull { it.positionKey() == ui.selectedPositionKey }

    BackHandler(onBack = onClose)
    LifecycleResumeEffect(Unit) {
        viewModel.startPolling()
        onPauseOrDispose { viewModel.stopPolling() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        DbtopTopBar(
            freshness = ui.freshness,
            isRefreshing = ui.isRefreshing,
            onRefresh = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.refreshNow()
            },
            onClose = onClose,
        )

        ui.error?.let { KohiNoticeBand(text = it, color = colors.error, urgent = true) }
        if (ui.everLoaded && ui.freshness is DataFreshness.Dead) {
            KohiNoticeBand(
                text = "SCAN OFFLINE — YIELD AND APR ARE HIDDEN · RESTART DEBANK/RUN.SH",
                color = colors.error,
                urgent = true,
            )
        }
        ui.criticalLendingRow?.let { critical ->
            KohiNoticeBand(
                text = "⚠ HIGH RISK · ${critical.name} · HF ${String.format(Locale.US, "%.2fx", critical.health ?: 0.0)}",
                color = colors.warning,
            )
        }

        DashboardSummary(
            netWorth = ui.state.netWorth,
            wallet = ui.state.wallet,
            perDay = currentPerDay,
            debt = ui.state.rows.sumOf { it.debt ?: 0.0 },
        )
        DashboardViewBand(
            selected = ui.selectedView,
            positionCount = ui.state.rows.size,
            walletCount = ui.state.walletTokens.size,
            onSelect = { nextView ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.selectView(nextView)
            },
        )

        KohiSectionBand(
            label = ui.selectedView.label,
            meta = "${ui.itemCount()} ITEMS",
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (ui.selectedView) {
                DbtopView.CHARTS -> ChartsView(
                    netWorth = ui.state.netWorth,
                    currentPerDay = currentPerDay,
                    curve = ui.state.curve,
                    daily = ui.state.daily,
                )
                DbtopView.WALLET -> WalletView(tokens = ui.state.walletTokens)
                DbtopView.POSITIONS -> PositionsView(
                    rows = ui.state.rows,
                    selectedKey = ui.selectedPositionKey,
                    showYield = currentPerDay != null,
                    nowSec = nowSec,
                    onSelect = { row ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.togglePosition(row.positionKey())
                    },
                )
            }
        }

        if (ui.selectedView == DbtopView.POSITIONS) {
            selectedRow?.let { row ->
                PositionDetailPane(
                    row = row,
                    showYield = currentPerDay != null && (row.expiry == null || row.expiry > nowSec),
                    onClose = { viewModel.togglePosition(row.positionKey()) },
                )
            }
        }

        DashboardHint(
            when {
                ui.selectedView == DbtopView.CHARTS -> "CURVE AND DAILY YIELD FOLLOW THE LATEST DBTOP SNAPSHOT"
                ui.selectedView == DbtopView.WALLET -> "WALLET BALANCES ARE READ-ONLY"
                selectedRow != null -> "${selectedRow.name.uppercase()} SELECTED · TAP AGAIN TO CLOSE"
                else -> "SELECT A POSITION TO INSPECT ITS BREAKDOWN"
            },
        )
    }
}

private fun DbtopUiState.itemCount(): Int = when (selectedView) {
    DbtopView.POSITIONS -> state.rows.size
    DbtopView.WALLET -> state.walletTokens.size
    DbtopView.CHARTS -> 2
}
