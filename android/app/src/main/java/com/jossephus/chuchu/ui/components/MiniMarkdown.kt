package com.jossephus.chuchu.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

/**
 * MiniMarkdownText — render tap bi doi markdown bang AnnotatedString, KHONG
 * them dependency nao. Sinh ra vi TaskDetailDialog truoc day dua nguyen van
 * markdown vao BasicText -> nguoi doc thay so '**', '#', '`' xuyen tac.
 *
 * Phu dung phan markdown agent thuong tra loi: heading #..####, **bold**,
 * *italic*, `code`, fence ba-dau-nhay (khoi mono), list gach-ngoang hoac so,
 * blockquote >, ngang --- , link [t](u) (hien nhan, khong bat su kien mo link).
 * Nhan nhung gi khong hieu la chu thuong — khong mat chu.
 */

/** Tap style gom mot lan o compose, truen cho builder thuan Kotlin ben duoi. */
private class MdStyles(
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

@Composable
fun MiniMarkdownText(markdown: String) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    // Styles phai duoc remember: tao moi moi recompose lam key cua
    // remember(markdown, styles) thay doi lien tuc -> parse lai toan bo text.
    val styles = remember(colors, type) {
        MdStyles(
            code = SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceVariant),
            bold = SpanStyle(fontWeight = FontWeight.Bold),
            italic = SpanStyle(fontStyle = FontStyle.Italic),
            link = SpanStyle(color = colors.accent),
            quote = SpanStyle(color = colors.textSecondary, fontStyle = FontStyle.Italic),
            muted = SpanStyle(color = colors.textMuted),
            h1 = SpanStyle(fontWeight = FontWeight.Bold, fontSize = type.body.fontSize * 1.25f),
            h2 = SpanStyle(fontWeight = FontWeight.Bold, fontSize = type.body.fontSize * 1.12f),
            h3 = SpanStyle(fontWeight = FontWeight.Bold),
        )
    }
    val annotated = remember(markdown, styles) { buildMiniMarkdown(markdown, styles) }
    BasicText(text = annotated, style = TextStyle(color = colors.textPrimary, fontSize = type.body.fontSize))
}

private val INLINE_MD = Regex(
    "`[^`\\n]+`"                       // `code`
    + "|\\*\\*[^*\\n]+\\*\\*"          // **bold**
    + "|\\*[^*\\n]+\\*"                // *italic*
    + "|\\[[^\\]\\n]+\\]\\([^)\\n]+\\)" // [label](url)
)

private fun buildMiniMarkdown(md: String, s: MdStyles): AnnotatedString = buildAnnotatedString {
    var inFence = false
    for (raw in md.lines()) {
        val line = raw.trimEnd()
        if (line.trimStart().startsWith("```")) {
            inFence = !inFence
            continue
        }
        if (inFence) {
            withStyle(s.code) { append(line); append('\n') }
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
            t.matches(Regex("(-{3,}|\\*{3,}|_{3,})")) -> {
                withStyle(s.muted) { append("────────────────────────────────────────") }
                append('\n')
            }
            Regex("^([-*]|\\d+[.)])\\s").containsMatchIn(t.take(4)) -> {
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

/** Xu ly inline trong MOT dong: code > bold > italic > link, phan con lai thuong. */
private fun AnnotatedString.Builder.appendInline(text: String, s: MdStyles) {
    var i = 0
    for (m in INLINE_MD.findAll(text)) {
        if (m.range.first > i) append(text.substring(i, m.range.first))
        val tok = m.value
        when {
            tok.startsWith("`") -> withStyle(s.code) { append(tok.trim('`')) }
            tok.startsWith("**") -> withStyle(s.bold) { append(tok.removeSurrounding("**")) }
            tok.startsWith("*") -> withStyle(s.italic) { append(tok.removeSurrounding("*")) }
            tok.startsWith("[") -> {
                val label = tok.substringAfter('[').substringBefore(']')
                withStyle(s.link) { append(label) }
            }
        }
        i = m.range.last + 1
    }
    if (i < text.length) append(text.substring(i))
}
