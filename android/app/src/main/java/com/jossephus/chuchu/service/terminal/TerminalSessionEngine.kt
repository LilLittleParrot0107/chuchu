package com.jossephus.chuchu.service.terminal

import android.content.ClipData
import android.util.Log
import com.jossephus.chuchu.model.AuthMethod
import com.jossephus.chuchu.model.MultiplexerType
import com.jossephus.chuchu.model.Transport
import com.jossephus.chuchu.service.mosh.MoshBootstrapParser
import com.jossephus.chuchu.service.mosh.MoshEventType
import com.jossephus.chuchu.service.mosh.MoshReconnectPolicy
import com.jossephus.chuchu.service.mosh.MoshState
import com.jossephus.chuchu.service.mosh.NativeMoshService
import com.jossephus.chuchu.service.multiplexer.MultiplexerAvailability
import com.jossephus.chuchu.service.multiplexer.MultiplexerCommandResult
import com.jossephus.chuchu.service.multiplexer.MultiplexerRegistry
import com.jossephus.chuchu.service.multiplexer.MultiplexerSessionAllocator
import com.jossephus.chuchu.service.multiplexer.RemoteMultiplexerSession
import com.jossephus.chuchu.service.ssh.HostKeyCheck
import com.jossephus.chuchu.service.ssh.HostKeyStore
import com.jossephus.chuchu.service.ssh.NativeSshService
import com.jossephus.chuchu.service.ssh.TailscaleStatusChecker
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class SessionStatus {
    Disconnected,
    Connecting,
    Reconnecting,
    Connected,
    Error,
}

data class SessionState(
    val status: SessionStatus = SessionStatus.Disconnected,
    val sessionKey: String? = null,
    val snapshot: TerminalSnapshot? = null,
    val title: String? = null,
    val pwd: String? = null,
    val bellCount: Int = 0,
    val nativeVersion: String? = null,
    val reconnectAttempt: Int = 0,
    val error: String? = null,
    val handle: Long = 0,
)

data class HostKeyPrompt(
    val host: String,
    val port: Int,
    val algorithm: String,
    val fingerprint: String,
    val previousFingerprint: String?,
)

