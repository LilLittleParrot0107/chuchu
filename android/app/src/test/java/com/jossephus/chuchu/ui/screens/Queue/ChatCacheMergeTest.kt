package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.*
import org.junit.Test

/** P3 max8 chat-cache (cand-2): merge tin cached + fresh theo key offset:sub, không trùng, giữ thứ tự. */
class ChatCacheMergeTest {
    @Test fun `merge gop khong trung giu thu tu`() {
        val cached = listOf(ChatCacheMsg("10:0", "a"), ChatCacheMsg("11:0", "b"))
        val fresh = listOf(ChatCacheMsg("11:0", "b"), ChatCacheMsg("12:0", "c"))
        val out = mergeChatCache(cached, fresh)
        assertEquals(listOf("10:0", "11:0", "12:0"), out.map { it.key })
    }

    @Test fun `cache rong thi lay fresh`() {
        val fresh = listOf(ChatCacheMsg("5:1", "x"))
        assertEquals(fresh, mergeChatCache(emptyList(), fresh))
    }
}
