package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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
    fun `roster xep theo uu tien - can duyet tren cung, ranh duoi day, cung hang giu thu tu server`() {
        val s = QueueState.parse("""{"rev":1,"agents":[
            {"pane":"p1","name":"a","tone":"dim","label":"idle"},
            {"pane":"p2","name":"b","tone":"accent","label":"working"},
            {"pane":"p3","name":"c","tone":"warn","label":"needs approval"},
            {"pane":"p4","name":"d","tone":"warn","label":"unknown"},
            {"pane":"p5","name":"e","tone":"accent","label":"busy"},
            {"pane":"p6","name":"f","tone":"error","label":"gone"},
            {"pane":"p7","name":"g","tone":"dim","label":"idle"},
            {"pane":"p8","name":"h","tone":"ok","label":"done"}
        ],"tasks":[]}""")
        // 16/9 user chốt: vừa xong (done) đứng NGAY DƯỚI đang chạy, trên idle; unknown sau idle.
        assertEquals(listOf("p3", "p2", "p5", "p8", "p1", "p7", "p4", "p6"), s.agents.map { it.pane })
        // Nhan tieng Viet cua qsrv cu cung xep dung sau khi dich.
        val v = QueueState.parse("""{"rev":1,"agents":[
            {"pane":"x","label":"ranh"},{"pane":"y","label":"cho duyet"}],"tasks":[]}""")
        assertEquals(listOf("y", "x"), v.agents.map { it.pane })
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

    @Test
    fun `doc duoc loai agent cua pane`() {
        // qsrv chuyển nguyên field `agent` của herdr (16/9) — app tô màu tên theo loại.
        val s = QueueState.parse(
            """{"agents":[{"pane":"w1:p1","name":"OC | build","glyph":"●","tone":"ok",
                "label":"ranh","word":"","agent":"opencode","chat_rev":"12.3"},
               {"pane":"w1:p2","name":"agy","glyph":"○","tone":"dim","label":"ranh","word":""}]}"""
        )
        assertEquals("opencode", s.agents[0].agent)
        assertEquals("12.3", s.agents[0].chatRev)
        assertNull(s.agents[1].agent)
    }

    // ── UI G1 (16/9): preview trên /state + trang /feed ─────────────────────────

    @Test
    fun `doc duoc preview tin cuoi cua agent`() {
        val s = QueueState.parse(
            """{"agents":[{"pane":"w1:p1","name":"OC | build","glyph":"●","tone":"ok",
                "label":"ranh","preview":"xong rồi anh","preview_ts":"2026-09-16T05:04:31.123Z"}]}"""
        )
        val a = s.agents.single()
        assertEquals("xong rồi anh", a.preview)
        assertEquals("2026-09-16T05:04:31.123Z", a.previewTs)
    }

    @Test
    fun `agent cu khong co preview van doc duoc`() {
        val a = QueueState.parse("""{"agents":[{"pane":"p1","name":"a"}]}""").agents.single()
        assertEquals("", a.preview)
        assertEquals("", a.previewTs)
    }

    @Test
    fun `chat send key khoa theo pane`() {
        assertEquals("chat-send:w1:p1", QueueOperationKey.chatSend("w1:p1"))
    }

    @Test
    fun `feed page doc duoc trang that`() {
        val page = FeedPage.parse(
            """{"rev":"7-ab12cd34","pane":null,"messages":[
               {"pane":"w1:p1","name":"OC | build","agent":"opencode","label":"dang chay","tone":"accent",
                "role":"assistant","ts":"2026-09-16T05:04:31.123Z","text":"đang sửa","uuid":"u1","off":100},
               {"pane":"w1:p2","name":"claude","agent":"claude","label":"ranh","tone":"dim",
                "role":"user","ts":"2026-09-16T05:05:00.000Z","text":"gửi việc","uuid":"","off":9}]}"""
        )
        assertEquals("7-ab12cd34", page.rev)
        assertNull(page.pane)
        assertEquals(2, page.messages.size)
        assertEquals(QueueTone.Accent, page.messages[0].tone)
        assertEquals("assistant", page.messages[0].role)
        assertEquals("opencode", page.messages[0].agent)
        // uuid một mình không đủ làm key: opencode có nhiều đoạn text cùng uuid, khác off.
        assertEquals("w1:p1:u1:100", page.messages[0].key)
        assertEquals("w1:p2:2026-09-16T05:05:00.000Z:9", page.messages[1].key)
    }

    @Test
    fun `feed bo qua phan tu rac chu khong vo danh sach`() {
        val page = FeedPage.parse(
            """{"rev":"r","messages":["rác",null,{"pane":"p1","text":"that","role":"assistant"}]}"""
        )
        assertEquals(1, page.messages.size)
        assertEquals("that", page.messages.single().text)
    }

    /** Timeline chỉ kể chuyện người↔agent; tool/think là nhiễu, không phải tin. */
    @Test
    fun `feed bo vai tro khong phai tin`() {
        val page = FeedPage.parse(
            """{"rev":"r","messages":[
               {"pane":"p1","role":"tool","text":"x"},
               {"pane":"p1","role":"think","text":"y"}]}"""
        )
        assertTrue(page.messages.isEmpty())
    }

    @Test
    fun `chatWhen doi gio iso thanh nhan ngan`() {
        val base = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse("2026-09-16T05:04:31")!!.time
        assertEquals("vừa xong", chatWhen("2026-09-16T05:04:31.123Z", base + 1_000))
        assertEquals("3′", chatWhen("2026-09-16T05:04:31.123Z", base + 3 * 60_000))
        assertEquals("", chatWhen("rác", base))
    }
}
