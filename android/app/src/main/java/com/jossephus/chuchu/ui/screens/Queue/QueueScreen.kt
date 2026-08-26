package com.jossephus.chuchu.ui.screens.Queue

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import com.jossephus.chuchu.ui.components.KohiCommandBand
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.KohiFeedbackBand
import com.jossephus.chuchu.ui.components.KohiNoticeBand
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import kotlinx.coroutines.delay

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
    // WHY: chi giu ID thay vi object — poller co the cap nhat/xoa task giua luc
    // dialog mo; resolve lai tu ui.state.tasks moi lan recompose de dialog luon
    // hien trang thai moi nhat thay vi snapshot dong bang luc mo.
    var inspectedTaskId by remember { mutableStateOf<Int?>(null) }
    val inspectedTask = inspectedTaskId?.let { id -> ui.state.tasks.firstOrNull { it.id == id } }
    var selectedPane by remember(initialPane) { mutableStateOf(initialPane) }
    var prompt by remember { mutableStateOf("") }

    val agents = ui.state.agents
    val pane = selectedPane
        ?.takeIf { candidate -> candidate == ALL_AGENTS || agents.any { it.pane == candidate } }
        ?: agents.firstOrNull()?.pane
        ?: ALL_AGENTS
    val selectedAgent = agents.firstOrNull { it.pane == pane }
    // WHY: qq chi giu 3 task DONE gan nhat trong view de list khong phinh vo
    // han theo thoi gian; muon xoa han thi dung CLR DONE (no moi don state).
    // Active dat truoc doneTail de thu tu doc chay tu viec pending sang viec
    // vua xong, giong hang doi that.
    val visibleTasks = remember(ui.state.tasks, pane) {
        val scoped =
            if (pane == ALL_AGENTS) ui.state.tasks else ui.state.tasks.filter { it.target == pane }
        val active = scoped.filterNot { it.isCompleted }
        val doneTail = scoped.filter { it.isCompleted }.takeLast(3)
        active + doneTail
    }
    val doneCount = visibleTasks.count { it.isCompleted }
    val isAdding = QueueOperationKey.ADD in ui.busyOps
    val isClearingDone =
        QueueOperationKey.clearDone(if (pane == ALL_AGENTS) null else pane) in ui.busyOps

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

    // Feedback la OVERLAY (khong in-flow): truoc day band chen vao Column roi
    // shrink di -> roster nhun len xuong moi lan send/copy (giat). Gio no dap
    // len vung band AGENTS ngay duoi command band, chi fade vao/ra — layout
    // khong bao gio doi chieu cao. Do cao command band duoc do de neo dung.
    var commandBandHeightPx by remember { mutableIntStateOf(0) }
    var composerHeightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        // Scrim status bar = surface: khop voi command band ngay duoi, het
        // seam "thanh noti khac mau phan duoi". Mau lay tu palette active.
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
                .statusBarsPadding(),
        ) {
            // Status ngan de title QUEUE khong bi ep thanh "QU…"; so luong agent
            // da co o band AGENTS, khong lap lai o day.
            val pendingCount = ui.state.tasks.count { !it.isCompleted && !it.isRunning }
            // Text status la logic thuan -> ham non-composable queueStatusText;
            // mau phu thuoc ChuColors.current nen van map tai cho goi.
            val status = queueStatusText(ui)
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
                // Toi mau nen theme: status bar + band + content + rail (man
                // rong) la MOT ton, khong con khoi surface sac bep o tren.
                containerColor = colors.background,
                modifier = Modifier.onSizeChanged { commandBandHeightPx = it.height },
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
                // Chu cai ngan doc duoc hon icon rieng le (↻/⚙ truoc day khong
                // ai giai thich duoc ma van giu dung do rong terminal).
                KohiCompactAction(label = "SYNC", onClick = onRefresh)
                KohiCompactAction(label = "CFG", onClick = { configOpen = true })
                // Clear done nam cung hang LOGS/SYNC chu khong o section band:
                // chip 26dp keo band 26dp len 36dp dung luc co viec xong, trong
                // khi qq giu band muc thuan thong tin mot dong.
                if (doneCount > 0) {
                    // Label co dinh "CLR DONE" ca khi dang chay: doi sang "CLR…"
                    // lam rong band nhay dong; trang thai busy da bao qua enabled.
                    KohiCompactAction(
                        label = "CLR DONE",
                        enabled = !isClearingDone,
                        danger = true,
                        onClick = { onClearDone(if (pane == ALL_AGENTS) null else pane) },
                    )
                }
            }

            // Paused da hien trong status cua command band -> khong lap lai
            // bang mot notice band 28dp nua.
            val notice = ui.error ?: ui.state.banner?.text
                ?.takeUnless { ui.state.paused && it.contains("paused", ignoreCase = true) }
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
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    visibleTasks.isEmpty() && ui.everLoaded -> EmptyQueueInspector(
                        agent = selectedAgent,
                        scopeLabel = selectedAgent?.name ?: "ALL AGENTS",
                        allTasks = ui.state.tasks,
                        pane = pane,
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
                    // Lan quet dau chua thanh con + loi mang: day nguoi ve hanh
                    // dong dung (kiem tra QSRV, pull CFG de retry) chu khong de
                    // roi vao danh sach gia hay spinner vo han.
                    !ui.everLoaded && ui.error != null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChuText(
                            "▌ OFFLINE — CHECK QSRV · PULL CFG TO RETRY",
                            style = ChuTypography.current.labelSmall,
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
                                selected = false,
                                showTarget = pane == ALL_AGENTS,
                                // Tap = mo thang sheet detail co scrim (user chot
                                // 27/8, dong bo voi dashboard) — buoc chon-roi-
                                // INSPECT trung gian da bo.
                                onClick = { inspectedTaskId = task.id },
                            )
                        }
                    }
                }
            }

            QueueComposer(
                modifier = Modifier.onSizeChanged { composerHeightPx = it.height },
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
                onDismiss = { inspectedTaskId = null },
                onCopy = { copyPrompt(task) },
                onAction = { action ->
                    onAction(action, task.id)
                    inspectedTaskId = null
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


        // Overlay feedback: dap len canh DUOI, ngay tren composer (user doi
        // 26/8 — truoc day o tren, che band AGENTS). Van la overlay fade
        // vao/ra, khong anh huong layout; truot len tu duoi cho hop huong.
        androidx.compose.animation.AnimatedVisibility(
            visible = ui.feedback != null,
            enter = androidx.compose.animation.fadeIn(
                androidx.compose.animation.core.tween(150),
            ) + androidx.compose.animation.slideInVertically(
                androidx.compose.animation.core.tween(150),
            ) { it / 3 },
            exit = androidx.compose.animation.fadeOut(
                androidx.compose.animation.core.tween(250),
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, -composerHeightPx) },
        ) {
            ui.feedback?.let { feedback ->
                KohiFeedbackBand(
                    text = feedback.text,
                    color = feedback.tone.color(),
                    onDismiss = { onConsumeFeedback(feedback.id) },
                )
            }
        }
    }
}

/**
 * WHY: text status la logic thuan, khong cham Compose state — cung mot
 * QueueUiState luon ra cung mot chuoi, test duoc khong can compose harness.
 * Thu tu if LA nghiep vu: loading nuot error (dang quet lai), error nuot
 * paused...; mau tuong ung van map rieng tai composable.
 */
private fun queueStatusText(ui: QueueUiState): String {
    val pendingCount = ui.state.tasks.count { !it.isCompleted && !it.isRunning }
    return when {
        ui.loading -> "SCANNING"
        ui.error != null -> "OFFLINE"
        ui.state.paused -> "PAUSED"
        !ui.everLoaded -> "NOT SCANNED"
        pendingCount > 0 -> "$pendingCount PENDING"
        else -> "LIVE"
    }
}

private const val DEFAULT_LOG_LINES = 60
private const val FEEDBACK_TTL_MS = 3_200L
