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
import com.jossephus.chuchu.ui.components.KohiBackHandler
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import com.jossephus.chuchu.ui.components.KohiCommandBand
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.KohiFeedbackBand
import com.jossephus.chuchu.ui.components.KohiNoticeBand
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.components.KohiSelectableRow
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.rememberTicking
import com.jossephus.chuchu.ui.screens.Files.MachineUiState
import com.jossephus.chuchu.ui.theme.AgentKind
import com.jossephus.chuchu.ui.theme.CHU_HAIRLINE_ALPHA
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.sessionColor
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
    onBack: () -> Unit = {},
    machine: MachineUiState = MachineUiState(),
    /** Ô trên dải machine (28/9): user chọn trong QUEUE SETTINGS. */
    stripCells: List<String> = StripCells.DEFAULT,
    onToggleStripCell: (String) -> Unit = {},
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
    onSubmitBlocked: (List<Int>) -> Unit = {},
    /** Model opencode (15/10): picker cho pane agent=opencode — chip ◆ cạnh nút ⊕. */
    ocPicker: QueueViewModel.OcPickerState? = null,
    onLoadOcModels: () -> Unit = {},
    onSwitchOcModel: (String, String, String?) -> Unit = { _, _, _ -> },
    // FILES (23/9, thay TIMELINE): file portal dufs nhúng vào trang phải của pager.
    portalUrl: String = "",
    /** Ô search tab FILES (25/9): UI debounce rồi gọi cái này → qsrv /files/search. */
    onSearchFiles: (suspend (String) -> com.jossephus.chuchu.ui.screens.Queue.FileSearchResult)? = null,
    /** Trang mở khi vào màn (deep link "file portal" từ terminal → Files); null = giữ trang đang có. */
    initialMode: QueueMode? = null,
    /** Kênh "về HỘI THOẠI" khi bấm tab QUEUE; session-queue truyền null (không collect). */
    homeRequests: kotlinx.coroutines.flow.Flow<Unit>? = null,
    // NEW SESSION (23/9): tấm trượt đáy chọn agent · thư mục · lệnh → qsrv mở phiên.
    launchDirs: List<LaunchDir> = emptyList(),
    onLaunchOpen: () -> Unit = {},
    onLaunch: (agent: String, cwd: String, prompt: String) -> Unit = { _, _, _ -> },
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
    // Bảng VIỆC là lớp riêng đè lên pager.
    var tasksOpen by rememberSaveable { mutableStateOf(false) }
    // Tấm NEW SESSION (23/9): mở là nạp danh sách thư mục gợi ý.
    var launchOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(launchOpen) { if (launchOpen) onLaunchOpen() }
    // Picker model opencode (15/10): mở bằng chip ◆ trong composer (chỉ pane opencode).
    var ocSheet by rememberSaveable { mutableStateOf(false) }

    // HorizontalPager: vuốt trái/phải giữa HỘI THOẠI (0) ↔ FILES (1) ↔ MACHINE (2).
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
        // Bấm tab = tới thẳng trang, KHÔNG lướt (user 27/9); vuốt tay vẫn đi lần lượt.
        pagerScope.launch { pagerState.scrollToPage(target.ordinal) }
    }
    // Deep link "file portal" (terminal) → mở thẳng trang FILES.
    LaunchedEffect(initialMode) { if (initialMode != null) pagerState.scrollToPage(initialMode.ordinal) }
    // 3/10 (user): bấm tab QUEUE lúc nào cũng về HỘI THOẠI — đang đọc thread thì
    // đóng về list, đang FILES/MACHINE thì scroll về trang 0. homeRequests là kênh
    // sự-kiện-một-lần do VM giữ: remount/xoay màn không phát lại (bài học tick Int cũ).
    // rememberUpdatedState: effect không restart theo recomposition nên phải đọc
    // chat.pane + callback hiện tại, không giữ closure cũ.
    val chatNow by rememberUpdatedState(chat)
    val onCloseNow by rememberUpdatedState(onCloseChat)
    LaunchedEffect(homeRequests) {
        homeRequests?.collect {
            if (chatNow.pane != null) onCloseNow()
            pagerState.scrollToPage(QueueMode.Threads.ordinal)
        }
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
    KohiBackHandler {
        when (queueBackAction(chatOpen, tasksOpen, mode)) {
            QueueBackAction.CloseChat -> onCloseChat()
            QueueBackAction.CloseTasks -> tasksOpen = false
            QueueBackAction.GoToThreads -> goTo(QueueMode.Threads)
            QueueBackAction.Leave -> onBack()
        }
    }
    if (launchOpen) {
        QueueLaunchSheet(
            dirs = launchDirs,
            onDismiss = { launchOpen = false },
            onStart = { agent, cwd, text ->
                launchOpen = false
                onLaunch(agent, cwd, text)
            },
        )
    }
    var prompt by rememberSaveable { mutableStateOf("") }
    // Đổi phiên chat (A→B): đóng tấm model và bỏ draft của phiên A — trước đây
    // chữ đang gõ dở theo sang phiên B và bấm SEND là gửi nhầm người nhận. Đóng
    // chat (pane → null) KHÔNG xoá để draft dùng chung với ô gõ bảng VIỆC.
    var lastChatPane by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(chat.pane) {
        val next = chat.pane
        if (lastChatPane != null && next != null && lastChatPane != next) {
            ocSheet = false
            prompt = ""
        }
        lastChatPane = next
    }
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
        // Pane tại lúc chọn file: upload xong sau khi user đổi phiên thì không
        // được dán path của phiên A vào ô soạn của phiên B.
        val targetPane = chat.pane
        chatScope.launch {
            val path = onUploadToInbox(safeName, size) { context.contentResolver.openInputStream(uri) }
            if (path == null) return@launch
            if (chat.pane == targetPane) {
                prompt = if (prompt.isBlank()) path else prompt.trimEnd() + " " + path
            } else {
                onShowFeedback("Uploaded for another session — path not inserted", QueueFeedbackTone.Warning)
            }
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
    // "Type something" trên thẻ NEEDS YOU: gửi số xong nhảy vào ô gõ, bàn phím lên luôn.
    val composerFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(inspectedTaskId) {
        if (inspectedTaskId != null) focusManager.clearFocus()
    }
    var selectedPane by remember(initialPane) { mutableStateOf(initialPane) }
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
        // Màn rộng (máy gập mở, ≥ 600dp — cùng ngưỡng KohiNavShell/Dbtop/composer):
        // dải agent trong chat xoay dọc trái. Chốt ở tầng BoxWithConstraints vì sâu
        // trong Column là ColumnScope che mất maxWidth.
        val wide = maxWidth >= 600.dp
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
                // 1B (duyệt preview 16/10): bỏ tiền tố "OC |" (màu band đã báo loại
                // agent) và bỏ .take(16) cắt cứng GIỮA TỪ — band tự Ellipsis theo
                // độ rộng thật của màn (mở 760dp hay gập 390dp đều dùng hết chỗ).
                title = if (chatOpen) cleanSessionName(chat.name) else "QUEUE",
                // Tên phiên tô màu theo loại agent khi mở CHAT (như roster).
                titleColor = if (chatOpen) AgentKind.of(chatAgent?.agent).sessionColor(chatAgent?.name ?: chat.name) else colors.textPrimary,
                status = if (chatOpen) "· CHAT" else status,
                statusColor = if (chatOpen) colors.textSecondary else statusColor,
                // Không có nút back trong app (user chốt 16/9): back hệ thống đóng chat (BackHandler) hoặc rời màn.
                onBack = null,
                // Toi mau nen theme: status bar + band + content + rail (man
                // rong) la MOT ton, khong con khoi surface sac bep o tren.
                containerColor = colors.background,
                // 17/10 (user: "tăng kích thước font của tên session ở trên cùng,
                // scale theo font chữ"): tiêu đề phiên bám cỡ chữ terminal (mặc định
                // 14sp): 14 → ~19sp, font to thì tiêu đề to theo (linear) — trần 26sp
                // cho band không vỡ, sàn 16sp. QUEUE (không phải tên phiên) giữ 18sp
                // (user chốt 17/9 "tăng kích thước chữ Queue").
                titleSize = if (chatOpen) {
                    val baseFont = chatFontSizeSp.takeIf { it > 0f } ?: 14f
                    (baseFont * 1.35f).coerceIn(16f, 26f).sp
                } else {
                    18.sp
                },
                modifier = Modifier.onSizeChanged { commandBandHeightPx = it.height },
            ) {
                if (chatOpen && chatListState.canScrollForward) {
                    // 4B (duyệt preview 16/10): ↓ "nhảy xuống tin mới nhất" chỉ hiện
                    // khi ĐÃ cuộn lên — đang ở đáy thì nút là affordance chết.
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

            // Dải máy (hàng liếc) + ô gõ — dùng chung cho chat lẫn bảng VIỆC; trong
            // nhánh wide-rail (2B) nó nằm trong CỘT PHẢI nên tách lambda khỏi chỗ gọi.
            val queueBottomBar: @Composable () -> Unit = {
                MachineStrip(machine, preview = true, cells = stripCells)
                QueueComposer(
                    modifier = Modifier.onSizeChanged { composerHeightPx = it.height },
                    value = prompt,
                    onValueChange = { prompt = it },
                    agent = if (chatOpen) chatAgent else selectedAgent,
                    sending = if (chatOpen) chat.sending else isAdding,
                    focusRequester = composerFocus,
                    placeholder = when {
                        // 1B: tên phiên sạch (bỏ "OC |") — hết cụt "dự á…".
                        chatOpen -> "Reply to ${cleanSessionName(chat.name)}…"
                        selectedAgent != null -> "Queue / reply to ${cleanSessionName(selectedAgent.name)}…"
                        else -> "Pick a session first…"
                    },
                    sendLabel = if (chatOpen) "[SEND ↵]" else "[SEND]",
                    // 3B (duyệt preview 16/10): màn CHAT bỏ "@agent" — đang ở trong phòng
                    // của chính phiên đó, placeholder đã nói rõ người nhận; chip ◆ model
                    // dời lên cuối hàng meta. Bảng VIỆC giữ @agent (đích gửi cần thấy).
                    showAgentMention = !chatOpen,
                    // ⊕ giữa ô gõ và [GỬI], cùng màu với nút gửi lúc rảnh (user 16/9: "màu đồng nhất").
                    trailing = if (!chatOpen) null else {
                        {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
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
                        }
                    },
                )
            }
            if (chatOpen) {
                // ── MÀN CHAT: roster + việc + dải máy nhường chỗ, chat ăn hết (user chốt 16/9 "mở hoàn toàn") ──
                // Dải chuyển agent NGAY TRONG CHAT (28/9): đang đọc một phiên vẫn nhảy sang
                // phiên khác không cần back. Cùng đường với chạm hàng HỘI THOẠI: nhớ pane làm
                // đích khi back ra + mở chat mới (ViewModel reset + poll lại).
                val onSwitchAgent: (String) -> Unit = { p -> selectedPane = p; onOpenChat(p) }
                // 2B (duyệt preview 16/10): rail chỉ khi có ≥2 phiên — 1 phiên thì cột
                // 172dp (mở) / hàng tab (gập) chỉ lặp lại thông tin đã có ở band + meta.
                val railVisible = agents.size > 1
                // 3B: chip ◆ model dời từ ô gõ lên CUỐI HÀNG META — ô gõ màn gập hết bị
                // bóp. BubbleShell cũng truyền chip qua slot này (18/10) — trước đó nó
                // còn nhét chip vào composer, đúng lỗi ô gõ bị bóp.
                val modelChipSlot: (@Composable () -> Unit)? = if (AgentKind.of(chatAgent?.agent) == AgentKind.OPENCODE) {
                    {
                        OcModelChip(
                            picker = ocPicker,
                            onClick = { ocSheet = true; onLoadOcModels() },
                            // Đừng để tên model dài bóp hàng meta (working · age · cwd).
                            modifier = Modifier.widthIn(max = 180.dp),
                        )
                    }
                } else null
                if (wide && railVisible) {
                    // RỘNG + ≥2 phiên: rail dọc TRÁI chạy THẲNG XUỐNG ĐÁY (vạch hairline
                    // mép phải), cột phải chứa chat + dải máy + ô gõ — machine/composer
                    // KHÔNG cắt ngang rail nữa (2B duyệt preview 16/10). 172 → 148dp.
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        QueueAgentRail(
                            agents = agents,
                            currentPane = chat.pane,
                            chatSeen = chatSeen,
                            onSwitch = onSwitchAgent,
                            orientation = AgentRailOrientation.Vertical,
                            modifier = Modifier.width(148.dp).fillMaxHeight(),
                        )
                        // Vạch phân cách rail|chat là BOX 1dp THẬT nằm NGOÀI rail:
                        // bản trước vẽ bằng drawBehind trên container rail → bị NỀN
                        // hàng (background đặc) đè mất, chỉ ló 2 khe 8dp trên/dưới =
                        // "đường phân cách đứt đoạn" + dải trông méo (user bắt 17/10).
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(colors.border.copy(alpha = CHU_HAIRLINE_ALPHA)),
                        )
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            ChatThreadView(
                                chat = chat,
                                chatAgent = chatAgent,
                                chatMessages = chatMessages,
                                chatFontSizeSp = chatFontSizeSp,
                                chatListState = chatListState,
                                onLoadOlder = onLoadOlderChat,
                                onAnswerBlocked = onAnswerBlocked,
                                onSubmitBlocked = onSubmitBlocked,
                                composerFocus = composerFocus,
                                keyboard = keyboard,
                                modelChip = modelChipSlot,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                            queueBottomBar()
                        }
                    }
                } else {
                    // HẸP (hoặc RỘNG nhưng chỉ 1 phiên): chat ăn hết; dải tab ngang
                    // NGAY TRÊN dải machine chỉ khi ≥2 phiên.
                    ChatThreadView(
                        chat = chat,
                        chatAgent = chatAgent,
                        chatMessages = chatMessages,
                        chatFontSizeSp = chatFontSizeSp,
                        chatListState = chatListState,
                        onLoadOlder = onLoadOlderChat,
                        onAnswerBlocked = onAnswerBlocked,
                        onSubmitBlocked = onSubmitBlocked,
                        composerFocus = composerFocus,
                        keyboard = keyboard,
                        modelChip = modelChipSlot,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    if (railVisible) {
                        QueueAgentRail(
                            agents = agents,
                            currentPane = chat.pane,
                            chatSeen = chatSeen,
                            onSwitch = onSwitchAgent,
                            orientation = AgentRailOrientation.Horizontal,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
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
                                        acc <= -threshold -> QueueMode.entries.getOrElse(mode.ordinal + 1) { mode }
                                        acc >= threshold -> QueueMode.entries.getOrElse(mode.ordinal - 1) { mode }
                                        else -> null
                                    }
                                    // goTo luôn đóng bảng VIỆC — quét ở mép trang
                                    // (đã là trang ngoài) thì vẫn đóng bảng được.
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
                // HorizontalPager: vuốt trái/phải siêu mượt giữa HỘI THOẠI ↔ FILES (đồng bộ Dashboard)
                // Không compose sẵn trang kề (mặc định 0): trang được compose ngay khi bắt đầu
                // kéo nên vẫn mượt, mà không phải recompose cả hai danh sách mỗi lần state đổi.
                HorizontalPager(
                    state = pagerState,
                    key = { page -> QueueMode.entries[page].name.lowercase() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { page ->
                    when (QueueMode.entries[page]) {
                        QueueMode.Threads -> QueueConversationList(
                            agents = agents,
                            selectedPane = pane,
                            chatSeen = chatSeen,
                            // Mở chat cũng nhớ phiên đó làm đích ô gõ khi quay lại (17/9).
                            onOpenChat = { p ->
                                selectedPane = p
                                onOpenChat(p)
                            },
                            onSelect = { p -> selectedPane = p },
                            onNewSession = { launchOpen = true },
                        )
                        // FILES = file portal dufs (trước là tab riêng ở thanh dưới; user gộp 23/9).
                        // BackHandler của nó đứng trong trang này nên back = lên thư mục cha, ở gốc thì về HỘI THOẠI.
                        QueueMode.Files -> com.jossephus.chuchu.ui.screens.Web.WebPortalScreen(
                            url = portalUrl,
                            onClose = { goTo(QueueMode.Threads) },
                            embedded = true,
                            onSearch = onSearchFiles,
                        )
                        // MACHINE (25/9): bảng usage/machine — tab xem, không ô gõ.
                        QueueMode.Machine -> MachineStrip(
                            machine,
                            cells = stripCells,
                            onUsageVisible = onUsageVisible,
                            onRefreshUsage = onRefreshUsage,
                            onSwitchAgyAccount = onSwitchAgyAccount,
                            asPage = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            }

            // (17/9 revert) Hàng chip ⏳ sát ô gõ đã GỠ — "như cũ" ở đây là KHÔNG có
            // hàng chip nào (pending chỉ còn trong bảng VIỆC); hai dòng cuối transcript
            // cũng không quay lại. Việc của pane này vẫn mở được qua [TASKS].

            // Ô gõ CHỈ hiện khi cần gõ (user 25/9: "ở conversation thì không hiện
            // thanh chat"): mở HỘI THOẠI (reply trong thread) hoặc bảng VIỆC (xếp
            // task). Ba tab CONVERSATIONS / FILES / MACHINE là bề mặt xem — không ô gõ.
            // Nhánh wide-rail (2B) đã gọi queueBottomBar trong CỘT PHẢI — bỏ ở đây
            // khỏi nhân đôi dải máy + ô gõ.
            if ((chatOpen || tasksOpen) && !(chatOpen && wide && agents.size > 1)) {
                queueBottomBar()
            }
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
            )
        }

        // Picker model opencode (15/10): tấm đáy, giữ mở sau khi chọn để đổi variant tiếp.
        if (ocSheet && chatOpen) {
            com.jossephus.chuchu.ui.components.KohiBottomSheet(onDismiss = { ocSheet = false }) {
                OcModelSheet(
                    state = ocPicker,
                    agentName = cleanSessionName(chatAgent?.name ?: chat.name),
                    onReload = onLoadOcModels,
                    onPick = { p, id, v -> onSwitchOcModel(p, id, v) },
                )
            }
        }

        if (configOpen || (ui.needsSetup && !setupPromptDismissed)) {
            QueueConfigDialog(
                currentUrl = currentUrl,
                currentToken = currentToken,
                stripCells = stripCells,
                onToggleStripCell = onToggleStripCell,
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

/**
 * Thân thread chat (hàng meta + danh sách tin): tách riêng để nhánh chatOpen rẽ
 * hẹp/rộng (28/9) mà không chép hai bản — meta và QueueChatView chỉ định nghĩa một lần.
 */
@Composable
// internal (đổi từ private 12/10): BubbleShell tái dùng đúng màn chat này —
// một implementation duy nhất cho cả Queue đầy đủ lẫn bubble, không fork UI chat.
internal fun ChatThreadView(
    chat: ChatUiState,
    chatAgent: QueueAgent?,
    chatMessages: List<ChatMessage>,
    chatFontSizeSp: Float,
    chatListState: LazyListState,
    onLoadOlder: () -> Unit,
    onAnswerBlocked: (Int) -> Unit,
    onSubmitBlocked: (List<Int>) -> Unit,
    composerFocus: FocusRequester,
    keyboard: SoftwareKeyboardController?,
    /** 3B (duyệt preview 16/10): chip ◆ model đứng CUỐI hàng meta (bên phải) thay vì
     *  trong ô gõ — màn gập hết bị bóp. null = không chip (agent không phải opencode). */
    modelChip: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    // Tuổi chat phải nhích theo đồng hồ, không đóng băng ở lúc compose.
    val now by rememberTicking(30_000L)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 4B (duyệt preview 16/10): chấm ĐỒNG BỘ với rail (runtimeDot: ○ done/idle,
            // ● working/blocked) thay vì "●" đậm cứng; bỏ "N tin" — đó là số tin ĐÃ TẢI
            // (đặt cạnh nút "N earlier" gây hiểu nhầm); gọn "1m ago · ~/chuchu" (bỏ
            // "updated "/"cwd ") để màn gập không mất cwd.
            ChuText(
                chatAgent?.let { "${runtimeDot(it)} " } ?: "… ",
                style = ChuTypography.current.labelSmall,
                color = chatAgent?.let { sessionStatusColor(it) } ?: colors.textMuted,
            )
            ChuText(
                buildString {
                    append(chatAgent?.label?.lowercase() ?: "…")
                    chatAge(chat.updatedAt, now).takeIf { it.isNotEmpty() }?.let { append(" · $it") }
                    // Dùng home server trả về, không hardcode /home/a: path
                    // /home/agent/... từng bị biến thành "~gent/...".
                    if (chat.cwd.isNotBlank()) {
                        val home = chat.home.trimEnd('/')
                        val shortCwd = if (home.isNotBlank() && chat.cwd.startsWith(home)) {
                            "~" + chat.cwd.removePrefix(home)
                        } else {
                            chat.cwd
                        }
                        append(" · $shortCwd")
                    }
                },
                style = ChuTypography.current.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            modelChip?.invoke()
        }
        QueueChatView(
            chat = chat,
            onLoadOlder = onLoadOlder,
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
            onSubmitBlocked = onSubmitBlocked,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

/** Việc cần làm với một cú back khi đang ở màn Queue. */
internal enum class QueueBackAction { CloseChat, CloseTasks, GoToThreads, Leave }
/**
 * Luật back của màn Queue, tách khỏi Compose để test được. Bóc lớp từ trên xuống:
 * chat toàn màn → bảng VIỆC → trang FILES/MACHINE lùi về HỘI THOẠI → ở gốc mới
 * nhường cho nav (thu app / popBackStack, tuỳ nơi gọi). Dialog là cửa sổ riêng,
 * tự xử lý back nên không nằm trong luật này.
 */
internal fun queueBackAction(chatOpen: Boolean, tasksOpen: Boolean, mode: QueueMode): QueueBackAction = when {
    chatOpen -> QueueBackAction.CloseChat
    tasksOpen -> QueueBackAction.CloseTasks
    mode != QueueMode.Threads -> QueueBackAction.GoToThreads
    else -> QueueBackAction.Leave
}

/**
 * Chip ◆ model opencode (15/10) — dùng chung màn CHAT đầy đủ và bubble shell.
 * Nhãn: model đang dùng (id + #variant), chưa nạp xong thì "◆ …" / "◆ model".
 */
@Composable
internal fun OcModelChip(
    picker: QueueViewModel.OcPickerState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val cur = picker?.session?.model
    val label = when {
        cur != null -> {
            // Tên hiển thị từ catalog thay vì cắt 14 ký tự id (mọi model
            // claude-sonnet… đều thành "claude-sonnet-"). Variant đứng trước
            // để chip hẹp không nuốt mất #max.
            val display = picker?.models
                ?.firstOrNull { it.id == cur.id && it.providerID == cur.providerID }
                ?.name
                ?.takeIf { it.isNotBlank() }
                ?: cur.id
            val variant = cur.variant?.takeIf { it.isNotBlank() }?.let { "#$it " } ?: ""
            "◆ $variant$display"
        }
        picker?.loading == true -> "◆ …"
        else -> "◆ model"
    }
    ChuButton(
        onClick = onClick,
        modifier = modifier,
        variant = ChuButtonVariant.Ghost,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp),
        minHeight = 34.dp,
    ) {
        ChuText(
            label,
            style = ChuTypography.current.labelSmall,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Tấm chọn model opencode (15/10). Một màn, không điều hướng: mỗi model một hàng; model
 * có variant thì hiện hàng chip variant ngay dưới — chạm chip là đổi luôn (tấm giữ mở để
 * chỉnh tiếp). Model/variant đang dùng tô accent. Đổi model chỉ ăn với pane opencode 2.
 */
@Composable
internal fun OcModelSheet(
    state: QueueViewModel.OcPickerState?,
    agentName: String,
    onReload: () -> Unit,
    onPick: (providerID: String, id: String, variant: String?) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val cur = state?.session?.model
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header dùng nguyên ngữ pháp section của app (▌ LABEL · meta + token hành
        // động) — hết khối "MODEL · …" + ⟳ riêng lẻ không giống ai.
        KohiSectionBand(
            label = "MODEL",
            meta = cleanSessionName(agentName),
            trailing = {
                KohiCompactAction(label = "SYNC", onClick = onReload)
            },
        )
        when {
            state?.error != null ->
                ChuText(state.error, style = type.bodySmall, color = colors.error)
            state?.loading == true && state.models.isEmpty() ->
                ChuText("loading models…", style = type.bodySmall, color = colors.textMuted)
            state?.models.isNullOrEmpty() ->
                ChuText("no models", style = type.bodySmall, color = colors.textMuted)
            else -> {
                // 16/10 (user: "list model sao toàn của b.ai vậy"): catalog ~95 model
                // từ ~3 provider xếp dọc 95 hàng như nhau thì không đọc ra cấu trúc —
                // header nhóm khi ĐỔI provider (qsrv xếp provider đang dùng lên đầu).
                val headerFlags = remember(state.models) {
                    val seen = HashSet<String>()
                    BooleanArray(state.models.size).also { flags ->
                        state.models.forEachIndexed { i, m -> flags[i] = seen.add(m.providerID) }
                    }
                }
                val providerCount = remember(state.models) {
                    HashMap<String, Int>().also { c ->
                        state.models.forEach { c[it.providerID] = (c[it.providerID] ?: 0) + 1 }
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(state.models, key = { _, m -> m.providerID + "/" + m.id }) { i, m ->
                        if (headerFlags[i]) {
                            ChuText(
                                "${m.providerID.uppercase()} · ${providerCount[m.providerID]}",
                                style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.textMuted,
                                modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 3.dp),
                            )
                        }
                        val isCur = cur != null && cur.providerID == m.providerID && cur.id == m.id
                        // Hàng model dùng nguyên khuôn chọn của app (KohiSelectableRow —
                        // như hàng POS/watch): surface + viền tone + rail 3dp khi đang
                        // dùng; variant là nhãn bracket [MAX] cùng ngữ pháp [SEND].
                        KohiSelectableRow(
                            selected = isCur,
                            tone = colors.accent,
                            onClick = { onPick(m.providerID, m.id, if (isCur) cur?.variant else null) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                ChuText(
                                    m.name,
                                    style = type.label.copy(
                                        fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = if (isCur) colors.accent else colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (m.variants.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        m.variants.forEach { v ->
                                            val vCur = isCur && cur?.variant == v
                                            ChuText(
                                                "[${v.uppercase()}]",
                                                style = type.labelSmall.copy(
                                                    fontWeight = if (vCur) FontWeight.Bold else FontWeight.Normal,
                                                ),
                                                color = if (vCur) colors.accent else colors.textSecondary,
                                                modifier = Modifier
                                                    .clickable { onPick(m.providerID, m.id, v) }
                                                    .padding(vertical = 3.dp),
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
    }
}
