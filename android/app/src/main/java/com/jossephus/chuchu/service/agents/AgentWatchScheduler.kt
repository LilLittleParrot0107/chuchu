package com.jossephus.chuchu.service.agents

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/** AlarmManager thuần: hẹn/huỷ nhịp poll, không biết gì về agent state. */
internal object AgentWatchScheduler {
    private const val TAG = "AgentWatchScheduler"

    fun schedule(context: Context, delayMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + delayMs
        val pi = pollIntent(context)
        // API 31+: chưa được cấp exact-alarm thì lùi về inexact; <31 exact luôn sẵn.
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }.onFailure {
            Log.w(TAG, "schedule failed (exact=$exact, delay=${delayMs}ms)", it)
        }
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(pollIntent(context)) }
    }

    private fun pollIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        7,
        Intent(context, AgentWatchReceiver::class.java).setAction(AgentWatchReceiver.ACTION_POLL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
