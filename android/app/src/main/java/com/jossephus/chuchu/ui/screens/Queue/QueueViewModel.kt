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

    /**
     * Nút ⟳ trên trang USAGE: bắn một phát `quota=force` ngay, không đợi nhịp
     * poll và không đợi TTL 10 phút của script. Bỏ luôn phản hồi — số mới sẽ
     * theo nhịp poll kế tiếp về, cái cần ở đây chỉ là ĐÁ cho server làm mới.
     */
    fun requestQuotaRefresh() {
        viewModelScope.launch(Dispatchers.IO) { client()?.machine("force") }
    }

    /** Bật khi màn Queue hiện, tắt khi rời — không poll sau lưng người dùng. */
    fun setMachinePolling(active: Boolean) = machinePoller.setWanted("queue", active)

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
        val c = client() ?: run { _chat.update { it.copy(loading = false, error = "Queue chưa cấu hình") }; return true }
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
        if (portal.isBlank()) { postFeedback("", "Chưa có Web portal URL trong Settings", QueueFeedbackTone.Error); return null }
        val home = _chat.value.home.ifBlank { "/home/a" }
        _chat.update { it.copy(uploading = true) }
        try {
            val result = withContext(Dispatchers.IO) {
                val input = open() ?: return@withContext InboxUploader.Result.Failed("Không mở được file")
                input.use { InboxUploader("${portal.removeSuffix("/home")}/home/inbox").put(name, it, length) }
            }
            return when (result) {
                InboxUploader.Result.Ok -> {
                    postFeedback("", "Đã tải lên $name", QueueFeedbackTone.Success)
                    "$home/inbox/$name"
                }
                is InboxUploader.Result.Failed -> { postFeedback(result.message, "Không tải lên được", QueueFeedbackTone.Error); null }
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
        val pane = _chat.value.pane ?: return
        if (text.isBlank() || _chat.value.sending) return
        val agent = _ui.value.state.agents.firstOrNull { it.pane == pane }
        if (agent != null && agent.label.trim().lowercase() in CHAT_QUEUE_WHEN) {
            addTask(text, pane, null)
            return
        }
        _chat.update { it.copy(sending = true) }
        viewModelScope.launch {
            try {
                val c = client() ?: return@launch
                val r = withContext(Dispatchers.IO) { c.chatSend(pane, text.trim()) }
                persistAuthRecovery(c)
                when (r) {
                    is QueueClient.Act.Ok -> postFeedback("", "Đã gửi vào ${_chat.value.name}", QueueFeedbackTone.Success)
                    is QueueClient.Act.Conflict -> postFeedback("", "Gửi lại", QueueFeedbackTone.Warning)
                    is QueueClient.Act.Failed -> {
                        _ui.update { it.copy(needsSetup = r.needsAuth) }
                        postFeedback(r.message, "Không gửi được", QueueFeedbackTone.Error)
                    }
                }
            } finally {
                _chat.update { it.copy(sending = false) }
            }
        }
    }

    /** Keep polling off while the app is paused, regardless of the visible route. */
    fun setAppActive(active: Boolean) {
        isAppActive = active
        syncPollingMode()
        syncChatPolling()
        // Cả poll /machine lẫn ý muốn làm mới quota đều dừng khi app xuống nền:
        // trang USAGE còn mở trong túi quần không được kéo theo claude 380MB/30s.
        machinePoller.setAppActive(active)
        if (!active) quotaWanted = false
    }

    /** Select foreground cadence only while the Queue destination is composed. */
    fun setQueueVisible(visible: Boolean) {
        isQueueVisible = visible
        syncPollingMode()
        syncChatPolling()
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
        return refreshMutex.withLock { when (val r = result) {
            is QueueClient.Fetch.Fresh -> {
                _ui.update {
                    it.copy(state = r.state, loading = false, everLoaded = true,
                            error = null, needsSetup = false)
                }
                _ambientSummary.value = QueueAmbientSummary.from(r.state, null)
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

    /** Xoá sạch toàn bộ các việc đã xong (hoàn tất) của agent hoặc tất cả */
    fun clearDoneTasks(targetPane: String? = null) {
        val key = QueueOperationKey.clearDone(targetPane)
        if (key in _ui.value.busyOps) return
        _ui.update { it.copy(busyOps = it.busyOps + key) }
        viewModelScope.launch {
            try {
                val c = client() ?: run {
                    _ui.update { it.copy(needsSetup = true) }
                    return@launch
                }
                val doneTasks = _ui.value.state.tasks.filter {
                    it.isCompleted &&
                        (targetPane == null || it.target == targetPane)
                }
                if (doneTasks.isEmpty()) {
                    postFeedback(
                        "There are no completed tasks to clear",
                        "No tasks to clear",
                        QueueFeedbackTone.Info,
                    )
                    return@launch
                }
                val results = withContext(Dispatchers.IO) {
                    // Chụp rev một lần trước vòng lặp: gửi rev theo từng task khi op
                    // yêu cầu (như runAction ở trên làm). Trước đây luôn truyền null,
                    // qsrv trả 409 cho cả lô nếu phiên khác vừa động vào hàng đợi —
                    // và lỗi đó bị nuốt vào cột "N/M failed" không ai biết vì sao.
                    val currentRev = _ui.value.state.rev
                    doneTasks.map { task ->
                        val rmAction = task.actions.firstOrNull {
                            it.op == "rm" || it.op == "del" || it.op == "delete" || it.danger
                        }
                        val rev = if (rmAction?.needsRev == true) currentRev else null
                        c.act(rmAction?.op ?: "rm", task.id, rev)
                    }
                }
                persistAuthRecovery(c)
                val removed = results.count { it is QueueClient.Act.Ok }
                refreshOnce()
                if (removed == doneTasks.size) {
                    postFeedback(
                        "Cleared $removed completed tasks",
                        "Completed tasks cleared",
                        QueueFeedbackTone.Success,
                    )
                } else {
                    postFeedback(
                        "Cleared $removed/${doneTasks.size} tasks; ${doneTasks.size - removed} failed",
                        "Queue cleanup was incomplete",
                        QueueFeedbackTone.Warning,
                    )
                }
            } finally {
                _ui.update { it.copy(busyOps = it.busyOps - key) }
            }
        }
    }

    val queueUrl: StateFlow<String> = settings.queueUrl
    val queueToken: StateFlow<String> = settings.queueToken

    fun saveConfig(url: String, token: String) {
        settings.setQueueUrl(url)
        settings.setQueueToken(token)
        // Task ids are only unique within one qsrv instance.
        responseCache.evictAll()
        // Summary ambient phải reset cùng state: nếu không, pill/FAB vẫn hiển thị
        // số liệu của qsrv CŨ trong khoảng thời gian trước khi refreshNow() kịp về.
        _ambientSummary.value = QueueAmbientSummary.Empty
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
        pollingMode = QueuePollingMode.Stopped
        super.onCleared()
    }

    companion object {
        /** 5s — ở Queue người ta liếc chứ không theo dõi; tab MACHINE thì 2s. */
        private const val MACHINE_POLL_MS = 5_000L
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
