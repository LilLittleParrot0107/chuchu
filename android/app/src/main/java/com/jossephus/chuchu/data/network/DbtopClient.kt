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
 * Chạy blocking I/O — yêu cầu gọi từ Dispatchers.IO.
 */
class DbtopClient(
    private val endpointUrl: String,
    private val authToken: String? = null,
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
            return FetchResult.Failed("Chưa cấu hình URL dbtop", isNetworkError = false)
        }

        return try {
            val url = URL(targetUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json, text/plain, */*")

                if (!authToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $authToken")
                }

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
                            return FetchResult.Failed("Server trả về nội dung rỗng (0 bytes)")
                        }

                        val parsedState = DbtopJson.decodeFromString<DbtopState>(body)
                        val freshness = parsedState.freshness()

                        FetchResult.Fresh(
                            state = parsedState,
                            freshness = freshness,
                            rawJson = body,
                        )
                    }

                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        FetchResult.Failed("Yêu cầu xác thực hoặc token không hợp lệ (401)", needsAuth = true)
                    }

                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        FetchResult.Failed("Bị từ chối (403) — kiểm tra kết nối mạng Tailscale", needsAuth = true)
                    }

                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        FetchResult.Failed("Không tìm thấy state.json (404) tại $targetUrl")
                    }

                    else -> {
                        val errText = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        FetchResult.Failed("Lỗi server ($code): ${errText?.take(120) ?: "Không có phản hồi"}")
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: SocketTimeoutException) {
            FetchResult.Failed("Quá thời gian kết nối (Timeout) — kiểm tra máy chủ hoặc Tailscale")
        } catch (e: ConnectException) {
            FetchResult.Failed("Không thể kết nối — dufs/web portal chưa bật hoặc sai cổng")
        } catch (e: UnknownHostException) {
            FetchResult.Failed("Không tìm thấy host Tailscale — kiểm tra lại VPN Tailscale")
        } catch (e: IOException) {
            FetchResult.Failed("Lỗi I/O mạng: ${e.localizedMessage ?: "Mạng chập chờn"}")
        } catch (e: Exception) {
            FetchResult.Failed("Lỗi phân tích state.json: ${e.localizedMessage ?: "Dữ liệu JSON không hợp lệ"}", isNetworkError = false)
        }
    }

    /** Xóa bộ nhớ đệm cache ETag khi cần reset */
    fun clearCache() {
        cachedEtag = null
        cachedLastModified = null
    }
}
