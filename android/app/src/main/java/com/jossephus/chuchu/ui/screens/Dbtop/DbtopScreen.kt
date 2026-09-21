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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
    // Lấy từ VM: nó nhích theo MỌI poll, kể cả khi server chết (xem DbtopUiState.nowSec).
    val nowSec = ui.nowSec
    val currentPerDay = ui.currentPerDay(nowSec)
    val selectedRow = ui.state.rows.firstOrNull { it.positionKey() == ui.selectedPositionKey }
    val watchlistItems = remember(ui.state, ui.spending) { ui.state.buildWatchlist(ui.spending?.px24 ?: emptyMap()) }
    // Tong debt chi phu thuoc snapshot — dung cong lai moi lan man recompose
    // (doi tab, chon row, xoay che do tien deu recompose ca screen).
    val totalDebt = remember(ui.state) { ui.state.rows.sumOf { it.debt ?: 0.0 } }
    val views = remember { DbtopView.entries }
    val pagerState = rememberPagerState(initialPage = ui.selectedView.ordinal) { views.size }
    val coroutineScope = rememberCoroutineScope()

    // Đồng bộ khi người dùng vuốt xong sang trang khác (settled)
    LaunchedEffect(pagerState.settledPage) {
        val target = views[pagerState.settledPage]
        if (ui.selectedView != target) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.selectView(target)
        }
    }

    // Đồng bộ khi ViewModel đổi view từ nguồn ngoài (vd: banner cảnh báo rủi ro)
    LaunchedEffect(ui.selectedView) {
        if (pagerState.currentPage != ui.selectedView.ordinal) {
            pagerState.animateScrollToPage(ui.selectedView.ordinal)
        }
    }

    BackHandler {
        if (pagerState.currentPage != 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(0)
            }
        } else {
            onClose()
        }
    }
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
                debt = totalDebt,
                moneyDisplay = ui.moneyDisplay,
                vndRate = ui.spending?.usdVnd ?: 0.0,
                onCycleMoney = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.cycleMoneyDisplay()
                },
            )
            DashboardViewBand(
                selected = views[pagerState.currentPage],
                onSelect = { nextView ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.selectView(nextView)
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(nextView.ordinal)
                    }
                },
            )

            // HorizontalPager: vuốt trái/phải di chuyển mượt mà giữa các tab POS, WATCH, CHART, SPEND
            // Không compose sẵn trang kề: CHART vẽ canvas, compose ngầm là tốn công vô ích.
            HorizontalPager(
                state = pagerState,
                key = { views[it] },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (views[page]) {
                    DbtopView.POSITIONS -> {
                        if (wide) {
                            // Layout Master-Detail tối ưu cho màn hình gập mở rộng của Vivo X Fold 5
                            Row(
                                modifier = Modifier.fillMaxSize(),
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
                                            maxHeight = androidx.compose.ui.unit.Dp.Infinity,
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
                    }
                    DbtopView.WATCHLIST -> WatchlistView(
                        items = watchlistItems,
                    )
                    DbtopView.CHARTS -> ChartsView(
                        netWorth = ui.state.netWorth,
                        currentPerDay = currentPerDay,
                        curve = ui.state.curve,
                        daily = ui.state.daily,
                        spending = ui.spending,
                        spendByDay = ui.spending?.byDay ?: emptyMap(),
                        cap = ui.state.cap.takeIf { it > 0 } ?: ui.state.rows.sumOf { it.cap }.takeIf { it > 0 } ?: ui.state.netWorth,
                        apr = ui.state.apr,
                    )
                    DbtopView.SPENDING -> SpendingView(
                        spending = ui.spending,
                        moneyDisplay = ui.moneyDisplay,
                    )
                }
            }

            // Màn hẹp: detail là BOTTOM SHEET (user đòi 27/8 từ popup giữa màn)
            if (!wide && ui.selectedView == DbtopView.POSITIONS) {
                selectedRow?.let { row ->
                    val dismiss = { viewModel.togglePosition(row.positionKey()) }
                    com.jossephus.chuchu.ui.components.KohiBottomSheet(onDismiss = dismiss) {
                        PositionDetailPane(
                            row = row,
                            showYield = currentPerDay != null && (row.expiry == null || row.expiry > nowSec),
                            onClose = dismiss,
                            maxHeight = 560.dp,
                        )
                    }
                }
            }
        }
    }
}

