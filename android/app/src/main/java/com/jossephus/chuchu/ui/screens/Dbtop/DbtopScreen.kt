package com.jossephus.chuchu.ui.screens.Dbtop

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Scrim status bar = surface khop voi DBTOP command band ngay duoi.
        Spacer(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(colors.background),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
        DbtopTopBar(
            // Suy lai tu ts SNAPSHOT moi lan ve: ui.freshness la gia tri dong
            // bang tai luc fetch — server chet thi no bao "UPDATED 5M AGO"
            // xanh mai mai. state.freshness(nowSec) tra Dead khi ts=0 (chua
            // load) hoac qua lau, trung thuc hon.
            freshness = ui.state.freshness(nowSec),
            isRefreshing = ui.isRefreshing,
            onRefresh = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.refreshNow()
            },
            onClose = onClose,
        )

        // Loi mang da do ca man roi thi khong xep them banner OFFLINE thu hai
        if (ui.error == null && ui.everLoaded && ui.state.freshness(nowSec) is DataFreshness.Dead) {
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
                selectedRow != null -> "${selectedRow.name.uppercase()} SELECTED · TAP AGAIN TO CLOSE"
                else -> "SELECT A POSITION TO INSPECT ITS BREAKDOWN"
            },
        )
    }
}
}

private fun DbtopUiState.itemCount(): Int = when (selectedView) {
    DbtopView.POSITIONS -> state.rows.size
    DbtopView.CHARTS -> 2
}
