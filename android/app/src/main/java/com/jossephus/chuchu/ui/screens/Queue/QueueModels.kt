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
    /** Loại agent herdr báo ("claude"/"opencode"/"agy") — tô màu tên theo loại (user chốt 16/9). */
    val agent: String? = null,
    /** Tin cuối của transcript, dồn 1 dòng ≤160 ký tự — hàng HỘI THOẠI (UI G1) đọc nhanh. */
    val preview: String = "",
    /** Giờ ISO UTC của tin cuối (qsrv /state preview_ts); "" = không có. */
    val previewTs: String = "",
    /** Thư mục làm việc hiện tại (working directory) của session agent. */
    val cwd: String = "",
) {
    /**
     * Thứ tự trên roster — số nhỏ lên trên (user chốt 4/9): thứ cần TAY người
     * (đang hỏi duyệt) trên cùng, rồi thứ đang chạy, rồi thứ chưa rõ, còn rảnh
     * xuống đáy. Đọc theo NHÃN đã dịch của qsrv (A_VIEW) chứ không theo tone:
     * tone chỉ là màu, hai trạng thái khác nhau có thể cùng màu.
     */
    // User chốt 16/9: vừa xong (done) kéo xuống NGAY DƯỚI các agent đang chạy, trên idle.
    val priority: Int get() = when (label.trim().lowercase()) {
        "needs approval", "blocked" -> 0
        "working", "busy", "sending", "running" -> 1
        "done" -> 2
        "idle" -> 3
        "unknown" -> 4
        else -> 5                                  // down/gone/nhãn lạ: cuối
    }
}

/**
 * Sắp theo [QueueAgent.priority]. Trong nhóm ĐÃ XONG / RẢNH, session có hoạt động gần nhất
 * (previewTs mới nhất) lên trước (18/9). Nhóm cần duyệt / đang chạy GIỮ thứ tự server
 * (= thứ tự herdr, sortedWith là stable): agent vừa nói mà nhảy lên đầu thì hàng đảo liên tục
 * khi nhiều agent cùng chạy — đúng cái đã cố tình tránh từ 4/9.
 */
