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
        assertEquals("waiting", first.stateLabel)
        assertEquals(QueueTone.Dim, first.tone)
        assertEquals(2, first.actions.size)
        assertTrue(first.actions[0].needsRev)
        assertTrue(first.actions[1].danger)

        assertEquals(1, s.globalActions.size)
        assertEquals("pause", s.globalActions[0].op)
        assertEquals("Pause", s.globalActions[0].label)
        assertEquals("5m ago", first.sub.substringAfterLast(" · "))
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
        assertEquals("Pause", s.globalActions.single().label)
    }

    @Test
    fun `task status helpers keep state rules in one place`() {
        val tasks = QueueState.parse(
            """{"tasks":[
                {"id":1,"state":"completed"},
                {"id":2,"state":"working"},
                {"id":3,"state":"pending"}
            ]}""",
        ).tasks

        assertTrue(tasks[0].isCompleted)
        assertTrue(tasks[1].isRunning)
        assertEquals(false, tasks[2].isCompleted)
        assertEquals(false, tasks[2].isRunning)
    }

    @Test
    fun `operation keys are canonical`() {
        val action = QueueAction("retry", "Retry", needsRev = false, danger = false)

        assertEquals("retry:9", action.operationKey(9))
        assertEquals("retry:-", action.operationKey(null))
        assertEquals("clear-done:*", QueueOperationKey.clearDone(null))
        assertTrue(QueueOperationKey.isClearDone("clear-done:w3:p1"))
    }

    @Test
    fun `ambient summary derives counts from canonical task states`() {
        val state = QueueState.parse(
            """{
                "agents":[{"pane":"p1","name":"agent","tone":"accent","label":"working"}],
                "tasks":[
                    {"id":1,"target":"p1","state":"working"},
                    {"id":2,"target":"p1","state":"pending"},
                    {"id":3,"target":"p1","state":"done"}
                ]
            }""",
        )

        val summary = QueueAmbientSummary.from(state, error = null)
        assertEquals(2, summary.totalActive)
        assertEquals(1, summary.runningCount)
        assertEquals(1, summary.pendingCount)
        assertEquals(1, summary.activeTaskId)
        assertEquals("running", summary.statusText)
    }

    /**
     * Parser dễ tính + `key = { it.id }` trong LazyColumn = văng app nếu hai
     * task cùng rơi về id mặc định. Task không có id thì bỏ, vì mọi thao tác
     * (top/up/del/retry) đều cần id nên có hiện ra cũng không làm gì được.
     */
    @Test
    fun `task khong co id bi bo de khong trung key`() {
        val s = QueueState.parse(
            """{"tasks":[
                {"text":"khong id"},
                {"text":"cung khong id"},
                {"id":8,"text":"co id"},
                {"id":8,"text":"trung id"}
            ]}"""
        )
        assertEquals(1, s.tasks.size)
        assertEquals(8, s.tasks.single().id)
        assertEquals(s.tasks.size, s.tasks.map { it.id }.distinct().size)
    }

    @Test
    fun `agent without a unique pane is omitted`() {
        val state = QueueState.parse(
            """{"agents":[
                {"pane":"","name":"invalid"},
                {"pane":"p1","name":"first"},
                {"pane":"p1","name":"duplicate"}
            ]}""",
        )

        assertEquals(listOf("first"), state.agents.map(QueueAgent::name))
    }

    @Test
    fun `feedback duoc rut gon thanh mot dong`() {
        assertEquals(
            "Đã thêm task #12 vào hàng đợi",
            normalizeQueueFeedbackText("  Đã thêm task #12\n  vào   hàng đợi  ", "fallback"),
        )
    }

    @Test
    fun `feedback rong dung noi dung du phong`() {
        assertEquals("Đã cập nhật hàng đợi", normalizeQueueFeedbackText("  \n ", "Đã cập nhật hàng đợi"))
    }

    @Test
    fun `feedback qua dai bi gioi han`() {
        val normalized = normalizeQueueFeedbackText("a".repeat(200), "fallback")
        assertEquals(160, normalized.length)
        assertTrue(normalized.endsWith("…"))
    }
}
