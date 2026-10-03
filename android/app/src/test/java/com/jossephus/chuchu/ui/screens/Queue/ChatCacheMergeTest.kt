package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.*
import org.junit.Test

/** P6 max8 chat-cache (cand-2 fix1): đường ghép duy nhất là withFreshPage + rev guard + LRU. */
class ChatCacheMergeTest {
    private fun msg(offset: Long, sub: Int = 0) = ChatMessage(
        role = "user", uuid = "u$offset:$sub", ts = "", text = "t$offset",
        offset = offset, sub = sub,
    )

    private fun page(rev: String, offsets: List<Long>, hasMore: Boolean = false, cursor: Long? = null) =
        ChatPage(
            pane = "w1:p1", name = "a", cwd = "", home = "", file = "", size = 0L,
            rev = rev, messages = offsets.map { msg(it) }, cursor = cursor, hasMore = hasMore,
        )

    private fun state(rev: String, offsets: List<Long>) = ChatUiState(
        pane = "w1:p1", name = "a", rev = rev, messages = offsets.map { msg(it) },
    )

    @Test fun `withFreshPage gop tiep khong trung giu thu tu`() {
        val out = state("100.10", listOf(10, 11)).withFreshPage(page("200.20", listOf(11, 12)))
        assertEquals(listOf(10L, 11L, 12L), out.messages.map { it.offset })
        assertEquals("200.20", out.rev)
    }

    @Test fun `truncate file thi thay toan bo lay cursor moi`() {
        val out = state("100.60", listOf(50, 60))
            .withFreshPage(page("200.6", listOf(5, 6), hasMore = true, cursor = 5L))
        assertEquals(listOf(5L, 6L), out.messages.map { it.offset })
        assertEquals("200.6", out.rev)
        assertTrue(out.hasMore)
        assertEquals(5L, out.cursor)
    }

    @Test fun `rev cu ve tre thi bo giu khung dang xem`() {
        val out = state("200.20", listOf(10, 11, 12)).withFreshPage(page("100.10", listOf(9)))
        assertEquals(listOf(10L, 11L, 12L), out.messages.map { it.offset })
        assertEquals("200.20", out.rev)
    }

    @Test fun `rev bang nhau thi bo`() {
        val out = state("200.20", listOf(10, 11)).withFreshPage(page("200.20", listOf(10, 11)))
        assertEquals(listOf(10L, 11L), out.messages.map { it.offset })
        assertEquals("200.20", out.rev)
    }

    @Test fun `rev so sanh theo so khong theo chuoi`() {
        // So chuỗi thì "90" > "100" (sai); rev qsrv là mtime_ns.size nên so số.
        assertFalse(isChatRevNewer("100.10", "90.5"))
        assertTrue(isChatRevNewer("90.5", "100.10"))
        assertTrue(isChatRevNewer("100.10", "100.20"))
        assertFalse(isChatRevNewer("100.20", "100.10"))
        assertFalse(isChatRevNewer("100.10", "100.10"))
        assertFalse(isChatRevNewer("100.10", ""))
        assertTrue(isChatRevNewer("", "100.10"))
    }

    @Test fun `snapshot qua nap thi rot pane cu nhat`() {
        val cache = ChatSnapshotCache()
        for (i in 0..MAX_CHAT_SNAPSHOTS) {
            cache.store("p$i", state("1.$i", listOf(i.toLong())))
        }
        assertEquals(MAX_CHAT_SNAPSHOTS, cache.size())
        assertNull(cache.forPane("p0"))
        assertNotNull(cache.forPane("p$MAX_CHAT_SNAPSHOTS"))
    }

    @Test fun `clear xoa het snapshot`() {
        val cache = ChatSnapshotCache()
        cache.store("p1", state("1.1", listOf(1)))
        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.forPane("p1"))
    }
}
