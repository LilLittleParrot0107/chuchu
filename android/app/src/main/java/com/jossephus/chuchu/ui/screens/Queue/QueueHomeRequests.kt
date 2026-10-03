package com.jossephus.chuchu.ui.screens.Queue

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Yêu cầu "về HỘI THOẠI" khi bấm tab QUEUE — sự kiện MỘT LẦN, không phải state.
 * Bài học 3/10: tick Int tồn dư nằm trong cây composition nên mỗi lần remount
 * (mở chat lật hideRail) effect chạy lại và tự đóng chat vừa mở. Channel.CONFLATED:
 *  - request phát trước khi collector tồn tại vẫn tới (bấm tab từ màn khác);
 *  - double-tap gộp còn một;
 *  - sau khi tiêu thụ thì remount/xoay màn KHÔNG phát lại.
 * VM giữ instance (sống theo Activity, chết theo process) — không replay qua process death.
 */
class QueueHomeRequests {
    private val channel = Channel<Unit>(Channel.CONFLATED)

    fun request() {
        channel.trySend(Unit)
    }

    val events: Flow<Unit> get() = channel.receiveAsFlow()
}
