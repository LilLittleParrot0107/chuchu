package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.jossephus.chuchu.ui.theme.ChuColors
import org.json.JSONArray
import org.json.JSONObject

/**
 * Defensive models for qsrv `GET /state?view=app`.
 *
 * Presentation metadata comes from qsrv. The parser only translates known
 * legacy labels and tolerates missing or future fields so server and app can
 * be upgraded independently.
 */

enum class QueueTone {
    Dim, Accent, Ok, Warn, Error;

    companion object {
        /** Unknown tones stay readable without pretending to carry meaning. */
        fun from(raw: String?): QueueTone = when (raw) {
            "accent" -> Accent
            "ok" -> Ok
            "warn" -> Warn
            "error" -> Error
            else -> Dim
        }
    }
}

data class QueueAction(
    val op: String,
    val label: String,
    val needsRev: Boolean,
    val danger: Boolean,
)

data class QueueTask(
    val id: Int,
    /** Agent pane used for the same scoped task view as qq. */
    val target: String,
    val text: String,
    val state: String,
    val glyph: String,
    val tone: QueueTone,
    val stateLabel: String,
    val sub: String,
    val actions: List<QueueAction>,
    val hasResp: Boolean = false,
)

internal val QueueTask.isCompleted: Boolean
    get() = state.equals("done", ignoreCase = true) || state.equals("completed", ignoreCase = true)

internal val QueueTask.isRunning: Boolean
    get() = state.equals("sent", ignoreCase = true) ||
        state.equals("sending", ignoreCase = true) ||
        state.equals("working", ignoreCase = true) ||
        state.equals("busy", ignoreCase = true)

// Đặt cạnh isCompleted/isRunning: "failed" là trạng thái kết thúc như "done",
// tách riêng để chỗ đếm active lọc đúng mà không phải so state string rải rác.
internal val QueueTask.isFailed: Boolean
    get() = state.equals("failed", ignoreCase = true)

internal fun QueueAction.operationKey(taskId: Int?): String = "$op:${taskId ?: "-"}"

internal object QueueOperationKey {
    const val ADD = "add"
    private const val CHAT_SEND_PREFIX = "chat-send:"

    /** Gửi thẳng vào pane từ hàng HỘI THOẠI (chip đang chọn) — khoá theo pane. */
    fun chatSend(targetPane: String): String = "$CHAT_SEND_PREFIX$targetPane"
}

data class QueueAgent(
    val pane: String,
    val name: String,
    val glyph: String,
    val tone: QueueTone,
    val label: String,
    /** Short attention marker from qsrv; qq calls this the agent word. */
    val word: String,
    /**
     * Vân tay (mtime.size) transcript Claude Code của agent — qsrv chỉ stat, không đọc.
     * App so với lần xem cuối để hiện "CHAT · MỚI". null = agent không có transcript.
     */
    val chatRev: String? = null,
    /** Loại agent herdr báo ("claude"/"opencode"/"agy"/"codex") — tô màu tên theo loại (user chốt 16/9). */
    val agent: String? = null,
    /** Tin cuối của transcript, dồn 1 dòng ≤160 ký tự — hàng HỘI THOẠI (UI G1) đọc nhanh. */
    val preview: String = "",
    /** Giờ ISO UTC của tin cuối (qsrv /state preview_ts); "" = không có. */
    val previewTs: String = "",
    /** Thư mục làm việc hiện tại (working directory) của session agent. */
    val cwd: String = "",
) {
    /** Trạng thái runtime đọc từ nhãn qsrv — xem [AgentState]. */
    val state: AgentState get() = AgentState.of(label)
}

