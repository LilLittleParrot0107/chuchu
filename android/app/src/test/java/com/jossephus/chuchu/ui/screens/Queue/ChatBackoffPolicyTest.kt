package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.*
import org.junit.Test

/** P3 max8 reconnect khôn (cand-2): offline pause không tăng backoff; reconnect reset; lỗi server backoff mũ. */
class ChatBackoffPolicyTest {
    @Test fun `offline thi pause khong goi poll`() {
        assertFalse(shouldPollChat(isOnline = false, failed = false))
        assertFalse(shouldPollChat(isOnline = false, failed = true))
    }

    @Test fun `reconnect reset backoff ve base`() {
        assertEquals(2000L, nextChatBackoff(currentBackoffMs = 16000L, reconnected = true, failed = false))
    }

    @Test fun `loi server backoff mu co tran`() {
        assertEquals(4000L, nextChatBackoff(currentBackoffMs = 2000L, reconnected = false, failed = true))
        assertEquals(30000L, nextChatBackoff(currentBackoffMs = 30000L, reconnected = false, failed = true))
    }
}
