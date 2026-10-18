package com.jossephus.chuchu.service.agents

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
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
    private const val ALERT_ID = 4101

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
        // 13/10: bỏ FGS "watching" — dọn luôn channel thường trú cũ khỏi Settings máy.
        runCatching { manager.deleteNotificationChannel("agent_watch") }
    }

    /**
     * Bubbles có đang được hệ thống cho phép cho kohi không (switch "bubbles" ở Settings
     * đọc cái này để hiện trạng thái thật). Android 11+: quyền bong bóng là PREFERENCE
     * của người dùng — app không tự bật được, chỉ mở được màn cài đặt của hệ thống
     * (giống Messenger). canBubble() của channel phản ánh cả preference cấp app.
     */
    fun bubblesAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(CHANNEL_ALERTS) ?: return false
        return channel.canBubble()
    }

    /** Gửi 1 alert THỬ (đúng đường thật: channel + shortcut + BubbleMetadata) để user
     *  xem bong bóng có nổi không — nút "send test bubble" ở Settings. */
    fun postTestAlert(context: Context) {
        postAlert(
            context,
            AgentAlertKind.Done,
            "w9:p9",
            "kohi · test",
            "Bong bóng thử nghiệm — nếu anh thấy nó nổi ở cạnh màn hình, mọi thứ đã chạy.",
        )
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
        val person = Person.Builder()
            .setName(name)
            .setKey(shortcutId)
            .setImportant(true)
            .build()
        pushShortcut(context, pane, name, shortcutId, person)

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        // Bubble: PendingIntent trong BubbleMetadata BẮT BUỘC mutable — hệ thống
        // nhét activity options của cửa sổ bubble vào lúc nở (NMS từ chối thẳng:
        // "PendingIntents attached to bubbles must be mutable").
        val bubbleFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        // Data URI tham gia PendingIntent identity (filterEquals). Chỉ dựa vào
        // pane.hashCode() thì hai pane trùng hash sẽ mở nhầm cửa sổ.
        val bubbleIntent = Intent(context, AgentBubbleActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("kohi://agent/bubble/" + Uri.encode(pane)))
            .putExtra(EXTRA_PANE, pane)
        val bubblePending = PendingIntent.getActivity(context, pane.hashCode(), bubbleIntent, bubbleFlags)
        val contentIntent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_AGENT_CHAT)
            .setData(Uri.parse("kohi://agent/open/" + Uri.encode(pane)))
            .putExtra(EXTRA_PANE, pane)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        val contentPending = PendingIntent.getActivity(context, pane.hashCode() + 1, contentIntent, flags)

        val body = refineAlertText(text).ifBlank {
            if (kind == AgentAlertKind.NeedsYou) "Waiting for your answer" else "Finished"
        }
        // 13/10 (user: "refine thông báo tin nhắn cuối cho thanh lịch hơn"): thay title
        // "✓ XONG · name" bằng notification HỘI THOẠI — tên agent là conversation title,
        // tin cuối là một message trong khung chat (MessagingStyle), trạng thái nằm ở
        // subText nhỏ ("● NEEDS YOU" / "✓ XONG"). Gọn, đúng ngữ cảnh, và vẫn là
        // conversation notification nên bubble giữ nguyên.
        val user = Person.Builder().setName("kohi").build()
        val style = NotificationCompat.MessagingStyle(user)
            .setConversationTitle(name)
            .addMessage(body, System.currentTimeMillis(), person)
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setShortcutId(shortcutId)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setSubText(
                when (kind) {
                    AgentAlertKind.NeedsYou -> "● NEEDS YOU"
                    AgentAlertKind.Done -> "✓ XONG"
                },
            )
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
                )
                    // 17/10 (user: "bubble quá bé để dùng"): không set height thì hệ
                    // thống nở cửa sổ theo mặc định thấp — đặt 600dp (trần chỉ là
                    // chiều cao màn).
                    .setDesiredHeight(600)
                    .build(),
            )
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(pane, ALERT_ID, builder.build())
            alertedPanes += pane
        }.onFailure {
            // NMS từ chối (thiếu quyền, bubble sai luật…) — nuốt lỗi nhưng để lại
            // vết trong logcat, đừng câm như đã từng (mutable PendingIntent, 13/10).
            Log.w("AgentNotifier", "notify failed pane=$pane", it)
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
        // alertedPanes là bộ nhớ process. Process chết rồi mở lại thì set rỗng,
        // các notification cũ vẫn nằm trong shade — quét active notifications
        // để dọn, chỉ đụng channel agent_alerts.
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return@runCatching
            for (status in manager.activeNotifications) {
                if (status.notification.channelId == CHANNEL_ALERTS) {
                    manager.cancel(status.tag, status.id)
                }
            }
        }.onFailure { Log.w("AgentNotifier", "cancelAll active scan failed", it) }
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
    private fun pushShortcut(
        context: Context,
        pane: String,
        agentName: String,
        shortcutId: String,
        person: Person,
    ) {
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
