package com.jossephus.chuchu.ui.screens.Queue

import org.json.JSONArray
import org.json.JSONObject

/**
 * Mô hình cho màn hình hàng đợi task (qsrv `GET /state?view=app`).
 *
 * Nguyên tắc: **app không đoán nghĩa**. Biểu tượng, màu, nhãn và danh sách thao
 * tác hợp lệ đều do qsrv gửi xuống — vì mỗi lần sửa phía app là 15 phút build CI,
 * còn sửa phía server là sửa xong dùng ngay. Trong một ngày (20/8) qd/qq/qsrv đã
 * lệch nghĩa ba lần; giữ bảng trạng thái ở một chỗ là cách duy nhất không tái diễn.
 *
 * Hệ quả cho việc parse: **không bao giờ ném lỗi vì thiếu hoặc lạ trường**.
 * Trường lạ thì bỏ qua, trường thiếu thì lấy mặc định — server mới nói chuyện
 * được với app cũ và ngược lại.
 */

enum class QueueTone {
    Dim, Accent, Ok, Warn, Error;

    companion object {
        /** Tông lạ → Dim: nhạt nhưng vẫn đọc được, không bao giờ vô hình. */
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
    /** Pane của agent nhận việc — dùng để lọc việc theo agent, y như qq. */
    val target: String,
    val text: String,
    val state: String,
    val glyph: String,
    val tone: QueueTone,
    val stateLabel: String,
    val sub: String,
    val actions: List<QueueAction>,
)

data class QueueAgent(
    val pane: String,
    val name: String,
    val glyph: String,
    val tone: QueueTone,
    val label: String,
    /** Chữ ngắn hiện cạnh tên khi cần chú ý ("cần anh", "còn job"); qq gọi là word. */
    val word: String,
)

data class QueueBanner(
    val tone: QueueTone,
    val text: String,
)

data class QueueState(
    val rev: String,
    val paused: Boolean,
    val banner: QueueBanner?,
    val summary: String,
    val globalActions: List<QueueAction>,
    val agents: List<QueueAgent>,
    val tasks: List<QueueTask>,
) {
    companion object {
        val Empty = QueueState(
            rev = "",
            paused = false,
            banner = null,
            summary = "",
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
            summary = o.optString("summary"),
            globalActions = o.optJSONArray("global_actions").mapObjects(::parseAction),
            agents = o.optJSONArray("agents").mapObjects(::parseAgent),
            // Bỏ task không có id: mọi thao tác đều cần id nên nó vô dụng, mà
            // giữ lại thì hai task như thế cùng thành id -1 → trùng key trong
            // LazyColumn → văng app. Parser dễ tính không được đẻ ra key trùng.
            tasks = o.optJSONArray("tasks").mapObjects(::parseTask).filter { it.id >= 0 },
        )

        private fun parseAction(o: JSONObject) = QueueAction(
            op = o.optString("op"),
            label = o.optString("label").ifEmpty { o.optString("op") },
            needsRev = o.optBoolean("needs_rev", false),
            danger = o.optBoolean("danger", false),
        )

        private fun parseAgent(o: JSONObject) = QueueAgent(
            pane = o.optString("pane"),
            name = o.optString("name").ifEmpty { o.optString("pane") },
            glyph = o.optString("glyph").ifEmpty { "•" },
            tone = QueueTone.from(o.optString("tone")),
            label = o.optString("label"),
            word = o.optString("word"),
        )

        private fun parseTask(o: JSONObject) = QueueTask(
            id = o.optInt("id", -1),
            target = o.optString("target"),
            text = o.optString("text"),
            state = o.optString("state"),
            glyph = o.optString("glyph").ifEmpty { "•" },
            tone = QueueTone.from(o.optString("tone")),
            // Thiếu nhãn thì hiện tên trạng thái thô, KHÔNG để trống: trạng thái
            // qd mới nghĩ ra phải nhìn thấy được, đừng lặng lẽ giống pending.
            stateLabel = o.optString("state_label").ifEmpty { o.optString("state") },
            sub = o.optString("sub"),
            actions = o.optJSONArray("actions").mapObjects(::parseAction),
        )

        /** Bỏ qua phần tử không phải object thay vì làm hỏng cả danh sách. */
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
