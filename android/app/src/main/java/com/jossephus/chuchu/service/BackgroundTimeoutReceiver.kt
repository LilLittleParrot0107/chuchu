package com.jossephus.chuchu.service

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jossephus.chuchu.data.repository.SettingsRepository
import com.jossephus.chuchu.service.terminal.TerminalSessionRepository

/**
 * Hẹn giờ "rời app N phút" bằng AlarmManager thay vì coroutine trong tiến trình.
 * Lý do (sự cố 7/9): không còn session SSH thì không có foreground service, Android
 * giết tiến trình kohi trong nền sau vài phút → delay() chết theo → DISCONNECT_VPN
 * không bao giờ được gửi, VPN treo cả đêm. Alarm sống ngoài tiến trình: hết giờ hệ
 * thống đánh thức receiver này đúng một nhịp. Mở app lại thì [cancel].
 * setAndAllowWhileIdle: không cần quyền exact alarm, doze có thể lùi vài phút — với
 * mốc 5–60 phút thì không sao.
 */
class BackgroundTimeoutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as Application
        val settings = SettingsRepository.getInstance(app)
        // Ngắt session nếu tiến trình còn sống (không thì đã chết cùng tiến trình rồi).
        runCatching { TerminalSessionRepository.getInstance(app).parkAll() }
            .onFailure { Log.w(TAG, "parkAll: ${it.message}") }
        if (settings.tailscaleFollowApp.value) TailscaleControl.disconnect(app)
        Log.i(TAG, "background timeout fired")
    }

    companion object {
        private const val TAG = "BackgroundTimeout"
        private const val ACTION = "com.jossephus.chuchu.BACKGROUND_TIMEOUT"

        private fun pending(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, BackgroundTimeoutReceiver::class.java).setAction(ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun schedule(context: Context, delayMs: Long) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val at = System.currentTimeMillis() + delayMs
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context)) }
                .onFailure { Log.w(TAG, "schedule: ${it.message}") }
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            runCatching { am.cancel(pending(context)) }
        }
    }
}