/**
 * Trạng thái runtime của một session, đọc từ NHÃN đã dịch của qsrv (A_VIEW) chứ không theo
 * tone: tone chỉ là màu, hai trạng thái khác nhau có thể cùng màu. MỘT chỗ duy nhất cho
 * roster, chấm trạng thái, màu tên và ô gõ — trước 21/9 bốn nơi tự liệt kê nhãn, lệch nhau.
 * Thứ tự khai báo = thứ tự roster (user chốt 4/9, 16/9): cần TAY người trên cùng, rồi đang
 * chạy, vừa xong NGAY DƯỚI đang chạy, rảnh, chưa rõ; nhãn lạ (down/gone) xuống đáy.
 */
enum class AgentState {
    Blocked, Working, Done, Idle, Unknown, Other;

    companion object {
        fun of(label: String): AgentState = when (label.trim().lowercase()) {
            "needs approval", "blocked" -> Blocked
            "working", "busy" -> Working
            "done" -> Done
            "idle" -> Idle
            "unknown" -> Unknown
            else -> Other
        }
    }
}

/**
 * Sắp theo [AgentState]. Trong nhóm ĐÃ XONG / RẢNH, session có hoạt động gần nhất
 * (previewTs mới nhất) lên trước (18/9). Nhóm cần duyệt / đang chạy GIỮ thứ tự server
 * (= thứ tự herdr, sortedWith là stable): agent vừa nói mà nhảy lên đầu thì hàng đảo liên tục
 * khi nhiều agent cùng chạy — đúng cái đã cố tình tránh từ 4/9.
 */
fun List<QueueAgent>.byPriority(): List<QueueAgent> = sortedWith(
    compareBy<QueueAgent> { it.state }
        .thenByDescending { if (it.state >= AgentState.Done) it.previewTs else "" }
)

data class QueueBanner(
    val tone: QueueTone,
    val text: String,
)

/** Transient action result; connection failures remain durable UI errors. */
enum class QueueFeedbackTone { Info, Success, Warning, Error }

data class QueueFeedback(
    val id: Long,
    val text: String,
    val tone: QueueFeedbackTone,
)

// Bản dịch tone -> màu sống ở models (không phải QueueScreen): mọi composable
// ngoài màn hình Queue (pill, FAB, banner) đều cần, để đây tránh mỗi nơi tự viết.
@Composable
internal fun QueueTone.color(): Color {
    val colors = ChuColors.current
    return when (this) {
        QueueTone.Accent -> colors.accent
        QueueTone.Ok -> colors.success
        QueueTone.Warn -> colors.warning
        QueueTone.Error -> colors.error
        QueueTone.Dim -> colors.textMuted
    }
}

@Composable
internal fun QueueFeedbackTone.color(): Color {
    val colors = ChuColors.current
    return when (this) {
        QueueFeedbackTone.Info -> colors.accent
        QueueFeedbackTone.Success -> colors.success
        QueueFeedbackTone.Warning -> colors.warning
        QueueFeedbackTone.Error -> colors.error
    }
}

private val queueFeedbackWhitespace = Regex("\\s+")
/** org.json trả chuỗi "null" cho optString của giá trị null — quy về null thật, rỗng cũng vậy. */
private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).takeIf { it.isNotBlank() && it != "null" }

/** Keep transient feedback compact; full details remain in logs/responses. */
internal fun normalizeQueueFeedbackText(raw: String, fallback: String): String {
    val compact = raw.trim().replace(queueFeedbackWhitespace, " ").ifBlank { fallback.trim() }
    if (compact.length <= 160) return compact
    return compact.take(159).trimEnd() + "…"
}

