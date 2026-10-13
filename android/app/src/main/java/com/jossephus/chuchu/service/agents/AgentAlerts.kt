package com.jossephus.chuchu.service.agents

import com.jossephus.chuchu.ui.screens.Queue.AgentState

/**
 * Luật báo thức của [AgentWatch] — tách khỏi Android để unit-test bằng JVM thuần.
 *
 * Hai sự kiện duy nhất đáng nổi thông báo (user chốt 12/10):
 *  · vào Blocked  → NEEDS YOU (agent đang chờ người trả lời)
 *  · vào Done     → XONG, kèm tin cuối của agent (preview ≤160 ký tự)
 * Rời khỏi hai trạng thái đó = huỷ thông báo (trả lời ở nơi khác, hoặc agent chạy
 * tiếp việc mới).
 *
 * Lần poll đầu sau khi service khởi động (seed) chỉ GHI NHỚ, không báo: trạng thái
 * lúc app vừa xuống nền là trạng thái user vừa nhìn thấy trong app — báo lại là spam.
 */
enum class AgentAlertKind { NeedsYou, Done }

data class AgentSnap(
    val pane: String,
    val name: String,
    val label: String,
    val preview: String,
)

sealed interface AgentAlertAction {
    val pane: String

    data class Post(
        override val pane: String,
        val kind: AgentAlertKind,
        val agentName: String,
        val text: String,
    ) : AgentAlertAction

    data class Cancel(override val pane: String) : AgentAlertAction
}

class AgentTransitionTracker {
    private val last = LinkedHashMap<String, AgentSnap>()
    private var seeded = false

    /** Đổi server (URL/token): rev + pane của qsrv cũ là rác — seed lại từ đầu. */
    fun reset() {
        last.clear()
        seeded = false
    }

    /**
     * Nạp trạng thái đã lưu (phiên trực bằng alarm sống qua process bị dọn): so TIẾP,
     * không seed lại — seed lại là nuốt mất chuyển trạng thái đã xảy ra lúc vắng mặt.
     */
    fun restore(agents: List<AgentSnap>) {
        last.clear()
        agents.forEach { last[it.pane] = it }
        seeded = true
    }

    fun onState(agents: List<AgentSnap>): List<AgentAlertAction> {
        if (!seeded) {
            seeded = true
            last.clear()
            agents.forEach { last[it.pane] = it }
            return emptyList()
        }
        val out = ArrayList<AgentAlertAction>()
        val current = HashSet<String>(agents.size)
        for (a in agents) {
            current += a.pane
            val prev = last[a.pane]
            if (prev != null) {
                val was = AgentState.of(prev.label)
                val now = AgentState.of(a.label)
                if (was != AgentState.Blocked && now == AgentState.Blocked) {
                    out += AgentAlertAction.Post(a.pane, AgentAlertKind.NeedsYou, a.name, a.preview)
                }
                if (was == AgentState.Blocked && now != AgentState.Blocked) {
                    out += AgentAlertAction.Cancel(a.pane)
                }
                if (was != AgentState.Done && now == AgentState.Done) {
                    out += AgentAlertAction.Post(a.pane, AgentAlertKind.Done, a.name, a.preview)
                }
                if (was == AgentState.Done && now != AgentState.Done) {
                    out += AgentAlertAction.Cancel(a.pane)
                }
            }
            last[a.pane] = a
        }
        // Pane biến mất khỏi /state (đóng phiên, đổi chủ) — dọn thông báo treo.
        for (pane in last.keys - current) {
            out += AgentAlertAction.Cancel(pane)
            last.remove(pane)
        }
        return out
    }
}

/**
 * Làm gọn tin cuối trước khi lên notification (user 13/10: "thanh lịch hơn"): dồn
 * khoảng trắng, bỏ ký hiệu markdown/quote/bullet đầu dòng, cắt ở RANH GIỚI TỪ (không
 * cắt giữa chữ) — tin cuối của agent hay có `**`, `#`, danh sách, URL dài.
 */
internal fun refineAlertText(raw: String, limit: Int = 140): String {
    // Bỏ markdown/quote/bullet theo TỪNG DÒNG trước khi dồn trắng — dồn trước là mất
    // anchor đầu dòng, bullet của dòng 2+ thành giữa câu (test bắt 13/10).
    var t = raw
    t = t.replace(Regex("(?m)^#{1,6}\\s*"), "")
    t = t.replace(Regex("(?m)^>\\s*"), "")
    t = t.replace(Regex("(?m)^[-•·]+\\s*"), "")
    t = t.replace(Regex("[`*]+"), "")
    t = t.replace(Regex("\\s+"), " ").trim()
    if (t.length > limit) {
        val cut = t.take(limit)
        t = cut.substringBeforeLast(' ', cut).trimEnd().trimEnd(',', ';', ':') + "…"
    }
    return t.trim()
}
