package com.jossephus.chuchu.service.push

import android.app.Activity
import android.content.Context
import android.util.Log
import com.jossephus.chuchu.data.repository.SettingsRepository
import com.jossephus.chuchu.service.agents.AgentWatch
import org.json.JSONObject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * UnifiedPush cho agent alerts. Backend chọn ntfy.sh (public) — payload chỉ là
 * wakeup {rev,pane,kind}, transcript vẫn fetch từ qsrv, nên không lộ nội dung.
 *
 * qsrv giữ endpoint + khóa WebPush; app chỉ đăng ký endpoint và nhận wakeup.
 */
object KohiPush {
    private const val TAG = "KohiPush"
    const val INSTANCE = "kohi-agent-alerts"
    private const val PREFS = "kohi_push"
    private const val KEY_ENDPOINT = "endpoint"

    /**
     * VAPID public key (P-256, base64url). Private key nằm ở
     * /home/a/.config/qsrv-push.json trên host. Đổi cặp khóa phải rebuild app.
     */
    const val VAPID_PUBLIC =
        "BFoHRENTqv0O3bq3vLQSnTk0yH36mSJpBsqX8oNvt7GL8lVokka8P0kr4rV5svlcZgcigwVfg8lJ0gqIcA1A5DE"

    private val io = Executors.newSingleThreadExecutor()

    fun distributor(context: Context): String? = runCatching {
        UnifiedPush.getAckDistributor(context) ?: UnifiedPush.getSavedDistributor(context)
    }.getOrNull()

    fun endpoint(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENDPOINT, null)

    /** Có distributor đã chọn thì đăng ký lại (idempotent). */
    fun ensureRegistered(context: Context): Boolean {
        val distributor = distributor(context) ?: return false
        return runCatching {
            UnifiedPush.register(
                context.applicationContext,
                INSTANCE,
                "kohi agent alerts",
                VAPID_PUBLIC,
            )
            true
        }.onFailure { Log.w(TAG, "UnifiedPush.register failed (distributor=$distributor)", it) }
            .getOrDefault(false)
    }

    /** Mở picker hệ thống để chọn distributor (ntfy), rồi đăng ký. */
    fun pickDistributor(activity: Activity, onDone: (Boolean) -> Unit) {
        runCatching {
            UnifiedPush.tryUseCurrentOrDefaultDistributor(activity) { ok ->
                if (ok) ensureRegistered(activity)
                onDone(ok && distributor(activity) != null)
            }
        }.onFailure {
            Log.w(TAG, "tryUseCurrentOrDefaultDistributor failed", it)
            onDone(false)
        }
    }

    internal fun storeEndpoint(context: Context, endpoint: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENDPOINT, endpoint)
            .apply()
    }

    /** Gửi endpoint + khóa WebPush về qsrv. Chạy trên executor, không chặn service. */
    internal fun registerWithServer(
        context: Context,
        endpoint: PushEndpoint,
        instance: String,
    ) {
        val settings = SettingsRepository.getInstance(context)
        val base = settings.queueUrl.value.trim().trimEnd('/')
        val token = settings.queueToken.value.trim()
        if (base.isBlank()) return
        val body = JSONObject()
            .put("endpoint", endpoint.url)
            .put("pub_key", endpoint.pubKeySet?.pubKey ?: "")
            .put("auth", endpoint.pubKeySet?.auth ?: "")
            .put("instance", instance)
            .toString()
        io.execute {
            runCatching {
                val conn = (URL("$base/push/register").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5_000
                    readTimeout = 8_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                }
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                conn.disconnect()
                if (code !in 200..299) Log.w(TAG, "push register HTTP $code")
            }.onFailure { Log.w(TAG, "push register failed", it) }
        }
    }

    internal fun unregisterWithServer(context: Context, instance: String) {
        val settings = SettingsRepository.getInstance(context)
        val base = settings.queueUrl.value.trim().trimEnd('/')
        val token = settings.queueToken.value.trim()
        if (base.isBlank()) return
        io.execute {
            runCatching {
                val conn = (URL("$base/push/unregister").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5_000
                    readTimeout = 8_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                }
                conn.outputStream.use { it.write(JSONObject().put("instance", instance).toString().toByteArray()) }
                conn.responseCode
                conn.disconnect()
            }.onFailure { Log.w(TAG, "push unregister failed", it) }
        }
    }
}

/** Service do UnifiedPush connector gọi khi có endpoint/message. */
class KohiPushService : PushService() {
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        KohiPush.storeEndpoint(this, endpoint.url)
        KohiPush.registerWithServer(this, endpoint, instance)
    }

    override fun onMessage(message: PushMessage, instance: String) {
        if (!message.decrypted) Log.w("KohiPush", "push message not decrypted")
        Thread {
            runCatching { AgentWatch.onPush(applicationContext) }
                .onFailure { Log.w("KohiPush", "push tick failed", it) }
            stopSelf()
        }.start()
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.w("KohiPush", "registration failed: $reason")
    }

    override fun onUnregistered(instance: String) {
        KohiPush.storeEndpoint(this, null)
        KohiPush.unregisterWithServer(this, instance)
    }
}
