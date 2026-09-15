package com.jossephus.chuchu.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.service.terminal.GhosttyBridge
import com.jossephus.chuchu.service.terminal.TerminalSnapshot
import kotlin.math.max
import kotlin.math.roundToInt

data class TerminalSelection(
    val anchorIndex: Int,
    val focusIndex: Int,
    /**
     * Có khi vùng chọn là MỘT LINK (giữ-thả trúng link, 15/9): chỉ tô và copy đúng các ô của
     * link ([TerminalLink.segments]), không phải dải ô liên tục từ đầu tới cuối — dải đó ôm cả
     * chữ của sidebar/pane bên cạnh nằm đầu hàng dưới (user chỉ ra 15/9). Kéo tay cầm là
     * thành vùng chọn thường, link bị bỏ.
     */
    val link: TerminalLink? = null,
) {
    /** Ô [cellIndex] có được tô/copy không — link thì theo từng khoảng, còn lại theo dải liên tục. */
    fun contains(cellIndex: Int, cellCount: Int): Boolean {
        val l = link
        if (l != null) return l.contains(cellIndex)
        val r = normalized(cellCount) ?: return false
        return cellIndex in r
    }

    fun normalized(cellCount: Int): IntRange? {
        if (cellCount <= 0) return null
        val start = minOf(anchorIndex, focusIndex).coerceIn(0, cellCount - 1)
        val end = maxOf(anchorIndex, focusIndex).coerceIn(0, cellCount - 1)
        return start..end
    }

    fun withStart(newStartCell: Int, updateAnchor: Boolean): TerminalSelection =
        if (updateAnchor) copy(anchorIndex = newStartCell, link = null)
        else copy(focusIndex = newStartCell, link = null)

    fun withEnd(newEndCell: Int, updateAnchor: Boolean): TerminalSelection =
        if (updateAnchor) copy(anchorIndex = newEndCell, link = null)
        else copy(focusIndex = newEndCell, link = null)

    /** Dời cả vùng chọn [deltaCells] ô (viewport cuộn). */
    fun shifted(deltaCells: Int): TerminalSelection =
        copy(anchorIndex = anchorIndex + deltaCells, focusIndex = focusIndex + deltaCells, link = link?.shifted(deltaCells))
}

data class TerminalSelectionState(
    val boundsLeft: Float,
    val boundsTop: Float,
    val boundsRight: Float,
    val boundsBottom: Float,
    /** Top-left px of the start cell, canvas-local. */
    val startOffset: Offset,
    /** Bottom-right px of the end cell (right edge + bottom edge), canvas-local. */
    val endOffset: Offset,
    val cellWidthPx: Float,
    val cellHeightPx: Float,
    val cols: Int,
    val canvasWidthPx: Int,
    val canvasHeightPx: Int,
    val text: String?,
)

internal fun TerminalSnapshot.wordAt(cellIndex: Int): IntRange? {
    if (cols <= 0 || cellIndex !in codepoints.indices) return null
    val row = cellIndex / cols
    val rowStart = row * cols
    val rowEnd = rowStart + cols - 1

    val cp = codepoints[cellIndex]
    if (cp == 0 || (cp == 32 && !isSpacerContinuation(cellIndex))) return null

    var start = cellIndex
    while (start > rowStart) {
        val prev = start - 1
        val prevCp = codepoints[prev]
        if (prevCp == 0 || (prevCp == 32 && !isSpacerContinuation(prev))) break
        start--
    }
    var end = cellIndex
    while (end < rowEnd) {
        val next = end + 1
        val nextCp = codepoints[next]
        if (nextCp == 0 || (nextCp == 32 && !isSpacerContinuation(next))) break
        end++
    }
    return start..end
}

