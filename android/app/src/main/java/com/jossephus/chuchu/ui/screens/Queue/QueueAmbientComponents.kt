package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import kotlinx.coroutines.delay

/**
 * FAB nhỏ gọn đặt tại góc màn hình Terminal.
 */
@Composable
fun QueueAmbientFab(
    summary: QueueAmbientSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Paused-only cũng phải thấy FAB: khi queue tạm dừng, không có gì chạy/block
    // nhưng người dùng vẫn cần đường vào Queue để resume.
    if (summary.totalActive == 0 && !summary.isAnyWorking && !summary.isAnyBlocked &&
        !summary.hasError && !summary.isPaused
    ) {
        return
    }

    val colors = ChuColors.current
    val typography = ChuTypography.current

    // hasError xếp trước isAnyBlocked: lỗi kết nối phải thắng màu cảnh báo,
    // nếu không queue chết trong lúc bị chặn thì pill vẫn giả vờ "chỉ là chờ duyệt".
    val borderColor = when {
        summary.hasError -> colors.error
        summary.isAnyBlocked -> colors.warning
        summary.isAnyWorking -> colors.success
        summary.totalActive > 0 -> colors.accent
        else -> colors.border
    }

    val iconColor = when {
        summary.hasError -> colors.error
        summary.isAnyBlocked -> colors.warning
        summary.isAnyWorking -> colors.success
        else -> colors.textMuted
    }

    val labelText = buildString {
        append("⚡")
        if (summary.runningCount > 0 && summary.totalActive > 1) {
            append(" ${summary.runningCount}/${summary.totalActive}")
        } else if (summary.totalActive > 0) {
            append(" ${summary.totalActive}")
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChuText(
                labelText,
                style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = iconColor,
            )
            if (summary.isAnyBlocked) {
                ChuText("!", style = typography.label.copy(fontWeight = FontWeight.Bold), color = colors.warning)
            }
        }
    }
}

/**
 * Thanh capsule Live Ticker nổi ở đỉnh màn hình Terminal.
 *
 * HÀNH VI (23/8): mở ĐỦ nội dung mỗi lần TRẠNG THÁI ĐỔI; sau 5 giây không có gì
 * thay đổi thì thu về một chip nhỏ VẪN NẰM GIỮA — chỉ còn chấm màu trạng thái
 * (và số việc đang chờ nếu có). Chấm màu là kênh truyền trạng thái khi đã thu:
 * xanh lá = đang chạy, vàng = bị chặn, đỏ = queue offline, xám sáng = paused.
 * Bấm ở dạng nào cũng nhảy THẲNG màn Queue, đã chọn sẵn agent của task đang
 * nhắc tới (23/8: bỏ hẳn QuickPeek sheet). ✕ chỉ ẩn cho tới khi nội dung đổi.
 */
@Composable
fun QueueAmbientTickerPill(
    summary: QueueAmbientSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current

    // Chữ ký nội dung: mọi thay đổi đáng kể đều bật lại dạng đầy đủ, reset đồng
    // hồ 5s và ân xá cho lệnh ✕ cũ — người dùng tắt thông báo CỦA NỘI DUNG ĐÓ,
    // không phải tắt vĩnh viễn. Trước đây key là activeTaskId nên khi paused
    // (id == null) bấm ✕ xong pill vẫn sống sót qua recomposition.
    val signature = buildString {
        append(summary.activeTaskId); append('|')
        append(summary.statusText); append('|')
        append(summary.pendingCount); append('|')
        append(summary.primaryAgentName); append('|')
        append(summary.isPaused); append('|')
        // totalActive phải vào chữ ký: running giảm (task xong bớt) không đổi
        // statusText/pendingCount thì pill vẫn phải re-expand báo số mới.
        append(summary.totalActive); append('|')
        append(summary.hasError)
    }
    // Saveable: xoay màn hình/process death không được "quên" lệnh ✕ của người dùng.
    var dismissedSignature by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(true) }

    LaunchedEffect(signature) {
        expanded = true
        delay(COLLAPSE_AFTER_MS)
        expanded = false
    }

    // totalActive > 0 đứng đầu: task chỉ đang "waiting" (chưa chạy, chưa block)
    // trước đây làm pill im lặng biến mất dù hàng đợi có việc thật.
    val shouldShow = (summary.totalActive > 0 || summary.isAnyWorking || summary.isAnyBlocked ||
        summary.isPaused || summary.hasError) && dismissedSignature != signature

    AnimatedVisibility(
        visible = shouldShow,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        // Đang chạy dùng XANH LÁ chứ không dùng accent: accent tím trùng với
        // màu selection của hàng agent, lại gây nhầm "đang được chọn".
        val statusColor = when {
            summary.hasError -> colors.error
            summary.isAnyBlocked -> colors.warning
            summary.isPaused -> colors.textSecondary
            else -> colors.success
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surface.copy(alpha = 0.95f))
                    .border(BorderStroke(1.dp, statusColor.copy(alpha = 0.7f)), RoundedCornerShape(4.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .animateContentSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ChuText("●", style = typography.labelSmall, color = statusColor)

                if (expanded) {
                    val message = buildString {
                        if (!summary.primaryAgentName.isNullOrBlank()) {
                            append("@${summary.primaryAgentName}: ")
                        }
                        if (summary.activeTaskId != null) {
                            append("#${summary.activeTaskId} ")
                        }
                        append(summary.statusText)
                        if (summary.pendingCount > 0) {
                            append(" · ${summary.pendingCount} waiting")
                        }
                    }

                    ChuText(
                        text = message,
                        style = typography.labelSmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    ChuText(
                        text = "✕",
                        style = typography.labelSmall,
                        color = colors.textMuted,
                        modifier = Modifier
                            // Padding TRƯỚC clickable để vùng đập trúng ✕ to hơn —
                            // hiện giờ mép 4dp của glyph không ăn click, bấm hụt.
                            .padding(start = 4.dp)
                            .clickable { dismissedSignature = signature },
                    )
                } else if (summary.totalActive > 0) {
                    // Dạng thu gọn: chấm màu + số việc. Vẫn giữa màn hình.
                    ChuText(
                        "${summary.totalActive}",
                        style = typography.labelSmall,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}

private const val COLLAPSE_AFTER_MS = 5_000L
