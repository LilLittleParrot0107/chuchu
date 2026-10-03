package com.jossephus.chuchu.ui.screens.Dbtop

import android.app.Application
import com.jossephus.chuchu.ui.components.KohiBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.jossephus.chuchu.ui.components.DAYS_PER_MONTH
import com.jossephus.chuchu.ui.components.chart.CashflowEngine
import com.jossephus.chuchu.ui.components.KohiNoticeBand
import com.jossephus.chuchu.ui.components.KohiSubTabs
import com.jossephus.chuchu.ui.components.rememberTallSafeInsets
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
    // Khoá Gemini của nút DỊCH trong buzz (27/9) — Settings đổi là sheet nhận ngay.
    val geminiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    // Lấy từ VM: nó nhích theo MỌI poll, kể cả khi server chết (xem DbtopUiState.nowSec).
    val nowSec = ui.nowSec
    val currentPerDay = ui.currentPerDay(nowSec)
    val selectedRow = ui.state.rows.firstOrNull { it.positionKey() == ui.selectedPositionKey }
    val watchlistItems = remember(ui.state, ui.spending) { ui.state.buildWatchlist(ui.spending?.px24 ?: emptyMap()) }
    // Bang summary va chart NET RATE dung CHUNG mot bo so KPI (user chot 26/9):
    // tinh mot lan o day roi truyen xuong, thay vi moi noi tu tinh lai.
    val spendByDay = ui.spending?.byDay ?: emptyMap()
    val capForKpi = ui.state.cap.takeIf { it > 0 }
        ?: ui.state.rows.sumOf { it.cap }.takeIf { it > 0 }
        ?: ui.state.netWorth
    val cashflowPoints = remember(ui.state.daily, spendByDay) {
        CashflowEngine.calculatePoints(ui.state.daily, spendByDay)
    }
    val fallbackSpendPerDay = ui.spending?.monthUsd?.takeIf { it > 0.0 }?.div(DAYS_PER_MONTH) ?: 0.0
    // 3/10 (user): SPEND chỉ 30 ngày — cắt cửa sổ TRƯỚC khi tính trailing để đường
    // dàn chi + yield% đều theo 30d; takeLast an toàn khi history < 30 ngày.
    val windowedPoints = remember(cashflowPoints) { cashflowPoints.takeLast(CashflowEngine.SPEND_WINDOW_DAYS) }
    val ratePoints = remember(windowedPoints, fallbackSpendPerDay) {
        CashflowEngine.calculateRatePoints(windowedPoints, fallbackSpendPerDay = fallbackSpendPerDay)
    }
    // %APR ung voi moi 1 USD/ngay — chinh he so bien truc USD thanh truc APR.
    val aprFactor = remember(capForKpi) { if (capForKpi > 0.0) 365.0 / capForKpi * 100.0 else null }
    // 3/10 (user): GROSS APR theo 30d — trung binh grossRate cac ngay do duoc trong
    // cua so chia cho von; het von hoac khong do duoc ngay nao thi null de UI hien "--".
    val gross30Apr = remember(ratePoints, capForKpi) {
        val rates = ratePoints.filter { it.coverage > 0.0 }.map { it.grossRate }
        if (rates.isEmpty() || capForKpi <= 0.0) null
        else rates.average() * 365.0 / capForKpi * 100.0
    }
    val kpiSummary = remember(capForKpi, currentPerDay, gross30Apr, ratePoints, windowedPoints) {
        CashflowEngine.computeKpis(capForKpi, currentPerDay, gross30Apr, ratePoints.lastOrNull()?.trailSpend, windowedPoints)
    }
    val pagerState = rememberPagerState(initialPage = ui.dashboardPage) { DbtopGroup.PAGE_COUNT }
    val coroutineScope = rememberCoroutineScope()
    // Nhớ sub-tab xem dở mỗi nhóm (user chốt 27/9: chạm WATCH/PROJ về đúng chỗ đang xem).
    var watchPage by rememberSaveable { mutableIntStateOf(DbtopGroup.WATCH.firstPage) }
    var projPage by rememberSaveable { mutableIntStateOf(DbtopGroup.PROJ.firstPage) }
    val currentPage = pagerState.currentPage
    val currentGroup = DbtopGroup.groupOf(currentPage)

    // Đồng bộ khi người dùng vuốt xong sang trang khác (settled)
    LaunchedEffect(pagerState.settledPage) {
        val settled = pagerState.settledPage
        when (DbtopGroup.groupOf(settled)) {
            DbtopGroup.WATCH -> watchPage = settled
            DbtopGroup.PROJ -> projPage = settled
            else -> Unit
        }
        if (ui.dashboardPage != settled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.selectPage(settled)
        }
    }

    // Đồng bộ khi ViewModel đổi trang từ nguồn ngoài (vd: banner cảnh báo rủi ro)
    LaunchedEffect(ui.dashboardPage) {
        if (pagerState.currentPage != ui.dashboardPage) {
            pagerState.animateScrollToPage(ui.dashboardPage)
        }
    }

    KohiBackHandler {
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
        // 30/9: dọc đủ + ngang thoáng (fix dải trống camera không chèn noti).
        val tallInsets = rememberTallSafeInsets()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(tallInsets),
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
                        viewModel.selectPage(0)
                        viewModel.togglePosition(critical.positionKey())
                    },
                )
            }

            DashboardSummary(
                netWorth = ui.state.netWorth,
                perDay = currentPerDay,
                kpis = kpiSummary,
                moneyDisplay = ui.moneyDisplay,
                vndRate = ui.spending?.usdVnd ?: 0.0,
                onCycleMoney = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.cycleMoneyDisplay()
                },
            )
            DashboardViewBand(
                selected = currentGroup,
                onSelect = { nextGroup ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    // Coerce đề phòng số trang đã lưu từ bản cũ (29/9 review: pager 8→7).
                    val target = when (nextGroup) {
                        DbtopGroup.POS -> 0
                        DbtopGroup.SPEND -> 1
                        DbtopGroup.WATCH -> watchPage.coerceIn(DbtopGroup.WATCH.pages)
                        DbtopGroup.PROJ -> projPage.coerceIn(DbtopGroup.PROJ.pages)
                    }
                    // Bấm tab = tới thẳng trang, KHÔNG lướt (user 27/9); vuốt tay vẫn
                    // đi lần lượt như cũ.
                    coroutineScope.launch {
                        pagerState.scrollToPage(target)
                    }
                },
            )

            // Dải sub-tab giờ nằm NGOÀI pager, suy ra từ currentPage; bấm thì nhảy
            // thẳng trang còn vuốt thì đi lần lượt qua từng sub-tab (user chốt 27/9).
            val subTabs = when (currentGroup) {
                DbtopGroup.WATCH -> listOf(
                    "TOKENS" to watchlistItems.size,
                    "GAINERS" to (ui.explorer?.gain?.size ?: 0),
                )
                DbtopGroup.PROJ -> listOf(
                    "BUZZ" to (ui.explorer?.x?.size ?: 0),
                    "FOLLOW" to (ui.explorer?.follow?.size ?: 0),
                    "PROJECTS" to (ui.explorer?.projects?.size ?: 0),
                )
                else -> null
            }
            if (subTabs != null) {
                KohiSubTabs(
                    tabs = subTabs,
                    selectedIndex = (currentPage - currentGroup.firstPage).coerceIn(0, subTabs.size - 1),
                    onSelect = { index ->
                        // Sub-tab cũng nhảy thẳng, cùng luật với tab chính (user 27/9).
                        coroutineScope.launch {
                            pagerState.scrollToPage(currentGroup.firstPage + index)
                        }
                    },
                )
            }

            // HorizontalPager: vuốt trái/phải đi lần lượt 7 trang — POS · SPEND ·
            // TOKENS · GAINERS · BUZZ · PROJECTS · YIELD (28/9: bỏ TRENDING,
            // BUZZ lên đầu PROJ).
            HorizontalPager(
                state = pagerState,
                key = { it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (page) {
                    0 -> {
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
                                        wallet = ui.state.wallet,
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
                                wallet = ui.state.wallet,
                                onSelect = { row ->
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.togglePosition(row.positionKey())
                                },
                            )
                        }
                    }
                    1 -> SpendingView(
                        spending = ui.spending,
                        flow = ui.flow,
                        moneyDisplay = ui.moneyDisplay,
                        currentPerDay = currentPerDay,
                        daily = ui.state.daily,
                        cap = capForKpi,
                        kpis = kpiSummary,
                        windowedPoints = windowedPoints,
                        ratePoints = ratePoints,
                        aprFactor = aprFactor,
                    )
                    in 2..3 -> WatchlistSubPane(
                        sub = page - DbtopGroup.WATCH.firstPage,
                        items = watchlistItems,
                        explorer = ui.explorer,
                        moneyDisplay = ui.moneyDisplay,
                        vndRate = ui.spending?.usdVnd ?: 0.0,
                    )
                    else -> ProjectsSubPane(
                        sub = page - DbtopGroup.PROJ.firstPage,
                        explorer = ui.explorer,
                        geminiKey = geminiKey,
                        hiddenFollows = viewModel.hiddenFollows.collectAsStateWithLifecycle().value,
                        onHideFollow = viewModel::hideFollow,
                        onUnhideAllFollows = viewModel::unhideAllFollows,
                    )
                }
            }

            // Màn hẹp: detail là BOTTOM SHEET (user đòi 27/8 từ popup giữa màn)
            if (!wide && currentGroup == DbtopGroup.POS) {
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

