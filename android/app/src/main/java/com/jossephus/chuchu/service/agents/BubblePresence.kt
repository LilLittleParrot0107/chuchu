package com.jossephus.chuchu.service.agents

/**
 * Pane nào đang có CỬA SỔ bubble mở trong tiến trình này.
 *
 * Bubble của kohi là bubble GẮN THÔNG BÁO: notification (tag=pane, id=ALERT_ID)
 * chính là cái neo. SystemUI thấy notification bị xoá là dỡ luôn cửa sổ. Vì vậy
 * mọi đường huỷ alert phải hỏi sổ này trước — nếu không, luật "rời Blocked/Done
 * thì huỷ báo" (12/10) sẽ bắn thẳng vào cửa sổ người dùng đang mở, đúng lúc họ
 * vừa trả lời xong và agent rời Blocked.
 *
 * Hai mức, vì "đang mở" và "đang nhìn" khác nhau:
 *  · [isOpen]    — activity còn sống, cửa sổ đang thu gọn thành đầu bubble hay
 *                  đang nở. Notification KHÔNG được xoá.
 *  · [isVisible] — activity đang start (nở ra trước mặt). Alert mới vẫn phải
 *                  cập nhật notification (nó là neo) nhưng im tiếng.
 *
 * ĐẾM THEO TOKEN, không phải theo pane (19/10 — rà soát bubble): cùng một pane có
 * thể có HAI instance activity sống song song, vì `documentLaunchMode="always"` tạo
 * task mới cho mỗi lần launch (bấm đầu bubble, rồi bấm shortcut hội thoại ở launcher
 * chẳng hạn). Sổ kiểu tập hợp theo pane sẽ coi instance chết là "cửa sổ đã đóng" và
 * [AgentNotifier.onBubbleClosed] xoá mất neo của cửa sổ còn lại — bubble đang mở tự
 * dưng tắt. Token là danh tính của TỪNG instance (lấy từ ViewModel để sống qua config
 * change), nên chỉ khi token cuối cùng đi thì pane mới thật sự đóng.
 *
 * Chỉ sống trong process: activity chết thì process cũng sắp chết theo, còn
 * notification sót lại đã có [AgentNotifier.cancelAll] dọn lúc mở app.
 */
internal object BubblePresence {

    /** pane → tập token của các instance đang sống. Mọi thao tác dưới [lock]. */
    private val open = HashMap<String, MutableSet<String>>()
    private val visible = HashMap<String, MutableSet<String>>()
    private val lock = Any()

    /** Cửa sổ bubble của [pane] vừa được tạo (hoặc tạo lại sau config change). */
    fun opened(pane: String, token: String) {
        if (pane.isBlank()) return
        synchronized(lock) { open.getOrPut(pane) { mutableSetOf() } += token }
    }

    /**
     * Instance [token] đóng hẳn. Config change thì đừng gọi — cửa sổ vẫn còn đó, và
     * instance mới dùng lại đúng token này (xem `BubbleWindowToken`).
     */
    fun closed(pane: String, token: String) {
        synchronized(lock) {
            drop(open, pane, token)
            drop(visible, pane, token)
        }
    }

    fun shown(pane: String, token: String) {
        if (pane.isBlank()) return
        synchronized(lock) { visible.getOrPut(pane) { mutableSetOf() } += token }
    }

    fun hidden(pane: String, token: String) {
        synchronized(lock) { drop(visible, pane, token) }
    }

    fun isOpen(pane: String): Boolean = synchronized(lock) { open[pane]?.isNotEmpty() == true }

    fun isVisible(pane: String): Boolean = synchronized(lock) { visible[pane]?.isNotEmpty() == true }

    fun reset() {
        synchronized(lock) {
            open.clear()
            visible.clear()
        }
    }

    /** Bỏ một token; không còn token nào thì quên luôn pane (khỏi phình map). */
    private fun drop(map: HashMap<String, MutableSet<String>>, pane: String, token: String) {
        val tokens = map[pane] ?: return
        tokens -= token
        if (tokens.isEmpty()) map -= pane
    }
}
