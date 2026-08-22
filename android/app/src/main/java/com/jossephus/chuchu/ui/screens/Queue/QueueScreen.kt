package com.jossephus.chuchu.ui.screens.Queue

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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

@Composable
fun QueueTone.color(): Color {
    val colors = ChuColors.current
    return when (this) {
        QueueTone.Accent -> colors.accent
        QueueTone.Ok -> colors.success
        QueueTone.Warn -> colors.warning
        QueueTone.Error -> colors.error
        QueueTone.Dim -> colors.textMuted
    }
}

enum class TaskStatusFilter(val label: String) {
    ALL("all"),
    PENDING("pending"),
    RUNNING("running"),
    DONE("done"),
    ERROR("error");

    fun matches(state: String): Boolean = when (this) {
        ALL -> true
        PENDING -> state.equals("pending", ignoreCase = true)
        RUNNING -> state.equals("sent", ignoreCase = true) || state.equals("sending", ignoreCase = true) ||
            state.equals("working", ignoreCase = true) || state.equals("busy", ignoreCase = true)
        DONE -> state.equals("done", ignoreCase = true) || state.equals("completed", ignoreCase = true)
        ERROR -> state.equals("failed", ignoreCase = true) || state.equals("error", ignoreCase = true) ||
            state.equals("unknown", ignoreCase = true)
    }
}

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
    onFetchResponse: (suspend (Int) -> String?)? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val context = LocalContext.current

    var configOpen by remember { mutableStateOf(false) }
    var logsOpen by remember { mutableStateOf(false) }
    var inspectedTask by remember { mutableStateOf<QueueTask?>(null) }
    var selectedPane by remember { mutableStateOf<String?>("ALL") }
    var selectedStatusFilter by remember { mutableStateOf(TaskStatusFilter.ALL) }
    var expandedTaskId by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var localToast by remember { mutableStateOf<String?>(null) }
    var newPromptText by remember { mutableStateOf("") }

    val showConfig = configOpen || ui.needsSetup
    val agents = ui.state.agents

    val pane = selectedPane?.takeIf { p -> p == "ALL" || agents.any { it.pane == p } }
        ?: agents.firstOrNull()?.pane ?: "ALL"

    val paneBaseTasks = remember(ui.state.tasks, pane) {
        if (pane == "ALL") ui.state.tasks else ui.state.tasks.filter { it.target == pane }
    }

    val statusCounts = remember(paneBaseTasks) {
        TaskStatusFilter.entries.associateWith { filter ->
            if (filter == TaskStatusFilter.ALL) paneBaseTasks.size
            else paneBaseTasks.count { filter.matches(it.state) }
        }
    }

    val activeTasks = remember(paneBaseTasks, selectedStatusFilter, searchQuery) {
        var list = paneBaseTasks
        if (selectedStatusFilter != TaskStatusFilter.ALL) {
            list = list.filter { selectedStatusFilter.matches(it.state) }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter { t ->
                t.text.lowercase().contains(q) ||
                    t.target.lowercase().contains(q) ||
                    "#${t.id}".contains(q) ||
                    t.stateLabel.lowercase().contains(q)
            }
        }
        list
    }

    val doneCount = remember(paneBaseTasks) {
        paneBaseTasks.count { it.state.equals("done", ignoreCase = true) || it.state.equals("completed", ignoreCase = true) }
    }

    fun copyToClipboard(text: String, label: String = "copied to clipboard") {
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
            delay(2500)
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
            // ==================== TOP BAR ====================
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
                        onClick = onBack,
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        ChuText("←", style = type.label, color = colors.textSecondary)
                    }
                    ChuText("queue", style = type.title.copy(fontWeight = FontWeight.Bold))
                    if (ui.state.paused) {
                        TuiBadge("paused", colors.warning)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Global server actions (pause/resume)
                    ui.state.globalActions.forEach { a ->
                        ChuButton(
                            onClick = { onAction(a, null) },
                            variant = ChuButtonVariant.Outlined,
                            bracketed = true,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            ChuText(a.label, style = type.labelSmall, color = if (a.danger) colors.error else colors.accent)
                        }
                    }

                    ChuButton(
                        onClick = { isSearchActive = !isSearchActive },
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        ChuText(if (isSearchActive) "✕" else "search", style = type.labelSmall, color = colors.textSecondary)
                    }

                    ChuButton(
                        onClick = {
                            logsOpen = true
                            onFetchLogs(60)
                        },
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        ChuText("logs", style = type.labelSmall, color = colors.textSecondary)
                    }

                    ChuButton(
                        onClick = onRefresh,
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        ChuText("⟳", style = type.label, color = colors.textSecondary)
                    }

                    ChuButton(
                        onClick = { configOpen = true },
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        ChuText("⚙", style = type.label, color = colors.textSecondary)
                    }
                }
            }

            // Notice / Error Banner if any
            val bannerNotice = ui.error ?: ui.state.banner?.text
            if (!bannerNotice.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (ui.error != null) colors.error.copy(alpha = 0.15f) else colors.warning.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    ChuText(
                        bannerNotice,
                        style = type.labelSmall,
                        color = if (ui.error != null) colors.error else colors.warning,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Search Bar
            if (isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChuTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "",
                        placeholder = "filter by text, #id, status…",
                        singleLine = true,
                        showLabel = false,
                        autoFocus = true,
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
                            ChuText("clear", style = type.labelSmall, color = colors.textMuted)
                        }
                    }
                }
            }

            // ==================== AGENT TABS (SCROLLABLE) ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuButton(
                    onClick = {
                        selectedPane = "ALL"
                        expandedTaskId = null
                    },
                    variant = if (pane == "ALL") ChuButtonVariant.Filled else ChuButtonVariant.Outlined,
                    bracketed = true,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    ChuText("all (${ui.state.tasks.size})", style = type.labelSmall)
                }

                agents.forEach { a ->
                    val isSelected = pane == a.pane
                    val count = ui.state.tasks.count { it.target == a.pane }
                    ChuButton(
                        onClick = {
                            selectedPane = a.pane
                            expandedTaskId = null
                        },
                        variant = if (isSelected) ChuButtonVariant.Filled else ChuButtonVariant.Outlined,
                        bracketed = true,
                        borderColor = if (isSelected) colors.accent else colors.border,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        ChuText(
                            "${a.glyph} ${a.name} ($count)",
                            style = type.labelSmall,
                            color = if (isSelected) colors.onAccent else a.tone.color(),
                        )
                    }
                }
            }

            // ==================== STATUS FILTER & CLEAR DONE ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskStatusFilter.entries.forEach { filter ->
                    val count = statusCounts[filter] ?: 0
                    val isSel = selectedStatusFilter == filter
                    ChuButton(
                        onClick = { selectedStatusFilter = filter },
                        variant = if (isSel) ChuButtonVariant.Filled else ChuButtonVariant.Ghost,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        ChuText(
                            "${filter.label} ($count)",
                            style = type.labelSmall,
                            color = if (isSel) colors.onAccent else colors.textSecondary,
                        )
                    }
                }

                if (doneCount > 0) {
                    ChuButton(
                        onClick = { onClearDone(if (pane == "ALL") null else pane) },
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        borderColor = colors.error,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        ChuText("✕ clear $doneCount done", style = type.labelSmall, color = colors.error)
                    }
                }
            }

            // ==================== TASK LIST ====================
            Box(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                when {
                    activeTasks.isEmpty() && ui.everLoaded -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ChuText(
                                "no tasks in this view",
                                style = type.body,
                                color = colors.textMuted,
                            )
                        }
                    }
                    !ui.everLoaded && ui.loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ChuText("loading queue…", style = type.body, color = colors.textMuted)
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
                        ) {
                            items(activeTasks, key = { it.id }) { task ->
                                val isExpanded = expandedTaskId == task.id
                                TaskCardItem(
                                    task = task,
                                    isExpanded = isExpanded,
                                    showTarget = pane == "ALL",
                                    onClick = {
                                        expandedTaskId = if (isExpanded) null else task.id
                                    },
                                    onInspect = { inspectedTask = task },
                                    onCopyPrompt = { copyToClipboard(task.text, "copied prompt #${task.id}") },
                                    onAction = { a -> onAction(a, task.id) },
                                )
                            }
                        }
                    }
                }
            }

            // ==================== QUICK ADD TASK BAR ====================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val targetLabel = if (pane == "ALL") (agents.firstOrNull()?.name ?: "agent") else (agents.firstOrNull { it.pane == pane }?.name ?: pane)
                    ChuTextField(
                        value = newPromptText,
                        onValueChange = { newPromptText = it },
                        label = "",
                        placeholder = "queue prompt for @$targetLabel…",
                        singleLine = true,
                        showLabel = false,
                        modifier = Modifier.weight(1f),
                    )
                    ChuButton(
                        onClick = {
                            val text = newPromptText.trim()
                            if (text.isNotEmpty()) {
                                val target = if (pane == "ALL") agents.firstOrNull()?.pane else pane
                                onAdd(text, target, null)
                                newPromptText = ""
                            }
                        },
                        variant = ChuButtonVariant.Filled,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        ChuText("send", style = type.label, color = colors.onAccent)
                    }
                }
            }
        }

        // ==================== TOAST OVERLAY ====================
        localToast?.let { toast ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp),
            ) {
                ChuCard(
                    background = colors.surface,
                    border = colors.accent,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    ChuText(
                        toast,
                        style = type.bodySmall,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // ==================== TASK DETAIL / RESPONSE DIALOG ====================
        inspectedTask?.let { task ->
            TaskDetailDialog(
                task = task,
                onDismiss = { inspectedTask = null },
                onCopy = { copyToClipboard(task.text, "copied prompt #${task.id}") },
                onAction = { a ->
                    onAction(a, task.id)
                    inspectedTask = null
                },
                onFetchResponse = onFetchResponse,
            )
        }

        // ==================== CONFIG DIALOG ====================
        if (showConfig) {
            QueueConfigDialog(
                currentUrl = currentUrl,
                currentToken = currentToken,
                onSave = { u, t ->
                    onSaveConfig(u, t)
                    configOpen = false
                },
                onDismiss = { configOpen = false },
            )
        }

        // ==================== LOGS DIALOG ====================
        if (logsOpen) {
            QueueLogsDialog(
                logs = ui.logs,
                loading = ui.logsLoading,
                error = ui.logsError,
                onRefresh = { onFetchLogs(60) },
                onDismiss = { logsOpen = false },
            )
        }
    }
}

// =============================================================================
// TASK CARD ITEM (IMMUNE TO FONT SCALING BREAKAGE)
// =============================================================================

@Composable
private fun TaskCardItem(
    task: QueueTask,
    isExpanded: Boolean,
    showTarget: Boolean,
    onClick: () -> Unit,
    onInspect: () -> Unit,
    onCopyPrompt: () -> Unit,
    onAction: (QueueAction) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val isRunning = task.state.equals("sent", true) || task.state.equals("sending", true) ||
        task.state.equals("working", true) || task.state.equals("busy", true)
    val isDone = task.state.equals("done", true) || task.state.equals("completed", true)

    val cardBorder = when {
        isExpanded -> colors.accent
        isRunning -> colors.accentSecondary
        task.state.equals("failed", true) -> colors.error
        else -> colors.border
    }

    ChuCard(
        background = colors.surfaceVariant,
        border = cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // TẦNG 1: METADATA HEADER (ID · TARGET · STATUS BADGE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChuText(
                        task.glyph,
                        style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = task.tone.color(),
                    )
                    ChuText(
                        "#${task.id}",
                        style = type.label.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                        color = colors.accentSecondary,
                    )
                    if (showTarget && task.target.isNotBlank()) {
                        ChuText(
                            "@${task.target}",
                            style = type.labelSmall,
                            color = colors.textMuted,
                        )
                    }
                }

                TuiBadge(task.stateLabel, task.tone.color())
            }

            // TẦNG 2: FULL WIDTH PROMPT TEXT
            ChuText(
                text = task.text,
                style = type.body.copy(
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                ),
                color = if (isDone) colors.textMuted else colors.textPrimary,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            // TẦNG 3: SUB-INFO (ELAPSED TIME / HAS RESPONSE BADGE)
            if (isRunning || task.hasResp || isDone) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isRunning) {
                        ChuText("● active", style = type.labelSmall, color = colors.accent)
                    }
                    if (task.hasResp || isDone) {
                        ChuText("· 📄 has response", style = type.labelSmall, color = colors.success)
                    }
                }
            }

            // TẦNG 4: ACTION BUTTONS (WHEN EXPANDED)
            if (isExpanded) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChuButton(
                            onClick = onInspect,
                            variant = ChuButtonVariant.Outlined,
                            bracketed = true,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            ChuText(if (task.hasResp || isDone) "view response" else "view prompt", style = type.labelSmall, color = colors.accent)
                        }

                        ChuButton(
                            onClick = onCopyPrompt,
                            variant = ChuButtonVariant.Ghost,
                            bracketed = true,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            ChuText("copy", style = type.labelSmall, color = colors.textSecondary)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        task.actions.forEach { a ->
                            ChuButton(
                                onClick = { onAction(a) },
                                variant = ChuButtonVariant.Outlined,
                                bracketed = true,
                                borderColor = if (a.danger) colors.error else colors.border,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                ChuText(
                                    a.label,
                                    style = type.labelSmall,
                                    color = if (a.danger) colors.error else colors.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// TASK DETAIL / RESPONSE DIALOG
// =============================================================================

@Composable
private fun TaskDetailDialog(
    task: QueueTask,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onAction: (QueueAction) -> Unit,
    onFetchResponse: (suspend (Int) -> String?)? = null,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val context = LocalContext.current
    var responseText by remember { mutableStateOf<String?>(null) }
    var isLoadingResponse by remember { mutableStateOf(false) }

    LaunchedEffect(task.id) {
        if (task.hasResp || task.state.equals("done", ignoreCase = true)) {
            if (onFetchResponse != null) {
                isLoadingResponse = true
                responseText = onFetchResponse(task.id)
                isLoadingResponse = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
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
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    ChuText("✕", style = type.label, color = colors.textMuted)
                }
            }

            ChuText("Prompt:", style = type.labelSmall, color = colors.textMuted)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (responseText != null) 120.dp else 240.dp)
                    .background(colors.surfaceVariant)
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                ChuText(
                    text = task.text,
                    style = type.body,
                    color = colors.textPrimary,
                )
            }

            if (isLoadingResponse) {
                ChuText("loading response from agent…", style = type.labelSmall, color = colors.accent)
            } else if (!responseText.isNullOrBlank()) {
                ChuText("Agent Response:", style = type.labelSmall, color = colors.accent)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .background(colors.surfaceVariant)
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    BasicText(
                        text = buildAnnotatedString {
                            append(responseText.orEmpty())
                        },
                        style = type.bodySmall.copy(color = colors.textPrimary, fontFamily = FontFamily.Monospace),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChuButton(
                        onClick = onCopy,
                        variant = ChuButtonVariant.Outlined,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        ChuText("📋 prompt", style = type.labelSmall, color = colors.textSecondary)
                    }
                    if (!responseText.isNullOrBlank()) {
                        ChuButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Agent Response", responseText))
                            },
                            variant = ChuButtonVariant.Outlined,
                            bracketed = true,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            ChuText("📋 response", style = type.labelSmall, color = colors.accent)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.actions.forEach { a ->
                        ChuButton(
                            onClick = { onAction(a) },
                            variant = ChuButtonVariant.Outlined,
                            bracketed = true,
                            borderColor = if (a.danger) colors.error else colors.border,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            ChuText(
                                a.label,
                                style = type.labelSmall,
                                color = if (a.danger) colors.error else colors.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// CONFIG & LOGS DIALOGS
// =============================================================================

@Composable
private fun QueueConfigDialog(
    currentUrl: String,
    currentToken: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    var urlInput by remember { mutableStateOf(currentUrl) }
    var tokenInput by remember { mutableStateOf(currentToken) }

    ChuDialog(
        title = "queue settings",
        confirmLabel = "save",
        dismissLabel = "cancel",
        onConfirm = { onSave(urlInput.trim(), tokenInput.trim()) },
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChuTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = "qsrv url",
                placeholder = "https://...ts.net/q",
                singleLine = true,
            )
            ChuTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = "auth token",
                placeholder = "Bearer token",
                singleLine = true,
            )
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

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText("daemon logs", style = type.title)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChuButton(
                        onClick = onRefresh,
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        ChuText("⟳", style = type.label, color = colors.textSecondary)
                    }
                    ChuButton(
                        onClick = onDismiss,
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        ChuText("✕", style = type.label, color = colors.textMuted)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(colors.surfaceVariant)
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    loading -> ChuText("fetching logs…", style = type.bodySmall, color = colors.textMuted)
                    error != null -> ChuText("error: $error", style = type.bodySmall, color = colors.error)
                    logs.isEmpty() -> ChuText("no logs available", style = type.bodySmall, color = colors.textMuted)
                    else -> Column {
                        logs.forEach { line ->
                            ChuText(
                                line,
                                style = type.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = if (line.contains("error", true) || line.contains("fail", true)) colors.error else colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
