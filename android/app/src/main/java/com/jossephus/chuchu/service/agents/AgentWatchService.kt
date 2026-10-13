package com.jossephus.chuchu.service.agents

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.jossephus.chuchu.data.repository.SettingsRepository
import com.jossephus.chuchu.ui.screens.Queue.QueueClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Trực agent khi app xuống nền: long-poll `/state` (wait=25s, rẻ như vòng poll
 * của tab Queue) và nổi NEEDS YOU / XONG qua [AgentNotifier].
 *
 * Vòng đời gắn với ON_STOP/ON_START của MainActivity: app còn nhìn thấy thì
 * không cần service (UI đã hiện trạng thái), app xuống nền mới trực. Chỉ chạy khi
 * đã có quyền thông báo + đã cấu hình Queue URL — service không thông báo được
 * là service vô nghĩa.
 *
 * Không NotificationManager.IMPORTANCE… bậc cao cho kênh trực: đây là foreground
 * service theo specialUse như SessionForegroundService (cùng cặp permission
 * FOREGROUND_SERVICE + FOREGROUND_SERVICE_SPECIAL_USE đã khai trong manifest).
 */
class AgentWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        AgentNotifier.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Bắt đầu trực TRƯỚC tiếng ngủ: START_STICKY để hệ thống dựng lại nếu bị
        // thu hồi RAM — lúc đó onStartCommand(intent=null) chạy lại nhánh này.
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        ServiceCompat.startForeground(
            this,
            AgentNotifier.WATCH_NOTIFICATION_ID,
            AgentNotifier.buildWatchNotification(this),
            type,
        )
        if (watchJob?.isActive != true) {
            watchJob = scope.launch { watchLoop() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun watchLoop() {
        val settings = SettingsRepository.getInstance(applicationContext)
        val tracker = AgentTransitionTracker()
        var client: QueueClient? = null
        var clientUrl = ""
        var clientToken = ""
        var rev: String? = null
        var failures = 0
        while (currentCoroutineContext().isActive) {
            // User tắt quyền thông báo / tắt switch agent alerts giữa chừng: vòng trực
            // thành vô nghĩa (switch nằm trong app nên thường không xảy ra khi đang nền —
            // đây là lưới an toàn, vd khôi phục backup lúc đang chạy).
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled() ||
                !settings.agentAlertsEnabled.value
            ) {
                stopSelf()
                return
            }
            val url = settings.queueUrl.value
            if (url.isBlank()) {
                delay(IDLE_RETRY_MS)
                continue
            }
            val token = settings.queueToken.value
            if (client == null || url != clientUrl || token != clientToken) {
                clientUrl = url
                clientToken = token
                client = QueueClient(url, token)
                rev = null
                tracker.reset()
            }
            val c = client ?: continue
            when (val fetch = c.fetch(rev, waitSec = LONGPOLL_S)) {
                is QueueClient.Fetch.Fresh -> {
                    failures = 0
                    if (fetch.state.rev.isNotBlank()) rev = fetch.state.rev
                    if (c.recoveredWithoutToken && settings.queueToken.value.isNotBlank()) {
                        settings.setQueueToken("")
                    }
                    val snaps = fetch.state.agents.map {
                        AgentSnap(pane = it.pane, name = it.name, label = it.label, preview = it.preview)
                    }
                    for (action in tracker.onState(snaps)) {
                        when (action) {
                            is AgentAlertAction.Post -> AgentNotifier.postAlert(
                                this,
                                action.kind,
                                action.pane,
                                action.agentName,
                                action.text,
                            )
                            is AgentAlertAction.Cancel -> AgentNotifier.cancel(this, action.pane)
                        }
                    }
                }
                QueueClient.Fetch.Unchanged -> failures = 0
                is QueueClient.Fetch.Failed -> {
                    failures++
                    // Mất Tailscale/ngủ sâu: giãn nhịp dần tới 60s, có mạng lại là về ngay.
                    delay((failures * 5_000L).coerceAtMost(MAX_BACKOFF_MS))
                }
            }
        }
    }

    companion object {
        private const val LONGPOLL_S = 25
        private const val IDLE_RETRY_MS = 30_000L
        private const val MAX_BACKOFF_MS = 60_000L

        fun startIfEnabled(context: Context) {
            val app = context.applicationContext
            val settings = SettingsRepository.getInstance(app)
            if (settings.queueUrl.value.isBlank()) return
            if (!settings.agentAlertsEnabled.value) return
            if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
            val intent = Intent(app, AgentWatchService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            runCatching { app.stopService(Intent(app, AgentWatchService::class.java)) }
        }
    }
}
