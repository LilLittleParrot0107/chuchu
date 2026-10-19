package com.jossephus.chuchu.service.agents

import java.util.concurrent.ConcurrentHashMap

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
 * Chỉ sống trong process: activity chết thì process cũng sắp chết theo, còn
 * notification sót lại đã có [AgentNotifier.cancelAll] dọn lúc mở app.
 */
internal object BubblePresence {

    private val open = ConcurrentHashMap.newKeySet<String>()
    private val visible = ConcurrentHashMap.newKeySet<String>()

    /** Cửa sổ bubble của [pane] vừa được tạo (hoặc tạo lại sau config change). */
    fun opened(pane: String) {
        if (pane.isNotBlank()) open += pane
    }

    /** Cửa sổ đóng hẳn. Config change thì đừng gọi — cửa sổ vẫn còn đó. */
    fun closed(pane: String) {
        open -= pane
        visible -= pane
    }

    fun shown(pane: String) {
        if (pane.isNotBlank()) visible += pane
    }

    fun hidden(pane: String) {
        visible -= pane
    }

    fun isOpen(pane: String): Boolean = pane in open

    fun isVisible(pane: String): Boolean = pane in visible

    /** Chỉ dùng cho test: object sống xuyên test case. */
    fun reset() {
        open.clear()
        visible.clear()
    }
}
