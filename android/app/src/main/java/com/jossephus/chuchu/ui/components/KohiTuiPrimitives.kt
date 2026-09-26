package com.jossephus.chuchu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.theme.CHU_HAIRLINE_ALPHA
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

/**
 * Shared native interpretation of qq/dbtop's visual grammar.
 *
 * These components deliberately preserve touch-sized targets while borrowing
 * the TUI hierarchy: one command band, full-width notices, scoped section
 * bands, and a single accent rail for the active row.
 */
@Composable
fun KohiCommandBand(
    title: String,
    status: String? = null,
    statusColor: Color = ChuColors.current.textMuted,
    // Màu tên band — mặc định textPrimary; màn CHAT Queue truyền màu theo LOẠI agent
    // (user chốt 16/9: tên session đổi màu theo loại, đồng bộ với roster).
    titleColor: Color = ChuColors.current.textPrimary,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    // null = surface (chuẩn). Queue/Dbtop truyền background để cả khối trên
    // cùng (status bar + band) tan vào nền theme như rail.
    containerColor: Color? = null,
    // Cỡ tiêu đề: mặc định theo type.title (16sp). Queue/Dashboard truyền cỡ to
    // hơn (user chốt 17/9: chữ QUEUE/DASHBOARD trên thanh trên to lên).
    titleSize: TextUnit? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor ?: colors.surface)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (onBack != null) {
                // Nut back to ngang nut back cua man Files (user chot 27/8):
                // chip 24dp cu vua lep vua duoi chuan touch-target.
                ChuButton(
                    onClick = onBack,
                    variant = ChuButtonVariant.Ghost,
                    bracketed = true,
                    borderColor = colors.border,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    minHeight = 34.dp,
                ) {
                    ChuText(
                        "←",
                        style = type.label.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                    )
                }
            }
            ChuText(
                title.uppercase(),
                style = type.title.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = titleSize ?: type.title.fontSize,
                    lineHeight = titleSize?.times(1.35f) ?: type.title.lineHeight,
                ),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!status.isNullOrBlank()) {
                ChuText("·", style = type.labelSmall, color = colors.textMuted)
                ChuText(
                    status,
                    style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = actions,
        )
    }
}

@Composable
fun KohiCompactAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    // Passthrough cho caller cần chip cao hơn (touch target lớn) — mặc định
    // giữ 24.dp để 5 chip trong command band không đè title.
    minHeight: Dp = 24.dp,
    // Passthrough cho accessibility: chip chỉ có text viết tắt (SYNC, CFG)
    // nên TalkBack cần mô tả đầy đủ do caller cung cấp.
    contentDescription: String? = null,
) {
    val colors = ChuColors.current
    ChuButton(
        onClick = onClick,
        enabled = enabled,
        variant = ChuButtonVariant.Ghost,
        // Bỏ bracket: 5 chip trong command band (PAUSE LOGS SYNC CFG + CLEAR)
        // mỗi cái mang "[ ]" tốn ~20dp đã đè title "QUEUE" thành "QU…".
        // qq dùng từ trần cho micro-action — làm theo để vừa.
        bracketed = false,
        borderColor = if (danger) colors.error else colors.border,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        minHeight = minHeight,
        contentDescription = contentDescription,
        modifier = modifier,
    ) {
        ChuText(
            label,
            style = ChuTypography.current.labelSmall,
            // Enabled phai SANG hon disabled — truoc day ca hai deu xam nen nut
            // bat nhin nhu bi vo hieu hoa.
            color = when {
                !enabled -> colors.textMuted
                danger -> colors.error
                else -> colors.textPrimary
            },
        )
    }
}

