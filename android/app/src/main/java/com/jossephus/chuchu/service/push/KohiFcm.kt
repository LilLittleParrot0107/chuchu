package com.jossephus.chuchu.service.push

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jossephus.chuchu.data.repository.SettingsRepository
import com.jossephus.chuchu.service.agents.AgentAlertKind
import com.jossephus.chuchu.service.agents.AgentWatch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * FCM chỉ là kênh đánh thức (giống Messenger); kohi vẫn tự dựng
 * notification/bubble qua AgentWatch + AgentNotifier.
 *
 * Data-only high-priority message {pane,kind,name,text}; app không hiển thị
 * payload trực tiếp mà chạy đúng một vòng /state để dedupe với alarm/guard.
 */
object KohiFcm {
    private const val TAG = "KohiFcm"
    const val INSTANCE = "kohi-agent-alerts"
    private const val PREFS = "kohi_fcm"
    private const val KEY_TOKEN = "token"

    private val io = Executors.newSingleThreadExecutor()

    fun token(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    /** Gọi mỗi lần watch bật/background: lấy token hiện tại và đăng ký lại qsrv. */
    fun ensureRegistered(context: Context) {
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "getToken failed", task.exception)
                    return@addOnCompleteListener
                }
                registerToken(context.applicationContext, task.result)
            }
        }.onFailure { Log.w(TAG, "FirebaseMessaging unavailable", it) }
    }

    fun registerToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, token)
            .apply()
        val settings = SettingsRepository.getInstance(context)
        val base = settings.queueUrl.value.trim().trimEnd('/')
        val queueToken = settings.queueToken.value.trim()
        if (base.isBlank() || token.isBlank()) return
        val body = JSONObject()
            .put("provider", "fcm")
            .put("token", token)
            .put("instance", INSTANCE)
            .toString()
        io.execute {
            runCatching {
                val conn = (URL("$base/push/register").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5_000
                    readTimeout = 8_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (queueToken.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer $queueToken")
                    }
                }
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                conn.disconnect()
                if (code !in 200..299) Log.w(TAG, "FCM register HTTP $code")
            }.onFailure { Log.w(TAG, "FCM register failed", it) }
        }
    }
}

class KohiFcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        KohiFcm.registerToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        Log.d("KohiFcm", "onMessageReceived data=" + d.keys)
        val pane = d["pane"].orEmpty()
        val kind = when (d["kind"]) {
            "blocked" -> AgentAlertKind.NeedsYou
            "done" -> AgentAlertKind.Done
            else -> null
        } ?: return
        if (pane.isBlank()) return
        val name = d["name"].orEmpty()
        val text = d["text"].orEmpty()
        Thread {
            runCatching {
                AgentWatch.onPush(applicationContext, pane, kind, name, text)
            }.onFailure { Log.w("KohiFcm", "push tick failed", it) }
        }.start()
    }
}