internal fun extractSelectionText(snapshot: TerminalSnapshot, range: IntRange): String? {
    if (snapshot.cols <= 0 || snapshot.codepoints.isEmpty()) return null

    val normalizedRange = IntRange(
        start = range.first.coerceIn(0, snapshot.codepoints.lastIndex),
        endInclusive = range.last.coerceIn(0, snapshot.codepoints.lastIndex),
    )
    val startRow = normalizedRange.first / snapshot.cols
    val endRow = normalizedRange.last / snapshot.cols
    val builder = StringBuilder(normalizedRange.last - normalizedRange.first + 1 + (endRow - startRow))

    for (row in startRow..endRow) {
        val rowStart = row * snapshot.cols
        val from = maxOf(normalizedRange.first, rowStart)
        val until = minOf(normalizedRange.last, rowStart + snapshot.cols - 1)
        var lastContentIdx = until
        while (lastContentIdx >= from) {
            val cp = snapshot.codepoints[lastContentIdx]
            if (cp != 0 && (cp != 32 || snapshot.isSpacerContinuation(lastContentIdx))) break
            lastContentIdx--
        }
        for (index in from..lastContentIdx) {
            val codepoint = snapshot.codepoints[index]
            if (codepoint == 0 || (codepoint == 32 && !snapshot.isSpacerContinuation(index))) {
                builder.append(' ')
            } else if (codepoint != 32) {
                builder.append(snapshot.glyphAt(index))
            }
        }
        if (row != endRow) {
            builder.append('\n')
        }
    }

    return builder.toString()
}

internal fun buildSelectionState(
    snapshot: TerminalSnapshot,
    selection: TerminalSelection,
    cellWidth: Float,
    cellHeight: Float,
    canvasWidthPx: Int,
    canvasHeightPx: Int,
    terminalHandle: Long,
    ghosttyBridge: GhosttyBridge,
): TerminalSelectionState? {
    val cols = max(snapshot.cols, 1)
    val cellCount = snapshot.codepoints.size

    val visibleRange = selection.normalized(cellCount) ?: return null
    val startCol = visibleRange.first % cols
    val startRow = visibleRange.first / cols
    val endCol = visibleRange.last % cols
    val endRow = visibleRange.last / cols
    val fullWidth = cols * cellWidth
    val boundsLeft = if (startRow == endRow) startCol * cellWidth else 0f
    val boundsRight = if (startRow == endRow) (endCol + 1) * cellWidth else fullWidth
    val boundsTop = startRow * cellHeight
    val boundsBottom = (endRow + 1) * cellHeight

    val screenOffset = snapshot.viewportScrollY * cols
    val screenStart = (minOf(selection.anchorIndex, selection.focusIndex) + screenOffset)
        .coerceAtLeast(0)
    val screenEnd = (maxOf(selection.anchorIndex, selection.focusIndex) + screenOffset)
        .coerceAtLeast(screenStart)
    // Link: chữ copy là chính URL đã ghép (không lấy dải ô, dải đó dính chữ sidebar hàng dưới).
    val text = if (selection.link != null) {
        selection.link.url
    } else if (terminalHandle != 0L && snapshot.cols > 0) {
        val screenText = ghosttyBridge.nativeFormatSelectionScreenRange(terminalHandle, screenStart, screenEnd)
        screenText
            ?: ghosttyBridge.nativeFormatSelectionRange(terminalHandle, visibleRange.first, visibleRange.last)
    } else {
        extractSelectionText(snapshot, visibleRange)
    }
    return TerminalSelectionState(
        boundsLeft = boundsLeft,
        boundsTop = boundsTop,
        boundsRight = boundsRight,
        boundsBottom = boundsBottom,
        startOffset = Offset(startCol * cellWidth, startRow * cellHeight),
        endOffset = Offset((endCol + 1) * cellWidth, (endRow + 1) * cellHeight),
        cellWidthPx = cellWidth,
        cellHeightPx = cellHeight,
        cols = cols,
        canvasWidthPx = canvasWidthPx,
        canvasHeightPx = canvasHeightPx,
        text = text,
    )
}

