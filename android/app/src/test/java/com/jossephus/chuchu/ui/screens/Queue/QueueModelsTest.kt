package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueModelsTest {

    /** Bản chụp thật từ `curl /state?view=app` ngày 20/8. */
    private val realPayload = """
    {"rev":"1787218211.571-0","paused":false,"banner":null,
     "summary":"1 dang cho · 1 xong",
     "global_actions":[{"op":"pause","label":"Tam dung","needs_rev":false,"danger":false}],
     "agents":[{"pane":"w3:p1","name":"lovely-agent","glyph":"●","tone":"ok","label":"ranh"}],
     "tasks":[
       {"id":3,"text":"chay test","state":"pending","glyph":"○","tone":"dim",
        "state_label":"dang cho","sub":"w3:p1 · lovely-agent · 5 phut truoc",
        "actions":[{"op":"top","label":"Len dau","needs_rev":true,"danger":false},
                   {"op":"del","label":"Xoa","needs_rev":false,"danger":true}]},
       {"id":4,"text":"da xong","state":"done","glyph":"✓","tone":"ok",
        "state_label":"xong","sub":"w3:p2 · chuchu · 2 gio truoc",
        "actions":[{"op":"del","label":"Xoa","needs_rev":false,"danger":true}]}]}
    """.trimIndent()

    @Test
    fun `doc duoc payload that`() {
        val s = QueueState.parse(realPayload)
        assertEquals("1787218211.571-0", s.rev)
        assertEquals(false, s.paused)
        assertNull(s.banner)
        assertEquals(2, s.tasks.size)
        assertEquals(1, s.agents.size)
        assertEquals("lovely-agent", s.agents[0].name)
        assertEquals(QueueTone.Ok, s.agents[0].tone)

        val first = s.tasks[0]
        assertEquals(3, first.id)
        assertEquals("dang cho", first.stateLabel)
        assertEquals(QueueTone.Dim, first.tone)
        assertEquals(2, first.actions.size)
        assertTrue(first.actions[0].needsRev)
        assertTrue(first.actions[1].danger)

        assertEquals(1, s.globalActions.size)
        assertEquals("pause", s.globalActions[0].op)
    }

    /**
     * Chốt chặn cho đúng lỗi đã xảy ra ngày 20/8: qd nghĩ ra trạng thái mới,
     * client cũ hiện nó thành `pending` nên người dùng không thấy gì bất thường.
     * Trạng thái lạ PHẢI giữ được tên thật và không rơi vào tông vô hình.
     */
    @Test
    fun `trang thai la van hien ten that`() {
        val s = QueueState.parse(
            """{"tasks":[{"id":9,"text":"x","state":"quarantined","glyph":"⚠",
                "tone":"warn","state_label":"quarantined","sub":"","actions":[]}]}"""
        )
        val t = s.tasks.single()
        assertEquals("quarantined", t.state)
        assertEquals("quarantined", t.stateLabel)
        assertEquals(QueueTone.Warn, t.tone)
    }

    @Test
    fun `tong la thi ve nhat chu khong vo hinh`() {
        val s = QueueState.parse("""{"tasks":[{"id":1,"tone":"neon-pink"}]}""")
        assertEquals(QueueTone.Dim, s.tasks.single().tone)
    }

    @Test
    fun `thieu truong khong lam vo`() {
        val s = QueueState.parse("{}")
        assertEquals("", s.rev)
        assertEquals(false, s.paused)
        assertTrue(s.tasks.isEmpty())
        assertTrue(s.agents.isEmpty())
        assertTrue(s.globalActions.isEmpty())
    }

    @Test
    fun `thieu state_label thi lay ten trang thai tho`() {
        val s = QueueState.parse("""{"tasks":[{"id":2,"state":"sending"}]}""")
        assertEquals("sending", s.tasks.single().stateLabel)
    }

    /** Server thêm trường mới không được làm app cũ hỏng. */
    @Test
    fun `truong la bi bo qua`() {
        val s = QueueState.parse(
            """{"rev":"r1","tuong_lai":{"a":1},
                "tasks":[{"id":5,"text":"t","priority":"high","eta_ms":900}]}"""
        )
        assertEquals("r1", s.rev)
        assertEquals(5, s.tasks.single().id)
    }

    @Test
    fun `phan tu khong phai object bi bo qua chu khong lam hong danh sach`() {
        val s = QueueState.parse("""{"tasks":["rac",null,{"id":7,"text":"that"}]}""")
        assertEquals(1, s.tasks.size)
        assertEquals(7, s.tasks.single().id)
    }

    @Test
    fun `doc duoc banner va trang thai tam dung`() {
        val s = QueueState.parse(
            """{"paused":true,"banner":{"tone":"warn","text":"Hang doi dang tam dung"},
                "global_actions":[{"op":"resume","label":"Chay tiep"}]}"""
        )
        assertTrue(s.paused)
        assertEquals(QueueTone.Warn, s.banner!!.tone)
        assertEquals("resume", s.globalActions.single().op)
        assertEquals(false, s.globalActions.single().needsRev)
    }

    @Test
    fun `action thieu nhan thi lay ten op`() {
        val s = QueueState.parse("""{"global_actions":[{"op":"pause"}]}""")
        assertEquals("pause", s.globalActions.single().label)
    }
}
