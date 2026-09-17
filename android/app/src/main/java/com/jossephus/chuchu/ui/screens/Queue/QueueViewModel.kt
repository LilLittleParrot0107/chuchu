package com.jossephus.chuchu.ui.screens.Queue

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jossephus.chuchu.data.repository.SettingsRepository
import com.jossephus.chuchu.data.network.InboxUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.jossephus.chuchu.data.model.machine.MachineSnapshot
import com.jossephus.chuchu.data.model.machine.derive
import com.jossephus.chuchu.ui.screens.Files.MachineUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Trạng thái màn hình hàng đợi.
 *
 * `state` giữ lại bản đọc được gần nhất kể cả khi đang mất mạng — mất sóng giữa
 * chừng thì vẫn thấy hàng đợi cũ kèm dòng báo lỗi, hơn là màn hình trắng.
 */
/** Trạng thái màn CHAT (tab Queue). [pane] null = đang đóng. */
data class ChatUiState(
    val pane: String? = null,
    val name: String = "",
    val cwd: String = "",
    val home: String = "",
    val size: Long = 0L,
    val rev: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val cursor: Long? = null,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val loadingOlder: Boolean = false,
    val sending: Boolean = false,
    val uploading: Boolean = false,
    val error: String? = null,
    val updatedAt: Long = 0L,
)

data class QueueUiState(
    val state: QueueState = QueueState.Empty,
    val loading: Boolean = false,
    val everLoaded: Boolean = false,
    val error: String? = null,
    val needsSetup: Boolean = false,
    val busyOps: Set<String> = emptySet(),
    val feedback: QueueFeedback? = null,
)

/**
 * Dòng thời gian (UI G1): tin cuối các session đang động, mới nhất ở cuối. [pane] null =
 * tất cả. Server trả trọn trang mỗi lần (không phân trang) — tin cũ trôi khỏi tầm mắt,
 * muốn đọc đủ thì mở hội thoại.
 */
data class FeedUiState(
    val messages: List<FeedMessage> = emptyList(),
    val rev: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val pane: String? = null,
    val updatedAt: Long = 0L,
)

class QueueViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(QueueUiState())
    val ui: StateFlow<QueueUiState> = _ui.asStateFlow()

    // ── Màn CHAT của một agent (16/9): đọc transcript qua qsrv /chat ──────────────
    private val _chat = MutableStateFlow(ChatUiState())
    val chat: StateFlow<ChatUiState> = _chat.asStateFlow()
    /** pane -> rev đã xem; app so với `QueueAgent.chatRev` để hiện "CHAT · MỚI". */
    private val _chatSeen = MutableStateFlow<Map<String, String>>(settings.allChatSeenRevs())
    val chatSeen: StateFlow<Map<String, String>> = _chatSeen.asStateFlow()
    private var chatJob: Job? = null

    // ── Dòng thời gian (UI G1): chỉ chạy khi chế độ DÒNG THỜI GIAN đang hiện ──────
    private val _feed = MutableStateFlow(FeedUiState())
    val feed: StateFlow<FeedUiState> = _feed.asStateFlow()
    private var feedJob: Job? = null
    private var feedWanted = false

    private val _ambientSummary = MutableStateFlow(QueueAmbientSummary.Empty)

    // ── Dải trạng thái máy trên ô nhập ────────────────────────────────────
    // Đứng ở Queue là lúc quyết giao việc, nên bốn số cần là RAM/CPU ("máy còn
    // tải nổi không") và quota 5H/tuần ("còn lượt không") — user chốt 3/9.
    // Poller /machine dùng chung với Terminal; chỉ chạy khi app foreground (P4).
    private val machinePoller = MachinePoller(viewModelScope, { client() }, { if (quotaWanted) "1" else null })
    val machine: StateFlow<MachineUiState> get() = machinePoller.state
    private var machineJob: Job? = null

    /** True khi trang USAGE đang hiện — chỉ khi đó mới xin server làm mới quota. */
    @Volatile private var quotaWanted = false

    fun setQuotaWanted(wanted: Boolean) { quotaWanted = wanted }

    /** True khi dải máy còn muốn số (màn Queue đang hiện). */
    private var machineWanted = false
    private var quotaTickerJob: Job? = null

    /**
     * Tự làm mới số quota mỗi 10 phút khi màn Queue đang hiện (user chốt 17/9):
     * trước đây quota chỉ được làm mới lúc trang USAGE mở, nên đứng ở DÒNG THỜI
     * GIAN cả buổi là số cũ dần mà không ai hay. ĐÁ `quota=1` (không phải force):
     * server thấy cache quá 30s thì gọi script làm mới, còn mỗi script tự chặn
     * thêm (claude TTL 600s, bai tối thiểu 60s/lần). Chỉ chạy khi app
     * FOREGROUND và Queue đang hiện, như mọi poll khác — bỏ túi là dừng.
     */
    private fun syncQuotaAutoRefresh() {
        if (!isAppActive || !machineWanted) {
            quotaTickerJob?.cancel(); quotaTickerJob = null
            return
        }
        if (quotaTickerJob?.isActive == true) return
        quotaTickerJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(QUOTA_AUTO_REFRESH_MS)
                client()?.machine("1")
            }
        }
    }

    /**
     * Nút ⟳ trên trang USAGE: bắn một phát `quota=force` ngay, không đợi nhịp
     * poll và không đợi TTL 10 phút của script. Bỏ luôn phản hồi — số mới sẽ
     * theo nhịp poll kế tiếp về, cái cần ở đây chỉ là ĐÁ cho server làm mới.
     */
    fun requestQuotaRefresh() {
        viewModelScope.launch(Dispatchers.IO) { client()?.machine("force") }
    }

    /** Bật khi màn Queue hiện, tắt khi rời — không poll sau lưng người dùng. */
    fun setMachinePolling(active: Boolean) {
        machineWanted = active
        machinePoller.setWanted("queue", active)
        syncQuotaAutoRefresh()
    }

    val ambientSummary: StateFlow<QueueAmbientSummary> = _ambientSummary.asStateFlow()

    private var pollJob: Job? = null
    private var pollingMode = QueuePollingMode.Stopped
    private var isAppActive = false
    private var isQueueVisible = false
    private val actionJobs = mutableMapOf<String, Job>()
    private var cachedClientUrl = ""
    private var cachedClientToken = ""
    private var cachedClient: QueueClient? = null
    private val refreshMutex = Mutex()
    private var feedbackSerial = 0L

    private fun postFeedback(
        raw: String,
        fallback: String,
        tone: QueueFeedbackTone,
    ) {
        val feedback = QueueFeedback(
            id = ++feedbackSerial,
            text = normalizeQueueFeedbackText(raw, fallback),
            tone = tone,
        )
        _ui.update { it.copy(feedback = feedback) }
    }

    private fun client(): QueueClient? {
        val url = settings.queueUrl.value
        if (url.isBlank()) {
            cachedClient = null
            return null
        }
        val token = settings.queueToken.value
        if (cachedClient == null || url != cachedClientUrl || token != cachedClientToken) {
            cachedClientUrl = url
            cachedClientToken = token
            cachedClient = QueueClient(url, token)
        }
        return cachedClient
    }

    /** Persist auth recovery so later launches do not keep retrying a stale token. */
    private fun persistAuthRecovery(client: QueueClient) {
        if (!client.recoveredWithoutToken || settings.queueToken.value.isBlank()) return
        settings.setQueueToken("")
        _ui.update { it.copy(needsSetup = false) }
        postFeedback(
            raw = "Removed the stale Queue token; using Tailscale identity",
            fallback = "Queue connection recovered",
            tone = QueueFeedbackTone.Info,
        )
    }

    fun chatSeenRev(pane: String): String? = _chatSeen.value[pane] ?: settings.chatSeenRev(pane)

    private fun markChatSeen(pane: String, rev: String) {
        if (rev.isBlank()) return
        settings.setChatSeenRev(pane, rev)
        _chatSeen.update { it + (pane to rev) }
    }

    /** Mở màn chat của [pane]: tải 50 tin cuối rồi long-poll chừng nào màn còn mở. */
    fun openChat(pane: String) {
        val name = _ui.value.state.agents.firstOrNull { it.pane == pane }?.name ?: pane
        _chat.value = ChatUiState(pane = pane, name = name, loading = true)
        syncChatPolling()
    }

    fun closeChat() {
        chatJob?.cancel(); chatJob = null
        _chat.value = ChatUiState()
    }

    private fun syncChatPolling() {
        val pane = _chat.value.pane
        val shouldRun = pane != null && isAppActive && isQueueVisible
        if (!shouldRun) { chatJob?.cancel(); chatJob = null; return }
        if (chatJob?.isActive == true) return
        chatJob = viewModelScope.launch {
            var backoff = 0L
            while (isActive && _chat.value.pane == pane) {
                val t0 = System.currentTimeMillis()
                val failed = chatRefreshOnce(pane, waitSec = LONGPOLL_S)
                backoff = if (failed) minOf(maxOf(backoff * 2, FOREGROUND_POLL_MS), MAX_FOREGROUND_BACKOFF_MS) else 0L
                val elapsed = System.currentTimeMillis() - t0
                delay(maxOf(backoff, MIN_GAP_MS - elapsed))
            }
        }
    }

    /** true nếu hỏng. Trang mới về thì GHÉP với tin cũ hơn đã tải (offset là vị trí byte, ổn định). */
    private suspend fun chatRefreshOnce(pane: String, waitSec: Int): Boolean {
        val c = client() ?: run { _chat.update { it.copy(loading = false, error = "Queue is not configured yet") }; return true }
        val since = _chat.value.rev.takeIf { it.isNotBlank() }
        val result = withContext(Dispatchers.IO) { c.chat(pane, limit = CHAT_PAGE, sinceRev = since, waitSec = waitSec) }
        persistAuthRecovery(c)
        if (_chat.value.pane != pane) return false
        return when (val r = result) {
            is QueueClient.ChatFetch.Fresh -> {
                val page = r.page
                _chat.update { cur ->
                    val firstNew = page.messages.firstOrNull()?.offset ?: Long.MAX_VALUE
                    val kept = cur.messages.filter { it.offset < firstNew }
                    val hasMore = if (kept.isEmpty()) page.hasMore else cur.hasMore
                    val cursor = if (kept.isEmpty()) page.cursor else cur.cursor
                    cur.copy(name = page.name.ifBlank { cur.name }, cwd = page.cwd, home = page.home.ifBlank { cur.home }, size = page.size, rev = page.rev,
                             messages = kept + page.messages, hasMore = hasMore, cursor = cursor,
                             loading = false, error = null, updatedAt = System.currentTimeMillis())
                }
                markChatSeen(pane, page.rev)
                false
            }
            QueueClient.ChatFetch.Unchanged -> { _chat.update { it.copy(loading = false, error = null) }; false }
            is QueueClient.ChatFetch.Failed -> {
                _chat.update { it.copy(loading = false, error = r.message) }
                if (r.needsAuth) _ui.update { it.copy(needsSetup = true) }
                true
            }
        }
    }

    /** Bật khi công tắc đang ở DÒNG THỜI GIAN; tắt khi sang HỘI THOẠI (đỡ tốn radio). */
    fun setFeedVisible(wanted: Boolean) {
        if (wanted == feedWanted) return
        feedWanted = wanted
        syncFeedPolling()
    }

    /** Chip session đổi lọc: rev cũ không còn nghĩa → xoá để lần tới lấy trọn trang. */
    fun setFeedPane(pane: String?) {
        val normalized = pane?.takeIf { it.isNotBlank() }
        if (_feed.value.pane == normalized) return
        _feed.update { it.copy(pane = normalized, rev = "", messages = emptyList(), loading = true, error = null) }
        syncFeedPolling()
    }

    private fun syncFeedPolling() {
        val shouldRun = feedWanted && isAppActive && isQueueVisible
        if (!shouldRun) { feedJob?.cancel(); feedJob = null; return }
        if (feedJob?.isActive == true) return
        feedJob = viewModelScope.launch {
            var backoff = 0L
            while (isActive && feedWanted) {
                val t0 = System.currentTimeMillis()
                val failed = feedRefreshOnce(waitSec = LONGPOLL_S)
                backoff = if (failed) minOf(maxOf(backoff * 2, FOREGROUND_POLL_MS), MAX_FOREGROUND_BACKOFF_MS) else 0L
                val elapsed = System.currentTimeMillis() - t0
                delay(maxOf(backoff, MIN_GAP_MS - elapsed))
            }
        }
    }

    /** true nếu hỏng (để giãn nhịp). Server trả trọn trang nên áp thẳng, không ghép. */
    private suspend fun feedRefreshOnce(waitSec: Int): Boolean {
        val c = client() ?: run { _feed.update { it.copy(loading = false, error = "Queue is not configured yet") }; return true }
        val cur = _feed.value
        val since = cur.rev.takeIf { it.isNotBlank() }
        val result = withContext(Dispatchers.IO) { c.feed(cur.pane, sinceRev = since, waitSec = waitSec) }
        persistAuthRecovery(c)
        if (_feed.value.pane != cur.pane) return false
        return when (val r = result) {
            is QueueClient.FeedFetch.Fresh -> {
                _feed.update { it.copy(messages = r.page.messages, rev = r.page.rev, loading = false, error = null, updatedAt = System.currentTimeMillis()) }
                false
            }
            QueueClient.FeedFetch.Unchanged -> { _feed.update { it.copy(loading = false, error = null) }; false }
            is QueueClient.FeedFetch.Failed -> {
                _feed.update { it.copy(loading = false, error = r.message) }
                if (r.needsAuth) _ui.update { it.copy(needsSetup = true) }
                true
            }
        }
    }

    /** "tải thêm": trang cũ hơn trước cursor, nối lên đầu. */
    fun loadOlderChat() {
        val cur = _chat.value
        val pane = cur.pane ?: return
        val before = cur.cursor ?: return
        if (cur.loadingOlder) return
        _chat.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            val c = client() ?: run { _chat.update { it.copy(loadingOlder = false) }; return@launch }
            val result = withContext(Dispatchers.IO) { c.chat(pane, limit = CHAT_PAGE, before = before) }
            if (_chat.value.pane != pane) return@launch
            _chat.update { st ->
                when (result) {
                    is QueueClient.ChatFetch.Fresh -> {
                        val older = result.page.messages.filter { m -> st.messages.none { it.key == m.key } }
                        st.copy(messages = older + st.messages, cursor = result.page.cursor, hasMore = result.page.hasMore, loadingOlder = false)
                    }
                    else -> st.copy(loadingOlder = false, error = (result as? QueueClient.ChatFetch.Failed)?.message ?: st.error)
                }
            }
        }
    }

    /**
     * ⊕ trong màn chat (16/9): tải file lên `~/inbox` trên host qua dufs (cùng đường với ⊕ của
     * terminal, không cần tài khoản) rồi trả về đường dẫn để dán vào tin. null nếu hỏng (đã báo).
     */
    suspend fun uploadToInbox(name: String, length: Long, open: () -> java.io.InputStream?): String? {
        val portal = settings.webPortalUrl.value.trim().trimEnd('/')
        if (portal.isBlank()) { postFeedback("", "No Web portal URL in Settings", QueueFeedbackTone.Error); return null }
        val home = _chat.value.home.ifBlank { "/home/a" }
        _chat.update { it.copy(uploading = true) }
        try {
            val result = withContext(Dispatchers.IO) {
                val input = open() ?: return@withContext InboxUploader.Result.Failed("Could not open the file")
                input.use { InboxUploader("${portal.removeSuffix("/home")}/home/inbox").put(name, it, length) }
            }
            return when (result) {
                InboxUploader.Result.Ok -> {
                    postFeedback("", "Uploaded $name", QueueFeedbackTone.Success)
                    "$home/inbox/$name"
                }
                is InboxUploader.Result.Failed -> { postFeedback(result.message, "Upload failed", QueueFeedbackTone.Error); null }
            }
        } finally {
            _chat.update { it.copy(uploading = false) }
        }
    }

    /**
     * Gửi từ màn chat (user chốt 16/9): agent ĐANG CHẠY thì xếp vào hàng đợi taskq để qd gõ
     * khi nó rảnh (không chen giữa việc đang làm); đang chờ duyệt/bị chặn/rảnh/xong thì gõ
     * thẳng vào pane — chờ duyệt mà xếp hàng là kẹt cả hai bên.
     */
    fun sendChat(text: String) {
        sendToPane(_chat.value.pane ?: return, text, fromChat = true)
    }

    /**
     * Gửi tới một pane từ hàng HỘI THOẠI (chip đang chọn) — cùng luật với màn chat, chỉ
     * khác không phụ thuộc màn chat đang mở. Khoá busyOps theo pane để nút GỬI biết chờ.
     */
    fun sendToPane(pane: String, text: String, fromChat: Boolean = false) {
        if (pane.isBlank() || text.isBlank()) return
        val key = QueueOperationKey.chatSend(pane)
        if (key in _ui.value.busyOps) return
        val agent = _ui.value.state.agents.firstOrNull { it.pane == pane }
        if (agent != null && agent.label.trim().lowercase() in CHAT_QUEUE_WHEN) {
            addTask(text, pane, null)
            return
        }
        _ui.update { it.copy(busyOps = it.busyOps + key) }
        if (fromChat) _chat.update { it.copy(sending = true) }
        viewModelScope.launch {
            try {
                val c = client() ?: return@launch
                val r = withContext(Dispatchers.IO) { c.chatSend(pane, text.trim()) }
                persistAuthRecovery(c)
                val name = agent?.name?.takeIf { it.isNotBlank() } ?: pane
                when (r) {
                    is QueueClient.Act.Ok -> postFeedback("", "Sent to $name", QueueFeedbackTone.Success)
                    is QueueClient.Act.Conflict -> postFeedback("", "Send again", QueueFeedbackTone.Warning)
                    is QueueClient.Act.Failed -> {
                        _ui.update { it.copy(needsSetup = r.needsAuth) }
                        postFeedback(r.message, "Send failed", QueueFeedbackTone.Error)
                    }
                }
            } finally {
                _ui.update { it.copy(busyOps = it.busyOps - key) }
                if (fromChat) _chat.update { it.copy(sending = false) }
            }
        }
    }

    /** Keep polling off while the app is paused, regardless of the visible route. */
    fun setAppActive(active: Boolean) {
        isAppActive = active
        syncPollingMode()
        syncChatPolling()
        syncFeedPolling()
        // Cả poll /machine lẫn ý muốn làm mới quota đều dừng khi app xuống nền:
        // trang USAGE còn mở trong túi quần không được kéo theo claude 380MB/30s.
        machinePoller.setAppActive(active)
        if (!active) quotaWanted = false
        syncQuotaAutoRefresh()
    }

    /** Select foreground cadence only while the Queue destination is composed. */
    fun setQueueVisible(visible: Boolean) {
        isQueueVisible = visible
        syncPollingMode()
        syncChatPolling()
        syncFeedPolling()
    }

    private fun syncPollingMode() {
        val targetMode = resolveQueuePollingMode(isAppActive, isQueueVisible)
        if (targetMode == pollingMode && pollJob?.isActive == true) return

        pollJob?.cancel()
        pollingMode = targetMode
        pollJob = when (targetMode) {
            QueuePollingMode.Stopped -> null
            QueuePollingMode.Ambient -> launchAmbientPolling()
            QueuePollingMode.Foreground -> launchForegroundPolling()
        }
    }

    // Cả hai chế độ đều LONG-POLL (pin, 7/9): request nằm ở server tới LONGPOLL_S hoặc
    // tới khi rev đổi. Lúc rảnh ~2 request/phút thay vì 30 (foreground) / 4–17 (ambient),
    // thay đổi hiện ngay. MIN_GAP chặn vòng xoáy khi rev đổi liên tục (agent đổi trạng
    // thái từng giây); hỏng thì giãn theo backoff như cũ.
    private fun launchForegroundPolling(): Job = viewModelScope.launch {
        var backoff = 0L
        var firstScan = true
        while (isActive) {
            val t0 = System.currentTimeMillis()
            // Mỗi lần mở Queue phải lấy lại full payload. Nếu chỉ gửi `since`,
            // một state UI cũ/khuyết có thể mắc kẹt mãi sau các lượt 304.
            val failed = refreshOnce(forceFull = firstScan, waitSec = LONGPOLL_S)
            firstScan = false
            backoff = if (failed) minOf(maxOf(backoff * 2, FOREGROUND_POLL_MS), MAX_FOREGROUND_BACKOFF_MS) else 0L
            val elapsed = System.currentTimeMillis() - t0
            delay(maxOf(backoff, MIN_GAP_MS - elapsed))
        }
    }

    /** Ambient (app mở nhưng không ở tab Queue): cũng long-poll, chỉ khác nhịp lúc hỏng. */
    private fun launchAmbientPolling(): Job = viewModelScope.launch {
        while (isActive) {
            val t0 = System.currentTimeMillis()
            val failed = refreshOnce(waitSec = LONGPOLL_S)
            val elapsed = System.currentTimeMillis() - t0
            delay(if (failed) AMBIENT_IDLE_POLL_MS else maxOf(0L, AMBIENT_MIN_GAP_MS - elapsed))
        }
    }

    /**
     * Trả về true nếu lần đọc này hỏng (để phía gọi giãn nhịp poll ra).
     * Mutex CHỈ bọc phần áp kết quả, KHÔNG bọc cú mạng: long-poll giữ tới 25s, mà
     * runAction/refreshNow cũng gọi hàm này — giữ mutex xuyên mạng là bấm SEND xong
     * phải đợi long-poll xả mới thấy hàng đợi đổi. Hai kết quả về gần nhau thì áp
     * theo thứ tự về (server luôn trả trạng thái tại lúc trả), không cần đánh số.
     */
    private suspend fun refreshOnce(forceFull: Boolean = false, waitSec: Int = 0): Boolean {
        val c = client() ?: run {
            refreshMutex.withLock {
                _ui.update { it.copy(needsSetup = true, loading = false, error = null) }
                _ambientSummary.value = QueueAmbientSummary.from(_ui.value.state, null)
            }
            return true
        }
        val since = _ui.value.state.rev.takeIf { !forceFull && _ui.value.everLoaded }
        _ui.update { it.copy(loading = forceFull || !it.everLoaded) }

        val result = withContext(Dispatchers.IO) { c.fetch(since, waitSec) }
        persistAuthRecovery(c)
        // State mới nhất được giữ lại đây để gọi autoClearDone SAU khi nhả mutex:
        // nó bắn HTTP riêng qua viewModelScope, không được nằm trong khoá poll.
        var fresh: QueueState? = null
        val failed = refreshMutex.withLock { when (val r = result) {
            is QueueClient.Fetch.Fresh -> {
                _ui.update {
                    it.copy(state = r.state, loading = false, everLoaded = true,
                            error = null, needsSetup = false)
                }
                _ambientSummary.value = QueueAmbientSummary.from(r.state, null)
                fresh = r.state
                false
            }
            QueueClient.Fetch.Unchanged -> {
                _ui.update {
                    it.copy(
                        loading = false,
                        error = null,
                    )
                }
                false
            }
            is QueueClient.Fetch.Failed -> {
                _ui.update {
                    it.copy(loading = false, error = r.message, needsSetup = r.needsAuth)
                }
                _ambientSummary.value = QueueAmbientSummary.from(_ui.value.state, r.message)
                true
            }
        } }
        // Việc đã xong tự biến khỏi hàng đợi (user chốt 17/9) — thay hẳn nút CLR.
        fresh?.let(::autoClearDone)
        return failed
    }

    fun refreshNow() {
        viewModelScope.launch { refreshOnce(forceFull = true) }
    }


    /** One operation key can own only one request, including blocking HTTP. */
    fun runAction(action: QueueAction, taskId: Int?) {
        val key = action.operationKey(taskId)
        if (actionJobs[key]?.isActive == true) return
        actionJobs[key] = viewModelScope.launch {
            _ui.update { it.copy(busyOps = it.busyOps + key) }
            try {
                val c = client() ?: run {
                    _ui.update { it.copy(needsSetup = true) }
                    return@launch
                }
                // Chỉ kèm rev khi server nói op này cần — kèm bừa thì thao tác
                // vô hại như xoá cũng hỏng chỉ vì phiên khác vừa động vào hàng đợi.
                val rev = if (action.needsRev) _ui.value.state.rev else null
                val result = withContext(Dispatchers.IO) { c.act(action.op, taskId, rev) }
                persistAuthRecovery(c)
                when (val r = result) {
                    is QueueClient.Act.Ok -> {
                        // Server xoá responses/<id>.md khi retry; giữ cache là dialog hiện
                        // câu trả lời CŨ sau khi task chạy lại xong (audit 4/9 #12).
                        if (taskId != null && action.op.lowercase() in setOf("retry", "del", "delete", "rm")) {
                            responseCache.remove(taskId)
                        }
                        _ui.update { it.copy(error = null) }
                        postFeedback("", "Queue updated", QueueFeedbackTone.Success)
                        refreshOnce()
                    }
                    is QueueClient.Act.Conflict -> {
                        postFeedback(
                            "The Queue changed in another session; latest state loaded",
                            "Queue reloaded",
                            QueueFeedbackTone.Warning,
                        )
                        refreshOnce()
                    }
                    is QueueClient.Act.Failed -> {
                        _ui.update { it.copy(needsSetup = r.needsAuth) }
                        postFeedback(r.message, "Action failed", QueueFeedbackTone.Error)
                    }
                }
            } finally {
                _ui.update { it.copy(busyOps = it.busyOps - key) }
                actionJobs.remove(key)
            }
        }
    }

    fun addTask(text: String, target: String?, mode: String? = null) {
        if (text.isBlank()) return
        val key = QueueOperationKey.ADD
        if (key in _ui.value.busyOps) return
        _ui.update { it.copy(busyOps = it.busyOps + key) }
        viewModelScope.launch {
            try {
                val c = client() ?: run {
                    _ui.update { it.copy(needsSetup = true) }
                    return@launch
                }
                val result = withContext(Dispatchers.IO) { c.add(text.trim(), target, mode) }
                persistAuthRecovery(c)
                when (val r = result) {
                    is QueueClient.Act.Ok -> {
                        _ui.update { it.copy(error = null) }
                        val agent = _ui.value.state.agents.firstOrNull { it.pane == target }
                        val destination = agent?.name?.takeIf(String::isNotBlank) ?: target ?: "agent"
                        val waiting = agent?.tone == QueueTone.Accent ||
                            agent?.tone == QueueTone.Warn || agent?.tone == QueueTone.Error
                        val queued = buildString {
                            append("Queued")
                            r.taskId?.let { append(" #$it") }
                            append(" for $destination")
                            append(if (waiting) " · waiting for the agent" else " · dispatching to the agent")
                        }
                        postFeedback("", queued, QueueFeedbackTone.Success)
                        refreshOnce()
                    }
                    is QueueClient.Act.Conflict -> {
                        postFeedback(
                            "The Queue changed in another session; submit the task again",
                            "Submit the task again",
                            QueueFeedbackTone.Warning,
                        )
                        refreshOnce()
                    }
                    is QueueClient.Act.Failed -> {
                        _ui.update { it.copy(needsSetup = r.needsAuth) }
                        postFeedback(r.message, "Could not add the task", QueueFeedbackTone.Error)
                    }
                }
            } finally {
                _ui.update { it.copy(busyOps = it.busyOps - key) }
            }
        }
    }

    /**
     * Tự dọn việc đã xong (user chốt 17/9: "tự động xoá queue message ... để không
     * cần dùng CLR nữa" — thời điểm chốt lại là KHI AGENT LÀM XONG). Mỗi vòng
     * /state mới, task nào `done` thì gửi `del` im lặng: không feedback, không
     * busyOps — đây là vệ sinh nền, không phải hành động user bấm.
     *
     * `failed`/`unknown` cố tình giữ lại: đó là việc cần người xem.
     */
    private val autoCleared = mutableSetOf<Int>()
    private val autoClearJobs = mutableMapOf<Int, Job>()

    private fun autoClearDone(state: QueueState) {
        for (task in state.tasks) {
            if (!task.isCompleted || !autoCleared.add(task.id)) continue
            if (autoClearJobs[task.id]?.isActive == true) continue
            autoClearJobs[task.id] = viewModelScope.launch {
                try {
                    val c = client() ?: return@launch
                    // `del` không nằm trong OPS_NEED_REV của qsrv (chỉ top/up) — gửi
                    // rev null để xoá trúng cả khi hàng đợi vừa nhích rev vì tin khác.
                    val rm = task.actions.firstOrNull {
                        it.op == "rm" || it.op == "del" || it.op == "delete" || it.danger
                    }
                    val rev = if (rm?.needsRev == true) state.rev else null
                    val r = withContext(Dispatchers.IO) { c.act(rm?.op ?: "del", task.id, rev) }
                    persistAuthRecovery(c)
                    when (r) {
                        // Xoá xong đọc lại state NGAY: /state cũ (còn task này) vẫn
                        // nằm trong cửa sổ so sánh rev — chờ nhịp poll sau thì hàng đã
                        // xoá còn hiện thêm một nhịp nữa.
                        is QueueClient.Act.Ok -> {
                            responseCache.remove(task.id)
                            refreshOnce()
                        }
                        // Xoá hỏng (mất mạng, 409...) thì trả id về để nhịp poll sau
                        // thử lại — không kẹt task cũ trong danh sách mãi.
                        else -> autoCleared.remove(task.id)
                    }
                } finally {
                    autoClearJobs.remove(task.id)
                }
            }
        }
    }

    /** Cỡ chữ terminal (Settings, sp) — màn CHAT dùng đúng cỡ này cho tin (user 16/9). */
    val terminalFontSize: StateFlow<Float> = settings.terminalFontSize
    val queueUrl: StateFlow<String> = settings.queueUrl
    val queueToken: StateFlow<String> = settings.queueToken

    fun saveConfig(url: String, token: String) {
        settings.setQueueUrl(url)
        settings.setQueueToken(token)
        // Task ids are only unique within one qsrv instance.
        responseCache.evictAll()
        autoCleared.clear()
        // Summary ambient phải reset cùng state: nếu không, pill/FAB vẫn hiển thị
        // số liệu của qsrv CŨ trong khoảng thời gian trước khi refreshNow() kịp về.
        _ambientSummary.value = QueueAmbientSummary.Empty
        // Feed là dữ liệu của qsrv CŨ — giữ lại là hiện tin của server khác.
        _feed.value = FeedUiState()
        _ui.update {
            it.copy(
                state = QueueState.Empty,
                error = null,
                needsSetup = false,
                everLoaded = false,
            )
        }
        refreshNow()
    }

    private val responseCache = android.util.LruCache<Int, String>(50)

    suspend fun loadTaskResponse(taskId: Int): String? {
        responseCache.get(taskId)?.let { return it }
        val c = client() ?: return null
        val response = withContext(Dispatchers.IO) {
            when (val r = c.fetchResponse(taskId)) {
                is QueueClient.FetchResponse.Success -> {
                    responseCache.put(taskId, r.markdown)
                    r.markdown
                }
                is QueueClient.FetchResponse.Failed -> null
            }
        }
        persistAuthRecovery(c)
        return response
    }

    fun showFeedback(text: String, tone: QueueFeedbackTone = QueueFeedbackTone.Info) {
        postFeedback(text, "Done", tone)
    }

    /** Chỉ xoá đúng event đã hiển thị; timeout cũ không được nuốt feedback mới. */
    fun consumeFeedback(id: Long) = _ui.update { current ->
        if (current.feedback?.id == id) current.copy(feedback = null) else current
    }

    override fun onCleared() {
        pollJob?.cancel()
        pollJob = null
        feedJob?.cancel()
        feedJob = null
        pollingMode = QueuePollingMode.Stopped
        super.onCleared()
    }

    companion object {
        /** 5s — ở Queue người ta liếc chứ không theo dõi; tab MACHINE thì 2s. */
        private const val MACHINE_POLL_MS = 5_000L
        /** Tự làm mới quota mỗi 10 phút khi Queue đang hiện (user chốt 17/9). */
        private const val QUOTA_AUTO_REFRESH_MS = 10 * 60_000L
        private const val LONGPOLL_S = 25
        /** Số tin mỗi trang màn CHAT. */
        private const val CHAT_PAGE = 50
        /** Nhãn agent mà tin từ chat đi qua hàng đợi thay vì gõ thẳng. */
        private val CHAT_QUEUE_WHEN = setOf("working", "busy", "sending", "running")
        private const val MIN_GAP_MS = 1_000L
        private const val AMBIENT_MIN_GAP_MS = 2_000L
        private const val FOREGROUND_POLL_MS = 2_000L
        private const val MAX_FOREGROUND_BACKOFF_MS = 30_000L
        private const val AMBIENT_IDLE_POLL_MS = 15_000L

        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(QueueViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return QueueViewModel(SettingsRepository.getInstance(application)) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
                }
            }
    }
}
