package com.jossephus.chuchu.ui.terminal

import com.jossephus.chuchu.service.terminal.TerminalSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Quét link trên lưới chữ (cách 1, 15/9): giữ-thả trên link phải ôm trọn link, kể cả phần
 * bị bẻ sang hàng dưới, và không được dính câu văn ở hàng dưới vào đuôi link.
 */
class TerminalLinksTest {

    @Test
    fun singleUrl_trailingPeriodTrimmed_cellsMapped() {
        val s = snapshotOf(40, "xem https://example.com/docs. ok")
        val links = s.findLinks()
        assertEquals(listOf("https://example.com/docs"), links.map { it.url })
        assertEquals(listOf(4..27), links[0].segments)
    }

    @Test
    fun parentheses_unbalancedDropped_balancedKept() {
        assertEquals(
            listOf("https://example.com", "https://en.wikipedia.org/wiki/Rust_(video_game)"),
            snapshotOf(60, "(https://example.com)", "https://en.wikipedia.org/wiki/Rust_(video_game)").findLinks().map { it.url },
        )
    }

    @Test
    fun fullWidthWrap_gluedAcrossRows() {
        // 20 cột: hàng đầu kín tới cột cuối -> terminal bẻ -> nối liền với hàng dưới.
        val s = snapshotOf(20, "go https://a.bc/defg", "hij/klm rest")
        val link = s.findLinks().single()
        assertEquals("https://a.bc/defghij/klm", link.url)
        assertEquals(listOf(3..19, 20..26), link.segments)
    }

    @Test
    fun claudeCodeWrap_indentedContinuation() {
        val s = snapshotOf(20, "  https://x.io/aaaaa", "  bbbbb/c and so")
        assertEquals("https://x.io/aaaaabbbbb/c", s.findLinks().single().url)
    }

    @Test
    fun nearEdgeWrap_longRunGlued_proseNotGlued() {
        // Hàng đầu 19/20 cột: app tự bẻ sớm. Đuôi dài liền 12 ký tự -> nối.
        assertEquals(
            "https://q.io/abcdefghijklmn",
            snapshotOf(20, "see https://q.io/ab", "cdefghijklmn tail").findLinks().single().url,
        )
        // Cùng hình dạng nhưng hàng dưới là câu văn -> KHÔNG nối.
        assertEquals(
            "https://q.io/ab",
            snapshotOf(20, "see https://q.io/ab", "and then run").findLinks().single().url,
        )
    }

    @Test
    fun leftSidebar_continuationAlignedToPaneNotScreen() {
        // herdr: sidebar 17 cột bên trái, pane bắt đầu ở cột 17; hàng đầu kín tới cột 39.
        // Hàng dưới có chữ của sidebar ("  master") TRƯỚC phần nối — không được đọc nhầm.
        val s = snapshotOf(
            40,
            "○ vbook          https://a.io/abcdefghij",
            "  master         lmn/op end",
        )
        val link = s.findLinks().single()
        assertEquals("https://a.io/abcdefghijlmn/op", link.url)
        assertEquals(listOf(17..39, 57..62), link.segments)
        assertNull(s.linkAt(43)) // chữ "master" của sidebar
    }

    @Test
    fun linkInTheMiddleOfAParagraph_blockStartIsTheParagraphStart() {
        // Link bắt đầu GIỮA câu; đầu khối = chỗ câu bắt đầu (cột 17), không phải chỗ "https".
        // Hàng dưới: chữ sidebar ở cột 2, phần nối ở cột 17, rồi câu văn tiếp tục sau dấu cách.
        val s = snapshotOf(
            40,
            "○ vbook          xem tại https://a.io/ab",
            "  master         cdef/gh rồi chạy tiếp",
        )
        val link = s.findLinks().single()
        assertEquals("https://a.io/abcdef/gh", link.url)
        assertEquals(listOf(25..39, 57..63), link.segments)
        // Thụt lề thêm 2 ô ở hàng dưới vẫn nối (Claude Code thụt đoạn văn).
        assertEquals(
            "https://a.io/abcdef/gh",
            snapshotOf(40, "○ vbook          xem tại https://a.io/ab", "  master           cdef/gh rồi").findLinks().single().url,
        )
    }

    @Test
    fun leftSidebar_onlySidebarTextBelow_noJoin() {
        val s = snapshotOf(40, "○ vbook          https://a.io/abcdefghij", "  master")
        assertEquals("https://a.io/abcdefghij", s.findLinks().single().url)
    }

