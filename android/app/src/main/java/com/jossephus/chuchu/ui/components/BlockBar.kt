package com.jossephus.chuchu.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

/**
 * Bar bằng ký tự khối, KÉO DÀI HẾT chỗ trống.
 *
 * Bản đầu để cố định 12 ô nên trên màn 6.7" nó teo lại giữa một khoảng trống to
 * (user chỉ ra 3/9, kèm ảnh). Giờ đo bề rộng thật của một ký tự `█` bằng chính
 * TextStyle sẽ vẽ, rồi lấp đầy chỗ còn lại — bar dài như thanh Dashboard đời
 * đầu, mà vẫn thẳng cột vì hai cột số bên phải có bề rộng cố định.
 */
@Composable
private fun barStyle(fontSize: Int): TextStyle = ChuTypography.current.labelSmall.copy(
    fontFamily = FontFamily.Monospace,
    fontSize = fontSize.sp,
    letterSpacing = (-0.2).sp,
)

@Composable
private fun cellsFor(width: androidx.compose.ui.unit.Dp, style: TextStyle): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(width, style) {
        // Đo MỘT DẢI rồi chia, không đo một ký tự: `letterSpacing` âm chỉ có tác
        // dụng GIỮA các ký tự, nên đo lẻ ra bề rộng dư → chia được ít ô → thanh
        // ngắn hơn thanh Dashboard cũ (user chỉ ra 3/9, lần thứ hai).
        val probe = 32
        val runPx = measurer.measure("█".repeat(probe), style).size.width.toFloat()
        val charPx = (runPx / probe).coerceAtLeast(0.5f)
        val availPx = with(density) { width.toPx() }
        (availPx / charPx).toInt().coerceIn(4, 160)
    }
}

@Composable
private fun blocks(fraction: Double?, cells: Int): String {
    val filled = ((fraction ?: 0.0).coerceIn(0.0, 1.0) * cells).toInt()
    return "█".repeat(filled) + "░".repeat((cells - filled).coerceAtLeast(0))
}

/**
 * Một hàng: nhãn · thanh · số · đuôi.
 *
 * Hai cột phải cố định bề rộng nên mọi thanh bắt đầu và kết thúc cùng toạ độ.
 * Đuôi quá dài thì CẮT, không bao giờ được phép đẩy thanh — nhưng cột đuôi phải
 * đủ rộng cho "95.1/467.7G", chỗ bản trước cắt cụt mất chữ G.
 */
@Composable
fun BlockBar(
    label: String,
    fraction: Double?,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    tail: String = "",
    alpha: Float = 1f,
    labelWidth: Int = 38,
    valueWidth: Int = 46,
    tailWidth: Int = 92,
    fontSize: Int = 9,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val bs = barStyle(fontSize)
    val textStyle = type.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
    )

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuText(label, style = textStyle, color = colors.textSecondary.copy(alpha = alpha),
            maxLines = 1, modifier = Modifier.width(labelWidth.dp))
        BoxWithConstraints(Modifier.weight(1f)) {
            ChuText(
                blocks(fraction, cellsFor(maxWidth, bs)),
                style = bs,
                color = (if (fraction == null) colors.border else color).copy(alpha = alpha),
                maxLines = 1,
                softWrap = false,
            )
        }
        ChuText(
            value,
            style = textStyle.copy(textAlign = TextAlign.End),
            color = colors.textPrimary.copy(alpha = alpha),
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp).width(valueWidth.dp),
        )
        if (tail.isNotEmpty()) {
            ChuText(tail, style = textStyle.copy(textAlign = TextAlign.End),
                color = colors.textMuted.copy(alpha = alpha), maxLines = 1,
                overflow = TextOverflow.Clip, softWrap = false,
                modifier = Modifier.padding(start = 4.dp).width(tailWidth.dp))
        }
    }
}

/**
 * Chỉ phần THANH — cho chỗ đã có hàng nhãn riêng (ba thanh Dashboard).
 * Cũng kéo dài hết bề ngang như thanh Dashboard vốn có.
 */
@Composable
fun BlockBarLine(
    fraction: Double?,
    color: Color,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    fontSize: Int = 9,
) {
    val colors = ChuColors.current
    val bs = barStyle(fontSize)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        ChuText(
            blocks(fraction, cellsFor(maxWidth, bs)),
            style = bs,
            color = (if (fraction == null) colors.border else color).copy(alpha = alpha),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Thanh nhiều đoạn — thay CompositionBar vẽ bằng Box, cũng dài hết bề ngang.
 * Đoạn nào có giá trị mà bị làm tròn về 0 ô thì vẫn được 1 ô: mất hẳn một
 * thành phần khỏi biểu đồ là nói dối về danh mục.
 */
@Composable
fun BlockSegmentBar(
    segments: List<Pair<Color, Double>>,
    modifier: Modifier = Modifier,
    fontSize: Int = 9,
) {
    val bs = barStyle(fontSize)
    val visible = segments.filter { it.second > 0.0 }
    val total = visible.sumOf { it.second }
    if (total <= 0.0) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cells = cellsFor(maxWidth, bs)
        val counts = visible.map { (it.second / total * cells).toInt().coerceAtLeast(1) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            visible.forEachIndexed { i, (color, _) ->
                ChuText("█".repeat(counts[i]), style = bs, color = color,
                    maxLines = 1, softWrap = false)
            }
        }
    }
}
