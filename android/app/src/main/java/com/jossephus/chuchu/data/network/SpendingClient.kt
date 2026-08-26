package com.jossephus.chuchu.data.network

import com.jossephus.chuchu.data.model.dbtop.DbtopJson
import com.jossephus.chuchu.data.model.dbtop.SpendingState
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client cho spending.json — cùng khuôn với DbtopClient: GET có điều kiện
 * ETag/If-Modified-Since qua dufs, 304 thì giữ bản cũ. File nhỏ (~2KB) nhưng
 * poll mỗi 20s nên vẫn đáng đi đường 304 thay vì tải lại.
 */
class SpendingClient(
    private val endpointUrl: String,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 5_000,
) {
    private var cachedEtag: String? = null
    private var cachedLastModified: String? = null

    sealed interface FetchResult {
        data class Fresh(val state: SpendingState) : FetchResult
        data object Unchanged : FetchResult
        data object Failed : FetchResult
    }

    fun fetch(): FetchResult {
        val target = endpointUrl.trim()
        if (target.isBlank()) return FetchResult.Failed
        return runCatching {
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
                        conn.getHeaderField("ETag")?.let { cachedEtag = it }
                        conn.getHeaderField("Last-Modified")?.let { cachedLastModified = it }
                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        FetchResult.Fresh(DbtopJson.decodeFromString(SpendingState.serializer(), body))
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
