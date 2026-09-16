package com.jossephus.chuchu.ui.components

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.theme.ChatTone
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

/**
 * MiniMarkdownText — render tap bi doi markdown bang AnnotatedString, KHONG
 * them dependency nao. Sinh ra vi TaskDetailDialog truoc day dua nguyen van
 * markdown vao BasicText -> nguoi doc thay so '**', '#', '`' xuyen tac.
 *
 * Phu dung phan markdown agent thuong tra loi: heading #..####, **bold**,
 * *italic*, `code`, fence ba-dau-nhay (khoi mono), list gach-ngoang hoac so,
 * blockquote >, ngang --- , link [t](u), va BANG | a | b | (25/8 — agent hay
 * tra loi so sanh dang bang). Link bam duoc o moi noi: [nhan](url), URL tran,
 * trong `code`, trong khoi ```, ke ca trong **dam** va *nghieng* (16/9).
 * Nhan nhung gi khong hieu la chu thuong — khong mat chu.
 *
 * 16/9 toi (prototype kohi-chat-typography, user chot "duyet roi"): doc theo
 * KHOI thay vi mot dong chay lien — doan thut dong dau 2ch, muc danh sach thut
 * le treo (dong thu 2 thang duoi chu), fence co NEN + nhan ngon ngu, quote co
 * vach mau trai, checkbox ☑/☐ that, tieu de co vach trai, hr la duong ke manh,
 * link gach chan. Nho vay tin dai nhin ra cau truc thay vi mot nui chu.
 */

/** Tap style gom mot lan o compose, truen cho builder thuan Kotlin ben duoi. */
internal class MdStyles(
    val code: SpanStyle,
    val bold: SpanStyle,
    val italic: SpanStyle,
    val link: SpanStyle,
    val quote: SpanStyle,
    val muted: SpanStyle,
    val h1: SpanStyle,
    val h2: SpanStyle,
    val h3: SpanStyle,
)

// ─────────────────────────── block parser ───────────────────────────

/** Mot khoi markdown: doan text thuong, hoac bang | a | b | co separator. */
internal sealed class MdBlock {
    data class Text(val lines: List<String>) : MdBlock()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock()
}

/** Dong separator cua bang GFM: | --- | :---: | (cho phep 2+ gach). */
internal fun isTableSeparator(line: String): Boolean {
    val t = line.trim().trim('|')
    if (!t.contains('-')) return false
    return t.split('|').all { c -> c.trim().matches(Regex(":?-{2,}:?")) }
}

internal fun splitRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

/**
 * Tach markdown thanh cac block. Bang duoc nhan khi mot dong bat dau bang
 * '|' va DONG KE TIEN la separator — tranh nham voi text co ky tu '|'.
 * Code fence duoc track de khong bao gio nham bang trong fence.
 */