data class QueueState(
    val rev: String,
    val paused: Boolean,
    val banner: QueueBanner?,
    val globalActions: List<QueueAction>,
    val agents: List<QueueAgent>,
    val tasks: List<QueueTask>,
) {
    companion object {
        val Empty = QueueState(
            rev = "",
            paused = false,
            banner = null,
            globalActions = emptyList(),
            agents = emptyList(),
            tasks = emptyList(),
        )

        fun parse(json: String): QueueState = fromObject(JSONObject(json))

        fun fromObject(o: JSONObject): QueueState = QueueState(
            rev = o.optString("rev"),
            paused = o.optBoolean("paused", false),
            banner = o.optJSONObject("banner")?.let {
                QueueBanner(QueueTone.from(it.optString("tone")), it.optString("text"))
            },
            globalActions = o.optJSONArray("global_actions").mapObjects(::parseAction),
            agents = o.optJSONArray("agents")
                .mapObjects(::parseAgent)
                .filter { it.pane.isNotBlank() }
                .distinctBy(QueueAgent::pane)
                // Sắp ở đây chứ không ở roster: agents.first() là pane mặc
                // định khi chưa chọn gì, nên mở Queue là đứng ngay ở agent
                // đang cần duyệt.
                .byPriority(),
            // Every action needs an id, and placeholder ids would collide in
            // LazyColumn keys. Invalid rows are safer to omit.
            tasks = o.optJSONArray("tasks")
                .mapObjects(::parseTask)
                .filter { it.id >= 0 }
                .distinctBy(QueueTask::id),
        )

        private fun parseAction(o: JSONObject): QueueAction {
            val op = o.optString("op")
            return QueueAction(
                op = op,
                // qsrv gửi sẵn nhãn (_OP_LABEL); thiếu thì lấy tên op cho nút khỏi trống.
                label = o.optString("label").ifBlank { op.replace('_', ' ').replaceFirstChar(Char::uppercase) },
                needsRev = o.optBoolean("needs_rev", false),
                danger = o.optBoolean("danger", false),
            )
        }

        private fun parseAgent(o: JSONObject) = QueueAgent(
            pane = o.optString("pane"),
            name = o.optString("name").ifEmpty { o.optString("pane") },
            glyph = o.optString("glyph").ifEmpty { "•" },
            tone = QueueTone.from(o.optString("tone")),
            label = o.optString("label"),
            word = o.optString("word"),
            chatRev = o.optStringOrNull("chat_rev"),
            agent = o.optStringOrNull("agent"),
            preview = o.optString("preview"),
            previewTs = o.optString("preview_ts"),
            cwd = o.optString("cwd"),
        )

        private fun parseTask(o: JSONObject) = QueueTask(
            id = o.optInt("id", -1),
            target = o.optString("target"),
            text = o.optString("text"),
            state = o.optString("state"),
            glyph = o.optString("glyph").ifEmpty { "•" },
            tone = QueueTone.from(o.optString("tone")),
            // Preserve an unknown raw state instead of silently looking pending.
            stateLabel = o.optString("state_label").ifEmpty { o.optString("state") },
            sub = o.optString("sub"),
            actions = o.optJSONArray("actions").mapObjects(::parseAction),
            hasResp = o.optBoolean("has_resp", false),
        )

        /** A malformed row must not hide the rest of a valid queue. */
        private fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
            if (this == null) return emptyList()
            val out = ArrayList<T>(length())
            for (i in 0 until length()) {
                optJSONObject(i)?.let { out.add(f(it)) }
            }
            return out
        }
    }
}


/**
 * Một tin trong màn CHAT của agent (qsrv `GET /chat`, 16/9). qsrv đọc thẳng transcript
 * Claude Code từ đuôi file, không sao chép; app chỉ giữ trang đang xem.
 *
 * [role]: `user` (anh gõ) · `assistant` (markdown) · `tool` (một lần gọi tool, [res] là
 * kết quả đã cắt còn 2 KB, [resLen] độ dài thật) · `think` (chỉ hiện một dòng nhỏ).
 * [offset] = vị trí byte của bản ghi trong file — ổn định vì file chỉ nối thêm, dùng
 * để ghép trang mới với trang cũ và làm key danh sách.
 */