@Composable
internal fun TerminalSelectionHandle(
    tipX: Float,
    tipY: Float,
    color: Color,
    borderColor: Color,
    cellWidthPx: Float,
    cellHeightPx: Float,
    cols: Int,
    startCellProvider: () -> Int,
    onDragToCell: (newCell: Int) -> Unit,
) {
    val density = LocalDensity.current
    val touchSizePx = with(density) { 44.dp.toPx() }
    val ballRadiusPx = with(density) { 9.dp.toPx() }
    val stemPx = with(density) { 6.dp.toPx() }
    val originX = (tipX - touchSizePx / 2f).roundToInt()
    val originY = (tipY - touchSizePx).roundToInt()

    Box(
        modifier =
            Modifier
                .offset { IntOffset(originX, originY) }
                .size(44.dp)
                .pointerInput(cellWidthPx, cellHeightPx, cols) {
                    var accumX = 0f
                    var accumY = 0f
                    var startCell = 0
                    detectDragGestures(
                        onDragStart = {
                            accumX = 0f
                            accumY = 0f
                            startCell = startCellProvider()
                        },
                        onDrag = { _, dragAmount ->
                            if (cellWidthPx <= 0f || cellHeightPx <= 0f || cols <= 0) return@detectDragGestures
                            accumX += dragAmount.x
                            accumY += dragAmount.y
                            val dCol = (accumX / cellWidthPx).roundToInt()
                            val dRow = (accumY / cellHeightPx).roundToInt()
                            val newCell = (startCell + dRow * cols + dCol).coerceAtLeast(0)
                            onDragToCell(newCell)
                        },
                    )
                },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val ballCenterY = size.height - ballRadiusPx - stemPx
            val ballCenter = Offset(cx, ballCenterY)
            drawLine(
                color = color,
                start = Offset(cx, ballCenterY + ballRadiusPx * 0.6f),
                end = Offset(cx, size.height),
                strokeWidth = 2.dp.toPx(),
            )
            drawCircle(color = borderColor, radius = ballRadiusPx + 1.dp.toPx(), center = ballCenter)
            drawCircle(color = color, radius = ballRadiusPx, center = ballCenter)
        }
    }
}

/**
 * Noi vung chon nhieu dong thanh MOT dong cho nut "copy 1 dòng".
 *
 * Cho dau cach vao cho xuong dong la SAI khi dong do bi be chi vi het be ngang:
 * ghostty (va cac app tu ve nhu herdr/Claude Code) cat giua chung mot tu, noi
 * lai co dau cach thi duong dan gay doi, lenh dan ra khong chay.
 *
 * Khong co co bao "dong nay bi be do het cho" trong chuoi da lay ra, nen dung
 * hinh hoc: dong nao trai kin [cols] o thi la bi be do het be ngang -> noi
 * DINH LIEN; dong ngan hon la xuong dong that -> noi bang mot dau cach.
 * Dong ket thuc bang dau cach da bi cat duoi (trim) nen do rong < cols, tu dong
 * roi vao nhanh "mot dau cach" — dung y nghia goc.
 */
internal fun joinSelectionLines(raw: String, cols: Int): String {
    val builder = StringBuilder(raw.length)
    var glueNext = false
    for (line in raw.split('\n')) {
        val body = line.trim()
        if (body.isEmpty()) {
            // Dong trong la ngat that, khong bao gio dinh lien qua no.
            glueNext = false
            continue
        }
        if (builder.isNotEmpty() && !glueNext) builder.append(' ')
        builder.append(body)
        glueNext = cols > 0 && terminalDisplayWidth(line.trimEnd()) >= cols
    }
    return builder.toString()
}

/** Do rong theo O TERMINAL, khong phai so ky tu: chu rong an 2 o, dau to hop an 0. */
internal fun terminalDisplayWidth(text: String): Int {
    var width = 0
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        i += Character.charCount(cp)
        width += when {
            isZeroWidthCodePoint(cp) -> 0
            isWideCodePoint(cp) -> 2
            else -> 1
        }
    }
    return width
}

private fun isZeroWidthCodePoint(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
    Character.NON_SPACING_MARK,
    Character.ENCLOSING_MARK,
    Character.COMBINING_SPACING_MARK,
    Character.FORMAT,
    -> true
    else -> false
}

private fun isWideCodePoint(cp: Int): Boolean =
    (cp in 0x1100..0x115F) ||
        (cp in 0x2E80..0x303E) ||
        (cp in 0x3041..0x33FF) ||
        (cp in 0x3400..0x4DBF) ||
        (cp in 0x4E00..0x9FFF) ||
        (cp in 0xA000..0xA4CF) ||
        (cp in 0xAC00..0xD7A3) ||
        (cp in 0xF900..0xFAFF) ||
        (cp in 0xFE30..0xFE6F) ||
        (cp in 0xFF00..0xFF60) ||
        (cp in 0xFFE0..0xFFE6) ||
        (cp in 0x1F300..0x1F64F) ||
        (cp in 0x1F900..0x1F9FF) ||
        (cp in 0x20000..0x3FFFD)