internal fun splitBlocks(md: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    var textBuf = mutableListOf<String>()
    var inFence = false
    val lines = md.lines()
    var i = 0

    fun flush() {
        if (textBuf.isNotEmpty()) {
            blocks += MdBlock.Text(textBuf.toList())
            textBuf = mutableListOf()
        }
    }

    while (i < lines.size) {
        val line = lines[i].trimEnd()
        if (line.trimStart().startsWith("```")) inFence = !inFence
        val t = line.trim()
        if (!inFence && t.startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
            flush()
            val header = splitRow(t)
            i += 2 // header + separator
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|") && !lines[i].trimStart().startsWith("```")) {
                rows += splitRow(lines[i])
                i += 1
            }
            blocks += MdBlock.Table(header = header, rows = rows)
            continue
        }
        textBuf += line
        i += 1
    }
    flush()
    return blocks
}

// ─────────────────────────── styles ───────────────────────────

@Composable
private fun rememberMdStyles(tone: ChatTone?): MdStyles {
    val colors = ChuColors.current
    val type = ChuTypography.current
    // Styles phai duoc remember: tao moi moi recompose lam key cua
    // remember(markdown, styles) thay doi lien tuc -> parse lai toan bo text.
    // tone: mau rieng cua tung loai agent (16/9, phuong an 2B) — null = mac dinh theme.
    return remember(colors, type, tone) {
        MdStyles(
            code = SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = tone?.codeBg ?: colors.border.copy(alpha = 0.3f),
                color = tone?.code ?: Color.Unspecified,
            ),
            bold = SpanStyle(fontWeight = FontWeight.Bold, color = tone?.bold ?: Color.Unspecified),
            italic = SpanStyle(fontStyle = FontStyle.Italic),
            // Gach chan manh: dau hieu "bam duoc" (user chot 16/9, prototype
            // kohi-chat-typography). Truoc day chi doi mau nen link chim vao cau.
            link = SpanStyle(
                color = tone?.link ?: colors.accent,
                textDecoration = TextDecoration.Underline,
            ),
            quote = SpanStyle(color = colors.textSecondary, fontStyle = FontStyle.Italic),
            muted = SpanStyle(color = tone?.meta ?: colors.textMuted),
            h1 = SpanStyle(fontWeight = FontWeight.Bold, fontSize = type.body.fontSize * 1.25f),
            h2 = SpanStyle(fontWeight = FontWeight.Bold, fontSize = type.body.fontSize * 1.12f),
            h3 = SpanStyle(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
fun MiniMarkdownText(
    markdown: String,
    fontSize: TextUnit = TextUnit.Unspecified,
    /** Mau theo loai agent cua phien (claude/opencode/agy) — null = mau theme. */
    tone: ChatTone? = null,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val styles = rememberMdStyles(tone)
    val resolvedFontSize = if (fontSize != TextUnit.Unspecified) fontSize else type.body.fontSize
    // Cỡ chữ theo caller (màn CHAT truyền cỡ chữ terminal trong Settings); dãn dòng để TỰ NHIÊN
    // của font (ascent+descent) — đúng cách terminal vẽ, user 16/9: 1,6 "thưa quá", terminal "đạt".
    val textStyle = type.body.copy(
        color = tone?.body ?: colors.textPrimary,
        fontSize = resolvedFontSize,
        lineHeight = TextUnit.Unspecified,
    )
    // Bề rộng 1 ký tự mono ≈ 0,6em (JetBrains Mono/Fira/Geist đều vậy). Thụt lề tính
    // theo cỡ chữ người dùng chọn trong Settings chứ không phải hằng số dp.
    val density = LocalDensity.current
    val chDp = with(density) { (resolvedFontSize * 0.6f).toDp() }
    val indent2 = with(density) { (chDp * 2).toSp() }

    Column {
        val blocks = remember(markdown) { splitBlocks(markdown) }
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Table -> MarkdownTable(block = block, styles = styles)
                is MdBlock.Text -> MdTextBlock(
                    lines = block.lines,
                    styles = styles,
                    textStyle = textStyle,
                    chDp = chDp,
                    indent2 = indent2,
                )
            }
        }
    }
}

// ─────────────────────────── units ───────────────────────────

/**
 * Mot dong markdown da phan loai. Truoc day ca khoi text la MOT AnnotatedString
 * nen khong the thut le doan, khong the treo le muc danh sach (AnnotatedString
 * khong co padding/le doan). Tach thanh don vi roi moi ve — parser thuan Kotlin
 * nen test duoc khong can compose harness.
 */
internal sealed class MdUnit {
    data class Para(val text: String) : MdUnit()
    data class Heading(val level: Int, val text: String) : MdUnit()
    /** Gach-ngoang; `checked != null` = muc checkbox `- [ ]` / `- [x]`. */
    data class Bullet(val text: String, val depth: Int, val checked: Boolean? = null) : MdUnit()
    /** Danh sách số; marker giữ nguyên như agent ghi ("1.", "2)"). */
    data class Numbered(val marker: String, val text: String, val depth: Int) : MdUnit()
    data class Quote(val text: String) : MdUnit()
    data class Fence(val lang: String, val lines: List<String>) : MdUnit()
    object Rule : MdUnit()
}

private val CHECKBOX_RE = Regex("^([-*])\\s+\\[([ xX])\\]\\s*(.*)$")
private val BULLET_RE = Regex("^([-*])\\s+(.*)$")
private val NUMBERED_RE = Regex("^(\\d+[.)])\\s+(.*)$")

/** Sâu hơn 3 cấp thì trên điện thoại vừa thụt vừa hẹp, không còn đọc được. */
private const val MD_MAX_DEPTH = 3

/**
 * Tach dong thanh don vi. Fence giu NGUYEN van tung dong (thut le code la
 * nghia) va khong cho dong ben trong bi hieu nham la list/bang. Fence khong
 * dong van hien — agent doi khi cat cut giua chung.
 */
internal fun parseMdUnits(lines: List<String>): List<MdUnit> {
    val units = mutableListOf<MdUnit>()
    var fenceLines: MutableList<String>? = null
    var fenceLang = ""

    for (raw in lines) {
        val line = raw.trimEnd()
        val t = line.trim()
        if (fenceLines != null) {
            if (t.startsWith("```")) {
                units += MdUnit.Fence(fenceLang, fenceLines!!.toList())
                fenceLines = null
            } else {
                fenceLines!!.add(line)
            }
            continue
        }
        if (t.startsWith("```")) {
            fenceLines = mutableListOf()
            fenceLang = t.removePrefix("```").trim()
            continue
        }
        if (t.isEmpty()) continue
        val depth = ((line.length - line.trimStart().length) / 2).coerceIn(0, MD_MAX_DEPTH)
        when {
            t.startsWith("#") && !t.dropWhile { it == '#' }.isBlank() -> units += MdUnit.Heading(
                level = t.takeWhile { it == '#' }.length.coerceAtMost(3),
                text = t.dropWhile { it == '#' || it == ' ' },
            )
            t.startsWith(">") -> units += MdUnit.Quote(t.removePrefix(">").trim())
            t.matches(HR_RE) -> units += MdUnit.Rule
            else -> {
                val checkbox = CHECKBOX_RE.find(t)
                val bullet = BULLET_RE.find(t)
                val numbered = NUMBERED_RE.find(t)
                when {
                    checkbox != null -> units += MdUnit.Bullet(
                        text = checkbox.groupValues[3],
                        depth = depth,
                        checked = checkbox.groupValues[2].equals("x", ignoreCase = true),
                    )
                    bullet != null -> units += MdUnit.Bullet(text = bullet.groupValues[2], depth = depth)
                    numbered != null -> units += MdUnit.Numbered(
                        marker = numbered.groupValues[1],
                        text = numbered.groupValues[2],
                        depth = depth,
                    )
                    else -> units += MdUnit.Para(t)
                }
            }
        }
    }
    if (fenceLines != null) units += MdUnit.Fence(fenceLang, fenceLines!!.toList())
    return units
}

