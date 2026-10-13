package com.jossephus.chuchu.service.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Luật báo NEEDS YOU / XONG của vòng trực nền (12/10): chỉ hai chuyển trạng thái
 * đáng nổi thông báo, seed im lặng, rời trạng thái là huỷ, pane mất là huỷ.
 */
class AgentTransitionTrackerTest {

    private fun snap(pane: String, label: String, preview: String = "tin cuối") =
        AgentSnap(pane = pane, name = "agent-$pane", label = label, preview = preview)

    @Test
    fun `lan dau seed im lang`() {
        val tracker = AgentTransitionTracker()
        assertTrue(tracker.onState(listOf(snap("w1:p1", "Working"))).isEmpty())
    }

    @Test
    fun `vao blocked thi bao needs you kem preview`() {
        val tracker = AgentTransitionTracker()
        tracker.onState(listOf(snap("w1:p1", "Working")))
        val actions = tracker.onState(listOf(snap("w1:p1", "Needs approval", preview = "Cho phep chay rm?")))
        assertEquals(1, actions.size)
        val post = actions[0] as AgentAlertAction.Post
        assertEquals(AgentAlertKind.NeedsYou, post.kind)
        assertEquals("w1:p1", post.pane)
        assertEquals("Cho phep chay rm?", post.text)
    }

    @Test
    fun `roi blocked thi huy`() {
        val tracker = AgentTransitionTracker()
        tracker.onState(listOf(snap("w1:p1", "Working")))
        tracker.onState(listOf(snap("w1:p1", "Needs approval")))
        val actions = tracker.onState(listOf(snap("w1:p1", "Working")))
        assertEquals(listOf(AgentAlertAction.Cancel("w1:p1")), actions)
    }

    @Test
    fun `xong viec thi bao done kem phan hoi cuoi`() {
        val tracker = AgentTransitionTracker()
        tracker.onState(listOf(snap("w1:p1", "Working")))
        val actions = tracker.onState(listOf(snap("w1:p1", "Done", preview = "Da sua xong bug")))
        val post = actions.single() as AgentAlertAction.Post
        assertEquals(AgentAlertKind.Done, post.kind)
        assertEquals("Da sua xong bug", post.text)
    }

    @Test
    fun `done sang working thi huy (viec moi bat dau)`() {
        val tracker = AgentTransitionTracker()
        tracker.onState(listOf(snap("w1:p1", "Working")))
        tracker.onState(listOf(snap("w1:p1", "Done")))
        val actions = tracker.onState(listOf(snap("w1:p1", "Working")))
        assertEquals(listOf(AgentAlertAction.Cancel("w1:p1")), actions)
    }

    @Test
    fun `blocked sang done thi huy needs you roi bao done`() {
        val tracker = AgentTransitionTracker()
        tracker.onState(listOf(snap("w1:p1", "Working")))
        tracker.onState(listOf(snap("w1:p1", "Needs approval")))
        val actions = tracker.onState(listOf(snap("w1:p1", "Done", preview = "Xong roi")))
        assertEquals(2, actions.size)
        assertEquals(AgentAlertAction.Cancel("w1:p1"), actions[0])
        val post = actions[1] as AgentAlertAction.Post
        assertEquals(AgentAlertKind.Done, post.kind)
    }

    @Test
    fun `trang thai khong doi thi khong bao`() {
        val tracker = AgentTransitionTracker()
        tracker.onState(listOf(snap("w1:p1", "Working")))
        assertTrue(tracker.onState(listOf(snap("w1:p1", "Working"))).isEmpty())
        tracker.onState(listOf(snap("w1:p1", "Needs approval")))
        assertTrue(tracker.onState(listOf(snap("w1:p1", "Blocked"))).isEmpty())
    }

    @Test
    fun `pane bien mat thi huy`() {
        val tracker = AgentTransitionTracker()
        tracker.onState(listOf(snap("w1:p1", "Working")))
        val actions = tracker.onState(listOf(snap("w1:p2", "Working")))
        assertEquals(listOf(AgentAlertAction.Cancel("w1:p1")), actions)
    }

    @Test
    fun `pane moi xuat hien khong bao`() {
        val tracker = AgentTransitionTracker()
        tracker.onState(listOf(snap("w1:p1", "Working")))
        val actions = tracker.onState(listOf(snap("w1:p1", "Working"), snap("w1:p9", "Needs approval")))
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `reset thi seed lai tu dau`() {
        val tracker = AgentTransitionTracker()
        tracker.onState(listOf(snap("w1:p1", "Working")))
        tracker.reset()
        assertTrue(tracker.onState(listOf(snap("w1:p1", "Needs approval"))).isEmpty())
        val actions = tracker.onState(listOf(snap("w1:p1", "Working")))
        assertEquals(listOf(AgentAlertAction.Cancel("w1:p1")), actions)
    }

    @Test
    fun `restore thi so tiep khong seed lai (song qua process chet)`() {
        val tracker = AgentTransitionTracker()
        tracker.restore(listOf(snap("w1:p1", "Working")))
        val actions = tracker.onState(listOf(snap("w1:p1", "Done", preview = "Xong")))
        val post = actions.single() as AgentAlertAction.Post
        assertEquals(AgentAlertKind.Done, post.kind)
    }
}