data class ChatMessage(
    val role: String,
    val uuid: String,
    val ts: String,
    val text: String = "",
    val toolName: String = "",
    val desc: String = "",
    val toolId: String = "",
    val res: String? = null,
    val resLen: Int = 0,
    val err: Boolean = false,
    val offset: Long = 0L,
    /** Thứ tự trong cùng bản ghi (một bản ghi assistant có thể vừa text vừa tool_use). */
    val sub: Int = 0,
    /**
     * Các đoạn dẫn trước của CÙNG một lượt (collapse ở read side — xem
     * [collapseAssistantTurns]); rỗng = tin một đoạn như trước. Bubble thường
     * chỉ hiện đoạn cuối, đoạn này nở ra khi chạm "N earlier".
     */
    val paras: List<String> = emptyList(),
) {
    val key: String get() = "$offset:$sub"
    val isTool: Boolean get() = role == "tool"
}

data class ChatPage(
    val pane: String,
    val name: String,
    val cwd: String,
    /** Home của user trên host (qsrv), để ghép đường dẫn file ⊕ tải lên: `<home>/inbox/<tên>`. */
    val home: String,
    val file: String,
    val size: Long,
    val rev: String,
    val messages: List<ChatMessage>,
    /** Offset để xin trang cũ hơn (`before=`); null = đã tới đầu file. */
    val cursor: Long?,
    val hasMore: Boolean,
) {
    companion object {
        fun parse(json: String): ChatPage {
            val o = JSONObject(json)
            val arr = o.optJSONArray("messages")
            val out = ArrayList<ChatMessage>(arr?.length() ?: 0)
            var lastOff = -1L
            var sub = 0
            if (arr != null) for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val role = m.optString("role")
                if (role !in CHAT_ROLES) continue
                val off = m.optLong("off", -1L)
                sub = if (off == lastOff) sub + 1 else 0
                lastOff = off
                out += ChatMessage(
                    role = role,
                    uuid = m.optString("uuid"),
                    ts = m.optString("ts"),
                    text = m.optString("text"),
                    toolName = m.optString("name"),
                    desc = m.optString("desc"),
                    toolId = m.optString("id"),
                    res = if (m.has("res") && !m.isNull("res")) m.optString("res") else null,
                    resLen = m.optInt("resLen", 0),
                    err = m.optBoolean("err", false),
                    offset = off,
                    sub = sub,
                )
            }
            return ChatPage(
                pane = o.optString("pane"),
                name = o.optString("name"),
                cwd = o.optString("cwd"),
                home = o.optString("home"),
                file = o.optString("file"),
                size = o.optLong("size", 0L),
                rev = o.optString("rev"),
                messages = out,
                cursor = if (o.has("cursor") && !o.isNull("cursor")) o.optLong("cursor") else null,
                hasMore = o.optBoolean("has_more", false),
            )
        }

        private val CHAT_ROLES = setOf("user", "assistant", "tool", "think")
    }
}

/**
 * Một dòng của `qsrv /files/search` (25/9): [path] tương đối trong vùng portal,
 * [isDir] = mục này là thư mục (qsrv đặt khóa JSON "dir" nhưng giá trị là BOOL).
 */
data class FileHit(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val mtimeMs: Long,
) {
    /** Thư mục CHỨA mục này ("" = gốc portal) — chạm kết quả là nhảy vào đó (prototype duyệt 25/9). */
    val parentDir: String get() = path.substringBeforeLast('/', "")
}

/**
 * Kết quả `GET /files/search`: [total] là TỔNG khớp trên chỉ mục, [hits] chỉ là
 * trang đầu (server cắt theo limit). [error] chỉ khác null khi client tự điền
 * lúc gọi hỏng — parse JSON thành công thì không bao giờ có.
 */
