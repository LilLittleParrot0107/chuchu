package com.jossephus.chuchu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Loại agent của một pane — user chốt 16/9: "trong queue, màu chữ của session
 * đổi màu theo loại session". Nguồn: field `agent` qsrv chuyển nguyên từ herdr.
 *
 * Loại lạ/thiếu -> [OTHER] chứ KHÔNG đoán: giữ màu cũ, thà nhạt còn hơn gán bừa
 * (bài học 16/9: ghép transcript theo tên đoán bừa đã mở nhầm phiên người khác).
 */
enum class AgentKind {
    CLAUDE,
    OPENCODE,
    AGY,
    OTHER;

    companion object {
        fun of(agent: String?): AgentKind = when (agent?.trim()?.lowercase()) {
            "claude" -> CLAUDE
            "opencode" -> OPENCODE
            "agy", "antigravity" -> AGY
            else -> OTHER
        }
    }
}

/**
 * Màu tên session trên roster — phương án 1B user chốt 16/9: lấy từ palette app
 * (warning / accentSecondary / success) nên tự đổi theo theme thay vì ghim màu.
 */
@Composable
@ReadOnlyComposable
fun AgentKind.rosterColor(): Color = when (this) {
    AgentKind.CLAUDE -> ChuColors.current.warning
    AgentKind.OPENCODE -> ChuColors.current.accentSecondary
    AgentKind.AGY -> ChuColors.current.success
    AgentKind.OTHER -> ChuColors.current.textPrimary
}

/**
 * Màu phần nội dung tin của agent — phương án 2B user chốt 16/9: đúng màu TỪNG
 * TOOL tự phát trong terminal, đo bằng `herdr pane read <pane> --ansi` ngày 16/9
 * (SGR 38;2;R;G;B), không phỏng đoán:
 *  - Claude Code: chữ = fg theme, `code` #B1B9F9, meta/tool/suy nghĩ #999999.
 *  - opencode: chữ = fg theme, **đậm** #EFC11A, `code` #97D67E (user chốt
 *    16/9 tối: hạ bão hòa từ #9EFF6E đo được vì neon quá rực trên nền app),
 *    link/nhãn
 *    #4E7CBF, meta #CBB9A6, nền khối code #2E2A61.
 *  - agy (Solarized): chữ #EEE8D5, suy nghĩ/meta #93A1A1, `code` #B58900,
 *    link #268BD2.
 *
 * null = giữ mặc định theme. Màu ghim theo tool là CHỦ Ý: đây là màu chính tool
 * đó phát ra (Claude Code phát #B1B9F9, agy phát Solarized…) chứ không phải màu
 * app tự chế, nên đổi theme app không được phép đổi chúng.
 */
@Immutable
data class ChatTone(
    val body: Color? = null,
    val bold: Color? = null,
    val code: Color? = null,
    val codeBg: Color? = null,
    val link: Color? = null,
    val meta: Color? = null,
    val toolName: Color? = null,
)

fun AgentKind.chatTone(): ChatTone = when (this) {
    AgentKind.CLAUDE -> ChatTone(
        code = Color(0xFFB1B9F9),
        meta = Color(0xFF999999),
    )

    AgentKind.OPENCODE -> ChatTone(
        bold = Color(0xFFEFC11A),
        code = Color(0xFF97D67E),
        codeBg = Color(0xFF2E2A61),
        link = Color(0xFF4E7CBF),
        meta = Color(0xFFCBB9A6),
        toolName = Color(0xFF4E7CBF),
    )

    AgentKind.AGY -> ChatTone(
        body = Color(0xFFEEE8D5),
        code = Color(0xFFB58900),
        link = Color(0xFF268BD2),
        meta = Color(0xFF93A1A1),
        toolName = Color(0xFF839496),
    )

    AgentKind.OTHER -> ChatTone()
}
