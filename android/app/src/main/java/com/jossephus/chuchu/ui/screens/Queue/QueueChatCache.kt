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

/** Một tin rút gọn để gộp cache — key là `offset:sub` của [ChatMessage.key]. */
data class ChatCacheMsg(val key: String, val text: String)

/**
 * Gộp tin cached với trang mới về: khử trùng theo key, giữ thứ tự xuất hiện.
 * Tin fresh trùng key thì lấy bản mới (vị trí cũ giữ nguyên để list khỏi nhảy).
 */
fun mergeChatCache(cached: List<ChatCacheMsg>, fresh: List<ChatCacheMsg>): List<ChatCacheMsg> {
    if (cached.isEmpty()) return fresh.toList()
    if (fresh.isEmpty()) return cached.toList()
    val merged = LinkedHashMap<String, ChatCacheMsg>(cached.size + fresh.size)
    for (m in cached) merged.putIfAbsent(m.key, m)
    for (m in fresh) merged[m.key] = m
    // LinkedHashMap giữ thứ tự chèn lần đầu — key trùng của fresh chỉ đổi text,
    // không đẩy tin lên đầu/đuôi nên LazyColumn không nhảy.
    return merged.values.toList()
}

/**
 * Có nên poll chat không. Mất mạng là dừng hẳn để khỏi bắn request chết
 * (radio thức + log lỗi loè), có mạng lại vòng poll tự chạy tiếp.
 */
fun shouldPollChat(isOnline: Boolean, failed: Boolean): Boolean = isOnline

// Base/cap trùng hằng số vòng poll chat trong QueueViewModel (FOREGROUND_POLL_MS
// = 2000, MAX_FOREGROUND_BACKOFF_MS = 30000) — giữ một nhịp 2s lúc thường, hỏng
// thì giãn mũ tới 30s. Để literal ở đây vì helper là pure, không với tới private
// const của ViewModel.
private const val CHAT_BACKOFF_BASE_MS = 2000L
private const val CHAT_BACKOFF_MAX_MS = 30000L

/**
 * Backoff vòng poll chat: reconnect về base để đọc ngay nhịp sau; lỗi server thì
 * nhân đôi có sàn base + trần max (tránh nhân từ 0 ra 0 rồi spam); còn lại giữ nguyên.
 */
fun nextChatBackoff(currentBackoffMs: Long, reconnected: Boolean, failed: Boolean): Long {
    if (reconnected) return CHAT_BACKOFF_BASE_MS
    if (failed) return minOf(maxOf(currentBackoffMs * 2, CHAT_BACKOFF_BASE_MS), CHAT_BACKOFF_MAX_MS)
    return currentBackoffMs
}

/**
 * Ghép trang mới [page] vào snapshot đang xem: giữ tin cũ hơn đã tải (offset là
 * vị trí byte, ổn định vì file chỉ nối thêm), nối trang mới vào đuôi. Lần đầu
 * (chưa có gì) thì lấy hasMore/cursor của server; đã có tin thì giữ cursor cũ
 * (cursor là mốc "trang cũ hơn", trang fresh long-poll không mang mốc đó).
 */
fun ChatUiState.withFreshPage(page: ChatPage): ChatUiState {
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
class ChatSnapshotCache {
    private val snapshots = mutableMapOf<String, ChatUiState>()

    /** Snapshot đã xem của [pane], null = chưa xem bao giờ. */
    fun forPane(pane: String): ChatUiState? = snapshots[pane]

    /** Cất snapshot đang xem; bỏ qua trang trắng để khỏi đè cache cũ. */
    fun store(pane: String, snapshot: ChatUiState) {
        if (snapshot.messages.isEmpty() && snapshot.rev.isBlank()) return
        snapshots[pane] = snapshot
    }
}
