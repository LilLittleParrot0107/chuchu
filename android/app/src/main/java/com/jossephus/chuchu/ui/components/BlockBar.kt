package com.jossephus.chuchu.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

/** Bar dài đúng bấy nhiêu ký tự — cố định để mọi dòng thẳng cột với nhau. */
const val BLOCK_BAR_CELLS = 12

/**
 * Thanh tiến trình bằng ký tự khối, kiểu terminal.
 *
 * Vì sao ký tự chứ không phải Box: cả app đọc như một cái terminal, và quan
 * trọng hơn — bar dài CỐ ĐỊNH 12 ô nên mọi dòng thẳng cột tuyệt đối. Bản cũ vẽ
 * bằng Box với `weight(1f)` thì đuôi dài ngắn khác nhau kéo bar co giãn theo,
 * mỗi dòng một chiều dài (user chỉ ra 3/9).
 *
 * Nhãn/giá trị/đuôi đều có bề rộng cố định; đuôi dài quá thì CẮT chứ không bao
 * giờ được phép đẩy bar.
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
    labelWidth: Int = 34,
    valueWidth: Int = 44,
    tailWidth: Int = 72,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    // Bar nhỏ hơn chữ một chút cho mảnh (user chốt 3/9) — nó nằm ở cột riêng
    // nên cỡ chữ khác không làm lệch các cột còn lại.
    val barStyle = type.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        letterSpacing = (-0.3).sp,
    )
    val textStyle = type.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
    )
    val filled = ((fraction ?: 0.0).coerceIn(0.0, 1.0) * BLOCK_BAR_CELLS).toInt()

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuText(label, style = textStyle, color = colors.textSecondary.copy(alpha = alpha),
            maxLines = 1, modifier = Modifier.width(labelWidth.dp))
        ChuText(
            "█".repeat(filled) + "░".repeat(BLOCK_BAR_CELLS - filled),
            style = barStyle,
            color = (if (fraction == null) colors.border else color).copy(alpha = alpha),
            maxLines = 1,
        )
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
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(start = 4.dp).width(tailWidth.dp))
        }
    }
}

/**
 * Chỉ phần THANH bằng ký tự khối, không nhãn không số — cho chỗ đã có sẵn
 * hàng nhãn riêng (Dashboard: OptionProgressBar, LendingHealthBar).
 *
 * Cỡ chữ nhỏ hơn thân bài để thanh mảnh (user chốt 3/9: mảnh hơn preview đầu).
 */
@Composable
fun BlockBarLine(
    fraction: Double?,
    color: Color,
    modifier: Modifier = Modifier,
    cells: Int = BLOCK_BAR_CELLS,
    alpha: Float = 1f,
    fontSize: Int = 9,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val filled = ((fraction ?: 0.0).coerceIn(0.0, 1.0) * cells).toInt()
    ChuText(
        "█".repeat(filled) + "░".repeat((cells - filled).coerceAtLeast(0)),
        style = type.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            letterSpacing = (-0.3).sp,
        ),
        color = (if (fraction == null) colors.border else color).copy(alpha = alpha),
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * Thanh nhiều đoạn bằng ký tự khối — thay CompositionBar vẽ bằng Box.
 * Mỗi đoạn chiếm số ô theo tỉ lệ, đoạn nào có giá trị mà bị làm tròn về 0 thì
 * vẫn được 1 ô: mất hẳn một thành phần khỏi biểu đồ là nói dối.
 */
@Composable
fun BlockSegmentBar(
    segments: List<Pair<Color, Double>>,
    modifier: Modifier = Modifier,
    cells: Int = BLOCK_BAR_CELLS,
    fontSize: Int = 9,
) {
    val type = ChuTypography.current
    val visible = segments.filter { it.second > 0.0 }
    val total = visible.sumOf { it.second }
    if (total <= 0.0) return
    val counts = visible.map { (it.second / total * cells).toInt().coerceAtLeast(1) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        visible.forEachIndexed { i, (color, _) ->
            ChuText(
                "█".repeat(counts[i]),
                style = type.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    letterSpacing = (-0.3).sp,
                ),
                color = color,
                maxLines = 1,
            )
        }
    }
}