data class FileSearchResult(
    val hits: List<FileHit>,
    val total: Int,
    val tookMs: Double,
    val error: String? = null,
) {
    companion object {
        fun parse(json: String): FileSearchResult {
            val o = JSONObject(json)
            val arr = o.optJSONArray("results")
            val out = ArrayList<FileHit>(arr?.length() ?: 0)
            if (arr != null) for (i in 0 until arr.length()) {
                val h = arr.optJSONObject(i) ?: continue
                out += FileHit(
                    path = h.optString("path"),
                    name = h.optString("name"),
                    isDir = h.optBoolean("dir", false),
                    size = h.optLong("size", 0L),
                    // qsrv trả mtime GIÂY (st.st_mtime) — UI quen mili giây như dufs.
                    mtimeMs = h.optLong("mtime", 0L) * 1000,
                )
            }
            return FileSearchResult(
                hits = out,
                total = o.optInt("total", out.size),
                tookMs = o.optDouble("took_ms", 0.0),
            )
        }
    }
}

/**
 * Gộp các đoạn assistant LIÊN TIẾP của cùng một lượt (không có user/tool chen giữa)
 * thành một bubble: đoạn cuối là `text`, các đoạn trước nằm trong [ChatMessage.paras]
 * (bubble thường chỉ hiện đoạn cuối). `think` bị bỏ khỏi tầm nhìn mặc định — và vì
 * nó bị bỏ NÊN hai đoạn assistant kề nhau qua một block think vẫn gộp làm một.
 * Key giữ của tin ĐẦU để bubble không đổi chỗ khi lượt dài thêm giữa hai lần poll.
 */
fun collapseAssistantTurns(messages: List<ChatMessage>): List<ChatMessage> {
    val out = ArrayList<ChatMessage>(messages.size)
    var i = 0
    while (i < messages.size) {
        val m = messages[i]
        when {
            m.role == "think" -> i++
            m.role == "assistant" && m.text.isBlank() -> i++
            m.role == "assistant" -> {
                val run = ArrayList<String>()
                run.add(m.text)
                var last = m
                var j = i + 1
                while (j < messages.size) {
                    val n = messages[j]
                    // think + assistant rỗng (bản ghi chỉ tool_use) là "vô hình" nên
                    // bị NHẢY QUA chứ không cắt lượt.
                    if (n.role == "think" || (n.role == "assistant" && n.text.isBlank())) { j++; continue }
                    if (n.role != "assistant") break
                    run.add(n.text); last = n; j++
                }
                out.add(if (run.size == 1) m else m.copy(text = last.text, ts = last.ts, paras = run.dropLast(1)))
                i = j
            }
            else -> { out.add(m); i++ }
        }
    }
    return out
}

/**
 * Prompt đang chặn một pane — qsrv `GET /blocked` bóc từ màn hình pane (21/9): Claude Code
 * xin quyền chạy lệnh (`permission`), AskUserQuestion (`question`) hay menu lạ (`generic`).
 * Thẻ NEEDS YOU cuối chat vẽ từ đây; chạm lựa chọn = `POST /blocked/answer {pane, n}`.
 */
