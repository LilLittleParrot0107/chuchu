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
import com.jossephus.chuchu.data.repository.SettingsRepository
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

    /** Pane đang có alert SỐNG (NEEDS YOU / XONG chưa được xử lý). */
    private val alertedPanes = ConcurrentHashMap.newKeySet<String>()

    /**
     * Pane mà notification hiện chỉ còn là CÁI NEO của cửa sổ bubble, không còn
     * là alert. Tách khỏi [alertedPanes] vì hai thứ này có vòng đời khác nhau:
     * alert hết hiệu lực ngay khi agent rời Blocked/Done, còn cửa sổ bubble thì
     * phải sống tiếp — user bấm vào bubble để trả lời chứ không phải để nó bụp tắt.
     */
    private val anchoredPanes = ConcurrentHashMap.newKeySet<String>()

    /** Nội dung alert gần nhất của từng pane, để dựng lại notification ở dạng neo. */
    private val lastAlerts = ConcurrentHashMap<String, AlertSnapshot>()

    /**
     * Khoá tuần tự hoá mọi thay đổi trạng thái alert/neo (19/10 — rà soát bubble).
     *
     * Các đường gọi đến từ ba thread khác nhau: vòng long-poll của [AgentWatch] chạy
     * trên `Dispatchers.IO`, alarm nổ trên Thread riêng của receiver, còn cửa sổ bubble
     * đóng/mở trên main thread. Không khoá thì "quyết định hạ neo" và "cửa sổ vừa đóng"
     * chen nhau: cancel kịp quyết Anchor → onBubbleClosed chạy trước khi kịp ghi sổ →
     * nó không thấy gì để dọn → notification neo nằm lại vĩnh viễn trong shade dù không
     * còn cửa sổ nào. Khoá này biến hai bước thành một khối.
     */
    private val lock = Any()

    private data class AlertSnapshot(
        val kind: AgentAlertKind,
        val name: String,
        val body: String,
        /** Thời điểm post alert gốc — neo giữ nguyên mốc này, không tự nhận "tin mới". */
        val at: Long,
    )

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
        val snapshot = AlertSnapshot(
            kind = kind,
            name = agentName.ifBlank { pane },
            body = refineAlertText(text).ifBlank {
                if (kind == AgentAlertKind.NeedsYou) "Waiting for your answer" else "Finished"
            },
            at = System.currentTimeMillis(),
        )
        // Alert thật: nội dung mới thay hẳn nội dung cũ, notification trở lại là alert
        // chứ không còn là cái neo im lặng nữa (bookkeeping nằm trong [show]).
        synchronized(lock) {
            lastAlerts[pane] = snapshot
            show(context, pane, snapshot, anchored = false)
        }
    }

    /**
     * Dựng (lại) notification của [pane].
     *
     * [anchored] = notification chỉ còn để giữ cửa sổ bubble: không kêu, không rung,
     * subText trung tính. Dùng khi alert vừa hết hiệu lực mà user vẫn đang mở bubble —
     * xoá notification lúc đó là SystemUI dỡ luôn cửa sổ.
     */
    private fun show(
        context: Context,
        pane: String,
        snapshot: AlertSnapshot,
        anchored: Boolean,
    ) {
        ensureChannels(context)
        val name = snapshot.name
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

        val body = snapshot.body
        // Im tiếng khi: (a) đây là neo — nó chỉ giữ cửa sổ, kêu thêm là nhiễu; (b) cửa sổ
        // bubble của CHÍNH pane này đang nở trước mặt user — nội dung vẫn phải cập nhật
        // (notification là neo, và vẫn là NEEDS YOU/XONG) nhưng user đang nhìn thẳng vào
        // hội thoại đó, kêu lên chỉ tổ giật mình. Thu gọn thành đầu bubble là kêu lại.
        val silent = anchored || BubblePresence.isVisible(pane)
        // 13/10 (user: "refine thông báo tin nhắn cuối cho thanh lịch hơn"): thay title
        // "✓ XONG · name" bằng notification HỘI THOẠI — tên agent là conversation title,
        // tin cuối là một message trong khung chat (MessagingStyle), trạng thái nằm ở
        // subText nhỏ ("● NEEDS YOU" / "✓ XONG"). Gọn, đúng ngữ cảnh, và vẫn là
        // conversation notification nên bubble giữ nguyên.
        val user = Person.Builder().setName("kohi").build()
        // Mốc thời gian: alert = bây giờ; neo = mốc của chính alert gốc. Dùng "bây giờ"
        // cho neo là mỗi lần hạ cấp, notification nhảy lên đầu shade như tin mới.
        val stamp = if (anchored) snapshot.at else System.currentTimeMillis()
        val style = NotificationCompat.MessagingStyle(user)
            .setConversationTitle(name)
            .addMessage(body, stamp, person)
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setShortcutId(shortcutId)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setSubText(
                if (anchored) {
                    "· ĐANG MỞ"
                } else {
                    when (snapshot.kind) {
                        AgentAlertKind.NeedsYou -> "● NEEDS YOU"
                        AgentAlertKind.Done -> "✓ XONG"
                    }
                },
            )
            // 19/10 (rà soát bubble): neo KHÔNG được autoCancel. Neo là thứ giữ cửa sổ
            // bubble; user bấm vào notification (hoặc action OPEN) mà nó tự huỷ thì
            // SystemUI dỡ luôn cửa sổ đang mở — đúng lỗi "bubble biến mất", chỉ khác
            // đường vào. Dọn neo là việc của [onBubbleClosed].
            .setAutoCancel(!anchored)
            // Báo thật thì phải kêu (mỗi lần báo một lần). Im khi [silent] — xem chỗ tính.
            .setOnlyAlertOnce(silent)
            .setSilent(silent)
            .setShowWhen(true)
            .setWhen(stamp)
            .setContentIntent(contentPending)
            .addAction(R.drawable.ic_notification, "OPEN", contentPending)
            // hideScreenContents cũng phải che preview ở lockscreen, không chỉ FLAG_SECURE.
            .setVisibility(
                if (SettingsRepository.getInstance(context).hideScreenContents.value) {
                    NotificationCompat.VISIBILITY_SECRET
                } else {
                    NotificationCompat.VISIBILITY_PRIVATE
                },
            )
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
            // Neo và alert loại trừ nhau: một pane hoặc đang báo, hoặc chỉ còn giữ cửa
            // sổ bubble. Bookkeeping ở đây để hai tập không bao giờ chồng nhau.
            if (anchored) {
                alertedPanes -= pane
                anchoredPanes += pane
            } else {
                anchoredPanes -= pane
                alertedPanes += pane
            }
        }.onFailure {
            // NMS từ chối (thiếu quyền, bubble sai luật…) — nuốt lỗi nhưng để lại
            // vết trong logcat, đừng câm như đã từng (mutable PendingIntent, 13/10).
            Log.w("AgentNotifier", "notify failed pane=$pane", it)
        }
    }

    /**
     * Alert của [pane] hết hiệu lực (agent rời Blocked/Done, hoặc phiên biến mất).
     *
     * Cửa sổ bubble của pane đang mở thì KHÔNG được xoá notification: notification
     * là cái neo của cửa sổ, xoá nó là SystemUI dỡ luôn bubble user đang trả lời.
     * Đây đúng là lỗi "bubble biến mất sau khi chat phản hồi lại": Blocked→Working
     * xảy ra ngay khi user vừa gửi câu trả lời, tracker bắn Cancel, notification
     * biến mất kéo theo cả cửa sổ. Thay vào đó hạ notification xuống dạng NEO im
     * lặng; cửa sổ đóng thì [onBubbleClosed] dọn nốt.
     */
    fun cancel(context: Context, pane: String) = synchronized(lock) {
        alertedPanes -= pane
        val snapshot = lastAlerts[pane]
        val bubbleOpen = BubblePresence.isOpen(pane)
        when (alertExit(bubbleOpen, notificationLive(context, pane), snapshot != null)) {
            AlertExit.Anchor -> {
                // Đọc lại cửa sổ NGAY TRONG khoá: cửa sổ có thể vừa đóng xong (onDestroy
                // trên main thread không chờ khoá này). Dựng neo lúc đó là tạo ra
                // notification "· ĐANG MỞ" không có cửa sổ nào và không ai dọn nữa.
                if (snapshot != null && BubblePresence.isOpen(pane)) {
                    show(context, pane, snapshot, anchored = true)
                } else {
                    clear(context, pane)
                }
            }
            // Neo sống tiếp nhưng không dựng lại được nội dung (process vừa restart):
            // vẫn phải nhớ để [onBubbleClosed] dọn lúc cửa sổ đóng.
            AlertExit.Leave -> anchoredPanes += pane
            AlertExit.Cancel -> clear(context, pane)
        }
    }

    /** Xoá hẳn notification của [pane] và quên mọi trạng thái của nó. */
    private fun clear(context: Context, pane: String) {
        anchoredPanes -= pane
        lastAlerts -= pane
        runCatching { NotificationManagerCompat.from(context).cancel(pane, ALERT_ID) }
    }

    /** Notification của [pane] còn sống trong hệ thống không (đã bị user vuốt mất chưa). */
    private fun notificationLive(context: Context, pane: String): Boolean = runCatching {
        NotificationManagerCompat.from(context).activeNotifications
            .any { it.tag == pane && it.id == ALERT_ID }
        // Không đọc được thì mặc định là CÒN: giữ nguyên hành vi chống mất bubble,
        // chỉ tệ hơn ở nhánh hiếm (user vuốt mất rồi mà ta vẫn dựng lại).
    }.getOrDefault(true)

    /**
     * Cửa sổ bubble của [pane] vừa đóng hẳn (gọi từ `AgentBubbleActivity.onDestroy`).
     * Notification chỉ còn là neo thì dọn luôn — hết lý do tồn tại. Còn alert thật
     * (agent vẫn đang chờ / vừa xong) thì để nguyên cho user thấy.
     */
    fun onBubbleClosed(context: Context, pane: String) = synchronized(lock) {
        if (BubblePresence.isOpen(pane)) return@synchronized
        if (!anchoredPanes.remove(pane)) return@synchronized
        lastAlerts -= pane
        runCatching { NotificationManagerCompat.from(context).cancel(pane, ALERT_ID) }
    }

    /** Dọn mọi alert của agent (user về app — UI đầy đủ đã hiện trạng thái). */
    fun cancelAll(context: Context) = synchronized(lock) {
        for (pane in alertedPanes.toList()) {
            // 19/10 (rà soát bubble): KHÔNG còn bỏ qua pane đang hiện. Bản trước miễn cho
            // pane đang nở trong bubble, nên alert user đã đọc trong đó vẫn treo
            // "● NEEDS YOU" trong shade, và không đường nào dọn nốt: đóng cửa sổ cũng
            // không, vì nó là alert chứ không phải neo. Giờ mọi pane đi qua đúng một cửa
            // [cancel]: cửa sổ còn mở thì alert bị HẠ xuống neo im lặng (không mất cửa
            // sổ), cửa sổ đã đóng thì dọn hẳn.
            cancel(context, pane)
        }
        // alertedPanes là bộ nhớ process. Process chết rồi mở lại thì set rỗng,
        // các notification cũ vẫn nằm trong shade — quét active notifications để dọn,
        // chỉ đụng channel agent_alerts. Mọi pane đều đi qua đúng một cửa [cancel]
        // để luật "cửa sổ còn mở thì giữ lại làm neo" không bị lách ở nhánh này.
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return@runCatching
            for (status in manager.activeNotifications) {
                if (status.notification.channelId != CHANNEL_ALERTS) continue
                val tag = status.tag ?: continue
                cancel(context, tag)
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
