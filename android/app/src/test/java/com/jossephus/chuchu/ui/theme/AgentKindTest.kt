package com.jossephus.chuchu.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Loại agent -> tông màu (user chốt 16/9, prototype kohi-agent-colors-prototype.html
 * phương án 1B cho roster + 2B cho chat). Màu chat là màu ĐO TỪ TERMINAL, test này
 * ghim lại để không ai "dọn dẹp" đổi mất.
 */
class AgentKindTest {

    @Test
    fun `nhan dien loai agent tu herdr`() {
        assertEquals(AgentKind.CLAUDE, AgentKind.of("claude"))
        assertEquals(AgentKind.OPENCODE, AgentKind.of("opencode"))
        assertEquals(AgentKind.AGY, AgentKind.of("agy"))
        assertEquals(AgentKind.AGY, AgentKind.of("antigravity"))
        // Lạ/thiếu thì giữ mặc định, KHÔNG đoán (bài học ghép transcript 16/9).
        assertEquals(AgentKind.OTHER, AgentKind.of(null))
        assertEquals(AgentKind.OTHER, AgentKind.of("  "))
        assertEquals(AgentKind.OTHER, AgentKind.of("gemini"))
        // 23/9: Codex CLI có họ riêng
        assertEquals(AgentKind.CODEX, AgentKind.of("codex"))
    }

    @Test
    fun `mau chat theo dung terminal do duoc`() {
        assertEquals(Color(0xFFB1B9F9), AgentKind.CLAUDE.chatTone().code)
        assertEquals(Color(0xFF999999), AgentKind.CLAUDE.chatTone().meta)

        assertEquals(Color(0xFFEFC11A), AgentKind.OPENCODE.chatTone().bold)
        assertEquals(Color(0xFF97D67E), AgentKind.OPENCODE.chatTone().code)
        assertEquals(Color(0xFF4E7CBF), AgentKind.OPENCODE.chatTone().link)

        assertEquals(Color(0xFFEEE8D5), AgentKind.AGY.chatTone().body)
        assertEquals(Color(0xFF93A1A1), AgentKind.AGY.chatTone().meta)
    }

    @Test
    fun `loai la giu nguyen mau theme`() {
        val other = AgentKind.OTHER.chatTone()
        assertNull(other.body)
        assertNull(other.bold)
        assertNull(other.code)
        assertNull(other.codeBg)
        assertNull(other.link)
        assertNull(other.meta)
        assertNull(other.toolName)
    }
}
