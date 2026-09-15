package com.jossephus.chuchu.ui.terminal

import com.jossephus.chuchu.service.terminal.TerminalSnapshot

/**
 * Link tìm thấy trên lưới chữ đang hiện.
 *
 * [segments] là các khoảng ô theo TỪNG HÀNG (link bẻ dòng thì nhiều khoảng), dùng để vẽ
 * gạch chân và để biết ngón tay có rơi vào link không. [startCell]/[endCell] là ô đầu và
 * ô cuối, đúng không gian chỉ số của [TerminalSelection].
 */
data class TerminalLink(
    val url: String,
    val segments: List<IntRange>,
) {
    val startCell: Int get() = segments.first().first
    val endCell: Int get() = segments.last().last
    fun contains(cellIndex: Int): Boolean = segments.any { cellIndex in it }
    fun shifted(deltaCells: Int): TerminalLink = copy(segments = segments.map { it.first + deltaCells..it.last + deltaCells })
}

/**
 * Quét link trên màn hình (15/9, user chốt "cách 1": giữ-thả trên link là chọn nguyên link).
 *
 * Vì sao phải tự ghép dòng: terminal chỉ ~45 cột, URL dài gần như luôn bị bẻ. Có hai kiểu bẻ:
 *  - Terminal/app bẻ vì hết bề ngang: hàng trên KÍN tới mép phải, hàng dưới nối tiếp ở đầu
 *    khối chữ (Claude Code thụt lề đoạn văn, nên cho phép vài dấu cách so với đầu khối).
 *  - App tự bẻ sớm vài ô trước mép (khung, lề phải): hàng trên chưa kín nhưng chạm gần mép.
 *    Nhánh này dễ dính nhầm câu văn ở hàng dưới, nên chỉ nối khi phần nối trông giống
 *    đuôi URL (có dấu của URL, hoặc dài ≥ 10 ký tự liền không cách), xem [continuationLooksLikeUrl].
 *
 * "Mép phải" và "đầu khối" tính theo PANE chứ không theo màn hình (user chỉ ra 15/9: herdr có
 * sidebar bên trái): đầu khối = cột đầu của đoạn chữ liền chứa link (ngắt bởi ≥ 2 dấu cách hoặc
 * ký tự kẻ khung), phần nối ở hàng dưới phải bắt đầu từ cột đó trở đi (thụt thêm ≤ 6); mép phải =
 * cột cuối màn hình, hoặc ký tự kẻ khung đứng ngay sau link. Chữ của sidebar nằm bên trái đầu
 * khối nên không bị đọc nhầm thành đuôi link.
 *
 * Chỉ nhận URL có scheme (http, https, ftp, ssh, git, file, mailto) hoặc `www.`. Không nhận
 * đường dẫn file: mục đích là mở/copy link, còn đường dẫn thì "copy 1 dòng" đã đủ.
 */
internal fun TerminalSnapshot.findLinks(): List<TerminalLink> {
    if (cols <= 0 || rows <= 0 || codepoints.isEmpty()) return emptyList()
    val lines = Array(rows) { rowText(it) }
    val out = ArrayList<TerminalLink>()
    val url = StringBuilder()
    val charFirst = ArrayList<Int>()   // ô đầu của glyph chứa ký tự thứ k của url
    val charLast = ArrayList<Int>()    // ô cuối (glyph rộng 2 ô thì = ô đầu + 1)
    var row = 0
    var fromIndex = 0
    while (row < rows) {
        val line = lines[row]
        val m = URL_REGEX.find(line.text, fromIndex)
        if (m == null) { row++; fromIndex = 0; continue }
        url.setLength(0); charFirst.clear(); charLast.clear()
        appendChars(url, charFirst, charLast, line, m.range)
        val blockStartCol = line.blockStartCol(m.range.first)
        var r = row
        var endIndex = m.range.last + 1
        // Nối tiếp xuống các hàng dưới chừng nào link còn chạm mép phải của pane.
        while (r + 1 < rows) {
            val edge = lines[r].paneRightEdge(endIndex, cols) ?: break
            val cont = continuationOf(lines[r], endIndex, edge, blockStartCol, lines[r + 1], url) ?: break
            appendChars(url, charFirst, charLast, lines[r + 1], cont)
            r++
            endIndex = cont.last + 1
        }
        val trimmed = trimTrailingPunctuation(url.toString())
        if (trimmed.length > URL_MIN_LENGTH && !trimmed.endsWith("://") && trimmed != "www.") {
            out += TerminalLink(trimmed, segmentsOf(charFirst, charLast, trimmed.length, cols))
        }
        // Quét tiếp từ chỗ link kết thúc, trên hàng cuối cùng nó chạm tới.
        row = r
        fromIndex = endIndex
        if (fromIndex >= lines[row].text.length) { row++; fromIndex = 0 }
    }
    return out
}

