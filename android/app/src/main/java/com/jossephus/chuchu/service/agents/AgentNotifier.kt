package com.jossephus.chuchu.service.agents

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.jossephus.chuchu.MainActivity
import com.jossephus.chuchu.R
import com.jossephus.chuchu.ui.bubble.AgentBubbleActivity
import java.util.concurrent.ConcurrentHashMap

/**
 * Thông báo cho agent (NEEDS YOU / XONG) — đường notification-bubble, vì máy user
 * (OriginOS 7) không có app-bubble kiểu Android 17: bubble ở đây là bubble GẮN
 * THÔNG BÁO (BubbleMetadata + conversation shortcut), hoạt động từ Android 10.
 *
 * Yêu cầu kỹ thuật của bubble (dính từng cái một):
 *  · shortcut phải long-lived + có Person  → push dynamic shortcut mỗi lần báo;
 *  · notification phải setShortcutId      → hệ thống mới cho phép nổi bubble;
 *  · channel phải allowBubbles (API 29) + user bật "Cho phép bong bóng" cho app.
 *
 * Tag thông báo = pane: một pane chỉ có MỘT thông báo sống (báo mới thay báo cũ),
 * huỷ theo tag không đụng thông báo của dịch vụ khác (SSH session, export…).
 */
object AgentNotifier {
    const val EXTRA_PANE = "com.jossephus.chuchu.extra.AGENT_PANE"

    private const val CHANNEL_ALERTS = "agent_alerts"
    private const val CHANNEL_WATCH = "agent_watch"
    private const val ALERT_ID = 4101
    const val WATCH_NOTIFICATION_ID = 3003

    private val alertedPanes = ConcurrentHashMap.newKeySet<String>()

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ALERTS) == null) {
            val channel = NotificationChannel(
                CHANNEL_ALERTS,
                "Agent alerts",
                NotificationManager.IMPORTANCE_HIGH,
            )
            channel.description = "Needs-you and finished alerts for agent sessions"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                channel.setAllowBubbles(true)
            }
            manager.createNotificationChannel(channel)
        }
        if (manager.getNotificationChannel(CHANNEL_WATCH) == null) {
            val channel = NotificationChannel(
                CHANNEL_WATCH,
                "Agent watch",
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.description = "Keeps watching agent sessions while the app is in the background"
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        }
    }

    fun postAlert(
        context: Context,
        kind: AgentAlertKind,
        pane: String,
        agentName: String,
        text: String,
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannels(context)
        val name = agentName.ifBlank { pane }
        val shortcutId = shortcutIdFor(pane)
        pushShortcut(context, pane, name, shortcutId)

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val bubbleIntent = Intent(context, AgentBubbleActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_PANE, pane)
        val bubblePending = PendingIntent.getActivity(context, pane.hashCode(), bubbleIntent, flags)
        val contentIntent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_AGENT_CHAT)
            .putExtra(EXTRA_PANE, pane)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        val contentPending = PendingIntent.getActivity(context, pane.hashCode() + 1, contentIntent, flags)

        val body = text.trim().ifBlank {
            if (kind == AgentAlertKind.NeedsYou) "Waiting for your answer" else "Finished"
        }
        val title = when (kind) {
            AgentAlertKind.NeedsYou -> "● NEEDS YOU · $name"
            AgentAlertKind.Done -> "✓ XONG · $name"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setShortcutId(shortcutId)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(contentPending)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setBubbleMetadata(
                NotificationCompat.BubbleMetadata.Builder(
                    bubblePending,
                    IconCompat.createWithResource(context, R.drawable.ic_notification),
                ).build(),
            )
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(pane, ALERT_ID, builder.build())
            alertedPanes += pane
        }
    }

    fun cancel(context: Context, pane: String) {
        alertedPanes -= pane
        runCatching { NotificationManagerCompat.from(context).cancel(pane, ALERT_ID) }
    }

    /** Dọn mọi alert của agent (user về app — UI đầy đủ đã hiện trạng thái). */
    fun cancelAll(context: Context) {
        for (pane in alertedPanes.toList()) {
            cancel(context, pane)
        }
    }

    fun buildWatchNotification(context: Context): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val tapIntent = Intent(context, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        val tapPending = PendingIntent.getActivity(context, 0, tapIntent, flags)
        return NotificationCompat.Builder(context, CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("kohi · watching agents")
            .setContentText("Alerts when an agent needs you or finishes")
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(tapPending)
            .build()
    }

    /** Dynamic shortcut id — chỉ [a-zA-Z0-9._] được phép. */
    private fun shortcutIdFor(pane: String): String =
        "agent_" + pane.map { if (it.isLetterOrDigit() || it == '_' || it == '.') it else '_' }
            .joinToString("")

    /**
     * Bubble chỉ nổi nếu notification trỏ tới một shortcut long-lived có Person
     * (Android 11+). Push trước mỗi lần notify — shortcut dynamic có thể bị hệ
     * thống cắt bớt khi app bị dọn nên không dựa vào lần push cũ.
     */
    private fun pushShortcut(context: Context, pane: String, agentName: String, shortcutId: String) {
        val person = Person.Builder()
            .setName(agentName)
            .setKey(shortcutId)
            .setImportant(true)
            .build()
        val bubbleIntent = Intent(context, AgentBubbleActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_PANE, pane)
        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(agentName.take(16))
            .setLongLabel(agentName)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(bubbleIntent)
            .setPerson(person)
            .setLongLived(true)
            .setCategories(setOf("android.shortcut.conversation"))
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
    }
}

const val ACTION_OPEN_AGENT_CHAT = "com.jossephus.chuchu.action.OPEN_AGENT_CHAT"