@Composable
fun KohiNoticeBand(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    urgent: Boolean = false,
) {
    val colors = ChuColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (urgent) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        ChuText(
            text,
            style = ChuTypography.current.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (urgent) colors.background else color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val FeedbackShape = RoundedCornerShape(4.dp)

/**
 * clickable KHÔNG ripple — ngôn ngữ TUI của Queue/Dashboard (chốt 18/9). Một chỗ định nghĩa
 * thay vì lặp `indication = null + interactionSource = remember { … }` ở từng nơi bấm.
 */
@Composable
fun Modifier.noRippleClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(
        enabled = enabled,
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick,
    )

/**
 * Transient feedback sau user action (gửi tin nhắn, copy prompt, v.v.).
 * Đồng bộ với ngôn ngữ hình khối Queue: floating card lùi lề 14dp, bo góc 4dp,
 * nền surfaceVariant đục kết hợp viền mờ 0.25f, glyph trạng thái căn thẳng,
 * nút đóng nhẹ nhàng không viền thô (user chốt 18/9).
 */
@Composable
fun KohiFeedbackBand(
    text: String,
    color: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val glyph = when (color) {
        colors.success -> "✓"
        colors.error -> "▲"
        else -> "●"
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant.copy(alpha = 0.96f), FeedbackShape)
                .border(1.dp, color.copy(alpha = 0.25f), FeedbackShape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChuText(
                glyph,
                style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
            Spacer(Modifier.width(8.dp))
            ChuText(
                text = text,
                style = type.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            ChuText(
                "×",
                style = type.labelSmall,
                color = colors.textMuted,
                // Vùng chạm ≥ 32dp cho nút đóng (chữ × chỉ ~10dp).
                modifier = Modifier
                    .noRippleClickable(onClick = onDismiss)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun KohiSectionBand(
    label: String,
    meta: String? = null,
    modifier: Modifier = Modifier,
    accent: Color = ChuColors.current.accent,
    // null = surface (chuan). Dbtop truyen background: bo cac dai xam cat
    // ngang man (user che 26/8), band chi con glyph ▌ + chu tren nen theme.
    containerColor: Color? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor ?: colors.surface)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChuText("▌", style = type.labelSmall, color = accent)
            ChuText(
                label.uppercase(),
                style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!meta.isNullOrBlank()) {
                ChuText(
                    "· $meta",
                    style = type.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = trailing,
        )
    }
}

/**
 * Dải sub-tab trong một tab dashboard (mock chốt 27/9, proto-build.html): nhãn + "· n",
 * tab đang chọn màu accent + gạch chân 2dp. Đổi tab bằng CHẠM — không vuốt ngang vì
 * pane nằm trong HorizontalPager của màn, vuốt sẽ bị pager nuốt mất.
 */
@Composable
fun KohiSubTabs(
    tabs: List<Pair<String, Int>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, colors.border.copy(alpha = CHU_HAIRLINE_ALPHA))),
    ) {
        tabs.forEachIndexed { index, (label, count) ->
            val active = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.padding(top = 5.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    ChuText(
                        label.uppercase(),
                        style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (active) colors.accent else colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ChuText("· $count", style = type.labelSmall, color = colors.textMuted, maxLines = 1)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.64f)
                        .height(2.dp)
                        .background(if (active) colors.accent else Color.Transparent),
                )
            }
        }
    }
}

@Composable
fun KohiSelectableRow(
    selected: Boolean,
    tone: Color,
    // null = hang tinh, khong bam duoc (hang Wallet — user chot 26/9): van dung
    // y nguyen khung rail/border/dem de kich thuoc & thang cot giong het cac
    // hang vi the, chi bo clickable/ripple.
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    // 0..1: nhung "muc do" (tien trinh option / health lending) vao NEN row
    // kieu btop — alpha thap nen chi la am hieu lien mat; so cu the phai nam
    // o dong chu (user chot 27/8). null = khong ve gi.
    fillFraction: Float? = null,
    fillColor: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = ChuColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (selected) colors.surface else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) tone.copy(alpha = 0.6f) else colors.border.copy(alpha = CHU_HAIRLINE_ALPHA),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        // Ve DUOI content va TAT khi selected: nen surface + rail da mang
        // trang thai chon, chong them fill la lau mau.
        if (!selected && fillFraction != null && fillFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fillFraction.coerceIn(0f, 1f))
                    .background((fillColor ?: tone).copy(alpha = 0.14f)),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(if (selected) tone else Color.Transparent),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.border.copy(alpha = CHU_HAIRLINE_ALPHA)),
    )
}
