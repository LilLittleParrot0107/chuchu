package com.jossephus.chuchu.ui.screens.Queue

/**
 * Cache transcript theo pane + chính sách reconnect khôn (cand-2, 10/2026).
 *
 * Khác cand-1 (VM giữ Map bản thô + NetworkCallback TRONG VM): cand-2 giữ NGUYÊN
 * snapshot [ChatUiState] theo pane (không tách messages/rev/cursor thành struct
 * riêng), ghép trang mới qua [withFreshPage]; còn mạng do TẦNG UI
 * (QueueDestination trong ApplicationNavController) nghe rồi đẩy vào VM qua
 * [QueueViewModel.setOnline] — VM chỉ là người nghe, không giữ callback nên
 * unit-test được mà không cần Context.
 *
 * Mở chat phải hiện tin cũ NGAY (kể cả mất mạng vẫn đọc được), rồi mới refresh
 * ngầm qua chatRefreshOnce. Mất mạng thì vòng poll dừng hẳn (không bắn
 * `client.chat`), có mạng lại thì reset backoff + đọc ngay.
 */

/** Số snapshot pane giữ tối đa — quá thì rớt pane ít xem nhất (LRU). */
const val MAX_CHAT_SNAPSHOTS = 20

/**
 * Có nên poll chat không. Mất mạng là dừng hẳn để khỏi bắn request chết
 * (radio thức + log lỗi loè), có mạng lại vòng poll tự chạy tiếp.
 */
fun shouldPollChat(isOnline: Boolean): Boolean = isOnline

/**
 * Backoff vòng poll chat: reconnect về base để đọc ngay nhịp sau; lỗi server thì
 * nhân đôi có sàn base + trần max (tránh nhân từ 0 ra 0 rồi spam); còn lại giữ nguyên.
 * Base/cap lấy từ QueueViewModel — một nguồn duy nhất, khỏi lệch nhịp 2s/30s.
 */
fun nextChatBackoff(currentBackoffMs: Long, reconnected: Boolean, failed: Boolean): Long {
    if (reconnected) return QueueViewModel.FOREGROUND_POLL_MS
    if (failed) return minOf(maxOf(currentBackoffMs * 2, QueueViewModel.FOREGROUND_POLL_MS), QueueViewModel.MAX_FOREGROUND_BACKOFF_MS)
    return currentBackoffMs
}

/**
 * true nếu [freshRev] mới hơn [currentRev]. Rev của qsrv là `mtime_ns.size`
 * (xem `chat_rev`) nên phải so SỐ từng phần — so chuỗi thì "90" > "100" là sai.
 * Không parse được mà chuỗi khác nhau thì coi như mới (đỡ kẹt); rỗng/không đổi
 * thì không mới hơn.
 */
fun isChatRevNewer(currentRev: String, freshRev: String): Boolean {
    if (freshRev.isBlank()) return false
    if (currentRev.isBlank()) return true
    if (freshRev == currentRev) return false
    val cur = currentRev.split('.')
    val fresh = freshRev.split('.')
    if (cur.size == 2 && fresh.size == 2) {
        val curMtime = cur[0].toLongOrNull()
        val freshMtime = fresh[0].toLongOrNull()
        if (curMtime != null && freshMtime != null && curMtime != freshMtime) {
            return freshMtime > curMtime
        }
        val curSize = cur[1].toLongOrNull()
        val freshSize = fresh[1].toLongOrNull()
        if (curSize != null && freshSize != null && curSize != freshSize) {
            return freshSize > curSize
        }
        if (curMtime != null || curSize != null) return true
    }
    return true
}

/**
 * Ghép trang mới [page] vào snapshot đang xem: giữ tin cũ hơn đã tải (offset là
 * vị trí byte, ổn định vì file chỉ nối thêm), nối trang mới vào đuôi. Lần đầu
 * (chưa có gì) thì lấy hasMore/cursor của server; đã có tin thì giữ cursor cũ
 * (cursor là mốc "trang cũ hơn", trang fresh long-poll không mang mốc đó).
 */
fun ChatUiState.withFreshPage(page: ChatPage): ChatUiState {
    // Trả lời về trễ (long-poll cũ xả sau khi trang mới đã vào) mà đè rev cũ lên
    // là mất tin — rev không mới hơn thì giữ nguyên khung đang xem.
    if (!isChatRevNewer(rev, page.rev)) return copy(loading = false, error = null)
    val firstNew = page.messages.firstOrNull()?.offset ?: Long.MAX_VALUE
    val kept = messages.filter { it.offset < firstNew }
    val hasMore = if (kept.isEmpty()) page.hasMore else this.hasMore
    val cursor = if (kept.isEmpty()) page.cursor else this.cursor
    return copy(
        name = page.name.ifBlank { name },
        cwd = page.cwd,
        home = page.home.ifBlank { home },
        size = page.size,
        rev = page.rev,
        messages = kept + page.messages,
        hasMore = hasMore,
        cursor = cursor,
        loading = false,
        error = null,
        updatedAt = System.currentTimeMillis(),
    )
}

/**
 * Holder cache transcript theo pane: giữ NGUYÊN snapshot [ChatUiState] (không
 * tách messages/rev/cursor... thành struct thô như cand-1) để mở lại là phát
 * đúng khung đang xem, kể cả lúc offline. Trang trắng (chưa tải được gì) không
 * đè cache cũ — nếu không rớt mạng một phát là mất sạch tin đã đọc.
 */
class ChatSnapshotCache(private val maxPanes: Int = MAX_CHAT_SNAPSHOTS) {
    // Access-order = LRU thật: pane vừa xem/sờ vào thì "mới", rớt pane cũ nhất.
    private val snapshots = object : LinkedHashMap<String, ChatUiState>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ChatUiState>): Boolean =
            size > maxPanes
    }

    /** Snapshot đã xem của [pane], null = chưa xem bao giờ. */
    fun forPane(pane: String): ChatUiState? = snapshots[pane]

    /** Cất snapshot đang xem; bỏ qua trang trắng để khỏi đè cache cũ. */
    fun store(pane: String, snapshot: ChatUiState) {
        if (snapshot.messages.isEmpty() && snapshot.rev.isBlank()) return
        snapshots[pane] = snapshot
    }

    /** Đổi server (saveConfig): snapshot cũ là của qsrv khác — bỏ hết. */
    fun clear() {
        snapshots.clear()
    }

    /** Số pane đang giữ — test nắp LRU. */
    fun size(): Int = snapshots.size
}