    @Test
    fun rightPaneBorder_isTheEdge() {
        // Kẻ khung │ sau một dấu cách đệm: link chạm mép pane dù còn 6 cột màn hình.
        val s = snapshotOf(30, "  https://a.io/abcdefghi │ x", "  ijk/l │ y")
        assertEquals("https://a.io/abcdefghiijk/l", s.findLinks().single().url)
    }

    @Test
    fun newSchemeOnNextRow_isSeparateLink() {
        val s = snapshotOf(20, "go https://a.bc/defg", "https://b.io/x")
        assertEquals(listOf("https://a.bc/defg", "https://b.io/x"), s.findLinks().map { it.url })
    }

    @Test
    fun twoLinksOneRow_linkAtPicksTheRightOne() {
        val s = snapshotOf(40, "https://a.io/x https://b.io/y")
        val links = s.findLinks()
        assertEquals(listOf("https://a.io/x", "https://b.io/y"), links.map { it.url })
        assertEquals("https://b.io/y", s.linkAt(20)?.url)
        assertNull(s.linkAt(14)) // dấu cách giữa hai link
        assertNull(s.linkAt(39))
    }

    @Test
    fun wwwWithoutScheme() {
        assertEquals("www.example.com", snapshotOf(40, "visit www.example.com now").findLinks().single().url)
    }

    @Test
    fun wideGlyphBeforeLink_cellIndicesAccountForSpacer() {
        // "日" chiếm ô 0-1 (ô 1 là spacer, ghi đè lên dấu cách thứ nhất), dấu cách ở ô 2, link từ ô 3.
        val s = snapshotOf(30, "X  https://a.io/b")
        s.codepoints[0] = 0x65E5
        s.codepoints[1] = 0x65E5
        s.flags[1] = TerminalSnapshot.CELL_FLAG_SPACER.toByte()
        val link = s.findLinks().single()
        assertEquals("https://a.io/b", link.url)
        assertEquals(3, link.startCell)
        assertEquals(16, link.endCell)
    }

    @Test
    fun linkSelection_coversOnlyLinkCells_untilAHandleMoves() {
        // Sidebar trái: dải ô liên tục từ đầu tới cuối link phủ cả "  master" ở đầu hàng dưới.
        val s = snapshotOf(40, "○ vbook          https://a.io/abcdefghij", "  master         lmn/op end")
        val link = s.findLinks().single()
        val sel = TerminalSelection(link.startCell, link.endCell, link)
        assertEquals(true, sel.contains(20, 80))
        assertEquals(true, sel.contains(60, 80))
        assertEquals(false, sel.contains(43, 80))   // ô của "master": KHÔNG tô, KHÔNG copy
        assertEquals(false, sel.contains(63, 80))   // " end" sau link
        // Cuộn viewport: link dời theo.
        assertEquals(true, sel.shifted(-40).contains(20 - 40 + 40, 80))
        assertEquals(listOf(-23..-1, 17..22), sel.shifted(-40).link!!.segments)
        // Kéo tay cầm -> vùng chọn thường, dải liên tục, không còn link.
        val dragged = sel.withEnd(70, updateAnchor = false)
        assertNull(dragged.link)
        assertEquals(true, dragged.contains(43, 80))
    }

    @Test
    fun urlOfSelection_joinsWrappedLines_rejectsProse() {
        assertEquals(
            "https://claude.ai/code/session_01",
            urlOfSelection("https://claude.ai/code/sessio\nn_01", 29),
        )
        assertEquals("https://x.io/a", urlOfSelection("(https://x.io/a)", 40))
        assertNull(urlOfSelection("hello world", 40))
        assertNull(urlOfSelection("xem https://x.io/a nhé", 40))
        assertNull(urlOfSelection(null, 40))
    }

    private fun snapshotOf(cols: Int, vararg rowsText: String): TerminalSnapshot {
        val rows = rowsText.size
        val cellCount = cols * rows
        val codepoints = IntArray(cellCount) { 32 }
        for ((r, text) in rowsText.withIndex()) {
            require(text.length <= cols) { "hàng $r dài ${text.length} > $cols cột" }
            for ((c, ch) in text.withIndex()) codepoints[r * cols + c] = ch.code
        }
        return TerminalSnapshot(
            cols = cols,
            rows = rows,
            cursorX = 0,
            cursorY = 0,
            cursorVisible = false,
            defaultBgArgb = 0xFF000000.toInt(),
            defaultFgArgb = 0xFFFFFFFF.toInt(),
            codepoints = codepoints,
            fgArgb = IntArray(cellCount) { 0xFFFFFFFF.toInt() },
            bgArgb = IntArray(cellCount) { 0xFF000000.toInt() },
            flags = ByteArray(cellCount),
        )
    }
}
