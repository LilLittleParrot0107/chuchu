package com.jossephus.chuchu.ui.components

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
            link = SpanStyle(color = tone?.link ?: colors.accent),
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
    // Cỡ chữ theo caller (màn CHAT truyền cỡ chữ terminal trong Settings); dãn dòng để TỰ NHIÊN
    // của font (ascent+descent) — đúng cách terminal vẽ, user 16/9: 1,6 "thưa quá", terminal "đạt".
    val textStyle = type.body.copy(
        color = tone?.body ?: colors.textPrimary,
        fontSize = if (fontSize != TextUnit.Unspecified) fontSize else type.body.fontSize,
        lineHeight = TextUnit.Unspecified,
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val blocks = remember(markdown) { splitBlocks(markdown) }
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Table -> MarkdownTable(block = block, styles = styles)
                is MdBlock.Text -> {
                    val md = block.lines.joinToString("\n")
                    val built = remember(md, styles) { buildMiniMarkdown(md, styles) }
                    // Moi block deu append newline ke ca block cuoi -> thua
                    // mot dong rong.
                    val annotated = if (built.endsWith("\n")) built.subSequence(0, built.length - 1) else built
                    // type.body mang fontFamily của Settings (trước đây TextStyle trần nên rơi về
                    // font hệ thống, khác hẳn phần còn lại).
                    BasicText(text = annotated, style = textStyle)
                }
            }
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
