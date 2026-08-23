package com.jossephus.chuchu.ui.screens.Queue

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.components.KohiCommandBand
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.KohiFeedbackBand
import com.jossephus.chuchu.ui.components.KohiNoticeBand
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.components.ChuText
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

/**
 * Native Queue follows qq's hierarchy: command band, agent rows, task rows,
 * one contextual detail pane, then a fixed composer.
 */
@Composable
fun QueueScreen(
    ui: QueueUiState,
    initialPane: String? = null,
    onAction: (QueueAction, Int?) -> Unit,
    onAdd: (String, String?, String?) -> Unit,
    onClearDone: (String?) -> Unit,
    onRefresh: () -> Unit,
    onFetchLogs: (Int) -> Unit = {},
    onShowFeedback: (String, QueueFeedbackTone) -> Unit = { _, _ -> },
    onConsumeFeedback: (Long) -> Unit = {},
    currentUrl: String,
    currentToken: String,
    onSaveConfig: (String, String) -> Unit,
    onFetchResponse: (suspend (Int) -> String?)? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val context = LocalContext.current

    var configOpen by remember { mutableStateOf(false) }
    var setupPromptDismissed by remember { mutableStateOf(false) }
    var logsOpen by remember { mutableStateOf(false) }
    var inspectedTask by remember { mutableStateOf<QueueTask?>(null) }
    var selectedPane by remember(initialPane) { mutableStateOf(initialPane) }
    var selectedTaskId by remember { mutableStateOf<Int?>(null) }
    var prompt by remember { mutableStateOf("") }

    val agents = ui.state.agents
    val pane = selectedPane
        ?.takeIf { candidate -> candidate == ALL_AGENTS || agents.any { it.pane == candidate } }
        ?: agents.firstOrNull()?.pane
        ?: ALL_AGENTS
    val selectedAgent = agents.firstOrNull { it.pane == pane }
    val visibleTasks = remember(ui.state.tasks, pane) {
        if (pane == ALL_AGENTS) ui.state.tasks else ui.state.tasks.filter { it.target == pane }
    }
    val selectedTask = visibleTasks.firstOrNull { it.id == selectedTaskId }
    val doneCount = visibleTasks.count { it.isCompleted }
    val isAdding = QueueOperationKey.ADD in ui.busyOps
    val isClearingDone = ui.busyOps.any(QueueOperationKey::isClearDone)

    fun copyPrompt(task: QueueTask) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Queue prompt", task.text))
        onShowFeedback("Prompt #${task.id} copied", QueueFeedbackTone.Info)
    }

    LaunchedEffect(ui.feedback?.id) {
        ui.feedback?.let { feedback ->
            delay(FEEDBACK_TTL_MS)
            onConsumeFeedback(feedback.id)
        }
    }
    LaunchedEffect(ui.needsSetup) {
        if (!ui.needsSetup) setupPromptDismissed = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Status ngan de title QUEUE khong bi ep thanh "QU…"; so luong agent
            // da co o band AGENTS, khong lap lai o day.
            val pendingCount = ui.state.tasks.count { !it.isCompleted && !it.isRunning }
            val status = when {
                ui.loading -> "SCANNING"
                ui.error != null -> "OFFLINE"
                ui.state.paused -> "PAUSED"
                !ui.everLoaded -> "NOT SCANNED"
                pendingCount > 0 -> "$pendingCount PENDING"
                else -> "LIVE"
            }
            val statusColor = when {
                ui.error != null -> colors.error
                ui.state.paused -> colors.warning
                ui.loading || !ui.everLoaded -> colors.textMuted
                pendingCount > 0 -> colors.accent
                else -> colors.success
            }
            KohiCommandBand(
                title = "QUEUE",
                status = status,
                statusColor = statusColor,
                onBack = onBack,
            ) {
                ui.state.globalActions.firstOrNull()?.let { action ->
                    val busy = action.operationKey(null) in ui.busyOps
                    KohiCompactAction(
                        label = if (busy) "WAIT" else action.label.uppercase(),
                        enabled = !busy,
                        danger = action.danger,
                        onClick = { onAction(action, null) },
                    )
                }
                KohiCompactAction(
                    label = "LOGS",
                    onClick = {
                        logsOpen = true
                        onFetchLogs(DEFAULT_LOG_LINES)
                    },
                )
                // Chu cai ngan doc duoc hon icon rieng le (↻/⚙ truoc day khong
                // ai giai thich duoc ma van giu dung do rong terminal).
                KohiCompactAction(label = "SYNC", onClick = onRefresh)
                KohiCompactAction(label = "CFG", onClick = { configOpen = true })
            }

            val notice = ui.error ?: ui.state.banner?.text
            if (!notice.isNullOrBlank()) {
                KohiNoticeBand(
                    text = notice,
                    color = if (ui.error != null) colors.error else ui.state.banner?.tone?.color() ?: colors.warning,
                    urgent = ui.error != null,
                )
            }

            QueueAgentRoster(
                agents = agents,
                tasks = ui.state.tasks,
                selectedPane = pane,
                onSelect = { nextPane ->
                    selectedPane = nextPane
                    selectedTaskId = null
                },
            )

            // Header vung content phai tu tra loi "duoi day thuoc ve agent nao":
            // TEN · STATUS · N TASKS tren mot dong duy nhat.
            KohiSectionBand(
                label = selectedAgent?.name ?: "ALL TASKS",
                meta = buildString {
                    selectedAgent?.let { append(it.label.uppercase()).append(" · ") }
                    append("${visibleTasks.size} TASKS")
                },
                accent = selectedAgent?.tone?.color() ?: colors.accent,
            ) {
                if (doneCount > 0) {
                    KohiCompactAction(
                        label = if (isClearingDone) "CLEARING" else "CLEAR DONE",
                        enabled = !isClearingDone,
                        danger = true,
                        onClick = { onClearDone(if (pane == ALL_AGENTS) null else pane) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    visibleTasks.isEmpty() && ui.everLoaded -> EmptyQueueInspector(
                        agent = selectedAgent,
                        scopeLabel = selectedAgent?.name ?: "ALL AGENTS",
                        tasks = visibleTasks,
                    )
                    !ui.everLoaded && ui.loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChuText(
                            "LOADING QUEUE…",
                            style = ChuTypography.current.label,
                            color = colors.textMuted,
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 6.dp),
                    ) {
                        items(visibleTasks, key = QueueTask::id) { task ->
                            QueueTaskRow(
                                task = task,
                                selected = selectedTaskId == task.id,
                                showTarget = pane == ALL_AGENTS,
                                onClick = {
                                    selectedTaskId = if (selectedTaskId == task.id) null else task.id
                                },
                            )
                        }
                    }
                }
            }

            selectedTask?.let { task ->
                QueueTaskDetailPane(
                    task = task,
                    busyOps = ui.busyOps,
                    onInspect = { inspectedTask = task },
                    onCopy = { copyPrompt(task) },
                    onAction = { action -> onAction(action, task.id) },
                )
            }

            ui.feedback?.let { feedback ->
                KohiFeedbackBand(
                    text = feedback.text,
                    color = feedback.tone.color(),
                    onDismiss = { onConsumeFeedback(feedback.id) },
                )
            }

            QueueComposer(
                value = prompt,
                onValueChange = { prompt = it },
                agent = selectedAgent,
                sending = isAdding,
                onSend = {
                    val text = prompt.trim()
                    if (text.isNotEmpty() && selectedAgent != null) {
                        onAdd(text, selectedAgent.pane, null)
                        prompt = ""
                    }
                },
            )
        }

        inspectedTask?.let { task ->
            TaskDetailDialog(
                task = task,
                onDismiss = { inspectedTask = null },
                onCopy = { copyPrompt(task) },
                onAction = { action ->
                    onAction(action, task.id)
                    inspectedTask = null
                },
                onFetchResponse = onFetchResponse,
            )
        }

        if (configOpen || (ui.needsSetup && !setupPromptDismissed)) {
            QueueConfigDialog(
                currentUrl = currentUrl,
                currentToken = currentToken,
                onSave = { url, token ->
                    onSaveConfig(url, token)
                    configOpen = false
                    setupPromptDismissed = false
                },
                onDismiss = {
                    configOpen = false
                    setupPromptDismissed = true
                },
            )
        }

        if (logsOpen) {
            QueueLogsDialog(
                logs = ui.logs,
                loading = ui.logsLoading,
                error = ui.logsError,
                onRefresh = { onFetchLogs(DEFAULT_LOG_LINES) },
                onDismiss = { logsOpen = false },
            )
        }
    }
}

@Composable
private fun QueueFeedbackTone.color(): Color {
    val colors = ChuColors.current
    return when (this) {
        QueueFeedbackTone.Info -> colors.accent
        QueueFeedbackTone.Success -> colors.success
        QueueFeedbackTone.Warning -> colors.warning
        QueueFeedbackTone.Error -> colors.error
    }
}

private const val DEFAULT_LOG_LINES = 60
private const val FEEDBACK_TTL_MS = 3_200L
