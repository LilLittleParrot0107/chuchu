package com.jossephus.chuchu.ui.screens.Queue

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuDialog
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.ChuTextField
import com.jossephus.chuchu.ui.components.TuiBadge
import com.jossephus.chuchu.ui.theme.ChuColorPalette
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import kotlinx.coroutines.delay

/**
 * Bộ lọc trạng thái tác vụ trong hàng đợi.
 */
enum class QueueStatusFilter(val label: String) {
    All("Tất cả"),
    Pending("Chờ"),
    Running("Đang chạy"),
    Done("Đã xong"),
}

@Composable
fun QueueScreen(
    ui: QueueUiState,
    onAction: (QueueAction, Int?) -> Unit,
    onAdd: (String, String?, String?) -> Unit,
    onRefresh: () -> Unit,
    onFetchLogs: (Int) -> Unit = {},
    currentUrl: String,
    currentToken: String,
    onSaveConfig: (String, String) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var configDialogOpen by remember { mutableStateOf(false) }
    var logsDialogOpen by remember { mutableStateOf(false) }
    var inspectingTask by remember { mutableStateOf<QueueTask?>(null) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPane by remember { mutableStateOf<String?>(null) } // null = Tất cả
    var selectedTaskId by remember { mutableStateOf<Int?>(null) }
    var statusFilter by remember { mutableStateOf(QueueStatusFilter.All) }
    var targetPaneForAdd by remember { mutableStateOf<String?>(null) }
    var showTargetSelectorSheet by remember { mutableStateOf(false) }

    val showConfig = configDialogOpen || ui.needsSetup
    val agents = ui.state.agents
    val allTasks = ui.state.tasks

    // Tự động gán target cho tác vụ mới theo agent đang chọn hoặc agent đầu
    val activeAddTarget = targetPaneForAdd
        ?: selectedPane
        ?: agents.firstOrNull()?.pane

    // Lọc theo Pane
    val paneFilteredTasks = if (selectedPane == null) {
        allTasks
    } else {
        allTasks.filter { it.target == selectedPane }
    }

    // Lọc theo Search Query & Status
    val displayTasks = paneFilteredTasks.filter { task ->
        val matchesSearch = if (searchQuery.isBlank()) true else {
            task.text.contains(searchQuery, ignoreCase = true) ||
                    task.target.contains(searchQuery, ignoreCase = true) ||
                    task.stateLabel.contains(searchQuery, ignoreCase = true) ||
                    task.id.toString() == searchQuery.trim()
        }
        val matchesStatus = when (statusFilter) {
            QueueStatusFilter.All -> true
            QueueStatusFilter.Pending -> task.state.equals("pending", ignoreCase = true)
            QueueStatusFilter.Running -> task.state.equals("running", ignoreCase = true) ||
                    task.state.equals("in_progress", ignoreCase = true) ||
                    task.state.equals("active", ignoreCase = true)
            QueueStatusFilter.Done -> task.state.equals("done", ignoreCase = true) ||
                    task.state.equals("completed", ignoreCase = true)
        }
        matchesSearch && matchesStatus
    }

    val pendingCount = paneFilteredTasks.count { it.state.equals("pending", ignoreCase = true) }
    val runningCount = paneFilteredTasks.count {
        it.state.equals("running", ignoreCase = true) ||
                it.state.equals("in_progress", ignoreCase = true) ||
                it.state.equals("active", ignoreCase = true)
    }
    val doneCount = paneFilteredTasks.count {
        it.state.equals("done", ignoreCase = true) ||
                it.state.equals("completed", ignoreCase = true)
    }

    // Toast feedback overlay
    var activeToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.toast) {
        if (ui.toast != null) {
            activeToast = ui.toast
            delay(2500)
            activeToast = null
        }
    }

    // Fetch logs when dialog opens
    LaunchedEffect(logsDialogOpen) {
        if (logsDialogOpen) {
            onFetchLogs(60)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Thanh tiêu đề & điều khiển trên cùng
            QueueHeader(
                ui = ui,
                pendingCount = pendingCount,
                runningCount = runningCount,
                isSearchOpen = isSearchOpen,
                onBack = onBack,
                onRefresh = onRefresh,
                onToggleSearch = {
                    isSearchOpen = !isSearchOpen
                    if (!isSearchOpen) searchQuery = ""
                },
                onOpenLogs = { logsDialogOpen = true },
                onAction = onAction,
                onOpenConfig = { configDialogOpen = true },
            )

            // 2. Thanh tìm kiếm tức thì khi bật Search
            AnimatedVisibility(visible = isSearchOpen) {
                QueueSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        isSearchOpen = false
                        searchQuery = ""
                    },
                )
            }

            // 3. Banner cảnh báo nếu tạm dừng hoặc lỗi server
            if (ui.state.paused) {
                QueuePausedBanner(
                    onResume = {
                        val resumeAction = ui.state.globalActions.firstOrNull { it.op == "resume" }
                        if (resumeAction != null) {
                            onAction(resumeAction, null)
                        } else {
                            onAction(QueueAction("resume", "Tiếp tục", false, false), null)
                        }
                    }
                )
            } else if (ui.error != null) {
                QueueErrorBanner(
                    message = ui.error,
                    onRetry = onRefresh,
                    onOpenConfig = { configDialogOpen = true },
                )
            } else if (ui.state.banner != null) {
                QueueInfoBanner(banner = ui.state.banner)
            }

            Spacer(Modifier.height(4.dp))

            // 4. Thanh Agent Chips (Chọn agent hoặc xem tất cả)
            AgentChipsRow(
                agents = agents,
                allTasks = allTasks,
                selectedPane = selectedPane,
                onSelectPane = { pane ->
                    selectedPane = pane
                    selectedTaskId = null
                    targetPaneForAdd = pane
                },
            )

            Spacer(Modifier.height(6.dp))

            // 5. Thanh lọc trạng thái (Tất cả / Chờ / Đang chạy / Đã xong)
            StatusFilterRow(
                current = statusFilter,
                allCount = paneFilteredTasks.size,
                pendingCount = pendingCount,
                runningCount = runningCount,
                doneCount = doneCount,
                onSelect = { statusFilter = it },
            )

            Spacer(Modifier.height(6.dp))

            // 6. Danh sách tác vụ
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
            ) {
                when {
                    displayTasks.isEmpty() && ui.everLoaded -> {
                        QueueEmptyState(
                            statusFilter = statusFilter,
                            searchQuery = searchQuery,
                            selectedPane = selectedPane,
                            agentName = agents.firstOrNull { it.pane == selectedPane }?.name,
                        )
                    }
                    displayTasks.isEmpty() && ui.loading -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            ChuText("Đang kết nối và tải hàng đợi…", color = colors.textMuted)
                        }
                    }
                    displayTasks.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            ChuText("Chưa có dữ liệu hàng đợi", color = colors.textMuted)
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp),
                        ) {
                            itemsIndexed(displayTasks, key = { _, t -> t.id }) { index, task ->
                                QueueTaskCard(
                                    task = task,
                                    indexLetter = if (index < 26) ('a' + index).toString() else "${index + 1}",
                                    selected = task.id == selectedTaskId,
                                    busyOps = ui.busyOps,
                                    onClick = {
                                        selectedTaskId = if (selectedTaskId == task.id) null else task.id
                                    },
                                    onInspect = { inspectingTask = task },
                                    onAction = onAction,
                                    onCopy = { text ->
                                        clipboardManager.setText(AnnotatedString(text))
                                        Toast.makeText(context, "Đã sao chép nội dung", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // 7. Thanh thêm tác vụ nhanh ở đáy
            QueueAddDock(
                agents = agents,
                activeTarget = activeAddTarget,
                onSelectTarget = { showTargetSelectorSheet = true },
                onAdd = { text, mode -> onAdd(text, activeAddTarget, mode) },
            )
        }

        // Floating Toast Feedback
        AnimatedVisibility(
            visible = activeToast != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp, start = 16.dp, end = 16.dp),
        ) {
            activeToast?.let { msg ->
                ChuCard(
                    background = colors.surface,
                    border = colors.accent,
                    modifier = Modifier.padding(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChuText("✦", style = type.label, color = colors.accent)
                        ChuText(msg, style = type.label, color = colors.textPrimary)
                    }
                }
            }
        }

        // Modal Cấu hình qsrv URL & Token
        if (showConfig) {
            QueueConfigDialog(
                currentUrl = currentUrl,
                currentToken = currentToken,
                onSave = { u, t ->
                    onSaveConfig(u, t)
                    configDialogOpen = false
                },
                onDismiss = if (ui.needsSetup) null else ({ configDialogOpen = false }),
            )
        }

        // Modal xem chi tiết tác vụ (Task Inspector)
        inspectingTask?.let { task ->
            TaskDetailDialog(
                task = task,
                busyOps = ui.busyOps,
                onAction = { action ->
                    onAction(action, task.id)
                    inspectingTask = null
                },
                onCopy = { text ->
                    clipboardManager.setText(AnnotatedString(text))
                    Toast.makeText(context, "Đã sao chép prompt", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { inspectingTask = null },
            )
        }

        // Modal xem nhật ký daemon (Live Logs)
        if (logsDialogOpen) {
            QueueLogsDialog(
                logs = ui.logs,
                loading = ui.logsLoading,
                error = ui.logsError,
                onRefresh = { onFetchLogs(80) },
                onCopyAll = {
                    val fullLog = ui.logs.joinToString("\n")
                    clipboardManager.setText(AnnotatedString(fullLog))
                    Toast.makeText(context, "Đã sao chép toàn bộ log", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { logsDialogOpen = false },
            )
        }

        // Modal chọn agent đích cho Add Bar
        if (showTargetSelectorSheet && agents.isNotEmpty()) {
            TargetAgentSelectorDialog(
                agents = agents,
                selected = activeAddTarget,
                onSelect = { pane ->
                    targetPaneForAdd = pane
                    showTargetSelectorSheet = false
                },
                onDismiss = { showTargetSelectorSheet = false },
            )
        }
    }
}

/**
 * Header thanh trên cùng với branding, live status dot, pause toggle, search, logs, refresh, và settings.
 */
@Composable
private fun QueueHeader(
    ui: QueueUiState,
    pendingCount: Int,
    runningCount: Int,
    isSearchOpen: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenLogs: () -> Unit,
    onAction: (QueueAction, Int?) -> Unit,
    onOpenConfig: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .border(1.dp, colors.border, RectangleShape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ChuButton(
            onClick = onBack,
            variant = ChuButtonVariant.Ghost,
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            ChuText("←", style = type.label, color = colors.textSecondary)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            ChuText("$ ", style = type.label, color = colors.textMuted)
            ChuText("kohi", style = type.label, color = colors.textPrimary)
            ChuText("/queue", style = type.label, color = colors.accent)

            Spacer(Modifier.width(6.dp))

            val statusDotColor = when {
                ui.loading -> colors.warning
                ui.error != null -> colors.error
                ui.state.paused -> colors.warning
                ui.everLoaded -> colors.success
                else -> colors.textMuted
            }
            ChuText("●", style = type.labelSmall, color = statusDotColor)

            Spacer(Modifier.width(6.dp))
            ChuText(
                if (pendingCount > 0) "${pendingCount} chờ" else if (runningCount > 0) "${runningCount} chạy" else "sẵn sàng",
                style = type.labelSmall,
                color = if (pendingCount > 0) colors.accent else colors.textMuted,
            )
        }

        // Nút Tạm dừng / Tiếp tục toàn cục
        ui.state.globalActions.forEach { action ->
            val isPause = action.op == "pause"
            ChuButton(
                onClick = { onAction(action, null) },
                variant = if (ui.state.paused) ChuButtonVariant.Filled else ChuButtonVariant.Outlined,
                borderColor = if (ui.state.paused) colors.warning else colors.border,
                backgroundColor = if (ui.state.paused) colors.warning else null,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                ChuText(
                    if (isPause) "‖ Tạm dừng" else "▶ Tiếp tục",
                    style = type.labelSmall,
                    color = if (ui.state.paused) colors.onAccent else colors.textSecondary,
                )
            }
        }

        // Nút Tìm kiếm
        ChuButton(
            onClick = onToggleSearch,
            variant = if (isSearchOpen) ChuButtonVariant.Filled else ChuButtonVariant.Outlined,
            borderColor = if (isSearchOpen) colors.accent else colors.border,
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            ChuText("🔍", style = type.label, color = if (isSearchOpen) colors.onAccent else colors.textSecondary)
        }

        // Nút Mở Daemon Logs
        ChuButton(
            onClick = onOpenLogs,
            variant = ChuButtonVariant.Outlined,
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            ChuText("📜", style = type.label, color = colors.textSecondary)
        }

        // Nút Refresh
        ChuButton(
            onClick = onRefresh,
            variant = ChuButtonVariant.Outlined,
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            ChuText("⟳", style = type.label, color = colors.textSecondary)
        }

        // Nút Settings
        ChuButton(
            onClick = onOpenConfig,
            variant = ChuButtonVariant.Outlined,
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            ChuText("⚙", style = type.label, color = colors.textSecondary)
        }
    }
}

/**
 * Thanh tìm kiếm tức thì.
 */
@Composable
private fun QueueSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(1.dp, colors.accent.copy(alpha = 0.5f), RectangleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChuText("🔍", style = type.label, color = colors.accent)
        ChuTextField(
            value = query,
            onValueChange = onQueryChange,
            label = "",
            placeholder = "Lọc tác vụ theo nội dung, @target, #id…",
            singleLine = true,
            showLabel = false,
            autoFocus = true,
            verticalPadding = 4.dp,
            modifier = Modifier.weight(1f),
        )
        if (query.isNotEmpty()) {
            ChuButton(
                onClick = { onQueryChange("") },
                variant = ChuButtonVariant.Ghost,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            ) {
                ChuText("✕", style = type.labelSmall, color = colors.textMuted)
            }
        }
        ChuButton(
            onClick = onClose,
            variant = ChuButtonVariant.Ghost,
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        ) {
            ChuText("Đóng", style = type.labelSmall, color = colors.textSecondary)
        }
    }
}

/**
 * Banner cảnh báo khi hàng đợi đang ở trạng thái Paused.
 */
@Composable
private fun QueuePausedBanner(onResume: () -> Unit) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    ChuCard(
        background = colors.warning.copy(alpha = 0.12f),
        border = colors.warning,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                ChuText("⏸", style = type.title, color = colors.warning)
                Column {
                    ChuText("HÀNG ĐỢI ĐANG TẠM DỪNG", style = type.label, color = colors.warning)
                    ChuText(
                        "Các agent sẽ không nhận thêm việc mới cho tới khi tiếp tục.",
                        style = type.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            ChuButton(
                onClick = onResume,
                variant = ChuButtonVariant.Filled,
                backgroundColor = colors.warning,
                bracketed = true,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                ChuText("▶ Chạy tiếp", style = type.labelSmall, color = colors.onAccent)
            }
        }
    }
}

/**
 * Banner báo lỗi kết nối.
 */
@Composable
private fun QueueErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onOpenConfig: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    ChuCard(
        background = colors.error.copy(alpha = 0.10f),
        border = colors.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                ChuText("⚠", style = type.title, color = colors.error)
                ChuText(message, style = type.bodySmall, color = colors.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChuButton(
                    onClick = onRetry,
                    variant = ChuButtonVariant.Ghost,
                    bracketed = true,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    ChuText("Thử lại", style = type.labelSmall, color = colors.textPrimary)
                }
                ChuButton(
                    onClick = onOpenConfig,
                    variant = ChuButtonVariant.Ghost,
                    bracketed = true,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    ChuText("Cấu hình", style = type.labelSmall, color = colors.accent)
                }
            }
        }
    }
}

/**
 * Banner thông báo từ server.
 */
@Composable
private fun QueueInfoBanner(banner: QueueBanner) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val toneColor = banner.tone.resolveColor(colors)
    ChuCard(
        background = toneColor.copy(alpha = 0.08f),
        border = toneColor.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChuText("ℹ", style = type.label, color = toneColor)
            ChuText(banner.text, style = type.bodySmall, color = toneColor)
        }
    }
}

