package com.jossephus.chuchu.service.agents

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.jossephus.chuchu.data.repository.SettingsRepository
import com.jossephus.chuchu.ui.screens.Queue.QueueClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Vòng trực agent bằng ALARM (13/10 — thay foreground service theo yêu cầu user:
 * "bỏ cái thông báo watching alert đi"). FGS bắt buộc phải có notification thường trú
 * theo luật Android, nên đổi sang alarm: KHÔNG thông báo thường trú; mỗi nhịp —
 * poll /state một phát (không long-poll), chấm chuyển trạng thái, nổ NEEDS YOU /
 * XONG, hẹn mốc kế. Từ 18/10 alarm chỉ là LƯỚI: guard long-poll sống thì 5 phút/lần,
 * process bị dọn thì 2 phút (bận) / 5 phút (rảnh) — xem [alarmIntervalMs]. Trước đó
 * 15s/60s chạy song song long-poll, tốn pin mà vẫn trễ khi OEM bóp alarm.
 *
 * MẠCH HAI (17/10, tái hiện E2E: AOSP cũng defer alarm theo app_standby ~100s,
 * ~100s, máy OEM còn hơn — user báo "không nhận được thông báo nào"): vòng trực
 * TRONG TIẾN TRÌNH. Tiến trình app sau ON_STOP vẫn sống hàng chục phút (queue
 * long-poll tiếp tục thấy được từ log mock 17/10), nên thêm một coroutine
 * long-poll /state (wait 25s) đánh giá chuyển trạng thái ngay khi server trả —
 * KHÔNG cần alarm. Tiến trình bị hệ thống dọn thì alarm còn lại là lưới dưới.
 * Hai mạch dùng chung tracker trong "agent_watch" prefs (khoá [evalLock] để
 * restore→so→save không xen nhau) nên không post trùng.
 *
 * Trạng thái trực (nhãn từng pane + rev) lưu SharedPreferences: process bị dọn giữa
 * phiên thì nhịp sau ĐỌC LẠI và so tiếp — không seed lại (seed lại = nuốt mất chuyển
 * trạng thái đã xảy ra lúc vắng mặt). ON_STOP xoá state = mở phiên mới sạch.
 */
object AgentWatch {
    private const val TAG = "AgentWatch"
    private const val PREFS = "agent_watch"
    private const val KEY_REV = "rev"
    private const val KEY_SNAP = "snap"

    private val guardScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var guardJob: Job? = null
    private var guardFailures = 0

    /** Khoá một lượt restore→so→save tracker giữa mạch alarm và mạch trong tiến trình. */
    private val evalLock = Any()

    fun start(context: Context) {
        val app = context.applicationContext
        // Phiên mới: xoá state cũ để nhịp đầu seed im lặng (trạng thái lúc này user vừa nhìn).
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        resume(context)
    }

