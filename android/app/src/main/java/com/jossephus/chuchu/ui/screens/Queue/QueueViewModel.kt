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
    val toast: String? = null,
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
    private val actionJobs = mutableMapOf<String, Job>()

    private fun client(): QueueClient? {
        val url = settings.queueUrl.value
        if (url.isBlank()) return null
        return QueueClient(url, settings.queueToken.value)
    }

    /** Gọi khi màn hình hiện ra. Huỷ bằng [stopPolling] khi màn hình khuất. */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            var backoff = IDLE_POLL_MS
            while (isActive) {
                val slept = refreshOnce()
                backoff = if (slept) minOf(backoff * 2, MAX_BACKOFF_MS) else IDLE_POLL_MS
                delay(backoff)
            }
        }
    }

    /** Polling thích ứng tiết kiệm pin cho Terminal/AccessoryBar. */
    fun startAmbientPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                val failed = refreshOnce()
                val summary = _ambientSummary.value
                val nextDelay = when {
                    failed -> 15_000L
                    summary.isAnyWorking || summary.isAnyBlocked -> 3_500L
                    summary.totalActive > 0 -> 8_000L
                    else -> 15_000L
                }
                delay(nextDelay)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /** Trả về true nếu lần đọc này hỏng (để phía gọi giãn nhịp poll ra). */
    private suspend fun refreshOnce(): Boolean {
        val c = client() ?: run {
            _ui.update { it.copy(needsSetup = true, loading = false, error = null) }
            _ambientSummary.value = QueueAmbientSummary.from(_ui.value.state, null)
            return true
        }
        val since = _ui.value.state.rev.takeIf { _ui.value.everLoaded }
        _ui.update { it.copy(loading = !it.everLoaded) }

        return when (val r = withContext(Dispatchers.IO) { c.fetch(since) }) {
            is QueueClient.Fetch.Fresh -> {
                _ui.update {
                    it.copy(state = r.state, loading = false, everLoaded = true,
                            error = null, needsSetup = false)
                }
                _ambientSummary.value = QueueAmbientSummary.from(r.state, null)
                false
            }
            QueueClient.Fetch.Unchanged -> {
                _ui.update { it.copy(loading = false, error = null) }
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
        viewModelScope.launch { refreshOnce() }
    }


    /**
     * Chạy một thao tác trên task. Một khoá = một job: bấm nhanh hai lần vào
     * cùng nút thì lần sau thay lần trước, không xếp chồng hai request.
     */
    fun runAction(action: QueueAction, taskId: Int?) {
        val key = "${action.op}:${taskId ?: "-"}"
        actionJobs[key]?.cancel()
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
                when (val r = withContext(Dispatchers.IO) { c.act(action.op, taskId, rev) }) {
                    is QueueClient.Act.Ok -> {
                        _ui.update { it.copy(toast = r.note.ifBlank { null }, error = null) }
                        refreshOnce()
                    }
                    is QueueClient.Act.Conflict -> {
                        _ui.update { it.copy(toast = "Hàng đợi vừa đổi — đã tải lại") }
                        refreshOnce()
                    }
                    is QueueClient.Act.Failed -> _ui.update {
                        it.copy(error = r.message, needsSetup = r.needsAuth)
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
        viewModelScope.launch {
            val c = client() ?: run {
                _ui.update { it.copy(needsSetup = true) }
                return@launch
            }
            when (val r = withContext(Dispatchers.IO) { c.add(text.trim(), target, mode) }) {
                is QueueClient.Act.Ok -> {
                    _ui.update { it.copy(toast = r.note.ifBlank { "Đã thêm việc" }, error = null) }
                    refreshOnce()
                }
                is QueueClient.Act.Conflict -> refreshOnce()
                is QueueClient.Act.Failed -> _ui.update {
                    it.copy(error = r.message, needsSetup = r.needsAuth)
                }
            }
        }
    }

    /** Xoá sạch toàn bộ các việc đã xong (hoàn tất) của agent hoặc tất cả */
    fun clearDoneTasks(targetPane: String? = null) {
        viewModelScope.launch {
            val c = client() ?: return@launch
            val doneTasks = _ui.value.state.tasks.filter {
                (it.state.equals("done", ignoreCase = true) || it.state.equals("completed", ignoreCase = true)) &&
                    (targetPane == null || it.target == targetPane)
            }
            if (doneTasks.isEmpty()) {
                _ui.update { it.copy(toast = "Không có tác vụ đã xong để dọn dẹp") }
                return@launch
            }
            _ui.update { it.copy(toast = "Đang dọn ${doneTasks.size} tác vụ đã xong…") }
            withContext(Dispatchers.IO) {
                for (task in doneTasks) {
                    val rmOp = task.actions.firstOrNull {
                        it.op == "rm" || it.op == "del" || it.op == "delete" || it.danger
                    }?.op ?: "rm"
                    c.act(rmOp, task.id, null)
                }
            }
            _ui.update { it.copy(toast = "Đã dọn dẹp ${doneTasks.size} tác vụ đã xong", error = null) }
            refreshOnce()
        }
    }

    fun fetchLogs(n: Int = 60) {
        viewModelScope.launch {
            _ui.update { it.copy(logsLoading = true, logsError = null) }
            val c = client() ?: run {
                _ui.update { it.copy(logsLoading = false, logsError = "Chưa cấu hình qsrv URL") }
                return@launch
            }
            when (val r = withContext(Dispatchers.IO) { c.fetchLogs(n) }) {
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
        _ui.update { it.copy(error = null, needsSetup = false, everLoaded = false) }
        refreshNow()
    }

    private val responseCache = mutableMapOf<Int, String>()

    suspend fun loadTaskResponse(taskId: Int): String? {
        responseCache[taskId]?.let { return it }
        val c = client() ?: return null
        return withContext(Dispatchers.IO) {
            when (val r = c.fetchResponse(taskId)) {
                is QueueClient.FetchResponse.Success -> {
                    responseCache[taskId] = r.markdown
                    r.markdown
                }
                is QueueClient.FetchResponse.Failed -> null
            }
        }
    }

    fun consumeToast() = _ui.update { it.copy(toast = null) }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    companion object {
        private const val IDLE_POLL_MS = 2_000L
        private const val MAX_BACKOFF_MS = 30_000L

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
