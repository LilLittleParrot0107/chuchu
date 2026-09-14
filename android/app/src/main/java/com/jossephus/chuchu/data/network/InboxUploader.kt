package com.jossephus.chuchu.data.network

import android.net.Uri
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Day file vao inbox qua dufs — HTTP PUT tren tailnet (14/9, user chot "duong dufs luon mo").
 *
 * Vi sao khong SFTP: SFTP qua libssh2 hoi dap tung goi 32 KB qua RTT, lai chay tren
 * dung luong cua vong doc terminal; PUT la MOT luong TCP, cua so tu noi, khong can tab
 * SSH dang noi va khong chen vao terminal. Do 14/9 tren tailnet: 42 MB/s.
 *
 * Khong co tai khoan, khong co o nhap gi: tailscale serve chi cho node trong tailnet
 * (toan thiet bi cua user), dufs cho ghi DUY NHAT vao /inbox va khong cho xoa.
 * SFTP van la duong du phong khi PUT khong thanh (xem TerminalScreen).
 */
class InboxUploader(
    /** Goc URL cua inbox tren portal, vi du `https://host/home/inbox` (khong co dau / cuoi). */
    private val baseUrl: String,
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 60_000,
) {
    sealed class Result {
        object Ok : Result()
        data class Failed(val message: String) : Result()
    }

    /**
     * PUT [input] len `[baseUrl]/[fileName]`. [length] > 0 thi gui Content-Length co dinh
     * (dufs ghi thang), khong biet thi chunked. [onProgress] nhan so byte da gui, goi moi 256 KB.
     */
    fun put(fileName: String, input: InputStream, length: Long, onProgress: (Long) -> Unit = {}): Result {
        val url = "${baseUrl.trimEnd('/')}/${Uri.encode(fileName)}"
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = "PUT"
                doOutput = true
                instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/octet-stream")
                if (length > 0) setFixedLengthStreamingMode(length) else setChunkedStreamingMode(BUFFER)
            }
            try {
                conn.outputStream.use { out ->
                    val buf = ByteArray(BUFFER)
                    var sent = 0L
                    var sinceReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        sent += n; sinceReport += n
                        if (sinceReport >= REPORT_EVERY) { sinceReport = 0; onProgress(sent) }
                    }
                    out.flush()
                    onProgress(sent)
                }
                val code = conn.responseCode
                if (code in 200..299) Result.Ok
                else Result.Failed("dufs PUT $code${conn.responseMessage?.let { " $it" } ?: ""}")
            } finally {
                conn.disconnect()
            }
        } catch (e: SocketTimeoutException) {
            Result.Failed("PUT timed out — check Tailscale")
        } catch (e: IOException) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private companion object {
        const val BUFFER = 256 * 1024
        const val REPORT_EVERY = 256 * 1024L
    }
}
