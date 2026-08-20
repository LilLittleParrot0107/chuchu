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
)

class QueueViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(QueueUiState())
    val ui: StateFlow<QueueUiState> = _ui.asStateFlow()

    private var pollJob: Job? = null
    private val actionJobs = mutableMapOf<String, Job>()

    private fun client(): QueueClient? {
        val url = settings.queueUrl.value
        val token = settings.queueToken.value
        if (url.isBlank() || token.isBlank()) return null
        return QueueClient(url, token)
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

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /** Trả về true nếu lần đọc này hỏng (để phía gọi giãn nhịp poll ra). */
    private suspend fun refreshOnce(): Boolean {
        val c = client() ?: run {
            _ui.update { it.copy(needsSetup = true, loading = false, error = null) }
            return true
        }
        val since = _ui.value.state.rev.takeIf { _ui.value.everLoaded }
        _ui.update { it.copy(loading = !it.everLoaded, needsSetup = false) }

        return when (val r = withContext(Dispatchers.IO) { c.fetch(since) }) {
            is QueueClient.Fetch.Fresh -> {
                _ui.update {
                    it.copy(state = r.state, loading = false, everLoaded = true, error = null)
                }
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

    fun addTask(text: String, target: String?) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val c = client() ?: run {
                _ui.update { it.copy(needsSetup = true) }
                return@launch
            }
            when (val r = withContext(Dispatchers.IO) { c.add(text.trim(), target) }) {
                is QueueClient.Act.Ok -> {
                    _ui.update { it.copy(toast = r.note.ifBlank { "Đã thêm" }, error = null) }
                    refreshOnce()
                }
                is QueueClient.Act.Conflict -> refreshOnce()
                is QueueClient.Act.Failed -> _ui.update {
                    it.copy(error = r.message, needsSetup = r.needsAuth)
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
                    throw IllegalArgumentException("Unknown ViewModel class: ${'$'}{modelClass.name}")
                }
            }
    }
}
