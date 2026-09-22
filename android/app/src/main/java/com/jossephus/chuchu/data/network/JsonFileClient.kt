package com.jossephus.chuchu.data.network

import com.jossephus.chuchu.data.model.dbtop.DbtopJson
import kotlinx.serialization.KSerializer
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client cho một file JSON nhỏ phục vụ qua dufs (spending.json, flow.json…) — cùng khuôn với
 * DbtopClient: GET có điều kiện ETag/If-Modified-Since, 304 thì giữ bản cũ. File chỉ vài KB
 * nhưng poll mỗi 20s nên vẫn đáng đi đường 304 thay vì tải lại. Trước 22/9 là SpendingClient
 * cứng cho spending.json; thêm flow.json thì chung một lớp, truyền serializer.
 */
class JsonFileClient<T>(
    private val endpointUrl: String,
    private val serializer: KSerializer<T>,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 5_000,
) {
    private var cachedEtag: String? = null
    private var cachedLastModified: String? = null

    sealed interface FetchResult<out T> {
        data class Fresh<T>(val state: T) : FetchResult<T>
        data object Unchanged : FetchResult<Nothing>
        data object Failed : FetchResult<Nothing>
    }

    fun fetch(): FetchResult<T> {
        val target = endpointUrl.trim()
        if (target.isBlank()) return FetchResult.Failed
        return runCatching<FetchResult<T>> {
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                cachedEtag?.takeIf { it.isNotBlank() }?.let { setRequestProperty("If-None-Match", it) }
                cachedLastModified?.takeIf { it.isNotBlank() }?.let { setRequestProperty("If-Modified-Since", it) }
            }
            try {
                when (conn.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> FetchResult.Unchanged
                    HttpURLConnection.HTTP_OK -> {
                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val parsed = DbtopJson.decodeFromString(serializer, body)
                        // Ghi validator SAU khi decode xong — cùng lý do với DbtopClient.
                        conn.getHeaderField("ETag")?.let { cachedEtag = it }
                        conn.getHeaderField("Last-Modified")?.let { cachedLastModified = it }
                        FetchResult.Fresh(parsed)
                    }
                    else -> FetchResult.Failed
                }
            } finally {
                // Khong disconnect(): giu keep-alive pool (cung ly do voi
                // DbtopClient/QueueClient — bai hoc 26/8).
                runCatching { conn.errorStream?.close() }
            }
        }.getOrDefault(FetchResult.Failed)
    }
}
