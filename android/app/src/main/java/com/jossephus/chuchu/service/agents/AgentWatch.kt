package com.jossephus.chuchu.service.agents

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.jossephus.chuchu.data.repository.SettingsRepository
import com.jossephus.chuchu.ui.screens.Queue.QueueClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Vòng trực agent bằng ALARM (13/10 — thay foreground service theo yêu cầu user:
 * "bỏ cái thông báo watching alert đi"). FGS bắt buộc phải có notification thường trú
 * theo luật Android, nên đổi sang alarm: KHÔNG thông báo thường trú; mỗi nhịp —
 * poll /state một phát (không long-poll), chấm chuyển trạng thái, nổ NEEDS YOU /
 * XONG, hẹn mốc kế. Nhịp thích ứng (17/10): có agent đang chạy/bị chặn = 15s,
 * rảnh = 60s (xem [watchIntervalMs]) — hết cảnh NEEDS YOU về trễ nửa phút. Máy
 * ngủ sâu thì Android bóp alarm xuống ~9' một nhịp (luật doze) — chấp nhận đánh
 * đổi, còn hơn notification thường trú.
 *
 * Trạng thái trực (nhãn từng pane + rev) lưu SharedPreferences: process bị dọn giữa
 * phiên thì nhịp sau ĐỌC LẠI và so tiếp — không seed lại (seed lại = nuốt mất chuyển
 * trạng thái đã xảy ra lúc vắng mặt). ON_STOP xoá state = mở phiên mới sạch.
 */
object AgentWatch {
    private const val PREFS = "agent_watch"
    private const val KEY_REV = "rev"
    private const val KEY_SNAP = "snap"

    fun start(context: Context) {
        val app = context.applicationContext
        val settings = SettingsRepository.getInstance(app)
        if (settings.queueUrl.value.isBlank()) return
        if (!settings.agentAlertsEnabled.value) return
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        // Phiên mới: xoá state cũ để nhịp đầu seed im lặng (trạng thái lúc này user vừa nhìn).
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        // Dọn channel "agent_watch" cũ (thời FGS) ngay khi vào phiên — khỏi đợi alert đầu.
        AgentNotifier.ensureChannels(app)
        schedule(app, 0)
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        runCatching { alarmManager(app)?.cancel(pollIntent(app)) }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** Một nhịp: poll → báo → hẹn nhịp kế. Chạy trên thread nền của receiver. */
    fun poll(context: Context) {
        val app = context.applicationContext
        val settings = SettingsRepository.getInstance(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Hẹn nhịp KẾ trước khi poll: goAsync chỉ cho ~10s, poll bị hệ thống cắt giữa
        // đường (mạng chậm) cũng không làm đứt chuỗi alarm. Nhịp lấy từ snapshot LƯU
        // (17/10): có agent đang chạy/bị chặn → 15s thay vì 60s — hết cảnh "trễ
        // thông báo nửa phút".
        schedule(app, watchIntervalMs(parseSnaps(prefs.getString(KEY_SNAP, null) ?: "[]")))
        if (settings.queueUrl.value.isBlank() || !settings.agentAlertsEnabled.value ||
            !NotificationManagerCompat.from(app).areNotificationsEnabled()
        ) {
            stop(app)
            return
        }
        val tracker = AgentTransitionTracker()
        prefs.getString(KEY_SNAP, null)?.let { saved ->
            runCatching { tracker.restore(parseSnaps(saved)) }
        }
        val client = QueueClient(settings.queueUrl.value, settings.queueToken.value)
        when (val fetch = client.fetch(prefs.getString(KEY_REV, null), waitSec = 0)) {
            is QueueClient.Fetch.Fresh -> {
                if (client.recoveredWithoutToken && settings.queueToken.value.isNotBlank()) {
                    settings.setQueueToken("")
                }
                val snaps = fetch.state.agents.map {
                    AgentSnap(pane = it.pane, name = it.name, label = it.label, preview = it.preview)
                }
                for (action in tracker.onState(snaps)) {
                    when (action) {
                        is AgentAlertAction.Post -> AgentNotifier.postAlert(
                            app, action.kind, action.pane, action.agentName, action.text,
                        )
                        is AgentAlertAction.Cancel -> AgentNotifier.cancel(app, action.pane)
                    }
                }
                prefs.edit()
                    .putString(KEY_REV, fetch.state.rev)
                    .putString(KEY_SNAP, snapsJson(snaps))
                    .apply()
                // Đổi alarm đang treo theo trạng thái MỚI (FLAG_UPDATE_CURRENT thay
                // thế — không đứt chuỗi): vừa thấy agent chạy → 15s; về rảnh → 60s.
                schedule(app, watchIntervalMs(snaps))
            }
            QueueClient.Fetch.Unchanged -> Unit
            is QueueClient.Fetch.Failed -> Unit
        }
    }

    private fun alarmManager(context: Context): AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    private fun pollIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        7,
        Intent(context, AgentWatchReceiver::class.java).setAction(AgentWatchReceiver.ACTION_POLL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun schedule(context: Context, delayMs: Long) {
        val am = alarmManager(context) ?: return
        val at = System.currentTimeMillis() + delayMs
        val pi = pollIntent(context)
        runCatching {
            // API 31+: chưa được cấp quyền exact-alarm thì lùi về inexact (máy idle có thể
            // trễ vài phút); Settings có nút "allow alarms" để xin quyền. <31 exact luôn sẵn.
            val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    private fun snapsJson(snaps: List<AgentSnap>): String {
        val arr = JSONArray()
        for (s in snaps) {
            arr.put(
                JSONObject()
                    .put("pane", s.pane)
                    .put("name", s.name)
                    .put("label", s.label)
                    .put("preview", s.preview),
            )
        }
        return arr.toString()
    }

    private fun parseSnaps(json: String): List<AgentSnap> {
        val arr = JSONArray(json)
        val out = ArrayList<AgentSnap>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pane = o.optString("pane")
            if (pane.isBlank()) continue
            out += AgentSnap(
                pane = pane,
                name = o.optString("name"),
                label = o.optString("label"),
                preview = o.optString("preview"),
            )
        }
        return out
    }
}

/** Nhận alarm trực: chạy một nhịp poll trên thread nền (goAsync — ngân sách ~10s). */
class AgentWatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_POLL) return
        val pending = goAsync()
        Thread {
            try {
                AgentWatch.poll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_POLL = "com.jossephus.chuchu.action.AGENT_WATCH_POLL"
    }
}
