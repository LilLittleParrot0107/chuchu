package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.*
import org.junit.Test

/** P3 max8 reconnect khôn (cand-2): offline pause không tăng backoff; reconnect reset; lỗi server backoff mũ. */
class ChatBackoffPolicyTest {
    @Test fun `offline thi pause khong goi poll`() {
        assertFalse(shouldPollChat(isOnline = false))
        assertTrue(shouldPollChat(isOnline = true))
    }

    @Test fun `reconnect reset backoff ve base`() {
        assertEquals(
            QueueViewModel.FOREGROUND_POLL_MS,
            nextChatBackoff(currentBackoffMs = 16000L, reconnected = true, failed = false),
        )
    }

    @Test fun `loi server backoff mu co tran`() {
        assertEquals(
            2 * QueueViewModel.FOREGROUND_POLL_MS,
            nextChatBackoff(
                currentBackoffMs = QueueViewModel.FOREGROUND_POLL_MS,
                reconnected = false,
                failed = true,
            ),
        )
        assertEquals(
            QueueViewModel.MAX_FOREGROUND_BACKOFF_MS,
            nextChatBackoff(
                currentBackoffMs = QueueViewModel.MAX_FOREGROUND_BACKOFF_MS,
                reconnected = false,
                failed = true,
            ),
        )
    }
}