/**
 * Thanh Agent Chips cuộn ngang.
 */
@Composable
private fun AgentChipsRow(
    agents: List<QueueAgent>,
    allTasks: List<QueueTask>,
    selectedPane: String?,
    onSelectPane: (String?) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Chip "Tất cả"
        val isAllSelected = selectedPane == null
        ChuButton(
            onClick = { onSelectPane(null) },
            variant = ChuButtonVariant.Outlined,
            borderColor = if (isAllSelected) colors.accent else colors.border,
            backgroundColor = if (isAllSelected) colors.accent.copy(alpha = 0.12f) else null,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ChuText("✦ Tất cả", style = type.label, color = if (isAllSelected) colors.accent else colors.textSecondary)
                ChuText(
                    "(${allTasks.size})",
                    style = type.labelSmall,
                    color = if (isAllSelected) colors.accent else colors.textMuted,
                )
            }
        }

        // Chips từng Agent
        agents.forEach { agent ->
            val isSelected = agent.pane == selectedPane
            val agentTaskCount = allTasks.count { it.target == agent.pane }
            val agentToneColor = agent.tone.resolveColor(colors)

            ChuButton(
                onClick = { onSelectPane(agent.pane) },
                variant = ChuButtonVariant.Outlined,
                borderColor = if (isSelected) colors.accent else colors.border,
                backgroundColor = if (isSelected) colors.accent.copy(alpha = 0.12f) else null,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChuText(agent.glyph, style = type.label, color = agentToneColor)
                    ChuText(
                        agent.name,
                        style = type.label,
                        color = if (isSelected) colors.textPrimary else colors.textSecondary,
                    )
                    if (agent.word.isNotBlank()) {
                        TuiBadge(agent.word, colors.warning)
                    }
                    if (agentTaskCount > 0) {
                        ChuText(
                            "($agentTaskCount)",
                            style = type.labelSmall,
                            color = if (isSelected) colors.accent else colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thanh chuyển trạng thái lọc tác vụ.
 */
@Composable
private fun StatusFilterRow(
    current: QueueStatusFilter,
    allCount: Int,
    pendingCount: Int,
    runningCount: Int,
    doneCount: Int,
    onSelect: (QueueStatusFilter) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        QueueStatusFilter.entries.forEach { filter ->
            val isSelected = filter == current
            val count = when (filter) {
                QueueStatusFilter.All -> allCount
                QueueStatusFilter.Pending -> pendingCount
                QueueStatusFilter.Running -> runningCount
                QueueStatusFilter.Done -> doneCount
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isSelected) colors.surfaceVariant else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) colors.accent else colors.border.copy(alpha = 0.4f),
                        RectangleShape,
                    )
                    .clickable { onSelect(filter) }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ChuText(
                        filter.label,
                        style = type.labelSmall,
                        color = if (isSelected) colors.accent else colors.textMuted,
                    )
                    ChuText(
                        "$count",
                        style = type.labelSmall,
                        color = if (isSelected) colors.textPrimary else colors.textMuted.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/**
 * Thẻ hiển thị một tác vụ trong hàng đợi.
 */
@Composable
private fun QueueTaskCard(
    task: QueueTask,
    indexLetter: String,
    selected: Boolean,
    busyOps: Set<String>,
    onClick: () -> Unit,
    onInspect: () -> Unit,
    onAction: (QueueAction, Int?) -> Unit,
    onCopy: (String) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val toneColor = task.tone.resolveColor(colors)
    val isDone = task.state.equals("done", ignoreCase = true) || task.state.equals("completed", ignoreCase = true)

    ChuCard(
        background = if (selected) colors.surface else colors.surfaceVariant,
        border = if (selected) colors.accent else colors.border,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Vạch màu trạng thái bên trái
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(toneColor),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Header của tác vụ: Index, glyph, trạng thái, và target
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ChuText(
                            "[$indexLetter] #${task.id}",
                            style = type.labelSmall,
                            color = if (selected) colors.accent else colors.textMuted,
                        )
                        ChuText(task.glyph, style = type.labelSmall, color = toneColor)
                        TuiBadge(task.stateLabel, toneColor)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (task.target.isNotBlank()) {
                            ChuText(
                                "@${task.target}",
                                style = type.labelSmall,
                                color = colors.accentSecondary,
                            )
                        }
                        ChuText(
                            if (selected) "▲" else "▼",
                            style = type.labelSmall,
                            color = colors.textMuted,
                        )
                    }
                }

                // Nội dung tác vụ
                ChuText(
                    text = task.text,
                    style = type.body,
                    color = if (isDone) colors.textMuted else colors.textPrimary,
                    maxLines = if (selected) 20 else 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Thông tin phụ (sub) nếu có
                if (task.sub.isNotBlank()) {
                    ChuText(
                        task.sub,
                        style = type.labelSmall,
                        color = colors.textMuted,
                    )
                }

                // Hàng thao tác (Hiện khi được chọn hoặc luôn có nút nhanh)
                if (selected) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Nút Copy
                        ChuButton(
                            onClick = { onCopy(task.text) },
                            variant = ChuButtonVariant.Ghost,
                            bracketed = true,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            ChuText("📋 Chép", style = type.labelSmall, color = colors.textSecondary)
                        }

                        // Nút Chi tiết
                        ChuButton(
                            onClick = onInspect,
                            variant = ChuButtonVariant.Ghost,
                            bracketed = true,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            ChuText("🔍 Chi tiết", style = type.labelSmall, color = colors.accent)
                        }

                        // Danh sách action server gửi xuống
                        task.actions.forEach { action ->
                            val opKey = "${action.op}:${task.id}"
                            val isBusy = opKey in busyOps

                            ChuButton(
                                onClick = { onAction(action, task.id) },
                                variant = ChuButtonVariant.Ghost,
                                bracketed = true,
                                enabled = !isBusy,
                                borderColor = if (action.danger) colors.error else colors.border,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                ChuText(
                                    if (isBusy) "…" else action.label,
                                    style = type.labelSmall,
                                    color = if (action.danger) colors.error else colors.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog hiển thị toàn diện chi tiết của một Task.
 */
@Composable
private fun TaskDetailDialog(
    task: QueueTask,
    busyOps: Set<String>,
    onAction: (QueueAction) -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val toneColor = task.tone.resolveColor(colors)

    ChuDialog(
        title = "Tác vụ #${task.id} (${task.stateLabel})",
        confirmLabel = "Đóng",
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Metadata bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChuText(task.glyph, style = type.label, color = toneColor)
                    TuiBadge(task.stateLabel, toneColor)
                }
                if (task.target.isNotBlank()) {
                    ChuText("Agent: @${task.target}", style = type.label, color = colors.accentSecondary)
                }
            }

            if (task.sub.isNotBlank()) {
                ChuText("Chi tiết: ${task.sub}", style = type.bodySmall, color = colors.textMuted)
            }

            // Prompt content container
            ChuCard(
                background = colors.background,
                border = colors.border,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    ChuText(
                        text = task.text,
                        style = type.bodySmall,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuButton(
                    onClick = { onCopy(task.text) },
                    variant = ChuButtonVariant.Outlined,
                    bracketed = true,
                    modifier = Modifier.weight(1f),
                ) {
                    ChuText("📋 Sao chép Prompt", style = type.labelSmall, color = colors.textPrimary)
                }
            }

            if (task.actions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    task.actions.forEach { action ->
                        val opKey = "${action.op}:${task.id}"
                        val isBusy = opKey in busyOps
                        ChuButton(
                            onClick = { onAction(action) },
                            variant = if (action.danger) ChuButtonVariant.Outlined else ChuButtonVariant.Filled,
                            borderColor = if (action.danger) colors.error else colors.accent,
                            backgroundColor = if (action.danger) null else colors.accent,
                            bracketed = true,
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            ChuText(
                                if (isBusy) "Đang xử lý…" else action.label,
                                style = type.labelSmall,
                                color = if (action.danger) colors.error else colors.onAccent,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog xem trực tiếp log của daemon qsrv/taskq.
 */
@Composable
private fun QueueLogsDialog(
    logs: List<String>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onCopyAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    ChuDialog(
        title = "Nhật ký Daemon (qsrv/taskq)",
        confirmLabel = "Đóng",
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ChuText(
                    if (loading) "Đang cập nhật log…" else "${logs.size} dòng log gần nhất",
                    style = type.labelSmall,
                    color = colors.textMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChuButton(
                        onClick = onCopyAll,
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        ChuText("Sao chép", style = type.labelSmall, color = colors.textSecondary)
                    }
                    ChuButton(
                        onClick = onRefresh,
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        ChuText("⟳ Làm mới", style = type.labelSmall, color = colors.accent)
                    }
                }
            }

            if (error != null) {
                ChuText("⚠ Lỗi: $error", style = type.bodySmall, color = colors.error)
            }

            ChuCard(
                background = Color(0xFF0D0E15),
                border = colors.border,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
            ) {
                if (logs.isEmpty() && !loading) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        ChuText("Chưa có nhật ký ghi nhận", color = colors.textMuted)
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            itemsIndexed(logs) { idx, line ->
                                Row {
                                    ChuText(
                                        "${idx + 1} ".padStart(4),
                                        style = type.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = colors.textMuted.copy(alpha = 0.5f),
                                    )
                                    val lineColor = when {
                                        line.contains("LOI", ignoreCase = true) || line.contains("error", ignoreCase = true) -> colors.error
                                        line.contains("WARN", ignoreCase = true) -> colors.warning
                                        line.contains("sent", ignoreCase = true) || line.contains("done", ignoreCase = true) -> colors.success
                                        else -> colors.textPrimary
                                    }
                                    ChuText(
                                        line,
                                        style = type.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = lineColor,
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

/**
 * Giao diện khi không có tác vụ nào trong danh sách.
 */
@Composable
private fun QueueEmptyState(
    statusFilter: QueueStatusFilter,
    searchQuery: String,
    selectedPane: String?,
    agentName: String?,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChuText("✦", style = type.headline, color = colors.textMuted)
        Spacer(Modifier.height(8.dp))
        val targetName = agentName ?: selectedPane ?: "hàng đợi"
        val filterName = if (statusFilter == QueueStatusFilter.All) "" else "(${statusFilter.label})"
        val searchNotice = if (searchQuery.isNotBlank()) " khớp từ khoá \"$searchQuery\"" else ""
        ChuText(
            "Không có tác vụ nào $filterName$searchNotice của $targetName",
            style = type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        ChuText(
            "Nhập nội dung vào ô bên dưới để giao việc mới",
            style = type.bodySmall,
            color = colors.textMuted,
        )
    }
}

/**
 * Thanh dock giao việc ở đáy màn hình.
 */
@Composable
private fun QueueAddDock(
    agents: List<QueueAgent>,
    activeTarget: String?,
    onSelectTarget: () -> Unit,
    onAdd: (String, String?) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    var inputText by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("auto") } // auto, prompt, raw

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .border(1.dp, colors.border, RectangleShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Nút chọn target agent
            if (agents.isNotEmpty()) {
                val targetLabel = activeTarget?.let { "@$it" } ?: "@agent"
                ChuButton(
                    onClick = onSelectTarget,
                    variant = ChuButtonVariant.Outlined,
                    bracketed = true,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    ChuText(
                        targetLabel,
                        style = type.labelSmall,
                        color = colors.accent,
                        maxLines = 1,
                    )
                }
            }

            ChuTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = "",
                placeholder = "Giao việc cho ${activeTarget?.let { "@$it" } ?: "agent"}…",
                singleLine = false,
                showLabel = false,
                autoFocus = false,
                verticalPadding = 6.dp,
                modifier = Modifier.weight(1f),
            )

            ChuButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onAdd(inputText.trim(), selectedMode)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank(),
                bracketed = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                ChuText("Gửi ➤", style = type.labelSmall, color = colors.onAccent)
            }
        }
    }
}

/**
 * Dialog cấu hình endpoint qsrv URL & Token.
 */
@Composable
private fun QueueConfigDialog(
    currentUrl: String,
    currentToken: String,
    onSave: (String, String) -> Unit,
    onDismiss: (() -> Unit)?,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    var token by remember(currentToken) { mutableStateOf(currentToken) }

    ChuDialog(
        title = "Cấu hình kết nối qsrv",
        confirmLabel = "Lưu cấu hình",
        onConfirm = { onSave(url, token) },
        onDismiss = { onDismiss?.invoke() },
        dismissLabel = if (onDismiss != null) "Đóng" else "Bỏ qua",
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            ChuText(
                "Nhập địa chỉ máy chủ qsrv. Nếu kết nối qua Tailscale, bạn không cần nhập token.",
                style = type.bodySmall,
                color = colors.textSecondary,
            )
            ChuTextField(
                value = url,
                onValueChange = { url = it },
                label = "Địa chỉ qsrv URL",
                placeholder = "https://server.tailnet.ts.net/q hoặc http://127.0.0.1:5002",
                singleLine = true,
                autoFocus = false,
            )
            ChuTextField(
                value = token,
                onValueChange = { token = it },
                label = "Bearer Token (tuỳ chọn)",
                placeholder = "Để trống nếu dùng qua Tailscale",
                singleLine = true,
                autoFocus = false,
                visualTransformation = PasswordVisualTransformation(),
            )
        }
    }
}

/**
 * Dialog chọn target agent cho tác vụ mới.
 */
@Composable
private fun TargetAgentSelectorDialog(
    agents: List<QueueAgent>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    ChuDialog(
        title = "Chọn Agent nhận việc",
        confirmLabel = "Đóng",
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            agents.forEach { agent ->
                val isSelected = agent.pane == selected
                val toneColor = agent.tone.resolveColor(colors)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) colors.surface else Color.Transparent)
                        .border(1.dp, if (isSelected) colors.accent else colors.border, RectangleShape)
                        .clickable { onSelect(agent.pane) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChuText(agent.glyph, style = type.label, color = toneColor)
                        ChuText(agent.name, style = type.label, color = colors.textPrimary)
                        ChuText("(@${agent.pane})", style = type.labelSmall, color = colors.textMuted)
                    }
                    if (isSelected) {
                        ChuText("✓", style = type.label, color = colors.accent)
                    }
                }
            }
        }
    }
}

/**
 * Chuyển đổi QueueTone sang màu sắc tương ứng trong theme của app.
 */
private fun QueueTone.resolveColor(colors: ChuColorPalette): Color = when (this) {
    QueueTone.Accent -> colors.accent
    QueueTone.Ok -> colors.success
    QueueTone.Warn -> colors.warning
    QueueTone.Error -> colors.error
    QueueTone.Dim -> colors.textMuted
}