data class BlockedPrompt(
    val kind: String,
    val title: String,
    val question: String,
    /** Lệnh + mô tả (prompt xin quyền) — hiện trong khung xám trên câu hỏi. */
    val detail: List<String>,
    val options: List<BlockedOption>,
    /** Chân prompt nguyên văn ("Esc to cancel · Tab to amend"). */
    val hint: String,
    /** Form AskUserQuestion chọn NHIỀU (23/9): ô "[ ]", tích rồi SUBMIT = `POST /blocked/answer {pane, ns}`. */
    val multi: Boolean = false,
    /** Form NHIỀU câu hỏi (23/9): số ô tab trên đầu form; ≥ 2 thì nút của thẻ chọn nhiều là NEXT, không phải SUBMIT. */
    val tabCount: Int = 0,
    /** "2/3" = đang ở câu 2 của 3 (rỗng khi form một câu hoặc trang Review). */
    val step: String = "",
    /** Trang cuối "Review your answers": detail = các cặp câu hỏi → trả lời, lựa chọn Submit answers / Cancel. */
    val review: Boolean = false,
    /**
     * Nút của thẻ chọn nhiều GỬI luôn (true) hay chỉ SANG CÂU KẾ (false) — qsrv tính theo từng TUI (23/9):
     * Claude/opencode form một câu = gửi, nhiều câu = sang câu kế rồi trang Review; agy câu cuối = gửi, câu giữa = Next.
     */
    val submits: Boolean = true,
) {
    /** Đổi khi prompt đổi — để biết số vừa gửi đã "ăn" (prompt biến mất/đổi) hay chưa. */
    val signature: String
        get() = "$kind|$title|$question|" + options.joinToString("|") { "${it.n}:${it.label}" }

    companion object {
        /** null = pane không có prompt bóc được (hoặc không có lựa chọn nào — không có gì để chạm). */
        fun parse(o: JSONObject?): BlockedPrompt? {
            if (o == null) return null
            val options = ArrayList<BlockedOption>()
            o.optJSONArray("options")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val x = arr.optJSONObject(i) ?: continue
                    val n = x.optInt("n", -1)
                    if (n < 1) continue
                    options += BlockedOption(
                        n, x.optString("label"), x.optString("desc"), x.optBoolean("selected", false),
                        checkbox = x.optBoolean("checkbox", false), checked = x.optBoolean("checked", false),
                    )
                }
            }
            if (options.isEmpty()) return null
            val detail = ArrayList<String>()
            o.optJSONArray("detail")?.let { arr ->
                for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let(detail::add)
            }
            return BlockedPrompt(
                kind = o.optString("kind").ifBlank { "generic" },
                title = o.optString("title"),
                question = o.optString("question"),
                detail = detail,
                options = options,
                hint = o.optString("hint"),
                multi = o.optBoolean("multi", false),
                tabCount = o.optJSONArray("tabs")?.length() ?: 0,
                step = o.optString("step"),
                review = o.optBoolean("review", false),
                submits = o.optBoolean("submits", (o.optJSONArray("tabs")?.length() ?: 0) < 2),
            )
        }
    }
}

/** `answered` của thẻ khi đã SUBMIT một bộ ô tích (không phải một số lựa chọn). */
/** Thư mục gợi ý cho tấm NEW SESSION (qsrv /launch/recent): cwd các phiên đang chạy + lịch sử mở. */
data class LaunchDir(val path: String, val short: String, val agent: String = "", val name: String = "")

const val BLOCKED_MULTI_SENT = -1

data class BlockedOption(
    val n: Int,
    val label: String,
    val desc: String = "",
    /** Lựa chọn Claude đang trỏ (❯) trên terminal. */
    val selected: Boolean = false,
    /** Ô tích của form chọn nhiều ("[ ] apple"); `checked` = đang tích trên terminal. */
    val checkbox: Boolean = false,
    val checked: Boolean = false,
) {
    /**
     * Lựa chọn "gõ tiếp": gửi số xong, câu trả lời gõ ở ô dưới đi thẳng vào pane. Claude Code:
     * "Type something." / "Chat about this"; opencode: "Type your own answer"; agy: "Write-in...".
     */
    val opensComposer: Boolean
        get() = label.trim().trimEnd('.', '…').lowercase().let {
            it == "type something" || it == "chat about this" || it == "type your own answer" || it.startsWith("write-in")
        }
}

// ── DÒNG THỜI GIAN kiểu T2 (user chốt 22/9): ô 30 phút, trong ô gom theo phiên ──────────────

/** "2026-09-16T05:04:31.123Z" → epoch giây; chuỗi lạ → null. */
fun isoEpochSec(ts: String): Long? {
    if (ts.length < 19) return null
    return try {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        f.timeZone = java.util.TimeZone.getTimeZone("UTC")
        f.parse(ts.substring(0, 19))?.time?.div(1000L)
    } catch (e: Exception) {
        null
    }
}