/** Link đang chứa ô [cellIndex], nếu có. */
internal fun TerminalSnapshot.linkAt(cellIndex: Int): TerminalLink? =
    findLinks().firstOrNull { it.contains(cellIndex) }

/**
 * Vùng chọn có phải ĐÚNG một URL không (cho nút [mở] trong menu copy/paste): nối dòng như
 * "copy 1 dòng", bỏ dấu bao quanh, rồi so khớp trọn chuỗi. null nếu không phải.
 */
internal fun urlOfSelection(text: String?, cols: Int): String? {
    val joined = joinSelectionLines(text ?: return null, cols).trim().trim('(', ')', '[', ']', '<', '>', '"', '\'', '`')
    val cleaned = trimTrailingPunctuation(joined)
    return cleaned.takeIf { it.length > URL_MIN_LENGTH && URL_REGEX.matches(it) }
}

/** Bỏ dấu câu bám đuôi; ngoặc đóng chỉ bỏ khi không có ngoặc mở tương ứng trong URL. */
internal fun trimTrailingPunctuation(url: String): String {
    var s = url
    while (s.isNotEmpty()) {
        val c = s.last()
        val drop = when (c) {
            '.', ',', ';', ':', '!', '?', '\'', '"', '`' -> true
            ')' -> s.count { it == '(' } < s.count { it == ')' }
            ']' -> s.count { it == '[' } < s.count { it == ']' }
            '}' -> s.count { it == '{' } < s.count { it == '}' }
            else -> false
        }
        if (!drop) break
        s = s.dropLast(1)
    }
    return s
}

// ────────────────────────────── phần trong ──────────────────────────────

private const val URL_MIN_LENGTH = 6
private const val MAX_CONTINUATION_INDENT = 6
/** Hàng chưa kín nhưng chữ chạm tới trong ngần này ô kể từ mép phải thì coi là "app tự bẻ". */
private const val NEAR_EDGE_CELLS = 8

// Ký tự kẻ khung (U+2500–U+257F) không bao giờ là một phần của URL: pane border của herdr/tmux.
private val URL_REGEX = Regex("""(?:https?://|ftp://|ssh://|git://|file://|mailto:|www\.)[^\s<>"'`\u2500-\u257F]+""")
private val SCHEME_START = Regex("""^(?:[a-zA-Z][a-zA-Z0-9+.-]*://|www\.)""")
private val URL_CHARS = Regex("""^[^\s<>"'`\u2500-\u257F]+""")
private const val URL_PUNCT = "/.-_=?&#%~:@"

private fun isBoxChar(c: Char): Boolean = c in '\u2500'..'\u257F' || c == '|'

/**
 * Chữ của một hàng (đã cắt dấu cách đuôi) kèm ánh xạ chỉ số ký tự → ô đầu/ô cuối của glyph đó.
 * [colOf] = cột màn hình của ký tự (ô đầu − đầu hàng).
 */
private class RowText(val text: String, val firstCell: IntArray, val lastCell: IntArray, val rowStart: Int) {
    fun colOf(charIndex: Int): Int = firstCell[charIndex] - rowStart
    fun endColOf(charIndex: Int): Int = lastCell[charIndex] - rowStart

    /** Cột đầu của khối chữ liền chứa ký tự [charIndex]: lùi trái tới khi gặp ≥ 2 dấu cách hoặc kẻ khung. */
    fun blockStartCol(charIndex: Int): Int {
        var k = charIndex
        while (k > 0) {
            val c = text[k - 1]
            if (isBoxChar(c)) break
            if (c == ' ' && (k - 2 < 0 || text[k - 2] == ' ')) break
            k--
        }
        return colOf(k)
    }

    /**
     * Cột mép phải của pane, nếu link kết thúc ở [endIndex] chạm được mép: sau link chỉ còn
     * dấu cách tới hết hàng (mép = cột cuối màn hình) hoặc tới một ký tự kẻ khung (mép = cột
     * trước nó). Còn chữ khác sau link thì link không "bị bẻ" → null.
     */
    fun paneRightEdge(endIndex: Int, cols: Int): Int? {
        var k = endIndex
        while (k < text.length && text[k] == ' ') k++
        if (k >= text.length) return cols - 1
        if (!isBoxChar(text[k])) return null
        // Kẻ khung ngay sau link (hoặc cách một dấu cách đệm) = link chạm mép pane.
        return if (k - endIndex <= 1) endColOf(endIndex - 1) else colOf(k) - 1
    }
}

