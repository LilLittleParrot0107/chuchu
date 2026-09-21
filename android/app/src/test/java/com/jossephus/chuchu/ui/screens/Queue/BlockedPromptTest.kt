package com.jossephus.chuchu.ui.screens.Queue

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Thẻ NEEDS YOU (21/9): đọc payload `GET /blocked` của qsrv. */
class BlockedPromptTest {

    /** Dạng thật qsrv bóc được khi Claude Code xin quyền chạy lệnh (đo trên máy 21/9). */
    private val permission = """{"ok":true,"pane":"w3:p1","st":"blocked","blocked":true,"prompt":{
        "kind":"permission","title":"Bash command",
        "question":"Do you want to proceed?",
        "detail":["git push -u origin fix/reader","Push the branch and set upstream"],
        "options":[{"n":1,"label":"Yes","desc":"","selected":true},
                   {"n":2,"label":"Yes, and always allow git push from this project","desc":"","selected":false},
                   {"n":4,"label":"No","desc":"","selected":false}],
        "hint":"Esc to cancel · Tab to amend"}}"""

    private fun prompt(json: String) = BlockedPrompt.parse(JSONObject(json).optJSONObject("prompt"))

    @Test
    fun `doc duoc prompt xin quyen, so lua chon giu y nhu terminal`() {
        val p = prompt(permission)!!
        assertEquals("permission", p.kind)
        assertEquals("Bash command", p.title)
        assertEquals("Do you want to proceed?", p.question)
        assertEquals(listOf("git push -u origin fix/reader", "Push the branch and set upstream"), p.detail)
        assertEquals(listOf(1, 2, 4), p.options.map { it.n })
        assertTrue(p.options[0].selected)
        assertFalse(p.options[1].selected)
        assertEquals("Esc to cancel · Tab to amend", p.hint)
    }

    @Test
    fun `khong co prompt hoac khong co lua chon nao thi null`() {
        assertNull(BlockedPrompt.parse(null))
        assertNull(prompt("""{"prompt":null}"""))
        assertNull(BlockedPrompt.parse(JSONObject("""{"kind":"generic","options":[]}""")))
        assertNull(BlockedPrompt.parse(JSONObject("""{"kind":"generic","options":[{"label":"khong co n"}]}""")))
    }

    @Test
    fun `cau hoi AskUserQuestion mang mo ta, lua chon go tiep mo o go duoi`() {
        val p = BlockedPrompt.parse(JSONObject("""{"kind":"question","title":"Chuyển cảnh",
            "question":"Dùng kiểu chuyển cảnh nào cho clip 3?",
            "options":[{"n":1,"label":"Cắt thẳng","desc":"nhanh, hợp nhịp nhạc","selected":true},
                       {"n":3,"label":"Type something.","desc":""},
                       {"n":4,"label":"Chat about this","desc":""}]}"""))!!
        assertEquals("nhanh, hợp nhịp nhạc", p.options[0].desc)
        assertFalse(p.options[0].opensComposer)
        assertTrue(p.options[1].opensComposer)
        assertTrue(p.options[2].opensComposer)
        assertEquals("generic", BlockedPrompt.parse(JSONObject("""{"options":[{"n":1,"label":"x"}]}"""))!!.kind)
    }

    @Test
    fun `chu ky doi khi prompt doi, khong doi theo chan prompt`() {
        val a = prompt(permission)!!
        assertNotEquals(a.signature, a.copy(question = "Allow this?").signature)
        assertNotEquals(a.signature, a.copy(options = a.options.drop(1)).signature)
        assertEquals(a.signature, a.copy(hint = "").signature)
    }
}