    /**
     * Khôi phục chuỗi alarm sau reboot / cập nhật APK mà KHÔNG xoá snapshot:
     * trạng thái đã lưu là mốc để so tiếp, xoá đi sẽ nuốt mất chuyển trạng thái
     * xảy ra trong lúc app không chạy. Settings đã persistence nên chỉ cần đọc.
     */
    fun resume(context: Context) {
        val app = context.applicationContext
        val settings = SettingsRepository.getInstance(app)
        if (settings.queueUrl.value.isBlank()) return
        if (!settings.agentAlertsEnabled.value) return
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        // Dọn channel "agent_watch" cũ (thời FGS) ngay khi vào phiên — khỏi đợi alert đầu.
        AgentNotifier.ensureChannels(app)
        // Guard long-poll bắt đầu ngay; alarm đầu chỉ để bắt trường hợp process chết yểu.
        schedule(app, WATCH_FIRST_ALARM_MS)
        startGuard(app)
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        guardJob?.cancel()
        guardJob = null
        guardFailures = 0
        runCatching { alarmManager(app)?.cancel(pollIntent(app)) }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Một nhịp: poll → báo → hẹn nhịp kế. Chạy trên thread nền của receiver. */
    fun poll(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Giữ chuỗi alarm kể cả khi tick bị hệ thống cắt giữa đường (mạng chậm).
        // 5 phút là lưới an toàn tối đa; sau tick sẽ hẹn lại theo trạng thái thật.
        schedule(app, WATCH_ALARM_MS)
        when (tick(app, waitSec = 0)) {
            is Tick.Fresh -> {
                val snaps = parseSnaps(prefs.getString(KEY_SNAP, null) ?: "[]")
                scheduleNext(app, snaps)
                if (snaps.isNotEmpty()) {
                    Log.d(TAG, "poll ok: " + snaps.joinToString { it.pane + "=" + it.label })
                }
            }
            Tick.Unchanged -> {
                val snaps = parseSnaps(prefs.getString(KEY_SNAP, null) ?: "[]")
                scheduleNext(app, snaps)
                Log.d(TAG, "poll: 304 không đổi")
            }
            // Lỗi mạng / vừa tắt alert: giữ lưới 5 phút, không đánh thức dày.
            Tick.Failed -> Log.d(TAG, "poll: fetch lỗi (chuỗi alarm vẫn giữ)")
        }
    }

    /**
     * Hẹn nhịp kế sau khi đã có snapshot mới. Guard long-poll còn sống thì alarm chỉ
     * là lưới; guard đã chết (process bị dọn) + có agent bận thì rút còn 2 phút để
     * NEEDS YOU không trễ quá lâu. Không hẹn nếu user vừa tắt alert (tick gọi stop).
     */
    private fun scheduleNext(app: Context, snaps: List<AgentSnap>) {
        val settings = SettingsRepository.getInstance(app)
        if (settings.queueUrl.value.isBlank() || !settings.agentAlertsEnabled.value) return
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        schedule(app, alarmIntervalMs(snaps, guardAlive = guardJob?.isActive == true))
    }

    private sealed interface Tick {
        /** Có trạng thái mới: đã so + đã lưu vào prefs. */
        data class Fresh(val actions: Int) : Tick
        data object Unchanged : Tick
        data object Failed : Tick
    }

    /**
     * Một lượt fetch + so chuyển trạng thái + lưu. Dùng chung cho mạch alarm
     * ([poll]) và mạch trong tiến trình ([guardLoop]) — [evalLock] chống xen.
     */
    private fun tick(context: Context, waitSec: Int): Tick {
        val settings = SettingsRepository.getInstance(context)
        if (settings.queueUrl.value.isBlank() || !settings.agentAlertsEnabled.value ||
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            stop(context)
            return Tick.Failed
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val client = QueueClient(settings.queueUrl.value, settings.queueToken.value)
        return synchronized(evalLock) {
            val since = prefs.getString(KEY_REV, null)
            when (val fetch = client.fetch(since, waitSec)) {
                is QueueClient.Fetch.Fresh -> {
                    // herdr timeout/lỗi: /state vẫn 200 nhưng agents rỗng. Coi như
                    // fetch lỗi, KHÔNG lưu rev/snap và KHÔNG cancel — nếu không
                    // một lần host chậm 3s sẽ xoá sạch NEEDS YOU/XONG.
                    if (!fetch.state.agentsError.isNullOrBlank()) {
                        Log.w(TAG, "state: agents_error=" + fetch.state.agentsError)
                        return@synchronized Tick.Failed
                    }
                    if (client.recoveredWithoutToken && settings.queueToken.value.isNotBlank()) {
                        settings.setQueueToken("")
                    }
                    val snaps = fetch.state.agents.map {
                        AgentSnap(pane = it.pane, name = it.name, label = it.label, preview = it.preview)
                    }
                    val tracker = AgentTransitionTracker()
                    prefs.getString(KEY_SNAP, null)?.let { saved ->
                        runCatching { tracker.restore(parseSnaps(saved)) }
                    }
                    val actions = tracker.onState(snaps)
                    for (action in actions) {
                        when (action) {
                            is AgentAlertAction.Post -> {
                                Log.d(TAG, "post " + action.kind + " pane=" + action.pane)
                                AgentNotifier.postAlert(
                                    context, action.kind, action.pane, action.agentName, action.text,
                                )
                            }
                            is AgentAlertAction.Cancel -> {
                                Log.d(TAG, "cancel pane=" + action.pane)
                                AgentNotifier.cancel(context, action.pane)
                            }
                        }
                    }
                    // commit() trước khi receiver finish: apply() bất đồng bộ có thể
                    // mất snapshot nếu process bị kill ngay sau khi post notification,
                    // lần sau sẽ post lại (rung lại) hoặc bỏ sót.
                    prefs.edit()
                        .putString(KEY_REV, fetch.state.rev)
                        .putString(KEY_SNAP, snapsJson(snaps))
                        .commit()
                    Tick.Fresh(actions.size)
                }
                QueueClient.Fetch.Unchanged -> Tick.Unchanged
                is QueueClient.Fetch.Failed -> Tick.Failed
            }
        }
    }

    /** Bật vòng trực trong tiến trình (mạch hai). Đang chạy thì bỏ qua. */
    private fun startGuard(app: Context) {
        if (guardJob?.isActive == true) return
        guardJob = guardScope.launch { guardLoop(app) }
    }

    /**
     * Long-poll /state liên tục (wait 25s) trong khi tiến trình sống. Server trả
     * state mới thì tick() so + post ngay; 304 thì lấp nhịp ngắn rồi lần lại;
     * lỗi mạng thì nghỉ 30s. Tiến trình chết = vòng chết — alarm còn lại gánh.
     */
    private suspend fun guardLoop(app: Context) {
        Log.d(TAG, "guard bật (mạch trong tiến trình)")
        while (true) {
            when (val t = tick(app, waitSec = 25)) {
                is Tick.Fresh -> {
                    guardFailures = 0
                    if (t.actions > 0) Log.d(TAG, "guard: " + t.actions + " hành động")
                    // State mới về liền long-poll tiếp — transition tới đâu báo tới đó.
                }
                Tick.Unchanged -> {
                    guardFailures = 0
                    delay(1_500)
                }
                Tick.Failed -> {
                    if (guardJob?.isActive != true) return
                    // Tailscale tắt / token sai / host ngủ: đừng 30s/lần mãi.
                    // 30s → 1m → 2m → 4m → 5m (cap), reset khi có state.
                    guardFailures = (guardFailures + 1).coerceAtMost(5)
                    val backoff = minOf(30_000L * (1L shl (guardFailures - 1)), 300_000L)
                    Log.d(TAG, "guard: lỗi, thử lại sau ${backoff / 1000}s")
                    delay(backoff)
                }
            }
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
        // API 31+: chưa được cấp quyền exact-alarm thì lùi về inexact (máy idle có thể
        // trễ vài phút); Settings có nút "allow alarms" để xin quyền. <31 exact luôn sẵn.
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }.onFailure {
            // OEM từ chối exact-alarm / quyền bị thu hồi: alarm trước vẫn còn, nhưng
            // phải thấy trong log thay vì im lặng mất chuỗi.
            Log.w(TAG, "schedule failed (exact=$exact, delay=${delayMs}ms)", it)
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
            } catch (e: Exception) {
                Log.w("AgentWatch", "alarm poll failed", e)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_POLL = "com.jossephus.chuchu.action.AGENT_WATCH_POLL"
    }
}

/**
 * AlarmManager mất sạch alarm sau reboot; cài APK mới cũng giết process và
 * thường xoá alarm. Không có receiver này thì alert nền chết im lặng cho tới
 * lần user mở app rồi xuống nền lại.
 */
class AgentBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        runCatching { AgentWatch.resume(context.applicationContext) }
            .onFailure { Log.w(TAG, "resume after boot/update failed", it) }
    }

    private companion object {
        const val TAG = "AgentBootReceiver"
    }
}
