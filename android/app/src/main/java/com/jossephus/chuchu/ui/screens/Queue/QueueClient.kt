package com.jossephus.chuchu.ui.screens.Queue

import com.jossephus.chuchu.data.network.normalizeQueueBaseUrl
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.SSLException

/**
 * Client HTTP cho qsrv. Chặn luồng — gọi từ Dispatchers.IO.
 *
 * qsrv chỉ nghe loopback và ra tailnet qua `tailscale serve`, nên đường đi này
 * không bao giờ chạm internet. Vẫn gửi Bearer token vì hàng đợi này **gõ được
 * lệnh vào agent**: một node tailnet bị chiếm cũng không được phép sai khiến.
 */
class QueueClient(
    private val baseUrl: String,
    private val token: String,
    private val connectTimeoutMs: Int = 4000,
    private val readTimeoutMs: Int = 8000,
) {
    @Volatile
    private var omitConfiguredToken = token.isBlank()

    /**
     * `qsrv` accepts the authenticated Tailscale identity without a token. If
     * an old token is still stored on the phone, it deliberately returns 401
     * so a broken configuration is visible. The native app can safely recover
     * by retrying without that token: Tailscale identity is still checked by
     * the server before the request reaches any queue operation.
     */
    @Volatile
    var recoveredWithoutToken: Boolean = false
        private set

    sealed interface Fetch {
        data class Fresh(val state: QueueState) : Fetch
        /** Server báo rev không đổi (304) — giữ nguyên state đang hiển thị. */
        data object Unchanged : Fetch
        data class Failed(val message: String, val needsAuth: Boolean = false) : Fetch
    }

    sealed interface Act {
        data class Ok(val rev: String, val taskId: Int? = null) : Act
        /** Hàng đợi đã đổi từ lúc đọc — phải tải lại rồi thao tác lại. */
        data class Conflict(val rev: String) : Act
        data class Failed(val message: String, val needsAuth: Boolean = false) : Act
    }

    sealed interface FetchLogs {
        data class Success(val lines: List<String>) : FetchLogs
        data class Failed(val message: String) : FetchLogs
    }

    sealed interface FetchResponse {
        data class Success(val markdown: String) : FetchResponse
        data class Failed(val message: String) : FetchResponse
    }

    fun fetch(sinceRev: String?): Fetch {
        val q = if (sinceRev.isNullOrEmpty()) "" else
            "&since=" + URLEncoder.encode(sinceRev, "UTF-8")
        return try {
            val (code, body) = request("/state?view=app$q", null)
            when {
                code == HttpURLConnection.HTTP_NOT_MODIFIED -> Fetch.Unchanged
                code == HttpURLConnection.HTTP_OK -> {
                    val state = QueueState.parse(body)
                    if (state.rev.isBlank()) {
                        Fetch.Failed(
                            "This URL does not point to qsrv — use the base URL ending in /q",
                            needsAuth = false,
                        )
                    } else {
                        Fetch.Fresh(state)
                    }
                }
                code == HttpURLConnection.HTTP_UNAUTHORIZED ->
                    Fetch.Failed("The token is invalid or has changed", needsAuth = true)
                code == HttpURLConnection.HTTP_FORBIDDEN ->
                    Fetch.Failed("Queue access denied (403) — open Tailscale and verify the account", needsAuth = true)
                code == HttpURLConnection.HTTP_NOT_FOUND ->
                    Fetch.Failed("QSRV was not found (404) — the URL must end in /q")
                else -> Fetch.Failed("Queue server error ($code)")
            }
        } catch (e: SocketTimeoutException) {
            Fetch.Failed("Queue timed out — check Tailscale")
        } catch (e: UnknownHostException) {
            Fetch.Failed("Queue host not found — check Tailscale VPN/DNS")
        } catch (e: SSLException) {
            Fetch.Failed("Could not establish HTTPS to Queue (${e.javaClass.simpleName})")
        } catch (e: IOException) {
            Fetch.Failed(offlineMessage(e))
        } catch (e: Exception) {
            Fetch.Failed(
                "Could not read Queue data (${e.javaClass.simpleName}: ${e.localizedMessage ?: "unknown error"})",
            )
        }
    }

    fun act(op: String, id: Int?, rev: String?): Act {
        val payload = JSONObject().apply {
            put("op", op)
            if (id != null) put("id", id)
            if (!rev.isNullOrEmpty()) put("rev", rev)
        }
        return send("/act", payload)
    }

    fun add(text: String, target: String?, mode: String? = null): Act {
        val payload = JSONObject().apply {
            put("text", text)
            if (!target.isNullOrEmpty()) put("target", target)
            if (!mode.isNullOrEmpty()) put("mode", mode)
        }
        return send("/add", payload)
    }

    fun fetchLogs(n: Int = 50): FetchLogs {
        return try {
            val (code, body) = request("/log?n=$n", null)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val o = JSONObject(body)
                    val arr = o.optJSONArray("lines")
                    val list = mutableListOf<String>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            list.add(arr.optString(i))
                        }
                    }
                    FetchLogs.Success(list)
                }
                else -> FetchLogs.Failed("Could not load logs ($code)")
            }
        } catch (e: IOException) {
            FetchLogs.Failed(offlineMessage(e))
        } catch (e: Exception) {
            FetchLogs.Failed("Could not read logs")
        }
    }

    fun fetchResponse(id: Int): FetchResponse {
        return try {
            val (code, body) = request("/response?id=$id", null)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val o = JSONObject(body)
                    val md = o.optString("markdown", "")
                    FetchResponse.Success(md)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    FetchResponse.Failed("This task does not have a response yet")
                }
                else -> FetchResponse.Failed("Could not load the response ($code)")
            }
        } catch (e: IOException) {
            FetchResponse.Failed(offlineMessage(e))
        } catch (e: Exception) {
            FetchResponse.Failed("Could not read the response")
        }
    }

    private fun send(path: String, payload: JSONObject): Act = try {
        val (code, body) = request(path, payload.toString().toByteArray(Charsets.UTF_8))
        val o = runCatching { JSONObject(body) }.getOrNull()
        when (code) {
            HttpURLConnection.HTTP_OK -> Act.Ok(
                rev = o?.optString("rev").orEmpty(),
                taskId = o?.takeIf { it.has("id") }?.optInt("id"),
            )
            HttpURLConnection.HTTP_CONFLICT -> Act.Conflict(o?.optString("rev").orEmpty())
            HttpURLConnection.HTTP_UNAUTHORIZED ->
                Act.Failed("The token is invalid or has changed", needsAuth = true)
            HttpURLConnection.HTTP_FORBIDDEN ->
                Act.Failed("Access denied — the request did not come through the tailnet", needsAuth = true)
            else -> Act.Failed("Queue command failed ($code)")
        }
    } catch (e: IOException) {
        Act.Failed(offlineMessage(e))
    } catch (e: Exception) {
        Act.Failed("Could not send the command")
    }

    private fun request(path: String, body: ByteArray?): Pair<Int, String> {
        val configuredToken = token.takeUnless { omitConfiguredToken || it.isBlank() }
        val first = requestOnce(path, body, configuredToken)
        if (first.first != HttpURLConnection.HTTP_UNAUTHORIZED || configuredToken == null) {
            return first
        }

        // A 401 is produced before qsrv mutates state, so retrying POST here
        // cannot duplicate an add/action. Custom servers that require a token
        // simply return 401 again and retain the original auth failure.
        val withoutToken = requestOnce(path, body, null)
        if (withoutToken.first in 200..299 ||
            withoutToken.first == HttpURLConnection.HTTP_NOT_MODIFIED
        ) {
            omitConfiguredToken = true
            recoveredWithoutToken = true
        }
        return withoutToken
    }

    private fun requestOnce(
        path: String,
        body: ByteArray?,
        bearerToken: String?,
    ): Pair<Int, String> {
        val endpoint = normalizeQueueBaseUrl(baseUrl)
        val conn = (URL(endpoint + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            if (!bearerToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setFixedLengthStreamingMode(body.size)
            }
        }
        return try {
            if (body != null) conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            // 4xx/5xx: inputStream ném FileNotFoundException, thân lỗi ở errorStream.
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            code to text
        } finally {
            // KHONG conn.disconnect(): no dong han socket, giet keep-alive pool
            // → moi lan poll 2s la mot cu bat tay TCP/TLS moi, ton pin radio.
            // Doc het + dong stream (use{} o tren) la du de tra connection ve pool.
            runCatching { conn.errorStream?.close() }
        }
    }

    private fun offlineMessage(e: IOException): String {
        val why = e.message.orEmpty()
        return when {
            why.contains("ECONNREFUSED", true) || why.contains("refused", true) ->
                "QSRV is not running on the host"
            why.contains("timed out", true) || why.contains("timeout", true) ->
                "The host did not respond — check Tailscale"
            else -> "Could not connect to Queue (${e.javaClass.simpleName}: ${why.ifBlank { "unknown error" }})"
        }
    }
}
