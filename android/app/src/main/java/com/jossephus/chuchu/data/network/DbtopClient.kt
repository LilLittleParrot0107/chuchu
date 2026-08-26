package com.jossephus.chuchu.data.network

import com.jossephus.chuchu.data.model.dbtop.DataFreshness
import com.jossephus.chuchu.data.model.dbtop.DbtopJson
import com.jossephus.chuchu.data.model.dbtop.DbtopState
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Client HTTP cho dbtop (lấy file state.json qua dufs/tailscale serve).
 *
 * Route này dựa vào danh tính Tailscale và cố ý không gửi Queue Bearer token.
 * dufs diễn giải mọi Authorization header như credential của chính dufs; gửi
 * queueToken tới đây làm một request hợp lệ biến thành HTTP 401.
 *
 * Chạy blocking I/O — yêu cầu gọi từ Dispatchers.IO.
 */
class DbtopClient(
    private val endpointUrl: String,
    private val connectTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 10000,
) {
    /** Cache Header để gửi kèm các lượt tải sau */
    @Volatile
    var cachedEtag: String? = null
        private set

    @Volatile
    var cachedLastModified: String? = null
        private set

    sealed interface FetchResult {
        /** Dữ liệu mới đã được phân tích thành công */
        data class Fresh(
            val state: DbtopState,
            val freshness: DataFreshness,
            val rawJson: String,
        ) : FetchResult

        /** Dữ liệu trên server chưa thay đổi (HTTP 304) — giữ nguyên UI hiện tại */
        data object Unchanged : FetchResult

        /** Thất bại khi kết nối hoặc đọc dữ liệu */
        data class Failed(
            val message: String,
            val needsAuth: Boolean = false,
            val isNetworkError: Boolean = true,
        ) : FetchResult
    }

    /**
     * Tải trạng thái state.json mới nhất.
     *
     * @param forceRefresh Nếu true, bỏ qua ETag/If-Modified-Since để ép tải lại toàn bộ.
     */
    fun fetch(forceRefresh: Boolean = false): FetchResult {
        val targetUrl = endpointUrl.trim()
        if (targetUrl.isBlank()) {
            return FetchResult.Failed("DBTOP URL is not configured", isNetworkError = false)
        }

        return try {
            val url = URL(targetUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json, text/plain, */*")

                // Gửi điều kiện cache nếu không ép refresh
                if (!forceRefresh) {
                    this@DbtopClient.cachedEtag?.takeIf { it.isNotBlank() }?.let {
                        setRequestProperty("If-None-Match", it)
                    }
                    this@DbtopClient.cachedLastModified?.takeIf { it.isNotBlank() }?.let {
                        setRequestProperty("If-Modified-Since", it)
                    }
                }
            }

            try {
                val code = conn.responseCode

                when (code) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> {
                        FetchResult.Unchanged
                    }

                    HttpURLConnection.HTTP_OK -> {
                        // Cập nhật ETag & Last-Modified cho các lần gọi sau
                        conn.getHeaderField("ETag")?.let { cachedEtag = it }
                        conn.getHeaderField("Last-Modified")?.let { cachedLastModified = it }

                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        if (body.isBlank()) {
                            return FetchResult.Failed("The server returned an empty response (0 bytes)")
                        }

                        val parsedState = DbtopJson.decodeFromString<DbtopState>(body)
                        if (parsedState.ts <= 0L) {
                            return FetchResult.Failed(
                                "The response is not a valid dbtop snapshot (missing ts)",
                                isNetworkError = false,
                            )
                        }
                        val freshness = parsedState.freshness()

                        FetchResult.Fresh(
                            state = parsedState,
                            freshness = freshness,
                            rawJson = body,
                        )
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        FetchResult.Failed(
                            "The dbtop route requires authentication (401) — check the /home/debank/state.json URL",
                            needsAuth = true,
                        )
                    }

                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        FetchResult.Failed("Access denied (403) — check the Tailscale connection", needsAuth = true)
                    }

                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        FetchResult.Failed("state.json was not found (404) at $targetUrl")
                    }

                    else -> {
                        val errText = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        FetchResult.Failed("Server error ($code): ${errText?.take(120) ?: "No response"}")
                    }
                }
            } finally {
                // KHONG conn.disconnect(): dong han socket lam moi poll 20s mo
                // TCP/TLS moi. Dong not errorStream la du de tra ve keep-alive pool.
                runCatching { conn.errorStream?.close() }
            }
        } catch (e: SocketTimeoutException) {
            FetchResult.Failed("Connection timed out — check the host or Tailscale")
        } catch (e: ConnectException) {
            FetchResult.Failed("Could not connect — the dufs/web portal may be offline or using another port")
        } catch (e: UnknownHostException) {
            FetchResult.Failed("Tailscale host not found — check the Tailscale VPN")
        } catch (e: IOException) {
            FetchResult.Failed("Network I/O error: ${e.localizedMessage ?: "Unstable network"}")
        } catch (e: Exception) {
            FetchResult.Failed("Could not parse state.json: ${e.localizedMessage ?: "Invalid JSON"}", isNetworkError = false)
        }
    }

}
