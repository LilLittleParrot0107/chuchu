package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.snapshotFlow
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.provider.OpenableColumns
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Row
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import com.jossephus.chuchu.ui.components.KohiCommandBand
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.KohiFeedbackBand
import com.jossephus.chuchu.ui.components.KohiNoticeBand
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.screens.Files.MachineUiState
import com.jossephus.chuchu.ui.theme.AgentKind
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.rosterColor
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
    onRefresh: () -> Unit,
    onShowFeedback: (String, QueueFeedbackTone) -> Unit = { _, _ -> },
    onConsumeFeedback: (Long) -> Unit = {},
    currentUrl: String,
    currentToken: String,
    onSaveConfig: (String, String) -> Unit,
    onFetchResponse: (suspend (Int) -> String?)? = null,
    onBack: () -> Unit = {},
    machine: MachineUiState = MachineUiState(),
    onMachineVisible: (Boolean) -> Unit = {},
    onUsageVisible: (Boolean) -> Unit = {},
    onRefreshUsage: () -> Unit = {},
    onSwitchAgyAccount: (String) -> Unit = {},
    // Màn CHAT của agent (16/9): xem transcript Claude Code ngay trong Queue.
    chat: ChatUiState = ChatUiState(),
    chatSeen: Map<String, String> = emptyMap(),
    onOpenChat: (String) -> Unit = {},
    onCloseChat: () -> Unit = {},
    onLoadOlderChat: () -> Unit = {},
    onSendChat: (String) -> Unit = {},
    /** Thẻ NEEDS YOU trong chat (21/9): chạm lựa chọn n → qsrv gõ số đó vào pane. */
    onAnswerBlocked: (Int) -> Unit = {},
    // Hàng HỘI THOẠI gửi tới chip đang chọn mà không cần mở chat (UI G1, 16/9).
    onSendToPane: (String, String) -> Unit = { _, _ -> },
    // DÒNG THỜI GIAN (UI G1): dữ liệu /feed + bật/tắt poll. Bỏ lọc theo chip 17/9 —
    // dòng thời gian luôn của cả chuồng; chọn phiên chỉ để nhắm ô gõ.
    feed: FeedUiState = FeedUiState(),
    onFeedVisible: (Boolean) -> Unit = {},
    /** ⊕ trong chat: tải file lên ~/inbox trên host, trả đường dẫn để dán vào tin (null = hỏng). */
    onUploadToInbox: suspend (name: String, length: Long, open: () -> java.io.InputStream?) -> String? = { _, _, _ -> null },
    /** Cỡ chữ terminal (sp) để tin trong chat cùng cỡ với terminal. */
    chatFontSizeSp: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val context = LocalContext.current
    val chatOpen = chat.pane != null
    val chatListState = rememberLazyListState()
    val chatScope = rememberCoroutineScope()
    // Dòng thời gian: giữ vị trí cuộn qua mỗi lần rời màn (đổi mode, mở chat, sang
    // màn khác rồi quay lại — user báo 17/9). Neo theo KEY tin đầu đang thấy; về tới
    // nơi mà neo rơi khỏi cửa sổ feed (tin cũ bị cắt) thì hiện điểm mới nhất.
    val feedListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var feedPinned by rememberSaveable { mutableStateOf(true) }
    var feedAnchorKey by rememberSaveable { mutableStateOf<String?>(null) }
    // G1 (user chốt 16/9): mặc định mở ở DÒNG THỜI GIAN; bảng VIỆC là lớp riêng đè lên.
    var tasksOpen by rememberSaveable { mutableStateOf(false) }

    // HorizontalPager: vuốt trái/phải giữa DÒNG THỜI GIAN (0) ↔ HỘI THOẠI (1), đồng bộ Dashboard.
    // Trang pager LÀ chế độ màn — nguồn sự thật duy nhất (21/9). Trước đây còn biến `mode`
    // chạy song song rồi hai effect đồng bộ qua lại + `modeBeforeChat` để nhớ đường về;
    // thực ra rememberPagerState tự lưu qua xoay màn, còn mở chat / bảng VIỆC chỉ tháo
    // pager khỏi cây UI chứ không mất trang — đóng lại là đứng đúng chỗ cũ.
    val pagerState = rememberPagerState(pageCount = { QueueMode.entries.size })
    val mode = QueueMode.entries[pagerState.currentPage]
    val pagerScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    fun goTo(target: QueueMode) {
        tasksOpen = false   // bảng VIỆC và pager cùng chiếm thân màn
        pagerScope.launch { pagerState.animateScrollToPage(target.ordinal) }
    }
    // Rung nhẹ khi sang trang; bỏ giá trị đầu để mở màn không rung.
    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.currentPage }.drop(1).collect {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    var configOpen by remember { mutableStateOf(false) }
    var setupPromptDismissed by remember { mutableStateOf(false) }
    // WHY: chi giu ID thay vi object — poller co the cap nhat/xoa task giua luc
    // dialog mo; resolve lai tu ui.state.tasks moi lan recompose de dialog luon
    // hien trang thai moi nhat thay vi snapshot dong bang luc mo.
    var inspectedTaskId by remember { mutableStateOf<Int?>(null) }
    val inspectedTask = inspectedTaskId?.let { id -> ui.state.tasks.firstOrNull { it.id == id } }

    // Back hệ thống bóc từng lớp (xem queueBackAction). Dialog task/CFG là cửa sổ riêng,
    // tự ăn back nên không có cấp ở đây; manifest đã tắt predictive back nên mỗi cử chỉ
    // là đúng một sự kiện — không cần cửa sổ "nuốt cú dội" như trước.
    BackHandler {
        when (queueBackAction(chatOpen, tasksOpen, mode)) {
            QueueBackAction.CloseChat -> onCloseChat()
            QueueBackAction.CloseTasks -> tasksOpen = false
            QueueBackAction.GoToTimeline -> goTo(QueueMode.Timeline)
            QueueBackAction.Leave -> onBack()
        }
    }
    var prompt by remember { mutableStateOf("") }
    // ⊕ trong chat: chọn file → tải lên ~/inbox qua dufs → dán đường dẫn vào ô gõ (user 16/9).
    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        var name = "file"
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> c.getString(i)?.let { name = it } }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { i -> if (!c.isNull(i)) size = c.getLong(i) }
            }
        }
        val safeName = name.replace(Regex("[/\\\\\u0000]"), "_")
        chatScope.launch {
            val path = onUploadToInbox(safeName, size) { context.contentResolver.openInputStream(uri) }
            if (path != null) prompt = if (prompt.isBlank()) path else prompt.trimEnd() + " " + path
        }
    }

    // Chỉ hỏi khi màn Queue còn hiện; rời màn là dừng poll.
    DisposableEffect(Unit) {
        onMachineVisible(true)
        onDispose { onMachineVisible(false) }
    }

    // Composer da cham vao la GIU focus mai; sheet detail la cua so Dialog
    // rieng, dong lai thi cua so app lay lai focus va Android tu dung IME cho
    // o dang focus — "back khoi detail la ban phim doi len" (user 28/8). Xoa
    // focus ngay khi mo detail: dong sheet xong khong con o nao doi keyboard.
    val focusManager = LocalFocusManager.current
    // Ô gõ đang được focus -> dải máy tự thu lại.
    var composerFocused by remember { mutableStateOf(false) }
    // "Type something" trên thẻ NEEDS YOU: gửi số xong nhảy vào ô gõ, bàn phím lên luôn.
    val composerFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(inspectedTaskId) {
        if (inspectedTaskId != null) focusManager.clearFocus()
    }
    var selectedPane by remember(initialPane) { mutableStateOf(initialPane) }
    // Chỉ long-poll /feed khi chế độ dòng thời gian đang hiện (đỡ tốn radio);
    // mở CHAT cũng tạm ngưng vì tin đã hiện trong chat.
    LaunchedEffect(mode, chatOpen) { onFeedVisible(mode == QueueMode.Timeline && !chatOpen) }

    val agents = ui.state.agents
    // Mặc định TẤT CẢ — dòng thời gian luôn của cả chuồng; đây chỉ là ĐÍCH cho ô gõ,
    // đổi bằng cách chạm một tin trên dòng thời gian / một dòng HỘI THOẠI (user 17/9).
    val pane = selectedPane
        ?.takeIf { candidate -> candidate == ALL_AGENTS || agents.any { it.pane == candidate } }
        ?: ALL_AGENTS
    val selectedAgent = agents.firstOrNull { it.pane == pane }
    // Agent đang mở CHAT — tô màu tên/tin theo LOẠI agent (user chốt 16/9, 1B + 2B).
    val chatAgent = if (chatOpen) agents.firstOrNull { it.pane == chat.pane } else null
    // Dọn "response thừa" (user duyệt 17/9): một lượt assistant nhiều đoạn về BỌT một
    // bubble, think ẩn. Tính ở đây để dòng đếm, danh sách và chip dùng chung một nguồn.
    val chatMessages = remember(chat.messages) { collapseAssistantTurns(chat.messages) }
    // (17/9 revert) chatChipTasks + hàng chip ⏳ đã gỡ — pending chỉ còn trong bảng VIỆC.
    // WHY: qq chi giu 3 task DONE gan nhat trong view de list khong phinh vo
    // han theo thoi gian; muon xoa han thi dung CLR DONE (no moi don state).
    // Active dat truoc doneTail de thu tu doc chay tu viec pending sang viec
    // vua xong, giong hang doi that. Bang VIEC luon hien toan chuong: bo loc theo
    // pane khi xoa rail chip (user 17/9) — rail tung lam ca viec loc lan chon dich.
    val visibleTasks = remember(ui.state.tasks) {
        val active = ui.state.tasks.filterNot { it.isCompleted }
        val doneTail = ui.state.tasks.filter { it.isCompleted }.takeLast(3)
        active + doneTail
    }
    val isAdding = QueueOperationKey.ADD in ui.busyOps

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

    // BoxWithConstraints để biết còn bao nhiêu chỗ SAU KHI bàn phím đã lấy
    // phần của nó (imePadding nằm trên modifier này nên maxHeight đã trừ IME).
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        // Roster cu (cao toi 35% man) da thay bang dai chip luon cao 36dp, khong
        // con tranh cho voi ban phim — hang so dpmax cu bo theo.
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
                title = if (chatOpen) chat.name.uppercase().take(16) else "QUEUE",
                // Tên phiên tô màu theo loại agent khi mở CHAT (như roster).
                titleColor = if (chatOpen) AgentKind.of(chatAgent?.agent).rosterColor() else colors.textPrimary,
                status = if (chatOpen) "· CHAT" else status,
                statusColor = if (chatOpen) colors.textSecondary else statusColor,
                // Không có nút back trong app (user chốt 16/9): back hệ thống đóng chat (BackHandler) hoặc rời màn.
                onBack = null,
                // Toi mau nen theme: status bar + band + content + rail (man
                // rong) la MOT ton, khong con khoi surface sac bep o tren.
                containerColor = colors.background,
                // Tiêu đề QUEUE to hơn chuẩn (user chốt 17/9: "tăng kích thước chữ Queue").
                titleSize = 18.sp,
                modifier = Modifier.onSizeChanged { commandBandHeightPx = it.height },
            ) {
                if (chatOpen) {
                    // Chỉ một nút: nhảy xuống tin mới nhất. Tìm kiếm [⌕] để bước 2.
                    KohiCompactAction(label = "↓", onClick = {
                        chatScope.launch { if (chatMessages.isNotEmpty()) chatListState.scrollToItem(chatMessages.size) }
                    })
                }
                // PAUSE/RESUME bỏ khỏi band (user chốt 17/9): không ai dùng, mà chip
                // nằm ngay cạnh [TASKS] làm band chật. Trạng thái PAUSED vẫn hiện ở
                // status nếu hàng đợi bị tạm dừng từ chỗ khác (qq).
                if (!chatOpen) ui.state.globalActions
                    .firstOrNull { it.op != "pause" && it.op != "resume" }?.let { action ->
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
                // [TASKS] giữ đường vào bảng hàng đợi cũ (UI G1 chỉ còn feed + hội thoại).
                if (!chatOpen) {
                    ChuButton(
                        onClick = { tasksOpen = !tasksOpen },
                        variant = ChuButtonVariant.Ghost,
                        bracketed = false,
                        borderColor = if (tasksOpen) colors.accent else colors.border,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        minHeight = 24.dp,
                    ) {
                        ChuText(
                            // Bỏ số việc (user chốt 17/9 — số nhảy liên tục mà không
                            // giúp quyết định gì); nhãn trần giữ band tĩnh.
                            "[TASKS]",
                            style = ChuTypography.current.labelSmall,
                            color = if (tasksOpen) colors.accent else colors.textPrimary,
                        )
                    }
                }
                if (!chatOpen) KohiCompactAction(label = "SYNC", onClick = onRefresh)
                if (!chatOpen) KohiCompactAction(label = "CFG", onClick = { configOpen = true })
            }

            if (chatOpen) {
                // ── MÀN CHAT: roster + việc + dải máy nhường chỗ, chat ăn hết (user chốt 16/9 "mở hoàn toàn") ──
                val dotColor = chatAgent?.tone?.color() ?: colors.textMuted
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChuText("● ", style = ChuTypography.current.labelSmall, color = dotColor)
                    ChuText(
                        buildString {
                            append(chatAgent?.label?.lowercase() ?: "…")
                            append(" · ${chatMessages.size} tin")
                            chatAge(chat.updatedAt).takeIf { it.isNotEmpty() }?.let { append(" · $it") }
                            if (chat.cwd.isNotBlank()) append(" · cwd ${chat.cwd.replace("/home/a", "~")}")
                        },
                        style = ChuTypography.current.labelSmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                QueueChatView(
                    chat = chat,
                    onLoadOlder = onLoadOlderChat,
                    messages = chatMessages,
                    fontSizeSp = chatFontSizeSp,
                    kind = AgentKind.of(chatAgent?.agent),
                    listState = chatListState,
                    onAnswerBlocked = { opt ->
                        onAnswerBlocked(opt.n)
                        if (opt.opensComposer) {
                            composerFocus.requestFocus()
                            keyboard?.show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
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

            QueueModeSwitch(
                mode = mode,
                threadsCount = agents.size,
                // Đổi chế độ thì đóng bảng VIỆC: hai thứ cùng chiếm thân màn.
                onSelect = { picked -> goTo(picked) },
            )

            // Bảng VIỆC (lớp cũ): luôn là toàn chuồng — bỏ lọc theo chip khi xoá rail
            // (user 17/9); header chỉ còn số việc.
            if (tasksOpen) {
                KohiSectionBand(
                    label = "ALL TASKS",
                    meta = "${visibleTasks.size} TASKS",
                    accent = colors.accent,
                )
            }

            if (tasksOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // Bảng VIỆC mở thì vuốt ngang cũng đổi mode (đóng bảng)
                        .pointerInput(Unit) {
                            var acc = 0f
                            val threshold = 90.dp.toPx()
                            detectHorizontalDragGestures(
                                onDragStart = { acc = 0f },
                                onDragEnd = {
                                    val next = when {
                                        acc <= -threshold -> QueueMode.Threads
                                        acc >= threshold -> QueueMode.Timeline
                                        else -> null
                                    }
                                    if (next != null) goTo(next)
                                },
                            ) { _, dx -> acc += dx }
                        },
                ) {
                    when {
                        visibleTasks.isEmpty() && ui.everLoaded -> EmptyQueueInspector(
                            agent = null,
                            scopeLabel = "ALL AGENTS",
                            allTasks = ui.state.tasks,
                            pane = ALL_AGENTS,
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
                                    showTarget = true,
                                    // Tap = mo thang sheet detail co scrim (user chot
                                    // 27/8, dong bo voi dashboard) — buoc chon-roi-
                                    // INSPECT trung gian da bo.
                                    onClick = { inspectedTaskId = task.id },
                                )
                            }
                        }
                    }
                }
            } else {
                // HorizontalPager: vuốt trái/phải siêu mượt giữa DÒNG THỜI GIAN ↔ HỘI THOẠI (đồng bộ Dashboard)
                // Không compose sẵn trang kề (mặc định 0): trang được compose ngay khi bắt đầu
                // kéo nên vẫn mượt, mà không phải recompose cả hai danh sách mỗi lần state đổi.
                HorizontalPager(
                    state = pagerState,
                    key = { page -> if (page == 0) "timeline" else "threads" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { page ->
                    when (page) {
                        0 -> QueueFeedView(
                            feed = feed,
                            onPick = { m ->
                                // Chạm tin = nhắm phiên đó cho ô gõ ngay dưới (user chốt 17/9):
                                // ở lại dòng thời gian, gõ request luôn tại chỗ.
                                selectedPane = m.pane
                            },
                            listState = feedListState,
                            pinned = feedPinned,
                            onPinnedChange = { feedPinned = it },
                            anchorKey = feedAnchorKey,
                            onAnchorChange = { feedAnchorKey = it },
                        )
                        1 -> QueueConversationList(
                            agents = agents,
                            selectedPane = pane,
                            chatSeen = chatSeen,
                            // Mở chat cũng nhớ phiên đó làm đích ô gõ khi quay lại (17/9).
                            onOpenChat = { p ->
                                selectedPane = p
                                onOpenChat(p)
                            },
                            onSelect = { p -> selectedPane = p },
                        )
                    }
                }
            }
            }

            // Dải máy + ô nhập có mặt ở MỌI chế độ (user chốt 17/9: gõ request ngay
            // trên dòng thời gian). Rail chip đã xoá — đích của ô gõ đổi bằng cách
            // chạm một tin (timeline) hoặc một dòng HỘI THOẠI.
            // Dải máy ghim ngay trên ô nhập: lúc gõ việc mới là lúc cần biết
            // máy còn tải nổi không và còn quota không (user chốt P2, 3/9).
            // Thu panel theo BÀN PHÍM, KHÔNG theo focus. Android không bỏ focus
            // khi đóng bàn phím: ô nhập giữ focus mãi sau lần chạm đầu, nên gắn
            // vào focus là panel bị khoá vĩnh viễn (bản .28, user báo 4/9).
            // Thứ thật sự tranh chỗ với panel là bàn phím, và chỉ nó.
            val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            MachineStrip(
                machine,
                onUsageVisible = onUsageVisible,
                onRefreshUsage = onRefreshUsage,
                onSwitchAgyAccount = onSwitchAgyAccount,
                collapse = imeUp,
                preview = chatOpen,
            )

            // (17/9 revert) Hàng chip ⏳ sát ô gõ đã GỠ — "như cũ" ở đây là KHÔNG có
            // hàng chip nào (pending chỉ còn trong bảng VIỆC); hai dòng cuối transcript
            // cũng không quay lại. Việc của pane này vẫn mở được qua [TASKS].

            // Một ô nhập cho cả ba ngữ cảnh: VIỆC (xếp hàng đợi) · HỘI THOẠI và
            // DÒNG THỜI GIAN (gửi tới phiên đang nhắm, agent bận thì xếp — sendToPane)
            // · CHAT (gõ thẳng vào pane).
            QueueComposer(
                modifier = Modifier.onSizeChanged { composerHeightPx = it.height },
                value = prompt,
                onValueChange = { prompt = it },
                agent = if (chatOpen) chatAgent else selectedAgent,
                sending = when {
                    chatOpen -> chat.sending
                    tasksOpen -> isAdding
                    else -> pane != ALL_AGENTS && QueueOperationKey.chatSend(pane) in ui.busyOps
                },
                onFocusChanged = { composerFocused = it },
                focusRequester = composerFocus,
                placeholder = when {
                    chatOpen -> "Reply to ${chat.name}…"
                    selectedAgent != null -> "Queue / reply to ${selectedAgent.name}…"
                    tasksOpen -> "Pick a session first…"
                    else -> "Tap a message to reply…"
                },
                sendLabel = if (chatOpen) "[SEND ↵]" else "[SEND]",
                // ⊕ giữa ô gõ và [GỬI], cùng màu với nút gửi lúc rảnh (user 16/9: "màu đồng nhất").
                trailing = if (!chatOpen) null else {
                    {
                        ChuButton(
                            onClick = { if (!chat.uploading) attachLauncher.launch("*/*") },
                            enabled = !chat.uploading,
                            variant = ChuButtonVariant.Ghost,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp),
                            minHeight = 34.dp,
                        ) {
                            ChuText(
                                if (chat.uploading) "…" else "⊕",
                                style = ChuTypography.current.label.copy(fontWeight = FontWeight.Bold),
                                color = if (chat.uploading) colors.disabledText else colors.textMuted,
                            )
                        }
                    }
                },
                onSend = {
                    val text = prompt.trim()
                    if (text.isEmpty()) return@QueueComposer
                    when {
                        chatOpen -> {
                            onSendChat(text)
                            prompt = ""
                        }
                        // Bảng VIỆC: xếp task như cũ, nhắm agent đang chọn.
                        tasksOpen -> selectedAgent?.let {
                            onAdd(text, it.pane, null)
                            prompt = ""
                        }
                        // HỘI THOẠI: gửi tới chip đang chọn (agent bận → hàng đợi).
                        pane != ALL_AGENTS -> {
                            onSendToPane(pane, text)
                            prompt = ""
                        }
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

private const val FEEDBACK_TTL_MS = 3_200L

/** Việc cần làm với một cú back khi đang ở màn Queue. */
internal enum class QueueBackAction { CloseChat, CloseTasks, GoToTimeline, Leave }

/**
 * Luật back của màn Queue, tách khỏi Compose để test được. Bóc lớp từ trên xuống:
 * chat toàn màn → bảng VIỆC → trang HỘI THOẠI lùi về DÒNG THỜI GIAN → ở gốc mới
 * nhường cho nav (thu app / popBackStack, tuỳ nơi gọi). Dialog là cửa sổ riêng,
 * tự xử lý back nên không nằm trong luật này.
 */
internal fun queueBackAction(chatOpen: Boolean, tasksOpen: Boolean, mode: QueueMode): QueueBackAction = when {
    chatOpen -> QueueBackAction.CloseChat
    tasksOpen -> QueueBackAction.CloseTasks
    mode != QueueMode.Timeline -> QueueBackAction.GoToTimeline
    else -> QueueBackAction.Leave
}
