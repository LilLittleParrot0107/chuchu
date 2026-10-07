package com.jossephus.chuchu.service

import android.content.Context
import com.jossephus.chuchu.service.ssh.TailscaleStatusChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Nút bật/tắt Tailscale bằng tay (user chốt 8/9: bỏ tự bật, chỉ một nút). Bật: gửi
 * CONNECT một lần rồi đợi tunnel ≤ [UP_WAIT_MS]; không lên thì nhảy sang app Tailscale
 * cho user bấm — CONNECT từ nền bị Android/vivo chặn im lặng là chuyện đã gặp. Tắt:
 * gửi DISCONNECT rồi đợi ≤ [DOWN_WAIT_MS]. Trạng thái đọc từ network interface.
 */
object TailscaleToggle {
    sealed class State {
        data object Down : State()
        data object Connecting : State()
        data object Disconnecting : State()
        data class Up(val address: String) : State()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<State>(State.Down)
    val state: StateFlow<State> = _state
    private var job: Job? = null

    /** Đọc lại trạng thái thật (gọi khi màn hình hiện). Không chen vào lúc đang chuyển. */
    fun refresh(context: Context) {
        if (job?.isActive == true) return
        scope.launch { _state.value = read(context) }
    }

    fun toggle(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            when (read(app)) {
                is State.Up -> {
                    _state.value = State.Disconnecting
                    TailscaleControl.disconnect(app, "button")
                    waitUntil(DOWN_WAIT_MS) { read(app) is State.Down }
                }
                else -> {
                    _state.value = State.Connecting
                    TailscaleControl.connect(app, "button")
                    val up = waitUntil(UP_WAIT_MS) { read(app) is State.Up }
                    if (!up) TailscaleControl.openApp(app)
                }
            }
            _state.value = read(app)
        }
    }

    private suspend fun waitUntil(timeoutMs: Long, cond: suspend () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(250)
            if (cond()) return true
        }
        return false
    }

    private suspend fun read(context: Context): State =
        withContext(Dispatchers.IO) {
            TailscaleStatusChecker(context).tailnetAddress()?.let { State.Up(it) } ?: State.Down
        }

    private const val UP_WAIT_MS = 4_000L   // 7/10 user: 10s lâu quá — chờ 4s rồi mở app Tailscale
    private const val DOWN_WAIT_MS = 5_000L
}