class TerminalSessionEngine(
    private val publishClipboardText: (String) -> Unit,
    private val scope: CoroutineScope,
    private val localShellService: NativeLocalShellService,
    private val hostKeyStore: HostKeyStore,
    private val tailscaleStatusChecker: TailscaleStatusChecker,
) {
    private data class ConnectionParams(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val authMethod: AuthMethod,
        val publicKeyOpenSsh: String,
        val privateKeyPem: String,
        val keyPassphrase: String,
        val transport: Transport,
        val postConnectCommand: String? = null,
        val multiplexer: MultiplexerType? = null,
        val multiplexerSessionName: String? = null,
        val multiplexerCreateIfMissing: Boolean = true,
    ) {
        fun multiplexerStartupCommand(): String? {
            val type = multiplexer ?: return null
            if (!type.runtimeSupported || transport == Transport.Mosh || transport == Transport.LocalShell) return null
            val sessionName = multiplexerSessionName?.takeIf { it.isNotBlank() } ?: return null
            val runtime = MultiplexerRegistry.forType(type) ?: return null
            return runtime.launchCommand(
                sessionName = sessionName,
                createIfMissing = multiplexerCreateIfMissing,
                trustedRemoteName = true,
            )
        }
    }

    private val dispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { r ->
                Thread(r, "terminal-session").apply { isDaemon = true }
            }
            .asCoroutineDispatcher()
    @Volatile private var disposed = false

    private val bridge = GhosttyBridge()
    private val nativeSsh = NativeSshService(hostKeyPolicy = ::verifyHostKey)
    private val moshService = NativeMoshService()

    private var handle: Long = 0L
    private var readJob: Job? = null
    private var cols = 80
    private var rows = 24
    private var screenWidth = 0
    private var screenHeight = 0
    private var cellWidth = 1
    private var cellHeight = 1
    private var lastSnapshotAtMs = 0L
    private var snapshotScheduled = false
    private val snapshotIntervalMs = 16L
    // Cổng vẽ: tab nền / màn tắt thì KHÔNG parse snapshot (6 JNI + 4 mảng mỗi chunk,
    // tới 60 lần/s mỗi tab — khoản pin lớn nhất còn lại, audit 4/9 P1). Dữ liệu
    // vẫn được đọc và bơm vào ghostty; chỉ phần dựng ảnh cho Kotlin là bỏ qua,
    // đánh dấu dirty để lúc bật lại dựng đúng một lần.
    @Volatile private var renderEnabled = true
    @Volatile private var snapshotDirty = false
    // Đánh thức read-loop ngay khi có phím gõ: vòng đọc ngủ theo idleReadDelayMs
    // (trần 64ms khi im lâu) nên echo của phím đầu tiên chờ hết delay + RTT —
    // cảm giác "gõ phát đầu hơi khựng" (audit 4/9 P6).
    private val readWake = Channel<Unit>(Channel.CONFLATED)
    private var title: String? = null
    private var pwd: String? = null
    private var images: List<ImagePlacement> = emptyList()
    private val parseScratch = TerminalSnapshot.ParseScratch()
    private val bitmapCache = TerminalSnapshot.BitmapCache()
    private var lastImageLoading = false
    private var pendingColorScheme: Int? = null
    private var pendingDefaultColors: DefaultColors? = null
    private var lastConnectionParams: ConnectionParams? = null
    private var reconnectJob: Job? = null
    private var disconnectRequested = false


    private val nativeVersion =
        if (bridge.isLoaded()) {
            runCatching { bridge.nativeVersion() }.getOrNull()
        } else {
            null
        }

    private val _state = MutableStateFlow(SessionState(nativeVersion = nativeVersion))
    val state: StateFlow<SessionState> = _state.asStateFlow()

    data class DefaultColors(
        val fg: IntArray?,
        val bg: IntArray?,
        val cursor: IntArray?,
        val palette: ByteArray?,
    )

    private val _hostKeyPrompt = MutableStateFlow<HostKeyPrompt?>(null)
    val hostKeyPrompt: StateFlow<HostKeyPrompt?> = _hostKeyPrompt.asStateFlow()
    private var hostKeyDecision: CompletableDeferred<Boolean>? = null

    fun connect(
        host: String,
        port: Int,
        username: String,
        password: String,
        authMethod: AuthMethod,
        publicKeyOpenSsh: String,
        privateKeyPem: String,
        keyPassphrase: String,
        transport: Transport,
        sessionKey: String,
        postConnectCommand: String? = null,
        multiplexer: MultiplexerType? = null,
        multiplexerSessionName: String? = null,
        multiplexerCreateIfMissing: Boolean = true,
    ) {
        disconnectRequested = false
        val params =
            ConnectionParams(
                host = host,
                port = port,
                username = username,
                password = password,
                authMethod = authMethod,
                publicKeyOpenSsh = publicKeyOpenSsh,
                privateKeyPem = privateKeyPem,
                keyPassphrase = keyPassphrase,
                transport = transport,
                postConnectCommand = postConnectCommand,
                multiplexer = multiplexer,
                multiplexerSessionName = multiplexerSessionName,
                multiplexerCreateIfMissing = multiplexerCreateIfMissing,
            )
        lastConnectionParams = params
        // Retry giua luc cu connect truoc con dang cho: huy no ngay, khong thi khoi
        // connect moi xep hang sau toi 10s poll tren dispatcher mot luong (9/9).
        nativeSsh.abortConnect()
        launchSession {
            reconnectJob?.cancel()
            reconnectJob = null
            _state.value =
                _state.value.copy(
                    status = SessionStatus.Connecting,
                    error = null,
                    reconnectAttempt = 0,
                    sessionKey = sessionKey,
                )
            if (!bridge.isLoaded()) {
                _state.value =
                    SessionState(
                        status = SessionStatus.Error,
                        sessionKey = sessionKey,
                        error =
                            "Native terminal library ${bridge.nativeStatus()}. Check ABI/NDK build.",
                    )
                return@launchSession
            }
            if (
                transport != Transport.Mosh &&
                    transport != Transport.LocalShell &&
                    !nativeSsh.isAvailable()
            ) {
                _state.value =
                    SessionState(
                        status = SessionStatus.Error,
                        sessionKey = sessionKey,
                        error = "Native SSH unavailable. Check ABI/NDK build.",
                    )
                return@launchSession
            }
            if (transport == Transport.Mosh && !moshService.isLoaded) {
                _state.value =
                    SessionState(
                        status = SessionStatus.Error,
                        sessionKey = sessionKey,
                        error = "Native mosh unavailable. Check ABI/NDK build.",
                    )
                return@launchSession
            }
            if (transport != Transport.LocalShell && username.isBlank()) {
                _state.value =
                    SessionState(
                        status = SessionStatus.Error,
                        sessionKey = sessionKey,
                        error = "Username required",
                    )
                return@launchSession
            }
            val multiplexerAvailability = checkMultiplexerAvailability(params)
            val multiplexerError = multiplexerAvailabilityErrorMessage(
                availability = multiplexerAvailability,
                selectedType = params.multiplexer,
            )
            if (multiplexerError != null) {
                _state.value =
                    SessionState(
                        status = SessionStatus.Error,
                        sessionKey = sessionKey,
                        error = multiplexerError,
                    )
                return@launchSession
            }
            try {
                establishConnection(params, username)
                _state.value =
                    _state.value.copy(
                        status = SessionStatus.Connected,
                        error = null,
                        reconnectAttempt = 0,
                        sessionKey = sessionKey,
                    )
                requestSnapshot(force = true)
                startReadLoop()
                sendStartupCommand(params)
            } catch (e: LinkageError) {
                Log.e("TerminalSession", "Connect failed", e)
                _state.value =
                    SessionState(
                        status = SessionStatus.Error,
                        sessionKey = sessionKey,
                        error = "Native terminal backend unavailable: ${e.message}",
                    )
            } catch (e: Exception) {
                Log.e("TerminalSession", "Connect failed", e)
                _state.value =
                    SessionState(
                        status = SessionStatus.Error,
                        sessionKey = sessionKey,
                        error = "${e::class.simpleName}: ${e.message}",
                    )
            }
        }
    }

    fun writeKey(key: Int, codepoint: Int, mods: Int, action: Int, utf8: String? = null) {
        launchSession {
            if (handle == 0L) return@launchSession
            val encoded =
                bridge.nativeEncodeKey(handle, key, codepoint, mods, action, utf8) ?: return@launchSession
            if (encoded.isEmpty()) return@launchSession
            try {
                writeRemote(encoded)
            } catch (_: Exception) {}
        }
    }

    /**
     * Send one key [repeat] times in a single write.
     *
     * The trackpad gesture can produce a dozen arrow presses inside one frame.
     * Sent one at a time, each became its own coroutine on this engine's single
     * thread plus its own SSH packet — the burst queued ahead of the read loop
     * and the echo arrived after the finger had already stopped. Encoding once
     * and concatenating collapses that to one packet.
     *
     * Deliberately N copies of the escape sequence rather than a parameterised
     * CUF (`ESC[nC`): readline and Ink parse the parameterised form as a
     * different key, not as n presses.
     */
    fun writeKeyRepeat(key: Int, codepoint: Int, mods: Int, action: Int, repeat: Int) {
        if (repeat <= 0) return
        launchSession {
            if (handle == 0L) return@launchSession
            val encoded =
                bridge.nativeEncodeKey(handle, key, codepoint, mods, action, null) ?: return@launchSession
            if (encoded.isEmpty()) return@launchSession
            val payload =
                if (repeat == 1) {
                    encoded
                } else {
                    ByteArray(encoded.size * repeat).also { out ->
                        for (i in 0 until repeat) {
                            encoded.copyInto(out, i * encoded.size)
                        }
                    }
                }
            try {
                writeRemote(payload)
            } catch (_: Exception) {}
        }
    }

    fun writeText(text: String) {
        launchSession {
            if (handle == 0L) return@launchSession
            if (text.isEmpty()) return@launchSession
            try {
                writeRemote(text.toByteArray(Charsets.UTF_8))
            } catch (_: Exception) {}
        }
    }

    fun writePaste(text: String) {
        launchSession {
            if (handle == 0L) return@launchSession
            if (text.isEmpty()) return@launchSession
            val encoded = bridge.nativeEncodePaste(handle, text) ?: return@launchSession
            if (encoded.isEmpty()) return@launchSession
            try {
                writeRemote(encoded)
            } catch (_: Exception) {}
        }
    }

    fun setColorScheme(isDark: Boolean) {
        val scheme = if (isDark) 1 else 0
        pendingColorScheme = scheme
        launchSession {
            if (handle == 0L) return@launchSession
            bridge.nativeSetColorScheme(handle, scheme)
        }
    }

    fun setDefaultColors(fg: IntArray?, bg: IntArray?, cursor: IntArray?, palette: ByteArray?) {
        pendingDefaultColors = DefaultColors(fg, bg, cursor, palette)
        launchSession {
            if (handle == 0L) return@launchSession
            bridge.nativeSetDefaultColors(handle, fg, bg, cursor, palette)
            requestSnapshot(force = true)
        }
    }

    fun sendFocusEvent(focused: Boolean) {
        launchSession {
            if (handle == 0L) return@launchSession
            val encoded = bridge.nativeEncodeFocus(handle, focused) ?: return@launchSession
            if (encoded.isEmpty()) return@launchSession
            try {
                writeRemote(encoded)
            } catch (_: Exception) {}
        }
    }

    fun sendMouseEvent(
        action: Int,
        button: Int,
        mods: Int,
        x: Float,
        y: Float,
        anyButtonPressed: Boolean,
        trackLastCell: Boolean,
    ) {
        launchSession {
            if (handle == 0L) return@launchSession
            val encoded =
                bridge.nativeEncodeMouse(
                    handle,
                    action,
                    button,
                    mods,
                    x,
                    y,
                    anyButtonPressed,
                    trackLastCell,
                ) ?: return@launchSession
            if (encoded.isEmpty()) return@launchSession
            try {
                writeRemote(encoded)
            } catch (_: Exception) {}
        }
    }

    fun resize(
        newCols: Int,
        newRows: Int,
        newCellWidth: Int,
        newCellHeight: Int,
        newScreenWidth: Int,
        newScreenHeight: Int,
    ) {
        launchSession {
            try {
                if (newCols <= 0 || newRows <= 0) return@launchSession
                if (newCellWidth <= 0 || newCellHeight <= 0) return@launchSession
                cols = newCols
                rows = newRows
                cellWidth = newCellWidth
                cellHeight = newCellHeight
                screenWidth = newScreenWidth
                screenHeight = newScreenHeight
                if (handle != 0L) {
                    bridge.nativeResize(handle, cols, rows, cellWidth, cellHeight)
                    // Flush pty output from the resize (e.g. the DEC 2048 in-band
                    // size report) so Textual-based TUIs detect the resize.
                    flushPtyWrites()
                    bridge.nativeSetMouseEncodingSize(
                        handle,
                        screenWidth,
                        screenHeight,
                        cellWidth,
                        cellHeight,
                        0,
                        0,
                        0,
                        0,
                    )
                    resizeRemote(cols, rows, ptyWidthPx(), ptyHeightPx())
                    requestSnapshot(force = true)
                }
            } catch (e: Exception) {
                Log.e("TerminalSession", "Resize failed", e)
                val transport = lastConnectionParams?.transport
                val shouldReconnect =
                    !disconnectRequested &&
                        lastConnectionParams != null &&
                        readLoopExitAction(transport) == ReadLoopExitAction.AutomaticReconnect
                if (shouldReconnect) {
                    scheduleReconnect("Connection interrupted: ${e.message ?: "resize failed"}")
                } else {
                    if (transport == Transport.Mosh) {
                        readJob?.cancel()
                        readJob = null
                        moshService.close()
                    }
                    _state.value =
                        _state.value.copy(
                            status = SessionStatus.Error,
                            error = "Resize failed: ${e.message}",
                        )
                }
            }
        }
    }

    fun scroll(delta: Int, x: Float, y: Float) {
        launchSession {
            if (handle == 0L || delta == 0) {
                return@launchSession
            }
            bridge.nativeScroll(handle, delta, x, y)
            flushPtyWrites()
            requestSnapshot(force = true)
        }
    }

    fun scrollToActive() {
        launchSession {
            if (handle == 0L) return@launchSession
            bridge.nativeScrollToActive(handle)
            requestSnapshot(force = true)
        }
    }

    suspend fun sftpListDirectory(path: String): List<String> =
        onSession { nativeSsh.sftpListDirectory(path) }

    suspend fun sftpRealpath(path: String): String =
        onSession { nativeSsh.sftpRealpath(path) }

    suspend fun sftpOpenWrite(path: String) =
        onSession { nativeSsh.sftpOpenWrite(path) }

    suspend fun sftpWriteChunk(data: ByteArray): Int =
        onSession { nativeSsh.sftpWriteChunk(data) }

    suspend fun sftpCloseWrite() =
        onSession { nativeSsh.sftpCloseWrite() }

    suspend fun sftpReadFile(path: String, maxBytes: Int): ByteArray =
        onSession { nativeSsh.sftpReadFile(path, maxBytes) }

    suspend fun sftpMkdir(path: String): Boolean =
        onSession { nativeSsh.sftpMkdir(path) }

    suspend fun sftpDelete(path: String, isDirectory: Boolean) =
        onSession {
            if (isDirectory) nativeSsh.sftpDeleteDirectory(path) else nativeSsh.sftpDeleteFile(path)
        }

    suspend fun checkMultiplexerAvailability(spec: TabSpec): MultiplexerAvailability =
        onSession { checkMultiplexerAvailability(spec.toConnectionParams()) }

    private suspend fun checkMultiplexerAvailability(params: ConnectionParams): MultiplexerAvailability {
        val type = params.multiplexer ?: return MultiplexerAvailability.Available
        val multiplexer = MultiplexerRegistry.forType(type)
            ?: return MultiplexerAvailability.UnsupportedMultiplexer(type)
        if (params.transport == Transport.Mosh || params.transport == Transport.LocalShell) {
            return MultiplexerAvailability.UnsupportedTransport(params.transport)
        }
        return runCatching {
            val result = runMultiplexerCommand(params, multiplexer.availabilityCommand())
            if (result.isSuccess) {
                MultiplexerAvailability.Available
            } else {
                MultiplexerAvailability.Missing(type)
            }
        }.getOrElse { error ->
            MultiplexerAvailability.Error(
                message = error.message ?: "Could not check ${type.label} on this host",
            )
        }
    }

    private fun multiplexerAvailabilityErrorMessage(
        availability: MultiplexerAvailability,
        selectedType: MultiplexerType?,
    ): String? = when (availability) {
        MultiplexerAvailability.Available -> null
        is MultiplexerAvailability.Missing ->
            "${availability.multiplexer.label} executable was not found on the remote host"
        is MultiplexerAvailability.UnsupportedMultiplexer ->
            "${availability.multiplexer.label} is not supported yet"
        is MultiplexerAvailability.UnsupportedTransport ->
            "${selectedType?.label ?: "Multiplexer"} is not supported for ${availability.transport}"
        is MultiplexerAvailability.Error -> availability.message
    }

    suspend fun listMultiplexerSessions(spec: TabSpec): List<RemoteMultiplexerSession> =
        onSession {
            val type = spec.multiplexer ?: throw IllegalStateException("No multiplexer selected")
            val multiplexer = MultiplexerRegistry.forType(type)
                ?: throw IllegalStateException("${type.label} is not supported yet")
            if (spec.transport == Transport.Mosh || spec.transport == Transport.LocalShell) {
                throw IllegalStateException("${type.label} is not supported for ${spec.transport} connections")
            }
            val result = runMultiplexerCommand(spec.toConnectionParams(), multiplexer.listSessionsCommand())
            if (!result.isSuccess) {
                throw IllegalStateException(result.stderr.ifBlank { "Failed to list ${type.label} sessions" })
            }
            multiplexer.parseSessions(result.stdout)
        }

    suspend fun resolveMultiplexerSessionName(
        spec: TabSpec,
        localSessionNames: Collection<String>,
        reuseDetachedChuchuSession: Boolean = false,
    ): String = onSession {
        val type = spec.multiplexer ?: MultiplexerRegistry.defaultType
        val multiplexer = MultiplexerRegistry.forType(type)
            ?: throw IllegalStateException("${type.label} is not supported yet")
        if (spec.transport == Transport.Mosh || spec.transport == Transport.LocalShell) {
            throw IllegalStateException("${type.label} is not supported for ${spec.transport} connections")
        }
        // KHONG chay checkMultiplexerAvailability rieng o day: listSessionsCommand
        // cua tung multiplexer da tu kiem binary va tra "X executable not found"
        // qua stderr. Vong check rieng ton nguyen mot cu TCP+SSH handshake nua
        // cho moi lan mo tab — new tab tung mat 3 handshake thay vi 2.
        val remoteSessions = listMultiplexerSessions(spec.copy(multiplexer = type))
        val existingName = spec.multiplexerSessionName?.takeIf { it.isNotBlank() }
        if (existingName != null && spec.multiplexerCreateIfMissing) return@onSession existingName
        if (existingName != null) {
            if (remoteSessions.any { it.name == existingName }) return@onSession existingName
            throw IllegalStateException("${type.label} session \"$existingName\" is no longer available")
        }
        if (reuseDetachedChuchuSession) {
            MultiplexerSessionAllocator.reusableDetachedChuchuSessionName(
                remoteSessions = remoteSessions,
                localSessionNames = localSessionNames,
            )?.let { return@onSession it }
        }
        multiplexer.defaultSessionName(remoteSessions, localSessionNames)
    }

    fun disconnect() {
        disconnectRequested = true
        reconnectJob?.cancel()
        reconnectJob = null
        lastConnectionParams = null
        cancelHostKeyPrompt()
        nativeSsh.abortConnect()          // dong tab dang noi: khong doi het poll
        launchSession {
            readJob?.cancel()
            readJob = null
            nativeSsh.close()
            moshService.close()
            localShellService.close()
            if (handle != 0L) {
                bridge.nativeDestroy(handle)
                handle = 0L
            }
            snapshotScheduled = false
            title = null
            pwd = null
            images = emptyList()
            _state.value =
                SessionState(
                    status = SessionStatus.Disconnected,
                    nativeVersion = nativeVersion,
                    sessionKey = _state.value.sessionKey,
                )
        }
    }

    /** Roi man terminal khi tab con dang noi (back): huy cu connect, tab o lai voi loi + Retry (9/9). */
    fun abortConnectIfPending() {
        val st = _state.value.status
        if (st == SessionStatus.Connecting || st == SessionStatus.Reconnecting) nativeSsh.abortConnect()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        disconnect()
        dispatcher.close()
    }

    private fun cancelHostKeyPrompt() {
        hostKeyDecision?.cancel()
        hostKeyDecision = null
        _hostKeyPrompt.value = null
    }

    fun respondToHostKey(accepted: Boolean) {
        hostKeyDecision?.complete(accepted)
        hostKeyDecision = null
        _hostKeyPrompt.value = null
    }

    private suspend fun verifyHostKey(
        host: String,
        port: Int,
        algorithm: String,
        keyBytes: ByteArray,
    ): Boolean {
        val (fingerprint, previousFingerprint) =
            when (val result = hostKeyStore.check(host, port, algorithm, keyBytes)) {
                is HostKeyCheck.Match -> return true
                is HostKeyCheck.Unknown -> result.fingerprint to null
                is HostKeyCheck.Changed -> result.fingerprint to result.previousFingerprint
            }
        val deferred =
            hostKeyDecision
                ?: CompletableDeferred<Boolean>().also {
                    hostKeyDecision = it
                    _hostKeyPrompt.value =
                        HostKeyPrompt(
                            host = host,
                            port = port,
                            algorithm = algorithm,
                            fingerprint = fingerprint,
                            previousFingerprint = previousFingerprint,
                        )
                }
        // await truc tiep: ham nay gio la suspend, khong runBlocking chiem
        // chet thread single-thread dispatcher cua engine trong luc prompt treo.
        val accepted = deferred.await()
        if (accepted) {
            hostKeyStore.saveKey(host, port, algorithm, keyBytes)
        }
        return accepted
    }

    private fun startReadLoop() {
        readJob =
            launchSession {
                val transport = lastConnectionParams?.transport
                var failed = false
                var moshFailureCode: Int? = null
                try {
                    when (transport) {
                        Transport.Mosh -> moshFailureCode = startMoshReadLoop()
                        Transport.LocalShell -> startLocalShellReadLoop()
                        else -> startSshReadLoop()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed = true
                    Log.e("TerminalSession", "Read loop failed", e)
                    if (transport == Transport.LocalShell && !disconnectRequested) {
                        _state.value =
                            _state.value.copy(
                                status = SessionStatus.Error,
                                error = "Local shell failed: ${e.message}",
                            )
                    }
                }
                when (readLoopExitAction(transport)) {
                    ReadLoopExitAction.Disconnect -> {
                        localShellService.close()
                        if (!disconnectRequested && !failed) {
                            _state.value = _state.value.copy(status = SessionStatus.Disconnected)
                        }
                    }
                    ReadLoopExitAction.RequireManualReconnect -> {
                        moshService.close()
                        if (!disconnectRequested) {
                            val failure = moshFailureCode?.let { " (failure code $it)" }.orEmpty()
                            _state.value =
                                _state.value.copy(
                                    status = SessionStatus.Error,
                                    error = "Mosh session ended$failure. Reconnect to start a new session.",
                                )
                        }
                    }
                    ReadLoopExitAction.AutomaticReconnect -> scheduleReconnect("Connection interrupted")
                }
            }
    }

    /**
     * Vòng đọc NGỦ THẬT (pin, 7/9): hết data thì chặn trong poll() native trên socket +
     * ống wake thay vì dậy 4–15 lần/giây hỏi "có gì không". Có data thì poll trả ngay;
     * phím gõ / việc xếp lên dispatcher thì [launchSession] gọi wake() trước nên cũng
     * trả ngay, rồi vòng nhường lượt (yield) cho việc đó chạy. Khi còn snapshot đang
     * hẹn (16ms) thì chỉ đợi ngắn để nó không bị giam sau poll.
     */
    private suspend fun runReadLoop(
        read: suspend (Int) -> ByteArray?,
        wait: (Int) -> Int,
        eof: () -> Boolean = { false },
    ) {
        // Đếm số lần poll() báo "có tin" mà read() vẫn rỗng: kênh EOF/HUP trả rỗng chứ
        // không null (readChannel: rc==0 -> slice rỗng), nên không có gì để break → vòng
        // xoay 100% CPU, điện thoại nóng rực (user báo 7/9, bản .40–.43). Trước đây
        // idleSleep 250ms che mất. Giờ: EOF thật thì thoát để đi đường reconnect;
        // không thì lùi dần tới 250ms.
        var emptyReadable = 0
        while (currentCoroutineContext().isActive) {
            val chunk = read(READ_CHUNK_BYTES) ?: break
            if (chunk.isEmpty()) {
                if (emptyReadable >= 2 && eof()) break
                val timeout = when {
                    snapshotScheduled -> SNAPSHOT_WAIT_MS
                    renderEnabled -> FOREGROUND_WAIT_MS
                    else -> BACKGROUND_WAIT_MS
                }
                val r = wait(timeout)
                when {
                    r < 0 -> delay(FOREGROUND_WAIT_MS.toLong())          // không poll được: ngủ thường
                    r == 1 -> {                                           // "có tin" nhưng vừa đọc rỗng
                        emptyReadable = (emptyReadable + 1).coerceAtMost(8)
                        delay((4L shl emptyReadable).coerceAtMost(250L))
                    }
                    else -> emptyReadable = 0
                }
                yield()                                                   // việc vừa đánh thức được chạy trước
                continue
            }
            emptyReadable = 0
            feedRemoteChunk(chunk)
        }
    }

    private suspend fun startSshReadLoop() =
        runReadLoop({ nativeSsh.read(it) }, { nativeSsh.waitReadable(it) }, { nativeSsh.isChannelEof() })

    private suspend fun startLocalShellReadLoop() =
        runReadLoop({ localShellService.read(it) }, { localShellService.waitReadable(it) })

    /**
     * MỌI việc xếp lên dispatcher của session đi qua đây: đánh thức read-loop đang chặn
     * trong poll() trước, không thì việc nằm chờ tới hết timeout (tối đa 30s ở nền).
     */
    /**
     * Chạy [block] trên luồng session và ĐÁNH THỨC vòng đọc trước (14/9, user báo ⊕ upload
     * chậm, lúc được lúc không). `withContext(dispatcher)` chỉ xếp hàng; vòng đọc đang ngủ
     * trong poll() tới 1 s ở foreground / 30 s ở nền (vá pin 7/9), nên MỖI chunk SFTP 64 KB
     * phải đợi poll hết giờ mới tới lượt: ~64 KB/s khi đang nhìn màn hình, còn rời app giữa
     * chừng thì mỗi chunk chờ 30 s → native báo "SFTP write stalled". launchSession đã wake
     * từ 7/9, đường withContext (SFTP, preflight multiplexer) thì chưa — đây là chỗ hụt.
     */
    private suspend fun <T> onSession(block: suspend CoroutineScope.() -> T): T {
        nativeSsh.wake()
        localShellService.wake()
        readWake.trySend(Unit)
        return withContext(dispatcher, block)
    }

    private fun launchSession(block: suspend CoroutineScope.() -> Unit): Job {
        // Đánh thức CẢ BA, không chọn theo transport: disconnect() xoá
        // lastConnectionParams TRƯỚC khi xếp việc, chọn theo nó là local shell không
        // được đánh thức → lệnh ngắt nằm chờ tới 30s ở nền (review 7/9). wake() trên
        // handle rỗng là no-op nên rẻ.
        nativeSsh.wake()
        localShellService.wake()
        readWake.trySend(Unit)
        return scope.launch(dispatcher, block = block)
    }

    private suspend fun feedRemoteChunk(chunk: ByteArray) {
        if (handle == 0L) return
        // wasImageLoading lay tu lan truoc thay vi goi JNI them mot lan;
        // pre-flush cung bo — post-flush cua chunk truoc da gui het pending.
        val wasImageLoading = lastImageLoading
        bridge.nativeWriteRemote(handle, chunk)
        flushPtyWrites()
        val isImageLoading = bridge.nativeIsImageLoading(handle)
        lastImageLoading = isImageLoading
        when {
            wasImageLoading && !isImageLoading -> {
                requestSnapshot(force = true)
            }
            !isImageLoading -> requestSnapshot()
        }
    }

    private suspend fun startMoshReadLoop(): Int? {
        Log.d("TerminalSession", "MOSH: read loop started")
        var loopCount = 0
        var lastActivityMs = System.currentTimeMillis()
        var failureCode: Int? = null
        while (currentCoroutineContext().isActive) {
            loopCount++

            // Drive retransmissions, acks, heartbeats, timeout checks
            val tickOk = moshService.maintenanceTick()
            if (!tickOk) {
                Log.w("TerminalSession", "MOSH: maintenanceTick returned false")
            }

            // Drive the UDP socket (send queued datagrams, receive inbound)
            val pumpOk = moshService.pumpNetwork()
            if (!pumpOk) {
                Log.w("TerminalSession", "MOSH: pumpNetwork returned false")
            }

            // Drain all available output events
            var hadEvent = false
            while (true) {
                val event = moshService.pollOutput() ?: break
                hadEvent = true
                when (event.eventType) {
                    MoshEventType.HostBytes.code -> {
                        if (event.payload.isNotEmpty() && handle != 0L) {
                            feedRemoteChunk(event.payload)
                        }
                    }
                    MoshEventType.Resize.code -> {
                        if (event.cols > 0 && event.rows > 0) {
                            cols = event.cols
                            rows = event.rows
                            bridge.nativeResize(handle, cols, rows, cellWidth, cellHeight)
                        }
                    }
                    MoshEventType.EchoAck.code -> Unit
                    MoshEventType.StateChanged.code -> {
                        Log.d("TerminalSession", "MOSH: STATE_CHANGED: ${String(event.payload)}")
                    }
                    MoshEventType.Diagnostic.code -> {
                        Log.d("TerminalSession", "MOSH: DIAG: ${String(event.payload)}")
                    }
                    else -> {
                        Log.d("TerminalSession", "MOSH: unknown event type=${event.eventType}")
                    }
                }
            }

            if (hadEvent && handle != 0L) {
                requestSnapshot()
                lastActivityMs = System.currentTimeMillis()
            }

            // Check session health
            val runtime = moshService.pollState()
            if (runtime != null) {
                if (runtime.state == MoshState.Failed.code) {
                    failureCode = runtime.lastFailureCode
                    Log.e("TerminalSession", "MOSH: session FAILED code=$failureCode")
                    break
                }
            }

            // Adaptive cadence: Mosh's retransmit/heartbeat timers are coarse, so
            // backing the pump off while idle is safe and avoids a 500 Hz spin.
            idleSleep(idleReadDelayMs(System.currentTimeMillis() - lastActivityMs))
        }
        Log.d("TerminalSession", "MOSH: read loop exited after $loopCount iterations")
        return failureCode
    }

    private suspend fun establishConnection(params: ConnectionParams, username: String) {
        if (handle != 0L) {
            bridge.nativeDestroy(handle)
            handle = 0L
        }
        nativeSsh.close()
        moshService.close()
        localShellService.close()
        handle = bridge.nativeCreate(cols, rows, 1000)
        applyTerminalOptions()

        when (params.transport) {
            Transport.Mosh -> establishMoshConnection(params, username)
            Transport.LocalShell -> establishLocalShellConnection()
            else -> establishSshConnection(params, username)
        }
    }

    private fun establishLocalShellConnection() {
        localShellService.start(cols, rows, screenWidth, screenHeight)
    }

    private suspend fun establishSshConnection(params: ConnectionParams, username: String) {
        check(nativeSsh.isAvailable()) { "Native SSH unavailable" }
        nativeSsh.connect(
            host = params.host,
            port = params.port,
            username = username,
            authMethod = params.authMethod,
            password = if (params.authMethod == AuthMethod.Password) params.password else "",
            publicKeyOpenSsh = params.publicKeyOpenSsh,
            privateKeyPem = params.privateKeyPem,
            keyPassphrase = params.keyPassphrase,
        )
        val startupCommand = params.multiplexerStartupCommand()?.trim().orEmpty()
        if (startupCommand.isNotEmpty()) {
            nativeSsh.openExecPty(startupCommand, cols, rows, ptyWidthPx(), ptyHeightPx())
        } else {
            nativeSsh.openShell(cols, rows, ptyWidthPx(), ptyHeightPx())
        }
    }

    private suspend fun establishMoshConnection(params: ConnectionParams, username: String) {
        Log.d("TerminalSession", "MOSH: Phase 1 — SSH bootstrap start")
        check(nativeSsh.isAvailable()) { "Native SSH unavailable for mosh bootstrap" }
        nativeSsh.connect(
            host = params.host,
            port = params.port,
            username = username,
            authMethod = params.authMethod,
            password = if (params.authMethod == AuthMethod.Password) params.password else "",
            publicKeyOpenSsh = params.publicKeyOpenSsh,
            privateKeyPem = params.privateKeyPem,
            keyPassphrase = params.keyPassphrase,
        )
        // Use exec channel to bypass shell init noise and MOTD.
        // Falls back to shell if the remote server doesn't support exec.
        val moshCommand = MoshReconnectPolicy.bootstrapCommand()
        val execOpened = runCatching { nativeSsh.openExec(moshCommand) }.getOrDefault(false)
        if (!execOpened) {
            Log.w("TerminalSession", "MOSH: exec channel unavailable, falling back to shell")
            nativeSsh.openShell(cols, rows, ptyWidthPx(), ptyHeightPx())
            val fallback = "$moshCommand\n"
            nativeSsh.write(fallback.toByteArray(Charsets.UTF_8))
        }
        Log.d(
            "TerminalSession",
            "MOSH: SSH connected, ${if (execOpened) "exec" else "shell"} channel opened",
        )

        // Read output until we find MOSH CONNECT, the channel hits EOF, or
        // we time out. If the first window has no output and no EOF on exec,
        // extend once to distinguish slow remote bootstrap from hard failure.
        val outputBuffer = StringBuilder()
        suspend fun readBootstrapWindow(timeoutMs: Long): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val chunk = nativeSsh.read(4096)
                if (chunk != null && chunk.isNotEmpty()) {
                    val text = String(chunk, Charsets.UTF_8)
                    outputBuffer.append(text)
                    val result = MoshBootstrapParser.parse(params.host, outputBuffer.toString())
                    if (result is MoshBootstrapParser.ParseResult.Success) {
                        Log.d(
                            "TerminalSession",
                            "MOSH: Parsed endpoint host=${result.endpoint.host} port=${result.endpoint.port}",
                        )
                        nativeSsh.close()
                        val configJson =
                            JSONObject()
                                .apply {
                                    put("host", result.endpoint.host)
                                    put("port", result.endpoint.port)
                                    put("keyBase64_22", result.endpoint.key)
                                    put("networkTimeoutMs", MoshReconnectPolicy.CLIENT_NETWORK_TIMEOUT_MS)
                                    put("maxRetransmitCount", MoshReconnectPolicy.CLIENT_MAX_RETRANSMIT_COUNT)
                                    put("useNetworkCrypto", true)
                                }
                                .toString()
                        if (!moshService.create(configJson)) {
                            throw IllegalStateException("Failed to create mosh client")
                        }
                        Log.d("TerminalSession", "MOSH: mosh client created, starting...")
                        if (!moshService.start()) {
                            throw IllegalStateException("Failed to start mosh client")
                        }
                        Log.d("TerminalSession", "MOSH: mosh client started, resizing $cols x $rows")
                        moshService.resize(cols, rows)
                        return true
                    }
                } else {
                    if (execOpened && nativeSsh.isChannelEof()) {
                        Log.d("TerminalSession", "MOSH: exec channel EOF reached")
                        return false
                    }
                    delay(50)
                }
            }
            return false
        }

        var found = readBootstrapWindow(timeoutMs = 10_000)
        if (!found && execOpened && outputBuffer.isEmpty() && !nativeSsh.isChannelEof()) {
            Log.w("TerminalSession", "MOSH: bootstrap still running after 10s with no output; extending window by 15s")
            found = readBootstrapWindow(timeoutMs = 15_000)
        }
        if (!found) {
            val eofBeforeClose = if (execOpened) nativeSsh.isChannelEof() else true
            nativeSsh.close()
            val rawOutput = outputBuffer.toString()
            val reason =
                if (rawOutput.isBlank()) {
                    if (execOpened && !eofBeforeClose) {
                        "Mosh bootstrap command still running after timeout (no output, no EOF)"
                    } else {
                        "Mosh bootstrap produced no output"
                    }
                } else {
                    rawOutput.trim()
                }
            Log.e("TerminalSession", "MOSH: Bootstrap failed: $reason")
            throw IllegalStateException(reason)
        }
        Log.d("TerminalSession", "MOSH: Phase 2 — mosh client ready")
    }

    private fun TabSpec.toConnectionParams(): ConnectionParams = ConnectionParams(
        host = host,
        port = port,
        username = username,
        password = password,
        authMethod = authMethod,
        publicKeyOpenSsh = publicKeyOpenSsh,
        privateKeyPem = privateKeyPem,
        keyPassphrase = keyPassphrase,
        transport = transport,
        postConnectCommand = postConnectCommand,
        multiplexer = multiplexer,
        multiplexerSessionName = multiplexerSessionName,
        multiplexerCreateIfMissing = multiplexerCreateIfMissing,
    )

    private suspend fun runMultiplexerCommand(
        params: ConnectionParams,
        command: String,
        timeoutMs: Long = 20_000,
    ): MultiplexerCommandResult {
        val ssh = NativeSshService(hostKeyPolicy = ::verifyHostKey)
        ssh.use { service ->
            service.connect(
                host = params.host,
                port = params.port,
                username = params.username,
                authMethod = params.authMethod,
                password = if (params.authMethod == AuthMethod.Password) params.password else "",
                publicKeyOpenSsh = params.publicKeyOpenSsh,
                privateKeyPem = params.privateKeyPem,
                keyPassphrase = params.keyPassphrase,
            )
            if (!service.openExec(withExitEnvelope(command))) {
                return MultiplexerCommandResult(1, "", "Remote server did not open an exec channel")
            }
            return readExecOutput(service, timeoutMs)
        }
    }

    private fun withExitEnvelope(command: String): String =
        "$command; printf '\nCHUCHU_EXIT:%s\n' \"\$?\""

    private suspend fun readExecOutput(
        service: NativeSshService,
        timeoutMs: Long,
    ): MultiplexerCommandResult {
        val output = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val chunk = service.read(4096)
            if (chunk != null && chunk.isNotEmpty()) {
                output.append(String(chunk, Charsets.UTF_8))
            } else if (service.isChannelEof()) {
                return parseCommandEnvelope(output.toString())
            } else {
                // delay chu khong Thread.sleep: ham chay tren single-thread
                // dispatcher cua engine, sleep la ghim chet thread do toi 20s.
                delay(25)
            }
        }
        return MultiplexerCommandResult(124, output.toString(), "Command timed out")
    }

    private fun parseCommandEnvelope(output: String): MultiplexerCommandResult {
        val marker = EXIT_ENVELOPE_REGEX.find(output)
            ?: return MultiplexerCommandResult(
                exitCode = 125,
                stdout = output,
                stderr = "Missing command exit marker",
            )
        val exitCode = marker.groupValues.getOrNull(1)?.toIntOrNull() ?: 125
        val cleanOutput = output.substring(0, marker.range.first).trimEnd()
        return MultiplexerCommandResult(exitCode = exitCode, stdout = cleanOutput, stderr = "")
    }

    private fun scheduleReconnect(reason: String) {
        if (disconnectRequested) {
            _state.value = _state.value.copy(status = SessionStatus.Disconnected)
            return
        }
        val params =
            lastConnectionParams
                ?: run {
                    _state.value = _state.value.copy(status = SessionStatus.Disconnected)
                    return
                }
        if (reconnectJob?.isActive == true) return
        reconnectJob =
            launchSession {
                readJob?.cancel()
                readJob = null
                var attempt = 0
                while (currentCoroutineContext().isActive && !disconnectRequested) {
                    attempt += 1
                    _state.value =
                        _state.value.copy(
                            status = SessionStatus.Reconnecting,
                            reconnectAttempt = attempt,
                            error = reason,
                        )
                    val delayMs = (1_000L shl (attempt - 1).coerceAtMost(5)).coerceAtMost(60_000L)
                    delay(delayMs)
                    if (params.username.isBlank()) {
                        _state.value =
                            _state.value.copy(
                                status = SessionStatus.Error,
                                error = "Username required",
                            )
                        return@launchSession
                    }
                    try {
                        establishConnection(params, params.username)
                        _state.value =
                            _state.value.copy(
                                status = SessionStatus.Connected,
                                reconnectAttempt = 0,
                                error = null,
                            )
                        requestSnapshot(force = true)
                        startReadLoop()
                        sendStartupCommand(params)
                        return@launchSession
                    } catch (e: Exception) {
                        Log.e("TerminalSession", "Reconnect attempt $attempt failed", e)
                        if (attempt >= 8) {
                            _state.value =
                                _state.value.copy(
                                    status = SessionStatus.Error,
                                    error = "Reconnect failed: ${e.message}",
                                )
                            return@launchSession
                        }
                    }
                }
            }
    }

    private fun applyTerminalOptions() {
        val localHandle = handle
        if (localHandle == 0L) return
        pendingColorScheme?.let { scheme -> bridge.nativeSetColorScheme(localHandle, scheme) }
        pendingDefaultColors?.let { colors ->
            bridge.nativeSetDefaultColors(
                localHandle,
                colors.fg,
                colors.bg,
                colors.cursor,
                colors.palette,
            )
        }
        if (screenWidth > 0 && screenHeight > 0) {
            bridge.nativeSetMouseEncodingSize(
                localHandle,
                screenWidth,
                screenHeight,
                cellWidth,
                cellHeight,
                0,
                0,
                0,
                0,
            )
        }
    }

    private suspend fun writeRemote(data: ByteArray) {
        when (lastConnectionParams?.transport) {
            Transport.Mosh -> moshService.sendInput(data)
            Transport.LocalShell -> localShellService.write(data)
            else -> nativeSsh.write(data)
        }
        readWake.trySend(Unit)
    }

    /** Ngủ [ms] nhưng dậy ngay nếu có phím gõ (readWake). */
    private suspend fun idleSleep(ms: Long) {
        withTimeoutOrNull(ms) { readWake.receive() }
    }

    private fun publishClipboard(text: String) {
        scope.launch(Dispatchers.Main.immediate) {
            runCatching {
                publishClipboardText(text)
            }.onFailure { error ->
                Log.w("TerminalSession", "OSC 52 clipboard update failed", error)
            }
        }
    }

    private suspend fun sendStartupCommand(params: ConnectionParams) {
        // Multiplexer startup is dispatched through openExecPty before this runs. Later writes on
        // that PTY follow the exec request, so the command is consumed by the attached session.
        sendPostConnectCommand(params.postConnectCommand)
    }

    private suspend fun sendPostConnectCommand(command: String?) {
        val trimmed = command?.trim().orEmpty()
        if (trimmed.isEmpty()) return
        sendInteractiveCommand(trimmed, "post-connect command")
    }

    private suspend fun sendInteractiveCommand(command: String, logLabel: String) {
        try {
            writeRemote("$command\n".toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e("TerminalSession", "$logLabel failed", e)
        }
    }

    private suspend fun flushPtyWrites() {
        if (handle == 0L) return
        repeat(8) {
            val ptyWrites = bridge.nativeDrainPtyWrites(handle)
            if (ptyWrites.isEmpty()) return
            try {
                writeRemote(ptyWrites)
            } catch (e: Exception) {
                Log.e("TerminalSession", "flushPtyWrites failed: ${e.message}")
                launchSession {
                    _state.value =
                        _state.value.copy(
                            status = SessionStatus.Error,
                            error = "Connection lost: ${e.message}",
                        )
                }
                return
            }
        }
    }

    // Pixel size reported to the remote pty (ws_xpixel/ypixel): the integer grid
    // extent (cols*cellW x rows*cellH), not the full canvas — apps derive
    // px-per-cell as ws_xpixel/cols, so the canvas would misplace Kitty graphics.
    // Falls back to the canvas before the first real cell size.
    private fun ptyWidthPx(): Int = if (cellWidth > 0) cols * cellWidth else screenWidth
    private fun ptyHeightPx(): Int = if (cellHeight > 0) rows * cellHeight else screenHeight

    private fun resizeRemote(cols: Int, rows: Int, widthPx: Int, heightPx: Int) {
        when (lastConnectionParams?.transport) {
            Transport.Mosh -> {
                Log.d("TerminalSession", "MOSH: resizeRemote ${cols}x${rows}")
                moshService.resize(cols, rows)
            }
            Transport.LocalShell -> localShellService.resize(cols, rows, widthPx, heightPx)
            else -> nativeSsh.resize(cols, rows, widthPx, heightPx)
        }
    }

    private companion object {
        // Idle thresholds and the poll delay for each tier.
        private const val ACTIVE_WINDOW_MS = 50L
        private const val NEAR_IDLE_WINDOW_MS = 500L
        private const val IDLE_WINDOW_MS = 3_000L
        private const val BACKGROUND_READ_DELAY_MS = 250L
        private const val SNAPSHOT_WAIT_MS = 16
        private const val FOREGROUND_WAIT_MS = 1_000
        private const val BACKGROUND_WAIT_MS = 30_000
        private const val MIN_READ_DELAY_MS = 2L
        private const val NEAR_IDLE_DELAY_MS = 8L
        private const val IDLE_DELAY_MS = 24L
        private const val MAX_READ_DELAY_MS = 64L
        private const val READ_CHUNK_BYTES = 65536
        private val EXIT_ENVELOPE_REGEX = Regex("(?:^|\\n)CHUCHU_EXIT:(\\d+)\\s*$")
    }

    // Read-loop poll interval as a function of how long the session has been idle:
    // MIN while data is flowing (snappy echo), ramping to MAX once quiet so an idle
    // terminal stops spinning. The first byte after idle restores MIN within one
    // MAX interval.
    private fun idleReadDelayMs(idleForMs: Long): Long {
        val d = when {
            idleForMs < ACTIVE_WINDOW_MS -> MIN_READ_DELAY_MS
            idleForMs < NEAR_IDLE_WINDOW_MS -> NEAR_IDLE_DELAY_MS
            idleForMs < IDLE_WINDOW_MS -> IDLE_DELAY_MS
            else -> MAX_READ_DELAY_MS
        }
        // Không ai nhìn thì không cần dậy 500 lần/s để bơm nhanh; 250ms là đủ để
        // dữ liệu vẫn chảy vào ghostty (tmux/htop ở tab nền không mất gì).
        return if (renderEnabled) d else maxOf(d, BACKGROUND_READ_DELAY_MS)
    }

    /** Repo gọi: chỉ tab đang hiện trên màn (và app ở foreground) mới được vẽ. */
    fun setRenderEnabled(enabled: Boolean) {
        if (renderEnabled == enabled) return
        renderEnabled = enabled
        if (enabled && snapshotDirty) {
            launchSession {
                snapshotDirty = false
                if (handle == 0L) return@launchSession
                emitSnapshot()
                lastSnapshotAtMs = System.currentTimeMillis()
            }
        }
    }

    private fun requestSnapshot(force: Boolean = false) {
        if (handle == 0L) return
        if (!renderEnabled) { snapshotDirty = true; return }
        val now = System.currentTimeMillis()
        val elapsed = now - lastSnapshotAtMs
        if (force || elapsed >= snapshotIntervalMs) {
            snapshotScheduled = false
            emitSnapshot()
            lastSnapshotAtMs = now
            return
        }
        if (snapshotScheduled) return
        snapshotScheduled = true
        val waitMs = (snapshotIntervalMs - elapsed).coerceAtLeast(1L)
        launchSession {
            delay(waitMs)
            snapshotScheduled = false
            if (handle == 0L) return@launchSession
            emitSnapshot()
            lastSnapshotAtMs = System.currentTimeMillis()
        }
    }

    private fun emitSnapshot() {
        if (handle == 0L) return
        try {
            val raw = bridge.nativeSnapshot(handle)
            val rawImages = bridge.nativeSnapshotImages(handle)
            images = TerminalSnapshot.parseImages(rawImages, bitmapCache) { id -> bridge.nativeImagePixels(handle, id) }
            val snap = TerminalSnapshot.fromByteBuffer(raw, images, parseScratch)
            val nextTitle = bridge.nativePollTitle(handle)
            val nextPwd = bridge.nativePollPwd(handle)
            val nextClipboard = bridge.nativePollClipboard(handle)
            val bellCount = bridge.nativeDrainBellCount(handle)
            if (nextTitle != null) {
                title = nextTitle
            }
            if (nextPwd != null) {
                pwd = nextPwd
            }
            if (nextClipboard != null) {
                publishClipboard(nextClipboard.toString(Charsets.UTF_8))
            }
            _state.value =
                _state.value.copy(
                    snapshot = snap,
                    title = title,
                    pwd = pwd,
                    bellCount = bellCount,
                    nativeVersion = nativeVersion,
                    handle = handle,
                )
        } catch (e: Exception) {
            Log.e("TerminalSession", "emitSnapshot failed", e)
        }
    }

}
