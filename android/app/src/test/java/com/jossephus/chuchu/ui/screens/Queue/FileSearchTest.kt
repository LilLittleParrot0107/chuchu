package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.ui.graphics.Color
import com.jossephus.chuchu.ui.screens.Web.highlightName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSearchTest {

    /** Shape thật của `qsrv /files/search` (25/9): khóa "dir" là BOOL, mtime là GIÂY. */
    private val realPayload = """
    {"ok":true,"total":2,"took_ms":4.2,"index_age_s":12.0,"index_size":123456,
     "results":[
       {"path":"debank/README.md","name":"README.md","dir":false,"size":1234,"mtime":1758700000},
       {"path":"debank","name":"debank","dir":true,"size":0,"mtime":1758600000}]}
    """.trimIndent()

    @Test
    fun `doc duoc payload that cua files_search`() {
        val r = FileSearchResult.parse(realPayload)
        assertEquals(2, r.total)
        assertEquals(4.2, r.tookMs, 0.001)
        assertEquals(2, r.hits.size)
        val file = r.hits[0]
        assertEquals("debank/README.md", file.path)
        assertEquals("README.md", file.name)
        assertEquals(false, file.isDir)
        assertEquals(1234L, file.size)
        // qsrv trả GIÂY — client đổi sang mili giây cho UI (dd/MM dùng Date(mtimeMs)).
        assertEquals(1758700000_000L, file.mtimeMs)
        val dir = r.hits[1]
        assertEquals(true, dir.isDir)
    }

    @Test
    fun `thieu truong thi khong do`() {
        val r = FileSearchResult.parse("""{"ok":true,"results":[]}""")
        assertEquals(0, r.hits.size)
        assertEquals(0, r.total)
        assertEquals(0.0, r.tookMs, 0.001)
    }

    @Test
    fun `parentDir la thu muc chua khong phai ten minh`() {
        val deep = FileHit(path = "a/b/c.txt", name = "c.txt", isDir = false, size = 1, mtimeMs = 1)
        assertEquals("a/b", deep.parentDir)
        val root = FileHit(path = "c.txt", name = "c.txt", isDir = false, size = 1, mtimeMs = 1)
        // Ở gốc portal: UI hiện "/" cho parentDir rỗng (prototype duyệt 25/9).
        assertEquals("", root.parentDir)
    }

    @Test
    fun `highlight to tu khop trong ten`() {
        val s = highlightName("kohi-search-v2.html", "search", Color.Red)
        assertEquals("kohi-search-v2.html", s.text)
        assertEquals(1, s.spanStyles.size)
        assertEquals(5, s.spanStyles[0].start)
        assertEquals(11, s.spanStyles[0].end)
        assertEquals(Color.Red, s.spanStyles[0].item.color)
    }

    @Test
    fun `highlight khong phan biet chu hoa va nhieu tu`() {
        val s = highlightName("Kohi-Search-V2.html", "kohi v2", Color.Red)
        assertEquals(2, s.spanStyles.size)
        assertEquals(0, s.spanStyles[0].start)
        assertEquals(4, s.spanStyles[0].end)
        assertEquals(12, s.spanStyles[1].start)
        assertEquals(14, s.spanStyles[1].end)
    }

    @Test
    fun `highlight khong khop thi khong co span`() {
        val s = highlightName("readme.md", "zzz", Color.Red)
        assertEquals("readme.md", s.text)
        assertTrue(s.spanStyles.isEmpty())
    }
}
