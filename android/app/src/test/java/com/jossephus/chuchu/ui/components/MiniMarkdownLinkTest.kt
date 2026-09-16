package com.jossephus.chuchu.ui.components

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Link phai BAM DUOC du agent boc URL trong kieu nao: **dam**, *nghieng*,
 * `code`, khoi ``` hay [nhan](url). User 16/9 (lan 2) bao URL nam trong
 * **...** khong bam duoc — day la test chan tai phat.
 */
class MiniMarkdownLinkTest {

    private val styles = MdStyles(
        code = SpanStyle(),
        bold = SpanStyle(fontWeight = FontWeight.Bold),
        italic = SpanStyle(),
        link = SpanStyle(),
        quote = SpanStyle(),
        muted = SpanStyle(),
        h1 = SpanStyle(),
        h2 = SpanStyle(),
        h3 = SpanStyle(),
    )

    private fun links(md: String): List<String> {
        val built = buildMiniMarkdown(md, styles)
        return built.getLinkAnnotations(0, built.length).map { (it.item as LinkAnnotation.Url).url }
    }

    @Test
    fun `url trong dam van thanh link`() {
        assertEquals(listOf("https://example.com/a.html"), links("**https://example.com/a.html**"))
    }

    @Test
    fun `url trong nghieng va dam-nghieng thanh link`() {
        assertEquals(listOf("https://example.com/b"), links("*https://example.com/b*"))
        assertEquals(listOf("https://example.com/c"), links("***https://example.com/c***"))
    }

    @Test
    fun `url tran trong code va trong fence thanh link`() {
        assertEquals(listOf("https://example.com/d"), links("xem https://example.com/d nhe"))
        assertEquals(listOf("https://example.com/e"), links("`https://example.com/e`"))
        assertEquals(listOf("https://example.com/f"), links("```\nhttps://example.com/f\n```"))
    }

    @Test
    fun `markdown link co nhan van thanh link`() {
        assertEquals(listOf("https://example.com/g"), links("[mo ra](https://example.com/g)"))
    }

    @Test
    fun `dau cau duoi url khong bi nuot vao link`() {
        assertEquals(listOf("https://example.com/h"), links("xem https://example.com/h."))
    }
}
