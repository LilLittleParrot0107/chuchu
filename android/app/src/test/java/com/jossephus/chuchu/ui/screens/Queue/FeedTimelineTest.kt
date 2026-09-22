package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T2 (user chốt 22/9): ô 30 phút, trong ô gom theo phiên, vạch MỚI, khối ghim phiên kẹt. */
class FeedTimelineTest {
    private fun m(pane: String, sec: Long, role: String = "assistant", label: String = "working", text: String = "t") =
        FeedMessage(pane = pane, name = pane, agent = "claude", label = label, tone = QueueTone.Dim, role = role,
            ts = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(sec * 1000)) + ".000Z",
            text = text, uuid = "u$sec", offset = sec)

    @Test
    fun `iso sang epoch`() {
        assertEquals(1789916314L, isoEpochSec("2026-09-20T14:58:34.000Z"))
        assertEquals(null, isoEpochSec("rác"))
    }

    @Test
    fun `trong o gom theo pane, sang o moi thi tach, co vach gio`() {
        val t0 = 1789916400L   // 15:00:00Z, đầu ô
        val msgs = listOf(m("A", t0 + 10), m("B", t0 + 20), m("A", t0 + 30), m("A", t0 + 1900), m("B", t0 + 1950))
        val items = feedTimelineItems(msgs)
        assertEquals(listOf("hour:$t0", "blk:A:u${t0 + 10}:${t0 + 10}", "blk:B:u${t0 + 20}:${t0 + 20}", "hour:${t0 + 1800}", "blk:A:u${t0 + 1900}:${t0 + 1900}", "blk:B:u${t0 + 1950}:${t0 + 1950}"), items.map { it.key })
        val a = items[1] as FeedItem.Block
        assertEquals(2, a.block.messages.size)   // A(10) + A(30) cùng khối dù B chen giữa
    }

    @Test
    fun `vach MOI dat truoc tin dau moi hon moc, khong dat khi toan tin moi`() {
        val t0 = 1789916400L
        val msgs = listOf(m("A", t0 + 10), m("B", t0 + 100), m("A", t0 + 200))
        val items = feedTimelineItems(msgs, sinceSec = t0 + 50)
        assertEquals(listOf("hour:$t0", "blk:A:u${t0 + 10}:${t0 + 10}", "new", "blk:B:u${t0 + 100}:${t0 + 100}", "blk:A:u${t0 + 200}:${t0 + 200}"), items.map { it.key })
        assertTrue(feedTimelineItems(msgs, sinceSec = t0).none { it is FeedItem.New })
        assertTrue(feedTimelineItems(msgs, sinceSec = 0L).none { it is FeedItem.New })
    }

    @Test
    fun `phien ket ghim theo tin cuoi`() {
        val t0 = 1789916400L
        val msgs = listOf(m("A", t0 + 10, label = "needs approval"), m("B", t0 + 20), m("A", t0 + 30, label = "needs approval", text = "hỏi anh"))
        val pinned = blockedBlocks(msgs)
        assertEquals(1, pinned.size)
        assertEquals("hỏi anh", pinned[0].messages.single().text)
        assertTrue(blockedBlocks(listOf(m("A", t0 + 10, label = "needs approval"), m("A", t0 + 40, label = "working"))).isEmpty())
    }
}
