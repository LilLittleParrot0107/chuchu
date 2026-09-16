package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** qsrv /chat → ChatPage: vai, kết quả tool, key theo offset, cursor/has_more. */
class ChatPageParseTest {
    private val body = """
        {"ok":true,"pane":"wH:p1","name":"director","cwd":"/home/a/aividstudio","file":"abc.jsonl","size":12345,
         "rev":"1789.12345","cursor":9000,"has_more":true,
         "messages":[
           {"role":"user","uuid":"u1","ts":"2026-09-16T05:04:31.123Z","text":"sao sai thế","off":100},
           {"role":"think","uuid":"a1","ts":"2026-09-16T05:04:40.000Z","off":200},
           {"role":"tool","uuid":"a1","id":"t1","ts":"2026-09-16T05:04:41.000Z","name":"Bash","desc":"Look at report","off":200,"res":"yield 61.18","resLen":900,"err":false},
           {"role":"assistant","uuid":"a2","ts":"2026-09-16T05:05:13.000Z","text":"Sai **thật**.","off":300},
           {"role":"result","for":"t1","res":"x"},
           {"role":"weird","text":"bỏ"}
         ]}
    """.trimIndent()

    @Test
    fun parsesRolesResultsAndPaging() {
        val p = ChatPage.parse(body)
        assertEquals("director", p.name)
        assertEquals("1789.12345", p.rev)
        assertEquals(9000L, p.cursor)
        assertTrue(p.hasMore)
        assertEquals(listOf("user", "think", "tool", "assistant"), p.messages.map { it.role })
        val tool = p.messages[2]
        assertEquals("Bash", tool.toolName)
        assertEquals("Look at report", tool.desc)
        assertEquals("yield 61.18", tool.res)
        assertEquals(900, tool.resLen)
        // hai tin cùng bản ghi (offset 200) phải có key khác nhau
        assertEquals("200:0", p.messages[1].key)
        assertEquals("200:1", p.messages[2].key)
        assertEquals("100:0", p.messages[0].key)
    }

    @Test
    fun endOfFileHasNoCursor() {
        val p = ChatPage.parse("""{"ok":true,"pane":"wH:p1","rev":"1.1","cursor":null,"has_more":false,"messages":[]}""")
        assertNull(p.cursor)
        assertEquals(false, p.hasMore)
        assertTrue(p.messages.isEmpty())
    }

    @Test
    fun clockAndAge() {
        assertEquals("", chatClock("rác"))
        assertEquals(5, chatClock("2026-09-16T05:04:31.123Z").length)
        assertEquals("updated 12s ago", chatAge(1_000_000L, 1_012_000L))
        assertEquals("updated 3m ago", chatAge(1_000_000L, 1_000_000L + 3 * 60_000L))
        assertEquals("", chatAge(0L))
    }
}
