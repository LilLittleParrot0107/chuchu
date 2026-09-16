package com.jossephus.chuchu.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser don vi markdown (16/9 toi, prototype kohi-chat-typography): cac muc
 * quyet dinh cach ve (thut le doan, treo le list, panel code, checkbox).
 * Test o day khoa HANH VI — renderer chi con viec ve theo don vi.
 */
class MiniMarkdownUnitTest {

    @Test
    fun `doan thuong giu nguyen chu va thut le do sau 0`() {
        val units = parseMdUnits(listOf("Xong anh, em chay test.", "", "Viec tiep theo:"))
        assertEquals(2, units.size)
        assertEquals(MdUnit.Para("Xong anh, em chay test."), units[0])
        assertEquals(MdUnit.Para("Viec tiep theo:"), units[1])
    }

    @Test
    fun `checkbox nhan ca x hoa va muc chua check`() {
        val units = parseMdUnits(listOf("- [x] deploy qsrv", "- [X] build apk", "- [ ] doi user"))
        assertEquals(
            listOf(
                MdUnit.Bullet("deploy qsrv", 0, checked = true),
                MdUnit.Bullet("build apk", 0, checked = true),
                MdUnit.Bullet("doi user", 0, checked = false),
            ),
            units,
        )
    }

    @Test
    fun `list long nhau tinh do sau theo so space dau dong`() {
        val units = parseMdUnits(
            listOf(
                "- muc cha",
                "  - muc con",
                "    - muc chau",
                "        - sau hon nua bi kep ve 3",
            ),
        )
        assertEquals(
            listOf(
                MdUnit.Bullet("muc cha", 0),
                MdUnit.Bullet("muc con", 1),
                MdUnit.Bullet("muc chau", 2),
                MdUnit.Bullet("sau hon nua bi kep ve 3", 3),
            ),
            units,
        )
    }

    @Test
    fun `danh sach so giu marker nhu agent ghi`() {
        val units = parseMdUnits(listOf("1. mot", "2) hai", "10. muoi"))
        assertEquals(
            listOf(
                MdUnit.Numbered("1.", "mot", 0),
                MdUnit.Numbered("2)", "hai", 0),
                MdUnit.Numbered("10.", "muoi", 0),
            ),
            units,
        )
    }

    @Test
    fun `tieu de kep cap 3 va cat dau thang`() {
        val units = parseMdUnits(listOf("# Ket qua", "### Sau", "##### Rat sau"))
        assertEquals(MdUnit.Heading(1, "Ket qua"), units[0])
        assertEquals(MdUnit.Heading(3, "Sau"), units[1])
        assertEquals(MdUnit.Heading(3, "Rat sau"), units[2])
    }

    @Test
    fun `quote va duong ke nhan dung`() {
        val units = parseMdUnits(listOf("> herdr go done ngay", "---", "***"))
        assertEquals(MdUnit.Quote("herdr go done ngay"), units[0])
        assertEquals(MdUnit.Rule, units[1])
        assertEquals(MdUnit.Rule, units[2])
    }

    @Test
    fun `fence giu nguyen van tung dong va nhan ngon ngu`() {
        val units = parseMdUnits(listOf("```bash", "  cd /home/a", "- muc trong code", "```"))
        assertEquals(1, units.size)
        val fence = units.first() as MdUnit.Fence
        assertEquals("bash", fence.lang)
        assertEquals(listOf("  cd /home/a", "- muc trong code"), fence.lines)
    }

    @Test
    fun `fence khong dong van hien chu khong mat`() {
        val units = parseMdUnits(listOf("```", "gh run watch 1 --exit-status"))
        val fence = units.single() as MdUnit.Fence
        assertEquals("", fence.lang)
        assertEquals(listOf("gh run watch 1 --exit-status"), fence.lines)
    }

    @Test
    fun `dong trong fence khong bi hieu la tieu de hay list`() {
        val units = parseMdUnits(listOf("```", "# khong phai tieu de", "| a | b |", "```"))
        val fence = units.single() as MdUnit.Fence
        assertTrue(fence.lines.contains("# khong phai tieu de"))
        assertTrue(fence.lines.contains("| a | b |"))
    }
}
