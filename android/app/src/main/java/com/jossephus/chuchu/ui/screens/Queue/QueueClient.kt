package com.jossephus.chuchu.ui.screens.Queue

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
    sealed interface Fetch {
        data class Fresh(val state: QueueState) : Fetch
        /** Server báo rev không đổi (304) — giữ nguyên state đang hiển thị. */
        data object Unchanged : Fetch
        data class Failed(val message: String, val needsAuth: Boolean = false) : Fetch
    }

    sealed interface Act {
        data class Ok(val note: String, val rev: String) : Act
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
                code == HttpURLConnection.HTTP_OK -> Fetch.Fresh(QueueState.parse(body))
                code == HttpURLConnection.HTTP_UNAUTHORIZED ->
                    Fetch.Failed("Token sai hoặc đã đổi", needsAuth = true)
                code == HttpURLConnection.HTTP_FORBIDDEN ->
                    Fetch.Failed("Bị từ chối — request không đi qua tailnet", needsAuth = true)
                else -> Fetch.Failed(serverMessage(body) ?: "Server trả lỗi $code")
            }
        } catch (e: IOException) {
            Fetch.Failed(offlineMessage(e))
        } catch (e: Exception) {
            Fetch.Failed("Không đọc được dữ liệu hàng đợi")
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
                else -> FetchLogs.Failed(serverMessage(body) ?: "Lỗi tải log ($code)")
            }
        } catch (e: IOException) {
            FetchLogs.Failed(offlineMessage(e))
        } catch (e: Exception) {
            FetchLogs.Failed("Không đọc được log")
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
                    FetchResponse.Failed("Chưa có kết quả cho task này")
                }
                else -> FetchResponse.Failed(serverMessage(body) ?: "Lỗi tải kết quả ($code)")
            }
        } catch (e: IOException) {
            FetchResponse.Failed(offlineMessage(e))
        } catch (e: Exception) {
            FetchResponse.Failed("Không đọc được kết quả")
        }
    }

    private fun send(path: String, payload: JSONObject): Act = try {
        val (code, body) = request(path, payload.toString().toByteArray(Charsets.UTF_8))
        val o = runCatching { JSONObject(body) }.getOrNull()
        when (code) {
            HttpURLConnection.HTTP_OK -> Act.Ok(
                note = o?.optString("note").orEmpty(),
                rev = o?.optString("rev").orEmpty(),
            )
            HttpURLConnection.HTTP_CONFLICT -> Act.Conflict(o?.optString("rev").orEmpty())
            HttpURLConnection.HTTP_UNAUTHORIZED ->
                Act.Failed("Token sai hoặc đã đổi", needsAuth = true)
            HttpURLConnection.HTTP_FORBIDDEN ->
                Act.Failed("Bị từ chối — request không đi qua tailnet", needsAuth = true)
            else -> Act.Failed(serverMessage(body) ?: "Server trả lỗi $code")
        }
    } catch (e: IOException) {
        Act.Failed(offlineMessage(e))
    } catch (e: Exception) {
        Act.Failed("Không gửi được lệnh")
    }

    private fun request(path: String, body: ByteArray?): Pair<Int, String> {
        val conn = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer $token")
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
            conn.disconnect()
        }
    }

    /** qsrv trả `{"error": "..."}`; chỉ lấy khi thực sự là chuỗi có nội dung. */
    private fun serverMessage(body: String): String? =
        runCatching { JSONObject(body).optString("error").takeIf { it.isNotBlank() } }.getOrNull()

    private fun offlineMessage(e: IOException): String {
        val why = e.message.orEmpty()
        return when {
            why.contains("ECONNREFUSED", true) || why.contains("refused", true) ->
                "qsrv không chạy trên máy chủ"
            why.contains("timed out", true) || why.contains("timeout", true) ->
                "Máy chủ không trả lời — kiểm tra Tailscale"
            else -> "Không kết nối được tới hàng đợi"
        }
    }
}