private fun TerminalSnapshot.rowText(row: Int): RowText {
    val rowStart = row * cols
    val rowEnd = rowStart + cols
    val sb = StringBuilder(cols)
    val first = IntArray(cols * 2)   // glyph ghép có thể dài hơn 1 char; cols*2 là trần đủ rộng
    val last = IntArray(cols * 2)
    var len = 0
    var contentLen = 0
    var i = rowStart
    while (i < rowEnd) {
        if (isSpacerContinuation(i)) { i++; continue }
        val cp = codepoints[i]
        val wide = i + 1 < rowEnd && isSpacerContinuation(i + 1)
        val glyph: CharSequence = when {
            cp == 0 || cp == 32 -> " "
            hasGrapheme(i) -> glyphAt(i)
            else -> StringBuilder(2).appendCodePoint(cp)
        }
        val cellLast = if (wide) i + 1 else i
        if (len + glyph.length > first.size) break
        for (k in glyph.indices) { first[len + k] = i; last[len + k] = cellLast }
        sb.append(glyph)
        len += glyph.length
        if (glyph != " ") contentLen = len
        i = cellLast + 1
    }
    sb.setLength(contentLen)
    return RowText(sb.toString(), first.copyOf(contentLen), last.copyOf(contentLen), rowStart)
}

private fun appendChars(url: StringBuilder, charFirst: MutableList<Int>, charLast: MutableList<Int>, line: RowText, range: IntRange) {
    url.append(line.text, range.first, range.last + 1)
    for (k in range) { charFirst += line.firstCell[k]; charLast += line.lastCell[k] }
}

/** Gom ô của [length] ký tự đầu thành các khoảng liên tục theo hàng. */
private fun segmentsOf(charFirst: List<Int>, charLast: List<Int>, length: Int, cols: Int): List<IntRange> {
    val out = ArrayList<IntRange>(2)
    var segStart = -1
    var segEnd = -1
    for (k in 0 until length) {
        val a = charFirst[k]
        val b = charLast[k]
        if (segStart >= 0 && a / cols == segStart / cols && a <= segEnd + 1) {
            segEnd = maxOf(segEnd, b)
        } else {
            if (segStart >= 0) out += segStart..segEnd
            segStart = a; segEnd = b
        }
    }
    if (segStart >= 0) out += segStart..segEnd
    return out
}

/**
 * Hàng [next] có phần nối tiếp của link đang dở ở hàng [cur] (kết thúc tại [endIndex], mép phải
 * của pane ở cột [edgeCol], khối chữ bắt đầu ở cột [blockStartCol]) không? Trả khoảng chỉ số ký
 * tự trong [next].text, null nếu không nối.
 */
private fun continuationOf(
    cur: RowText,
    endIndex: Int,
    edgeCol: Int,
    blockStartCol: Int,
    next: RowText,
    urlSoFar: CharSequence,
): IntRange? {
    if (next.text.isEmpty()) return null
    val urlEndCol = cur.endColOf(endIndex - 1)
    val full = urlEndCol == edgeCol
    val nearEdge = urlEndCol >= edgeCol - NEAR_EDGE_CELLS
    if (!full && !nearEdge) return null
    // Phần nối phải đứng từ đầu khối trở đi: chữ bên trái đầu khối là sidebar/pane khác.
    var start = 0
    while (start < next.text.length && (next.text[start] == ' ' || next.colOf(start) < blockStartCol)) start++
    if (start >= next.text.length) return null
    if (next.colOf(start) - blockStartCol > MAX_CONTINUATION_INDENT) return null
    if (isBoxChar(next.text[start])) return null
    val run = URL_CHARS.find(next.text.substring(start))?.value ?: return null
    if (SCHEME_START.containsMatchIn(run)) return null       // link mới, không phải đuôi link cũ
    if (!full && !continuationLooksLikeUrl(run, urlSoFar)) return null
    return start until start + run.length
}

private fun continuationLooksLikeUrl(run: String, urlSoFar: CharSequence): Boolean {
    if (run.length < 4) return false
    if (run.any { it in URL_PUNCT }) return true
    if (urlSoFar.isNotEmpty() && urlSoFar.last() in URL_PUNCT) return true
    return run.length >= 10
}
