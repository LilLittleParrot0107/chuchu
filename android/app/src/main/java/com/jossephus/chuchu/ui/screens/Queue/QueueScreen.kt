package com.jossephus.chuchu.ui.screens.Queue

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuDialog
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.ChuTextField
import com.jossephus.chuchu.ui.components.TuiBadge
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import kotlinx.coroutines.delay

/**
 * Màn hình hàng đợi — dựng theo khung dọc trực quan của qq (TUI terminal),
 * dễ theo dõi, không phải lướt ngang.
 *
 * Cải tiến:
 *  1. Cấu trúc dọc chuẩn qq: danh sách agent dọc -> thanh mục agent -> danh sách việc 1 dòng.
 *  2. Nút "Dọn xong" (Clear Done) 1 chạm: Xoá tất cả việc đã hoàn tất mà không phải xoá thủ công.
 *  3. Thao tác mở rộng ngay dưới việc được chọn (Ưu tiên / Thử lại / Xoá / Copy / Chi tiết).
 *  4. Xem live daemon logs ([log]) và cấu hình qsrv ([⚙]).
 */

// Bảng màu qq đồng bộ TUI
private val QQ_FG = Color(0xFFE5E5E5)
private val QQ_DIM = Color(0xFF7A7A7A)
private val QQ_ACC = Color(0xFF6FBCF7)
private val QQ_WARN = Color(0xFFE58A2B)
private val QQ_ERR = Color(0xFFE05C5C)
private val QQ_GOLD = Color(0xFFF6C17E)
private val QQ_BAND = Color(0xFF1E1E24)
private val QQ_SEL = Color(0xFF243447)
private val QQ_LOG_BG = Color(0xFF0F1117)