fun List<QueueAgent>.byPriority(): List<QueueAgent> = sortedWith(
    compareBy<QueueAgent> { it.priority }
        .thenByDescending { if (it.priority >= 2) it.previewTs else "" }
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
private val justNowLegacy = Regex("\\bvua xong\\b", RegexOption.IGNORE_CASE)
private val minutesAgoLegacy = Regex("\\b(\\d+) phut truoc\\b", RegexOption.IGNORE_CASE)
private val hoursAgoLegacy = Regex("\\b(\\d+) gio truoc\\b", RegexOption.IGNORE_CASE)
private val daysAgoLegacy = Regex("\\b(\\d+) ngay truoc\\b", RegexOption.IGNORE_CASE)

/** Keep legacy qsrv payloads from leaking Vietnamese labels into the app. */
private fun englishQueueLabel(raw: String): String = when (raw.trim().lowercase()) {
    "dang cho" -> "waiting"
    "dang gui" -> "sending"
    "da gui", "dang chay" -> "running"
    "xong" -> "done"
    "that bai" -> "failed"
    "khong ro", "chua ro" -> "unknown"
    "con job" -> "busy"
    "cho duyet", "can anh" -> "needs approval"
    "ranh" -> "idle"
    "hang doi dang tam dung" -> "Queue is paused"
    else -> raw
}

private fun englishQueueActionLabel(op: String): String = when (op.lowercase()) {
    "top" -> "Move first"
    "up" -> "Move up"
    "del", "delete", "rm" -> "Delete"
    "retry" -> "Retry"
    "pause" -> "Pause"
    "resume" -> "Resume"
    "cancel" -> "Cancel"
    else -> op.replace('_', ' ').ifBlank { "Action" }
}

private fun englishQueueSub(raw: String): String = raw
    .replace(justNowLegacy, "just now")
    .replace(minutesAgoLegacy, "$1m ago")
    .replace(hoursAgoLegacy, "$1h ago")
    .replace(daysAgoLegacy, "$1d ago")

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
                QueueBanner(QueueTone.from(it.optString("tone")), englishQueueLabel(it.optString("text")))
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

        private fun parseAction(o: JSONObject) = QueueAction(
            op = o.optString("op"),
            label = englishQueueActionLabel(o.optString("op")),
            needsRev = o.optBoolean("needs_rev", false),
            danger = o.optBoolean("danger", false),
        )

        private fun parseAgent(o: JSONObject) = QueueAgent(
            pane = o.optString("pane"),
            name = o.optString("name").ifEmpty { o.optString("pane") },
            glyph = o.optString("glyph").ifEmpty { "•" },
            tone = QueueTone.from(o.optString("tone")),
            label = englishQueueLabel(o.optString("label")),
            word = englishQueueLabel(o.optString("word")),
            chatRev = o.optString("chat_rev").takeIf { it.isNotBlank() && it != "null" },
            agent = o.optString("agent").takeIf { it.isNotBlank() && it != "null" },
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
            stateLabel = englishQueueLabel(o.optString("state_label").ifEmpty { o.optString("state") }),
            sub = englishQueueSub(o.optString("sub")),
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
 * Một tin trên DÒNG THỜI GIAN (qsrv `GET /feed`, UI G1 16/9): tin cuối của các session
 * đang động, gộp theo giờ. [role] chỉ có `user` (anh gõ) và `assistant` — tool/think bị
 * server lọc bỏ để dòng thời gian đọc như tin nhắn.
 */
data class FeedMessage(
    val pane: String,
    val name: String,
    val agent: String?,
    val label: String,
    val tone: QueueTone,
    val role: String,
    val ts: String,
    val text: String,
    val uuid: String = "",
    /** Vị trí bản ghi trong transcript — ổn định, dùng làm key danh sách. */
    val offset: Long = 0L,
    /** Đoạn dẫn trước của cùng lượt trên DÒNG THỜI GIAN (xem [collapseFeedTurns]). */
    val paras: List<String> = emptyList(),
) {
    // uuid một mình không đủ: một bản ghi assistant có thể có nhiều đoạn text. offset
    // của opencode là rowid message nên hai đoạn cùng offset nhưng khác uuid.
    val key: String get() = "$pane:${uuid.ifBlank { ts }}:$offset"
}

data class FeedPage(
    val rev: String,
    val pane: String?,
    val messages: List<FeedMessage>,
) {
    companion object {
        fun parse(json: String): FeedPage {
            val o = JSONObject(json)
            val arr = o.optJSONArray("messages")
            val out = ArrayList<FeedMessage>(arr?.length() ?: 0)
            if (arr != null) for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val role = m.optString("role")
                if (role !in FEED_ROLES) continue
                out += FeedMessage(
                    pane = m.optString("pane"),
                    name = m.optString("name"),
                    agent = m.optString("agent").takeIf { it.isNotBlank() && it != "null" },
                    label = englishQueueLabel(m.optString("label")),
                    tone = QueueTone.from(m.optString("tone")),
                    role = role,
                    ts = m.optString("ts"),
                    text = m.optString("text"),
                    uuid = m.optString("uuid"),
                    offset = m.optLong("off", 0L),
                )
            }
            return FeedPage(
                rev = o.optString("rev"),
                pane = o.optString("pane").takeIf { it.isNotBlank() && it != "null" },
                messages = out,
            )
        }

        private val FEED_ROLES = setOf("user", "assistant")
    }
}

// ── Dọn "response thừa" trong transcript (user duyệt 17/9, 5 mục) ──────────────
// qsrv giữ NGUYÊN dữ liệu (hạ tầng chung với qq); mọi cắt gọt nằm ở tầng đọc của
// app. Các hàm dưới thuần Kotlin, không Compose — unit test chạy được trên JVM.

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
 * Như [collapseAssistantTurns] cho DÒNG THỜI GIAN: feed xen kẽ nhiều pane nên
 * một lượt phải là assistant LIÊN TIẾP CÙNG PANE. Nhãn/chấm màu lấy của tin cuối
 * (trạng thái mới nhất của phiên), key giữ của tin đầu.
 */
fun collapseFeedTurns(messages: List<FeedMessage>): List<FeedMessage> {
    val out = ArrayList<FeedMessage>(messages.size)
    var i = 0
    while (i < messages.size) {
        val m = messages[i]
        if (m.role == "assistant" && m.text.isNotBlank()) {
            val run = ArrayList<String>()
            run.add(m.text)
            var last = m
            var j = i + 1
            while (j < messages.size && messages[j].role == "assistant" && messages[j].pane == m.pane && messages[j].text.isNotBlank()) {
                run.add(messages[j].text); last = messages[j]; j++
            }
            out.add(if (run.size == 1) m else m.copy(name = last.name, label = last.label, tone = last.tone, ts = last.ts, text = last.text, paras = run.dropLast(1)))
            i = j
        } else {
            out.add(m); i++
        }
    }
    return out
}

/**
 * Tin gửi từ ô gõ hiện CẢ HAI: pending task (vừa xếp) và user message (khi agent
 * kịp đọc vào transcript). Việc nào text đã xuất hiện trong transcript thì ẩn chip,
 * hết tranh chỗ — đó chính là mục dedup của thoả thuận 17/9.
 */
fun pendingChipTasks(tasks: List<QueueTask>, messages: List<ChatMessage>): List<QueueTask> {
    val echoed = HashSet<String>()
    for (m in messages) if (m.role == "user") echoed.add(m.text.trim())
    return tasks.filter { !it.isCompleted && !it.isFailed && it.text.trim() !in echoed }
}
