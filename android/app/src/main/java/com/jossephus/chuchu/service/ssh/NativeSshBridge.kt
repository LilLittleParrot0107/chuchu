package com.jossephus.chuchu.service.ssh

class NativeSshBridge {
    companion object {
        private val loadError: Throwable? =
            runCatching { System.loadLibrary("chuchu_jni") }.exceptionOrNull()
    }

    fun isLoaded(): Boolean = loadError == null

    fun nativeStatus(): String {
        return if (loadError == null) {
            "loaded"
        } else {
            val message = loadError.message?.takeIf { it.isNotBlank() } ?: "unknown"
            "not loaded (${loadError::class.simpleName}: $message)"
        }
    }

    external fun nativeCreateSession(): Long

    external fun nativeDestroySession(handle: Long)

    external fun nativeConnect(handle: Long, host: String, port: Int, username: String): Boolean

    external fun nativeGetLastError(handle: Long): String?

    external fun nativeGetHostKey(handle: Long): ByteArray?

    external fun nativeGetHostKeyAlgorithm(handle: Long): String?

    external fun nativeAuthenticateNone(handle: Long): Boolean

    external fun nativeAuthenticatePassword(handle: Long, password: String): Boolean

    external fun nativeAuthenticatePublicKeyMemory(
        handle: Long,
        publicKeyOpenSsh: String?,
        privateKeyPem: String,
        passphrase: String?,
    ): Boolean

    external fun nativeOpenShell(
        handle: Long,
        cols: Int,
        rows: Int,
        widthPx: Int,
        heightPx: Int,
        term: String,
    ): Boolean

    external fun nativeOpenExec(handle: Long, command: String): Boolean

    external fun nativeOpenExecPty(
        handle: Long,
        command: String,
        cols: Int,
        rows: Int,
        widthPx: Int,
        heightPx: Int,
        term: String,
    ): Boolean

    external fun nativeChannelEof(handle: Long): Boolean

    external fun nativeResize(
        handle: Long,
        cols: Int,
        rows: Int,
        widthPx: Int,
        heightPx: Int,
    ): Boolean

    external fun nativeIpcExchange(handle: Long, request: ByteArray): ByteArray?

    external fun nativeSftpInit(handle: Long): Boolean

    external fun nativeSftpListDirectory(handle: Long, path: String): Array<String>?

    external fun nativeSftpRealpath(handle: Long, path: String): String?

    external fun nativeSftpOpenWrite(handle: Long, path: String): Boolean

    external fun nativeSftpWriteChunk(handle: Long, data: ByteArray): Int

    external fun nativeSftpCloseWrite(handle: Long): Boolean

    external fun nativeSftpReadFile(handle: Long, path: String, maxBytes: Int): ByteArray?

    external fun nativeSftpMkdir(handle: Long, path: String): Boolean

    external fun nativeSftpDeleteFile(handle: Long, path: String): Boolean

    external fun nativeSftpDeleteDirectory(handle: Long, path: String): Boolean

    external fun nativeClose(handle: Long)
    /** poll() trên socket + ống wake: 1 = có data, 2 = bị đánh thức, 0 = hết giờ, -1 = lỗi. */
    external fun nativeWaitReadable(handle: Long, timeoutMs: Int): Int
    /** Gọi từ bất kỳ thread nào để kéo read-loop ra khỏi nativeWaitReadable. */
    external fun nativeWake(handle: Long)

    /** Huy cu connect/handshake/auth/mo kenh dang cho tren [handle] (9/9): dat co + ghi ong wake. */
    external fun nativeAbortConnect(handle: Long)

    external fun nativeGenerateEd25519Key(comment: String, passphrase: String?): Array<String>?
}
