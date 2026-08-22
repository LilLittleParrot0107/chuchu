package com.jossephus.chuchu.ui.screens.Queue

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jossephus.chuchu.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
data class QueueUiState(
    val state: QueueState = QueueState.Empty,
    val loading: Boolean = false,
    val everLoaded: Boolean = false,
    val error: String? = null,
    val needsSetup: Boolean = false,
    val busyOps: Set<String> = emptySet(),
    val feedback: QueueFeedback? = null,
    val logs: List<String> = emptyList(),
    val logsLoading: Boolean = false,
    val logsError: String? = null,
)

class QueueViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(QueueUiState())
    val ui: StateFlow<QueueUiState> = _ui.asStateFlow()

    private val _ambientSummary = MutableStateFlow(QueueAmbientSummary.Empty)
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

    /** Keep polling off while the app is paused, regardless of the visible route. */
    fun setAppActive(active: Boolean) {
        isAppActive = active
        syncPollingMode()
    }

    /** Select foreground cadence only while the Queue destination is composed. */
    fun setQueueVisible(visible: Boolean) {
        isQueueVisible = visible
        syncPollingMode()
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

    private fun launchForegroundPolling(): Job = viewModelScope.launch {
        var backoff = FOREGROUND_POLL_MS
        var firstScan = true
        while (isActive) {
            // Mỗi lần mở Queue phải lấy lại full payload. Nếu chỉ gửi `since`,
            // một state UI cũ/khuyết có thể mắc kẹt mãi sau các lượt 304.
            val failed = refreshOnce(forceFull = firstScan)
            firstScan = false
            backoff = if (failed) {
                minOf(backoff * 2, MAX_FOREGROUND_BACKOFF_MS)
            } else {
                FOREGROUND_POLL_MS
            }
            delay(backoff)
        }
    }

    /** Polling thích ứng tiết kiệm pin cho Terminal/AccessoryBar. */
    private fun launchAmbientPolling(): Job = viewModelScope.launch {
        while (isActive) {
            val failed = refreshOnce()
            val summary = _ambientSummary.value
            val nextDelay = when {
                failed -> AMBIENT_IDLE_POLL_MS
                summary.isAnyWorking || summary.isAnyBlocked -> AMBIENT_BUSY_POLL_MS
                summary.totalActive > 0 -> AMBIENT_ACTIVE_POLL_MS
                else -> AMBIENT_IDLE_POLL_MS
            }
            delay(nextDelay)
        }
    }

    /** Trả về true nếu lần đọc này hỏng (để phía gọi giãn nhịp poll ra). */
    private suspend fun refreshOnce(forceFull: Boolean = false): Boolean = refreshMutex.withLock {
        val c = client() ?: run {
            _ui.update { it.copy(needsSetup = true, loading = false, error = null) }
            _ambientSummary.value = QueueAmbientSummary.from(_ui.value.state, null)
            return@withLock true
        }
        val since = _ui.value.state.rev.takeIf { !forceFull && _ui.value.everLoaded }
        _ui.update { it.copy(loading = forceFull || !it.everLoaded) }

        val result = withContext(Dispatchers.IO) { c.fetch(since) }
        persistAuthRecovery(c)
        return@withLock when (val r = result) {
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
        }
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
                    doneTasks.map { task ->
                        val rmOp = task.actions.firstOrNull {
                            it.op == "rm" || it.op == "del" || it.op == "delete" || it.danger
                        }?.op ?: "rm"
                        c.act(rmOp, task.id, null)
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

    fun fetchLogs(n: Int = 60) {
        viewModelScope.launch {
            _ui.update { it.copy(logsLoading = true, logsError = null) }
            val c = client() ?: run {
                _ui.update { it.copy(logsLoading = false, logsError = "QSRV URL is not configured") }
                return@launch
            }
            val result = withContext(Dispatchers.IO) { c.fetchLogs(n) }
            persistAuthRecovery(c)
            when (val r = result) {
                is QueueClient.FetchLogs.Success -> {
                    _ui.update { it.copy(logs = r.lines, logsLoading = false, logsError = null) }
                }
                is QueueClient.FetchLogs.Failed -> {
                    _ui.update { it.copy(logsLoading = false, logsError = r.message) }
                }
            }
        }
    }

    val queueUrl: StateFlow<String> = settings.queueUrl
    val queueToken: StateFlow<String> = settings.queueToken

    fun saveConfig(url: String, token: String) {
        settings.setQueueUrl(url)
        settings.setQueueToken(token)
        // Task ids are only unique within one qsrv instance.
        responseCache.clear()
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

    private val responseCache = mutableMapOf<Int, String>()

    suspend fun loadTaskResponse(taskId: Int): String? {
        responseCache[taskId]?.let { return it }
        val c = client() ?: return null
        val response = withContext(Dispatchers.IO) {
            when (val r = c.fetchResponse(taskId)) {
                is QueueClient.FetchResponse.Success -> {
                    responseCache[taskId] = r.markdown
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
        private const val FOREGROUND_POLL_MS = 2_000L
        private const val MAX_FOREGROUND_BACKOFF_MS = 30_000L
        private const val AMBIENT_BUSY_POLL_MS = 3_500L
        private const val AMBIENT_ACTIVE_POLL_MS = 8_000L
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
