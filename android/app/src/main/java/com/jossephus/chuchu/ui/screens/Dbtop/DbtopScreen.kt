package com.jossephus.chuchu.ui.screens.Dbtop

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jossephus.chuchu.data.model.dbtop.DataFreshness
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiNoticeBand
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
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
    val nowSec = androidx.compose.runtime.remember(ui.state) { System.currentTimeMillis() / 1_000L }
    val currentPerDay = ui.currentPerDay(nowSec)
    val selectedRow = ui.state.rows.firstOrNull { it.positionKey() == ui.selectedPositionKey }
    val watchlistItems = androidx.compose.runtime.remember(ui.state, ui.spending) { ui.state.buildWatchlist(ui.spending?.px24 ?: emptyMap()) }

    BackHandler(onBack = onClose)
    LifecycleResumeEffect(Unit) {
        viewModel.startPolling()
        onPauseOrDispose { viewModel.stopPolling() }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val wide = maxWidth >= 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            DbtopTopBar(
                freshness = ui.state.freshness(nowSec),
                isRefreshing = ui.isRefreshing,
                onRefresh = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.refreshNow()
                },
                onClose = onClose,
            )

            if (ui.error == null && ui.everLoaded && ui.state.freshness(nowSec) is DataFreshness.Dead) {
                KohiNoticeBand(
                    text = "SCAN OFFLINE — YIELD AND APR ARE HIDDEN · RESTART DEBANK/RUN.SH",
                    color = colors.error,
                    urgent = true,
                )
            }
            ui.criticalLendingRow?.let { critical ->
                KohiNoticeBand(
                    text = "⚠ HIGH RISK · ${critical.name} · HF ${String.format(Locale.US, "%.2fx", critical.health ?: 0.0)} · TAP TO VIEW",
                    color = colors.warning,
                    modifier = Modifier.clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.selectView(DbtopView.POSITIONS)
                        viewModel.togglePosition(critical.positionKey())
                    },
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
                onSelect = { nextView ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.selectView(nextView)
                },
            )

            KohiSectionBand(
                label = ui.selectedView.label,
                meta = "${ui.itemCount(watchlistItems.size)} ITEMS",
                containerColor = colors.background,
            )

            if (wide && ui.selectedView == DbtopView.POSITIONS) {
                // Layout Master-Detail tối ưu cho màn hình gập mở rộng của Vivo X Fold 5
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight(),
                    ) {
                        PositionsView(
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(colors.surface)
                            .border(1.dp, colors.border),
                    ) {
                        if (selectedRow != null) {
                            PositionDetailPane(
                                row = selectedRow,
                                showYield = currentPerDay != null && (selectedRow.expiry == null || selectedRow.expiry > nowSec),
                                onClose = { viewModel.togglePosition(selectedRow.positionKey()) },
                            )
                        } else {
                            YieldInsightPane(
                                state = ui.state,
                                currentPerDay = currentPerDay,
                            )
                        }
                    }
                }
            } else {
                // Layout chuẩn cho màn hình ngoài / màn hình hẹp
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
                            spendByDay = ui.spending?.byDay ?: emptyMap(),
                        )
                        DbtopView.WATCHLIST -> WatchlistView(
                            items = watchlistItems,
                        )
                        DbtopView.SPENDING -> SpendingView(
                            spending = ui.spending,
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

                // Man hep: detail la BOTTOM SHEET (user doi 27/8 tu popup giua
                // man): van scrim mo + tap ra ngoai de dong, nhung khung nam
                // sat day — tay voi toi de hon va hop thao tac vuot.
                if (ui.selectedView == DbtopView.POSITIONS) {
                    selectedRow?.let { row ->
                        val dismiss = { viewModel.togglePosition(row.positionKey()) }
                        androidx.compose.ui.window.Dialog(
                            onDismissRequest = dismiss,
                            // decorFitsSystemWindows=false: cua so dialog phu ca man va TU nhan
                            // inset — thieu no thi navigationBarsPadding trong dialog tra 0,
                            // sheet de len navbar va "lem" mat may dong cuoi (bug 27/8).
                            properties = androidx.compose.ui.window.DialogProperties(
                                usePlatformDefaultWidth = false,
                                decorFitsSystemWindows = false,
                            ),
                        ) {
                            // fillMaxSize + clickable = vung scrim bat tap-ra-ngoai
                            // (voi usePlatformDefaultWidth=false, cua so dialog chiem
                            // ca man nen onDismissRequest khong tu bat tap nua).
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    // Scrim tu ve — dim cua window co the khong an
                                    // khi decorFitsSystemWindows=false.
                                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null,
                                        onClick = dismiss,
                                    ),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null,
                                        ) {}
                                        .background(colors.surface)
                                        .border(1.dp, colors.border)
                                        .navigationBarsPadding(),
                                ) {
                                    PositionDetailPane(
                                        row = row,
                                        showYield = currentPerDay != null && (row.expiry == null || row.expiry > nowSec),
                                        onClose = dismiss,
                                        maxHeight = 480.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DbtopUiState.itemCount(watchlistCount: Int): Int = when (selectedView) {
    DbtopView.POSITIONS -> state.rows.size
    DbtopView.WATCHLIST -> watchlistCount
    DbtopView.CHARTS -> 2
    DbtopView.SPENDING -> spending?.count ?: 0
}
