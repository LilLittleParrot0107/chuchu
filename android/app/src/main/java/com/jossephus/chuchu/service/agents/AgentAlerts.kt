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

// Nhịp alarm (18/10 — user: "muốn tiết kiệm pin như Collie"): alarm KHÔNG còn là
// kênh chính. Mạch long-poll trong tiến trình đã cho thông báo tức thì; alarm chỉ
// là lưới dự phòng, nên không được chạy dày:
//  · guard còn sống  → 5 phút/lần (cứu guard chết âm thầm, không đua với long-poll);
//  · process bị dọn  → 2 phút/lần khi có agent bận, 5 phút/lần khi rảnh.
// Trước đây 15s/60s chạy song song long-poll: ~200–380 lần thức mạng/giờ, mà khi
// OEM bóp alarm thì vẫn trễ. Push (FCM/UnifiedPush) sẽ thay hẳn phần này.
internal const val WATCH_ALARM_MS = 5 * 60_000L
internal const val WATCH_ALARM_ACTIVE_MS = 2 * 60_000L
internal const val WATCH_FIRST_ALARM_MS = 2 * 60_000L

/** Nhịp alarm kế: guard sống thì chậm; guard chết mà có việc thì nhanh hơn. */
internal fun alarmIntervalMs(snaps: List<AgentSnap>, guardAlive: Boolean): Long {
    // qsrv gửi nhãn hiển thị ("needs approval", "unknown"), không phải mã thô
    // ("blocked", "unsure"). So qua AgentState để 2 phút thật sự bật cho pane
    // đang chờ duyệt — trước đây test dùng mã thô nên pass mà production thì không.
    val busy = snaps.any {
        when (AgentState.of(it.label)) {
            AgentState.Blocked, AgentState.Working, AgentState.Unknown -> true
            else -> it.label.trim().equals("sent", ignoreCase = true)
        }
    }
    return if (busy && !guardAlive) WATCH_ALARM_ACTIVE_MS else WATCH_ALARM_MS
}

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
            val now = AgentState.of(a.label)
            if (prev == null) {
                // Pane mới xuất hiện sau lần seed đầu. Nếu nó đã blocked (agent mở
                // ra rồi hỏi quyền ngay), phải báo; trước đây seed im lặng làm mất
                // NEEDS YOU của phiên mới.
                if (now == AgentState.Blocked) {
                    out += AgentAlertAction.Post(a.pane, AgentAlertKind.NeedsYou, a.name, a.preview)
                }
                last[a.pane] = a
                continue
            }
            val was = AgentState.of(prev.label)
            // Một pane chỉ có MỘT notification. Cancel phải đứng trước Post khi
            // chuyển Done→Blocked hoặc Blocked→Done, nếu không cancel sẽ xoá
            // đúng cái vừa post (Done→Blocked từng mất NEEDS YOU).
            if (now == AgentState.Blocked) {
                if (was == AgentState.Done) out += AgentAlertAction.Cancel(a.pane)
                if (was != AgentState.Blocked) {
                    out += AgentAlertAction.Post(a.pane, AgentAlertKind.NeedsYou, a.name, a.preview)
                }
            } else if (now == AgentState.Done) {
                if (was == AgentState.Blocked) out += AgentAlertAction.Cancel(a.pane)
                if (was != AgentState.Done) {
                    out += AgentAlertAction.Post(a.pane, AgentAlertKind.Done, a.name, a.preview)
                }
            } else if (was == AgentState.Blocked || was == AgentState.Done) {
                out += AgentAlertAction.Cancel(a.pane)
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

/** Xử lý notification khi alert của một pane hết hiệu lực. */
enum class AlertExit {
    /** Không còn gì để giữ — xoá hẳn notification. */
    Cancel,

    /** Cửa sổ bubble đang mở: hạ notification xuống dạng NEO im lặng. */
    Anchor,

    /** Cửa sổ đang mở nhưng không dựng lại được nội dung — để nguyên, đóng cửa sổ sẽ dọn. */
    Leave,
}

/**
 * Alert hết hiệu lực (agent rời Blocked/Done, hoặc pane biến mất khỏi `/state`) thì
 * làm gì với notification của pane?
 *
 * Notification gắn bubble CHÍNH LÀ cái neo của cửa sổ — xoá nó là SystemUI dỡ luôn
 * bubble. Nên khi user đang mở cửa sổ, alert hết hiệu lực chỉ được phép HẠ CẤP chứ
 * không được xoá. Đây đúng là lỗi "bubble biến mất sau khi chat phản hồi lại":
 * Blocked → Working xảy ra ngay lúc user vừa gửi câu trả lời, tracker bắn Cancel,
 * notification biến mất kéo theo cả cửa sổ.
 *
 * Hàm thuần để test được — luật này đã sai một lần nên cần khoá lại bằng test.
 */
internal fun alertExit(bubbleOpen: Boolean, hasSnapshot: Boolean): AlertExit = when {
    !bubbleOpen -> AlertExit.Cancel
    hasSnapshot -> AlertExit.Anchor
    else -> AlertExit.Leave
}
