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
    private const val CLEAR_DONE_PREFIX = "clear-done:"

    fun clearDone(targetPane: String?): String = "$CLEAR_DONE_PREFIX${targetPane ?: "*"}"
    fun isClearDone(key: String): Boolean = key.startsWith(CLEAR_DONE_PREFIX)
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
 * Sắp theo [QueueAgent.priority], giữ nguyên thứ tự server (= thứ tự herdr)
 * trong cùng một hạng — sortedBy là stable — để hai agent cùng đang chạy không
 * đổi chỗ nhau mỗi lần poll.
 */
fun List<QueueAgent>.byPriority(): List<QueueAgent> = sortedBy { it.priority }

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
