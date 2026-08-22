package com.jossephus.chuchu.ui.screens.Queue

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
                .distinctBy(QueueAgent::pane),
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
