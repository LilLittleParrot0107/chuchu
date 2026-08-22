package com.jossephus.chuchu.testing

import java.io.BufferedInputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

data class RecordedHttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
)

data class StubHttpResponse(
    val code: Int,
    val body: String = "",
    val headers: Map<String, String> = emptyMap(),
)

/** Minimal dependency-free HTTP/1.1 server for JVM client contract tests. */
class LoopbackHttpServer(
    private val handler: (RecordedHttpRequest) -> StubHttpResponse,
) : Closeable {
    private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newSingleThreadExecutor()
    val baseUrl = "http://127.0.0.1:${socket.localPort}"

    init {
        executor.execute {
            while (!socket.isClosed) {
                try {
                    socket.accept().use(::serve)
                } catch (_: SocketException) {
                    // Closing the server socket is how a test stops the loop.
                }
            }
        }
    }

    private fun serve(client: Socket) {
        val input = BufferedInputStream(client.getInputStream())
        val requestParts = readAsciiLine(input).split(' ', limit = 3)
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readAsciiLine(input)
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase()] =
                    line.substring(separator + 1).trim()
            }
        }
        val bodyBytes = ByteArray(headers["content-length"]?.toIntOrNull() ?: 0)
        var offset = 0
        while (offset < bodyBytes.size) {
            val count = input.read(bodyBytes, offset, bodyBytes.size - offset)
            if (count < 0) break
            offset += count
        }
        val response = handler(
            RecordedHttpRequest(
                method = requestParts.getOrElse(0) { "" },
                path = requestParts.getOrElse(1) { "" },
                headers = headers,
                body = bodyBytes.copyOf(offset).toString(StandardCharsets.UTF_8),
            ),
        )
        writeResponse(client, response)
    }

    private fun writeResponse(client: Socket, response: StubHttpResponse) {
        val responseBytes = response.body.toByteArray(StandardCharsets.UTF_8)
        val reason = when (response.code) {
            200 -> "OK"
            304 -> "Not Modified"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Response"
        }
        client.getOutputStream().use { output ->
            val headers = buildString {
                append("HTTP/1.1 ${response.code} $reason\r\n")
                append("Content-Type: application/json\r\n")
                response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                append("Content-Length: ${responseBytes.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(headers.toByteArray(StandardCharsets.US_ASCII))
            output.write(responseBytes)
            output.flush()
        }
    }

    override fun close() {
        socket.close()
        executor.shutdownNow()
    }

    private fun readAsciiLine(input: BufferedInputStream): String {
        val bytes = ArrayList<Byte>()
        while (true) {
            val next = input.read()
            if (next < 0 || next == '\n'.code) break
            if (next != '\r'.code) bytes += next.toByte()
        }
        return bytes.toByteArray().toString(StandardCharsets.US_ASCII)
    }
}
