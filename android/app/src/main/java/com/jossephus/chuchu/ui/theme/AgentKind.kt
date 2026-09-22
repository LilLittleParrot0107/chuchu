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

// ── Sắc riêng của từng phiên trong họ màu của loại agent (user chốt 22/9: băm tên, ±20% sáng,
// ±20° tông, 8 nấc). Cùng tên = cùng sắc mọi nơi (roster, CHAT, DÒNG THỜI GIAN); đổi tên là
// đổi sắc, như herdr. Độ sáng kẹp [0.35, 0.90] để không chìm trên nền tối, không trắng bệch.
// Chi phí: một hashCode chuỗi mỗi lần vẽ tem — không đáng đo, khỏi cache.

const val SESSION_SHADE_STEPS = 8
const val SESSION_SHADE_HUE_DEG = 20f
const val SESSION_SHADE_LIGHT = 0.20f

/** Nấc 0..steps-1 của tên phiên; String.hashCode theo chuẩn JVM nên ổn định qua các lần mở app. */
fun sessionStep(name: String, steps: Int = SESSION_SHADE_STEPS): Int {
    val key = name.trim().lowercase()
    if (key.isEmpty() || steps <= 1) return steps / 2
    return Math.floorMod(key.hashCode(), steps)
}

/** rgb 0..1 → (h 0..360, s 0..1, l 0..1). */
fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val l = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    var h = when (max) {
        r -> (g - b) / d + (if (g < b) 6f else 0f)
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    if (h < 0f) h += 360f
    return floatArrayOf(h, s, l)
}

/** (h 0..360, s 0..1, l 0..1) → rgb 0..1. */
fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
    if (s == 0f) return floatArrayOf(l, l, l)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    fun ch(t0: Float): Float {
        var t = t0; if (t < 0f) t += 1f; if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    val hk = ((h % 360f) + 360f) % 360f / 360f
    return floatArrayOf(ch(hk + 1f / 3f), ch(hk), ch(hk - 1f / 3f))
}

/** Sắc nấc [step] của màu gốc: tông ±[hueDeg], sáng ±[lightAmp], sáng kẹp [lMin, lMax]. Thuần, test được. */
fun shadeRgb(
    r: Float, g: Float, b: Float, step: Int,
    steps: Int = SESSION_SHADE_STEPS, hueDeg: Float = SESSION_SHADE_HUE_DEG, lightAmp: Float = SESSION_SHADE_LIGHT,
    lMin: Float = 0.35f, lMax: Float = 0.90f,
): FloatArray {
    val t = if (steps > 1) step.toFloat() / (steps - 1) * 2f - 1f else 0f
    val hsl = rgbToHsl(r, g, b)
    return hslToRgb(hsl[0] + hueDeg * t, hsl[1], (hsl[2] + lightAmp * t).coerceIn(lMin, lMax))
}

fun sessionShade(base: Color, name: String): Color {
    val rgb = shadeRgb(base.red, base.green, base.blue, sessionStep(name))
    return Color(rgb[0], rgb[1], rgb[2], base.alpha)
}

/** Màu của MỘT phiên: họ màu theo loại ([rosterColor]) lệch theo tên phiên. Dùng thay rosterColor ở mọi chỗ có tên. */
@Composable
@ReadOnlyComposable
fun AgentKind.sessionColor(name: String): Color = sessionShade(rosterColor(), name)

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
