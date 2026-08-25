package com.jossephus.chuchu.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parser block cua MiniMarkdown — bang markdown phai duoc nhan dung. */
class MiniMarkdownBlockTest {

    @Test
    fun `bang gfm chuan tach thanh header va rows`() {
        val md = """
            | ten | gia tri |
            | --- | --- |
            | alpha | 1 |
            | beta | 2 |
        """.trimIndent()

        val blocks = splitBlocks(md)

        assertEquals(1, blocks.size)
        val table = blocks.first() as MdBlock.Table
        assertEquals(listOf("ten", "gia tri"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("alpha", "1"), table.rows[0])
    }

    @Test
    fun `separator voi colon van duoc nhan`() {
        assertTrue(isTableSeparator("| :--- | ---: |"))
        assertTrue(isTableSeparator("| :---: |"))
    }

    @Test
    fun `text co ky tu pipe nhung khong co separator khong phai bang`() {
        val md = "a | b | c"
        val blocks = splitBlocks(md)
        assertTrue(blocks.first() is MdBlock.Text)
    }

    @Test
    fun `bang trong code fence khong bi nhan la bang`() {
        val md = """
            ```
            | a | b |
            | --- | --- |
            ```
        """.trimIndent()

        val blocks = splitBlocks(md)
        assertTrue(blocks.all { it is MdBlock.Text })
    }

    @Test
    fun `text va bang lan lo tach dung so block`() {
        val md = """
            doan dau

            | a | b |
            | --- | --- |
            | 1 | 2 |

            doan cuoi
        """.trimIndent()

        val blocks = splitBlocks(md)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MdBlock.Text)
        assertTrue(blocks[1] is MdBlock.Table)
        assertTrue(blocks[2] is MdBlock.Text)
    }

    @Test
    fun `hang thieu cot hon header van giu nguyen`() {
        val md = """
            | a | b | c |
            | --- | --- | --- |
            | chi mot cot |
        """.trimIndent()

        val table = splitBlocks(md).first() as MdBlock.Table
        assertEquals(1, table.rows.size)
        assertEquals(listOf("chi mot cot"), table.rows[0])
        assertFalse(table.rows[0].size == table.header.size)
    }
}