@Composable
fun QueueScreen(
    ui: QueueUiState,
    onAction: (QueueAction, Int?) -> Unit,
    onAdd: (String, String?, String?) -> Unit,
    onClearDone: (String?) -> Unit,
    onRefresh: () -> Unit,
    onFetchLogs: (Int) -> Unit = {},
    onConsumeToast: () -> Unit = {},
    currentUrl: String,
    currentToken: String,
    onSaveConfig: (String, String) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val context = LocalContext.current

    var configOpen by remember { mutableStateOf(false) }
    var logsOpen by remember { mutableStateOf(false) }
    var inspectedTask by remember { mutableStateOf<QueueTask?>(null) }
    var selectedPane by remember { mutableStateOf<String?>(null) }
    var selectedTask by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var localToast by remember { mutableStateOf<String?>(null) }

    val showConfig = configOpen || ui.needsSetup
    val agents = ui.state.agents

    // Tự động chọn agent đầu nếu chưa chọn hoặc agent đã mất
    val pane = selectedPane?.takeIf { p -> p == "ALL" || agents.any { it.pane == p } }
        ?: agents.firstOrNull()?.pane ?: "ALL"

    val activeTasks = remember(ui.state.tasks, pane, searchQuery) {
        val list = if (pane == "ALL") {
            ui.state.tasks
        } else {
            ui.state.tasks.filter { it.target == pane }
        }
        if (searchQuery.isBlank()) {
            list
        } else {
            val q = searchQuery.trim().lowercase()
            list.filter { t ->
                t.text.lowercase().contains(q) ||
                    t.target.lowercase().contains(q) ||
                    "#${t.id}".contains(q) ||
                    t.stateLabel.lowercase().contains(q)
            }
        }
    }

    val doneCount = remember(activeTasks) {
        activeTasks.count { it.state.equals("done", ignoreCase = true) || it.state.equals("completed", ignoreCase = true) }
    }

    val activeAgent = agents.firstOrNull { it.pane == pane }

    fun copyToClipboard(text: String, label: String = "Đã copy vào clipboard") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Queue Prompt", text))
        localToast = label
    }

    LaunchedEffect(ui.toast) {
        val t = ui.toast
        if (!t.isNullOrBlank()) {
            localToast = t
            onConsumeToast()
        }
    }

    LaunchedEffect(localToast) {
        if (localToast != null) {
            delay(2600)
            localToast = null
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
            // Thanh tiêu đề đỉnh
            TopBand(
                ui = ui,
                onAction = onAction,
                onRefresh = onRefresh,
                onLogs = {
                    onFetchLogs(80)
                    logsOpen = true
                },
                onConfig = { configOpen = !configOpen },
                onToggleSearch = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) searchQuery = ""
                },
                isSearchActive = isSearchActive,
                onBack = onBack,
            )

            if (showConfig) {
                ConfigPanel(
                    currentUrl = currentUrl,
                    currentToken = currentToken,
                    onSave = { u, t ->
                        onSaveConfig(u, t)
                        configOpen = false
                    },
                    onDismiss = if (ui.needsSetup) null else ({ configOpen = false }),
                )
                return@Column
            }

            // Thanh tìm kiếm nhanh dạng inline nếu bật
            if (isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(QQ_BAND)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChuText("🔍", style = type.labelSmall, color = QQ_ACC)
                    Spacer(Modifier.width(6.dp))
                    ChuTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "",
                        placeholder = "Lọc việc theo nội dung, #id, trạng thái…",
                        singleLine = true,
                        showLabel = false,
                        autoFocus = true,
                        verticalPadding = 4.dp,
                        modifier = Modifier.weight(1f),
                    )
                    if (searchQuery.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        ChuButton(
                            onClick = { searchQuery = "" },
                            variant = ChuButtonVariant.Ghost,
                            bracketed = true,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            ChuText("x", style = type.labelSmall, color = QQ_DIM)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ==================== DANH SÁCH AGENT THEO CHIỀU DỌC ====================
            // Dòng "Tất cả"
            val totalTaskCount = ui.state.tasks.size
            AgentRow(
                glyph = "✦",
                name = "Tất cả các agent",
                tone = QueueTone.Accent,
                word = "",
                count = totalTaskCount,
                selected = pane == "ALL",
                onClick = {
                    selectedPane = "ALL"
                    selectedTask = null
                },
            )

            // Từng agent theo chiều dọc
            agents.forEach { a ->
                AgentRow(
                    glyph = a.glyph,
                    name = a.name,
                    tone = a.tone,
                    word = a.word,
                    count = ui.state.tasks.count { it.target == a.pane },
                    selected = a.pane == pane,
                    onClick = {
                        selectedPane = a.pane
                        selectedTask = null
                    },
                )
            }

            if (agents.isEmpty() && ui.everLoaded) {
                ChuText(
                    "  không thấy agent nào",
                    style = type.labelSmall,
                    color = QQ_DIM,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(4.dp))

            // ==================== THANH MỤC & NÚT XOÁ ĐÃ XONG ====================
            Band {
                ChuText(
                    if (pane == "ALL") "Tất cả tác vụ" else (activeAgent?.name ?: "chưa có agent"),
                    style = type.label,
                    color = QQ_FG,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                ChuText(
                    "${activeTasks.size} việc",
                    style = type.labelSmall,
                    color = QQ_DIM,
                )

                // Nút XOÁ SẠCH CÁC VIỆC ĐÃ XONG (1 chạm)
                if (doneCount > 0) {
                    ChuButton(
                        onClick = { onClearDone(if (pane == "ALL") null else pane) },
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        borderColor = QQ_ERR,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        ChuText(
                            "✕ dọn $doneCount xong",
                            style = type.labelSmall,
                            color = QQ_ERR,
                        )
                    }
                }
            }

            // ==================== DANH SÁCH VIỆC (CHIỀU DỌC) ====================
            Box(modifier = Modifier.weight(1f)) {
                when {
                    activeTasks.isEmpty() && ui.everLoaded -> Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ChuText("chưa có việc nào", style = type.bodySmall, color = QQ_DIM)
                        ChuText("gõ nội dung bên dưới rồi bấm gửi", style = type.labelSmall, color = QQ_DIM)
                    }
                    activeTasks.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        ChuText("đang tải dữ liệu…", color = QQ_DIM)
                    }
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(activeTasks, key = { _, t -> t.id }) { k, task ->
                            TaskRow(
                                task = task,
                                letter = if (k < 26) ('a' + k).toString() else "·",
                                selected = task.id == selectedTask,
                                busyOps = ui.busyOps,
                                onClick = {
                                    selectedTask = if (selectedTask == task.id) null else task.id
                                },
                                onAction = onAction,
                                onCopy = { copyToClipboard(task.text, "Đã sao chép prompt #${task.id}") },
                                onInspect = { inspectedTask = task },
                            )
                        }
                    }
                }
            }

            // ==================== THANH SOẠN PROMPT Ở ĐÁY ====================
            AddBar(
                targetAgentName = if (pane == "ALL") (agents.firstOrNull()?.name ?: "agent") else (activeAgent?.name ?: "agent"),
                onAdd = { text ->
                    val effectiveTarget = if (pane == "ALL") agents.firstOrNull()?.pane else pane
                    onAdd(text, effectiveTarget, null)
                },
            )
        }

        // Dialog xem chi tiết task
        inspectedTask?.let { task ->
            TaskDetailDialog(
                task = task,
                onDismiss = { inspectedTask = null },
                onCopy = { copyToClipboard(task.text, "Đã sao chép prompt #${task.id}") },
                onAction = { a ->
                    onAction(a, task.id)
                    inspectedTask = null
                },
            )
        }

        // Dialog xem live daemon logs
        if (logsOpen) {
            QueueLogsDialog(
                logs = ui.logs,
                loading = ui.logsLoading,
                error = ui.logsError,
                onRefresh = { onFetchLogs(100) },
                onDismiss = { logsOpen = false },
            )
        }

        // Toast thông báo
        AnimatedVisibility(
            visible = localToast != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
        ) {
            localToast?.let { msg ->
                ChuCard(
                    background = colors.surfaceVariant,
                    border = colors.accent,
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) {
                    ChuText(
                        msg,
                        style = type.labelSmall,
                        color = colors.accent,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Một thanh nền đậm chạy hết bề ngang — qq gọi là band. */
@Composable
private fun Band(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(QQ_BAND)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun TopBand(
    ui: QueueUiState,
    onAction: (QueueAction, Int?) -> Unit,
    onRefresh: () -> Unit,
    onLogs: () -> Unit,
    onConfig: () -> Unit,
    onToggleSearch: () -> Unit,
    isSearchActive: Boolean,
    onBack: () -> Unit,
) {
    val type = ChuTypography.current
    val pending = ui.state.tasks.count { it.state == "pending" }
    Band {
        ChuText("HÀNG ĐỢI", style = type.label, color = QQ_FG)

        // Banner thông báo lỗi hoặc PAUSED
        val notice = ui.error ?: ui.state.banner?.text
        if (notice != null) {
            val tone = if (ui.error != null) QueueTone.Error else ui.state.banner?.tone ?: QueueTone.Warn
            ChuText(
                notice,
                style = type.labelSmall,
                color = tone.color(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
        } else {
            Spacer(Modifier.weight(1f))
            if (pending > 0) {
                ChuText("$pending chờ", style = type.labelSmall, color = QQ_GOLD)
            }
        }

        // Global actions từ server
        ui.state.globalActions.forEach { a ->
            ChuButton(
                onClick = { onAction(a, null) },
                variant = ChuButtonVariant.Ghost,
                bracketed = true,
                borderColor = if (a.danger) QQ_ERR else QQ_DIM,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            ) {
                ChuText(a.label, style = type.labelSmall, color = if (a.danger) QQ_ERR else QQ_FG)
            }
        }

        // Nút tìm kiếm
        ChuText(
            if (isSearchActive) "✕" else "🔍",
            style = type.label,
            color = if (isSearchActive) QQ_ACC else QQ_DIM,
            modifier = Modifier.clickable(onClick = onToggleSearch),
        )

        // Nút xem log
        ChuText(
            "log",
            style = type.labelSmall,
            color = QQ_DIM,
            modifier = Modifier.clickable(onClick = onLogs),
        )

        // Nút refresh
        ChuText(
            "⟳",
            style = type.label,
            color = QQ_DIM,
            modifier = Modifier.clickable(onClick = onRefresh),
        )

        // Nút config
        ChuText(
            "⚙",
            style = type.label,
            color = QQ_DIM,
            modifier = Modifier.clickable(onClick = onConfig),
        )

        // Nút đóng
        ChuText(
            "✕",
            style = type.label,
            color = QQ_DIM,
            modifier = Modifier.clickable(onClick = onBack),
        )
    }
}

@Composable
private fun AgentRow(
    glyph: String,
    name: String,
    tone: QueueTone,
    word: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val type = ChuTypography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) QQ_SEL else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuText("▌", style = type.label, color = if (selected) QQ_ACC else Color.Transparent)
        Spacer(Modifier.width(6.dp))
        ChuText(glyph, style = type.label, color = tone.color())
        Spacer(Modifier.width(8.dp))
        ChuText(
            name,
            style = type.label,
            color = if (selected) QQ_FG else QQ_DIM,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (word.isNotBlank()) {
            ChuText(word, style = type.labelSmall, color = QQ_WARN)
            Spacer(Modifier.width(8.dp))
        }
        ChuText(
            if (count > 0) "$count" else " ",
            style = type.labelSmall,
            color = if (count > 0) QQ_ACC else QQ_DIM,
        )
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun TaskRow(
    task: QueueTask,
    letter: String,
    selected: Boolean,
    busyOps: Set<String>,
    onClick: () -> Unit,
    onAction: (QueueAction, Int?) -> Unit,
    onCopy: () -> Unit,
    onInspect: () -> Unit,
) {
    val type = ChuTypography.current
    val isDone = task.state.equals("done", ignoreCase = true) || task.state.equals("completed", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) QQ_SEL else Color.Transparent)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChuText("▌", style = type.label, color = if (selected) QQ_ACC else Color.Transparent)
            ChuText(letter, style = type.labelSmall, color = QQ_DIM)
            Spacer(Modifier.width(6.dp))
            ChuText(task.glyph, style = type.label, color = task.tone.color())
            Spacer(Modifier.width(8.dp))
            ChuText(
                task.text.replace('
', ' '),
                style = type.label,
                // Việc đã xong thì mờ đi
                color = if (isDone) QQ_DIM else QQ_FG,
                maxLines = if (selected) 4 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
        }

        // Khi bấm vào dòng: hiển thị thanh thao tác đầy đủ của việc
        if (selected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 26.dp, end = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText("#${task.id} ${task.stateLabel}", style = type.labelSmall, color = QQ_DIM)

                task.actions.forEach { a ->
                    ChuButton(
                        onClick = { onAction(a, task.id) },
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        enabled = "${a.op}:${task.id}" !in busyOps,
                        borderColor = if (a.danger) QQ_ERR else QQ_DIM,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        ChuText(
                            a.label,
                            style = type.labelSmall,
                            color = if (a.danger) QQ_ERR else QQ_FG,
                        )
                    }
                }

                // Nút copy prompt
                ChuButton(
                    onClick = onCopy,
                    variant = ChuButtonVariant.Ghost,
                    bracketed = true,
                    borderColor = QQ_DIM,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    ChuText("copy", style = type.labelSmall, color = QQ_DIM)
                }

                // Nút xem chi tiết prompt dài
                ChuButton(
                    onClick = onInspect,
                    variant = ChuButtonVariant.Ghost,
                    bracketed = true,
                    borderColor = QQ_ACC,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    ChuText("chi tiết", style = type.labelSmall, color = QQ_ACC)
                }
            }
        }
    }
}

@Composable
private fun AddBar(
    targetAgentName: String,
    onAdd: (String) -> Unit,
) {
    val type = ChuTypography.current
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(QQ_BAND)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuTextField(
            value = text,
            onValueChange = { text = it },
            label = "",
            placeholder = "Giao việc cho @$targetAgentName…",
            singleLine = true,
            showLabel = false,
            modifier = Modifier.weight(1f),
            autoFocus = false,
            verticalPadding = 6.dp,
        )
        Spacer(Modifier.width(6.dp))
        ChuButton(
            onClick = {
                if (text.isNotBlank()) {
                    onAdd(text.trim())
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
            ChuText("gửi", style = type.labelSmall, color = ChuColors.current.onAccent)
        }
    }
}

@Composable
private fun TaskDetailDialog(
    task: QueueTask,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onAction: (QueueAction) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    ChuDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChuText("#${task.id}", style = type.headline, color = colors.accent)
                    TuiBadge(task.stateLabel, task.tone.color())
                }
                ChuButton(
                    onClick = onDismiss,
                    variant = ChuButtonVariant.Ghost,
                    bracketed = true,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    ChuText("x", style = type.label, color = colors.textMuted)
                }
            }

            ChuText("Nội dung prompt:", style = type.labelSmall, color = colors.textMuted)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .background(colors.surface)
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                ChuText(
                    task.text,
                    style = type.body,
                    color = colors.textPrimary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuButton(
                    onClick = onCopy,
                    variant = ChuButtonVariant.Outlined,
                    bracketed = true,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    ChuText("📋 Sao chép", style = type.labelSmall, color = colors.textSecondary)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.actions.forEach { a ->
                        ChuButton(
                            onClick = { onAction(a) },
                            variant = ChuButtonVariant.Outlined,
                            bracketed = true,
                            borderColor = if (a.danger) QQ_ERR else colors.border,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            ChuText(
                                a.label,
                                style = type.labelSmall,
                                color = if (a.danger) QQ_ERR else colors.accent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueLogsDialog(
    logs: List<String>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.scrollToItem(logs.size - 1)
        }
    }

    ChuDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChuText("$ ", style = type.headline, color = colors.textMuted)
                    ChuText("daemon logs", style = type.headline)
                    if (loading) {
                        ChuText("…", style = type.labelSmall, color = QQ_GOLD)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChuButton(
                        onClick = onRefresh,
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        ChuText("⟳", style = type.label, color = colors.accent)
                    }
                    ChuButton(
                        onClick = onDismiss,
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        ChuText("x", style = type.label, color = colors.textMuted)
                    }
                }
            }

            if (error != null) {
                ChuText(error, style = type.bodySmall, color = QQ_ERR)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 320.dp)
                    .background(QQ_LOG_BG)
                    .padding(8.dp),
            ) {
                if (logs.isEmpty() && !loading) {
                    ChuText(
                        "Không có log gần đây",
                        style = type.bodySmall,
                        color = QQ_DIM,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(logs) { _, line ->
                            ChuText(
                                line,
                                style = type.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                ),
                                color = when {
                                    line.contains("ERROR", ignoreCase = true) || line.contains("fail", ignoreCase = true) -> QQ_ERR
                                    line.contains("WARN", ignoreCase = true) -> QQ_WARN
                                    line.contains("START", ignoreCase = true) || line.contains("DONE", ignoreCase = true) -> QQ_ACC
                                    else -> QQ_FG
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigPanel(
    currentUrl: String,
    currentToken: String,
    onSave: (String, String) -> Unit,
    onDismiss: (() -> Unit)?,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    var token by remember(currentToken) { mutableStateOf(currentToken) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChuText(
            "Chỉ cần địa chỉ. Đi qua tailnet thì máy chủ đã biết chắc là bạn, " +
                "không cần token. Ô token bên dưới chỉ dùng khi chạy qsrv " +
                "không nằm sau tailscale serve.",
            style = type.bodySmall,
            color = colors.textSecondary,
        )
        ChuTextField(
            value = url,
            onValueChange = { url = it },
            label = "Địa chỉ qsrv",
            placeholder = "https://may.tailnet.ts.net/q",
            singleLine = true,
            autoFocus = false,
        )
        ChuTextField(
            value = token,
            onValueChange = { token = it },
            label = "Token (không bắt buộc)",
            placeholder = "để trống nếu dùng qua tailnet",
            singleLine = true,
            autoFocus = false,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChuButton(
                onClick = { onSave(url, token) },
                enabled = url.isNotBlank(),
                bracketed = true,
            ) { ChuText("Lưu", color = colors.onAccent) }
            if (onDismiss != null) {
                ChuButton(onClick = onDismiss, variant = ChuButtonVariant.Ghost, bracketed = true) {
                    ChuText("Đóng", color = colors.textSecondary)
                }
            }
        }
    }
}

/** qq dùng vàng cho "đang chạy", mờ cho "xong" — giữ đúng nghĩa đó. */
private fun QueueTone.color(): Color = when (this) {
    QueueTone.Accent -> QQ_GOLD
    QueueTone.Ok -> QQ_DIM
    QueueTone.Warn -> QQ_WARN
    QueueTone.Error -> QQ_ERR
    QueueTone.Dim -> QQ_DIM
}