// ─────────────────────────── unit renderer ───────────────────────────

@Composable
private fun MdTextBlock(
    lines: List<String>,
    styles: MdStyles,
    textStyle: TextStyle,
    chDp: Dp,
    indent2: TextUnit,
) {
    val colors = ChuColors.current
    val units = remember(lines) { parseMdUnits(lines) }
    Column {
        units.forEach { unit ->
            when (unit) {
                is MdUnit.Para -> BasicText(
                    text = remember(unit.text, styles) { inlineAnnotated(unit.text, styles) },
                    // Thut dong dau 2ch: thay cho dong trong ngan cach doan —
                    // doc ra cho ngat doan ma khong phi ca mot dong.
                    style = textStyle.copy(textIndent = TextIndent(firstLine = indent2)),
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                is MdUnit.Heading -> {
                    val span = when (unit.level) {
                        1 -> styles.h1
                        2 -> styles.h2
                        else -> styles.h3
                    }
                    val annotated = remember(unit.text, styles, span) {
                        buildAnnotatedString { withStyle(span) { appendInline(unit.text, styles) } }
                    }
                    val barW = 3.dp
                    val withBar = unit.level <= 2
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (withBar) 10.dp else 8.dp, bottom = if (withBar) 6.dp else 4.dp)
                            .then(
                                if (withBar) {
                                    Modifier.drawBehind {
                                        drawRect(
                                            color = colors.accentSecondary,
                                            size = Size(barW.toPx(), size.height.toFloat()),
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(start = if (withBar) 11.dp else 0.dp),
                    ) {
                        BasicText(text = annotated, style = textStyle)
                    }
                }

                is MdUnit.Bullet -> MdListItem(
                    marker = when (unit.checked) {
                        null -> "−"
                        true -> "☑"
                        false -> "☐"
                    },
                    markerColor = when (unit.checked) {
                        null -> colors.accent
                        true -> colors.success
                        false -> colors.textMuted
                    },
                    contentColor = if (unit.checked == true) colors.textSecondary else textStyle.color,
                    text = unit.text,
                    depth = unit.depth,
                    styles = styles,
                    textStyle = textStyle,
                    chDp = chDp,
                )

                is MdUnit.Numbered -> MdListItem(
                    marker = unit.marker,
                    markerColor = colors.accent,
                    contentColor = textStyle.color,
                    text = unit.text,
                    depth = unit.depth,
                    styles = styles,
                    textStyle = textStyle,
                    chDp = chDp,
                )

                is MdUnit.Quote -> {
                    val barW = 3.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .drawBehind {
                                drawRect(
                                    color = colors.accentSecondary.copy(alpha = 0.7f),
                                    size = Size(barW.toPx(), size.height.toFloat()),
                                )
                            }
                            .padding(start = 10.dp),
                    ) {
                        BasicText(
                            text = remember(unit.text, styles) {
                                buildAnnotatedString { withStyle(styles.quote) { appendInline(unit.text, styles) } }
                            },
                            style = textStyle,
                        )
                    }
                }

                is MdUnit.Fence -> MdFence(fence = unit, styles = styles, textStyle = textStyle)

                MdUnit.Rule -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .height(1.dp)
                        .background(colors.border),
                )
            }
        }
    }
}

/**
 * Muc danh sach: marker o cot rieng, chu thut le treo — dong thu 2 (khi xuong
 * dong) thang duoi chu dong 1 chu khong dinh le trai nhu truoc.
 */
@Composable
private fun MdListItem(
    marker: String,
    markerColor: Color,
    contentColor: Color,
    text: String,
    depth: Int,
    styles: MdStyles,
    textStyle: TextStyle,
    chDp: Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = chDp * (2 * depth), bottom = 4.dp),
    ) {
        BasicText(
            text = marker,
            style = textStyle.copy(color = markerColor),
            maxLines = 1,
            modifier = Modifier.width(chDp * 2.6f),
        )
        BasicText(
            text = remember(text, styles) { inlineAnnotated(text, styles) },
            style = textStyle.copy(color = contentColor),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Khoi ``` thanh mot panel co nen + vien + nhan ngon ngu — truoc day chi la
 * tung dong chu to mau nen, lan vao chu thuong khong phan biet duoc.
 */
@Composable
private fun MdFence(fence: MdUnit.Fence, styles: MdStyles, textStyle: TextStyle) {
    val colors = ChuColors.current
    val bg = styles.code.background
    val codeOnly = SpanStyle(fontFamily = styles.code.fontFamily, color = styles.code.color ?: textStyle.color)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column {
            if (fence.lang.isNotBlank()) {
                BasicText(
                    text = fence.lang,
                    style = textStyle.copy(color = colors.textMuted, fontSize = textStyle.fontSize * 0.85f),
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.End),
                )
            }
            // Link trong khoi code van bam duoc (16/9) — khong duoc ha xuong.
            BasicText(
                text = remember(fence.lines, styles) {
                    buildAnnotatedString {
                        fence.lines.forEachIndexed { i, line ->
                            if (i > 0) append('\n')
                            withStyle(codeOnly) { appendPlainLinkified(line, styles) }
                        }
                    }
                },
                style = textStyle,
            )
        }
    }
}

// ─────────────────────────── table renderer ───────────────────────────

@Composable
private fun MarkdownTable(block: MdBlock.Table, styles: MdStyles) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, colors.border, RoundedCornerShape(4.dp)),
    ) {
        // Header — nen surfaceVariant, chu dam
        Row(modifier = Modifier.fillMaxWidth().background(colors.surfaceVariant)) {
            block.header.forEach { h ->
                BasicText(
                    text = inlineAnnotated(h, styles),
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = type.labelSmall.fontSize,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        block.rows.forEachIndexed { index, row ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border.copy(alpha = 0.4f)),
            )
            // Zebra nhe de doc hang dai
            Row(
                modifier = Modifier.fillMaxWidth().background(
                    if (index % 2 == 1) colors.surfaceVariant.copy(alpha = 0.4f) else Color.Transparent,
                ),
            ) {
                row.forEach { cell ->
                    BasicText(
                        text = inlineAnnotated(cell, styles),
                        style = TextStyle(
                            color = colors.textSecondary,
                            fontSize = type.labelSmall.fontSize,
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
                // Pad nhung hang it cot hon header de khong xep lung tung
                repeat((block.header.size - row.size).coerceAtLeast(0)) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ─────────────────────────── inline builder ───────────────────────────

/** Cell/table can AnnotatedString dung inline style — goi helper nay. */
private fun inlineAnnotated(text: String, s: MdStyles): AnnotatedString = buildAnnotatedString {
    appendInline(text, s)
}

private val HR_RE = Regex("(-{3,}|\\*{3,}|_{3,})")
private val LIST_RE = Regex("^([-*]|\\d+[.)])\\s")

private val INLINE_MD = Regex(
    "`[^`\\n]+`"                       // `code`
    + "|\\*\\*\\*[^*\\n]+\\*\\*\\*"      // ***bold-italic*** — phai truoc bold
    + "|\\*\\*[^*\\n]+\\*\\*"          // **bold**
    + "|\\*[^*\\n]+\\*"                // *italic*
    + "|\\[[^\\]\\n]+\\]\\([^)\\n]+\\)" // [label](url)
)

internal fun buildMiniMarkdown(md: String, s: MdStyles): AnnotatedString = buildAnnotatedString {
    var inFence = false
    for (raw in md.lines()) {
        val line = raw.trimEnd()
        if (line.trimStart().startsWith("```")) {
            inFence = !inFence
            continue
        }
        if (inFence) {
            // Link trong khối code cũng phải bấm được (16/9): các bản trả lời hay đặt URL
            // trong ``` — trước đây khối này chỉ append chữ thường nên không có link.
            withStyle(s.code) { appendPlainLinkified(line, s); append('\n') }
            continue
        }
        val t = line.trim()
        when {
            t.isEmpty() -> append('\n')
            // Dau "#" troong thi bo qua chu tao khoang trong vo hinh.
            t.startsWith("#") && !t.dropWhile { it == '#' }.isBlank() -> {
                val level = t.takeWhile { it == '#' }.length.coerceAtMost(3)
                val style = when (level) { 1 -> s.h1; 2 -> s.h2; else -> s.h3 }
                withStyle(style) { appendInline(t.dropWhile { it == '#' || it == ' ' }, s) }
                append('\n')
            }
            t.startsWith(">") -> {
                append("▏ ")
                withStyle(s.quote) { appendInline(t.removePrefix(">").trim(), s) }
                append('\n')
            }
            t.matches(HR_RE) -> {
                withStyle(s.muted) { append("────────────────────────────────") }
                append('\n')
            }
            LIST_RE.containsMatchIn(t.take(4)) -> {
                val markerEnd = t.indexOfFirst { it == ' ' }.coerceAtLeast(1)
                withStyle(s.bold) { append(t.take(markerEnd)); append(' ') }
                appendInline(t.substring(markerEnd + 1), s)
                append('\n')
            }
            else -> {
                appendInline(t, s)
                append('\n')
            }
        }
    }
}

/**
 * Link BẤM ĐƯỢC (16/9, user: "link bên Queue phải bấm vào được"): `[nhãn](url)` và URL trần
 * đều thành [LinkAnnotation.Url] — BasicText của Compose 1.7 tự mở qua LocalUriHandler, không
 * cần ClickableText. Dùng cho markdown lẫn chữ thường ([LinkifiedText]).
 */
private val BARE_URL = Regex("""(?:https?://|www\.)[^\s<>"'`]+""")

private fun trimUrlTail(u: String): String {
    var s = u
    while (s.isNotEmpty()) {
        val c = s.last()
        val drop = when (c) {
            '.', ',', ';', ':', '!', '?', '\'', '"' -> true
            ')' -> s.count { it == '(' } < s.count { it == ')' }
            ']' -> s.count { it == '[' } < s.count { it == ']' }
            else -> false
        }
        if (!drop) break
        s = s.dropLast(1)
    }
    return s
}

private fun AnnotatedString.Builder.appendLinked(label: String, url: String, s: MdStyles) {
    val target = if (url.startsWith("www.")) "https://$url" else url
    withLink(LinkAnnotation.Url(target, TextLinkStyles(style = s.link))) { append(label) }
}

/** Chữ thường: chỉ bắt URL trần, không parse markdown. */
private fun AnnotatedString.Builder.appendPlainLinkified(text: String, s: MdStyles) {
    var i = 0
    for (m in BARE_URL.findAll(text)) {
        val url = trimUrlTail(m.value)
        if (url.length < 8) continue
        if (m.range.first > i) append(text.substring(i, m.range.first))
        appendLinked(url, url, s)
        i = m.range.first + url.length
    }
    if (i < text.length) append(text.substring(i))
}

@Composable
fun LinkifiedText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val styles = rememberMdStyles(tone = null)
    val built = remember(text, styles) { buildAnnotatedString { appendPlainLinkified(text, styles) } }
    BasicText(text = built, style = style.copy(color = color), modifier = modifier)
}

/** Xu ly inline trong MOT dong: code > bold-italic > bold > italic > link. */
private fun AnnotatedString.Builder.appendInline(text: String, s: MdStyles) {
    var i = 0
    for (m in INLINE_MD.findAll(text)) {
        if (m.range.first > i) appendPlainLinkified(text.substring(i, m.range.first), s)
        val tok = m.value
        when {
            tok.startsWith("`") -> withStyle(s.code) { appendPlainLinkified(tok.trim('`'), s) }
            // Nội dung trong **đậm**/*nghiêng* vẫn phải linkify: URL hay được agent bọc đậm
            // (16/9 user: link trong chat opencode không bấm được — URL nằm trong **...**).
            tok.startsWith("***") -> withStyle(s.bold.copy(fontStyle = FontStyle.Italic)) {
                appendPlainLinkified(tok.removeSurrounding("***"), s)
            }
            tok.startsWith("**") -> withStyle(s.bold) { appendPlainLinkified(tok.removeSurrounding("**"), s) }
            tok.startsWith("*") -> withStyle(s.italic) { appendPlainLinkified(tok.removeSurrounding("*"), s) }
            tok.startsWith("[") -> {
                val label = tok.substringAfter('[').substringBefore(']')
                val url = tok.substringAfter("](", "").substringBeforeLast(')').trim()
                if (url.isNotBlank()) appendLinked(label, url, s) else withStyle(s.link) { append(label) }
            }
        }
        i = m.range.last + 1
    }
    if (i < text.length) appendPlainLinkified(text.substring(i), s)
}
