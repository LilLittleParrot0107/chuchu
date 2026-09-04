package com.jossephus.chuchu.ui.screens.Queue

import com.jossephus.chuchu.data.model.machine.MachineSnapshot
import com.jossephus.chuchu.data.model.machine.derive
import com.jossephus.chuchu.ui.screens.Files.MachineUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Một poller `/machine` dùng chung cho Queue lẫn Terminal (trước là hai bản chép
 * y hệt ở hai ViewModel — audit 4/9). Chạy khi: app ở FOREGROUND **và** còn ít
 * nhất một nguồn muốn số (dải Queue, nửa MACHINE tab Files, dải trên compose box).
 *
 * Trước đây rời màn mới tắt, bỏ túi thì vẫn hỏi 5s một lần; đang mở trang USAGE
 * thì server còn spawn claude 380MB mỗi 30s cho không ai (audit 4/9 P4).
 *
 * [quota] trả tham số `quota` cho request (Queue dùng "1" khi trang USAGE mở).
 */
internal class MachinePoller(
    private val scope: CoroutineScope,
    private val client: () -> QueueClient?,
    private val quota: () -> String? = { null },
    private val noClientMessage: String = "No qsrv address",
) {
    private val _state = MutableStateFlow(MachineUiState())
    val state: StateFlow<MachineUiState> = _state.asStateFlow()

    private val wanted = mutableSetOf<String>()
    private var appActive = true
    private var job: Job? = null

    @Synchronized
    fun setWanted(who: String, on: Boolean) {
        if (on) wanted += who else wanted -= who
        sync()
    }

    @Synchronized
    fun setAppActive(on: Boolean) {
        appActive = on
        sync()
    }

    private fun sync() {
        val run = appActive && wanted.isNotEmpty()
        if (!run) {
            job?.cancel(); job = null
            return
        }
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            var prev: MachineSnapshot? = null
            while (isActive) {
                val c = client()
                if (c == null) {
                    _state.value = MachineUiState(error = noClientMessage)
                } else {
                    when (val r = c.machine(quota())) {
                        is QueueClient.MachineFetch.Ok -> {
                            // Nhịp đầu chỉ có ảnh chụp, chưa có hiệu -> cpuPct null.
                            _state.value = MachineUiState(readout = derive(prev, r.snapshot), error = null)
                            prev = r.snapshot
                        }
                        is QueueClient.MachineFetch.Failed ->
                            // Giữ số cũ để dải còn cái mà hiện; tuổi thật nằm trong
                            // snapshot.ts nên UI tự làm mờ khi nguội.
                            _state.value = _state.value.copy(error = r.message)
                    }
                }
                delay(MACHINE_POLL_MS)
            }
        }
    }

    companion object {
        const val MACHINE_POLL_MS = 5_000L
    }
}
