package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import kotlinx.coroutines.delay

/**
 * FAB nhỏ gọn đặt tại góc màn hình Terminal.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun QueueAmbientFab(
    summary: QueueAmbientSummary,
    onClick: () -> Unit,
    onLongClick: () -> Unit = onClick,
    modifier: Modifier = Modifier,
) {
    if (summary.totalActive == 0 && !summary.isAnyWorking && !summary.isAnyBlocked && !summary.hasError) {
        return
    }

    val colors = ChuColors.current
    val typography = ChuTypography.current
    val haptics = LocalHapticFeedback.current

    val borderColor = when {
        summary.isAnyBlocked -> colors.warning
        summary.hasError -> colors.error
        summary.isAnyWorking -> colors.accent
        summary.totalActive > 0 -> colors.accent
        else -> colors.border
    }

    val iconColor = when {
        summary.isAnyBlocked -> colors.warning
        summary.hasError -> colors.error
        summary.isAnyWorking -> colors.accent
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChuText(
                labelText,
                style = typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
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
 * Bấm ở dạng nào cũng mở QuickPeek. ✕ chỉ ẩn cho tới khi nội dung thay đổi.
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
        append(summary.hasError)
    }
    var dismissedSignature by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(true) }

    LaunchedEffect(signature) {
        expanded = true
        delay(COLLAPSE_AFTER_MS)
        expanded = false
    }

    // hasError phải nằm trong điều kiện hiện: trước đây qsrv chết thì pill im
    // lặng biến mất, người đang chờ kết quả không hề hay biết queue ngắt.
    val shouldShow = (summary.isAnyWorking || summary.isAnyBlocked ||
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
                horizontalArrangement = Arrangement.spacedBy(7.dp),
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
                        style = typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
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
                            .clickable { dismissedSignature = signature }
                            .padding(start = 4.dp),
                    )
                } else if (summary.totalActive > 0) {
                    // Dạng thu gọn: chấm màu + số việc. Vẫn giữa màn hình.
                    ChuText(
                        "${summary.totalActive}",
                        style = typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}

private const val COLLAPSE_AFTER_MS = 5_000L

/**
 * Micro-Queue Quick Peek Bottom Sheet.
 *
 * Ngữ pháp hình ảnh bám theo màn Queue đầy đủ sau đợn tinh chỉnh 22/8:
 * band tiêu đề "▌ QUICK QUEUE · N ACTIVE", chấm runtime ● ○, nhãn trạng thái
 * in hoa đậm màu tone thay vì badge nền.
 *
 * Task mà pill đang nhắc tới (summary.activeTaskId) được PIN lên đầu với con
 * trỏ ">" và rail màu accent — người dùng bấm pill để xem đúng việc đó, không
 * bị lạc giữa danh sách.
 *
 * [OPEN … IN QUEUE] ở chân sheet mở màn Queue đã chọn sẵn agent của task pin
 * (pane được truyền qua điều hướng); không có task pin thì mở Queue tổng.
 */
@Composable
fun QueueQuickPeekBottomSheet(
    summary: QueueAmbientSummary,
    onAction: (QueueAction, Int) -> Unit,
    onOpenInQueue: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current

    val orderedTasks = summary.topTasks.sortedByDescending { it.id == summary.activeTaskId }
    val pinnedTask = orderedTasks.firstOrNull { it.id == summary.activeTaskId }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(colors.surface)
                    .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .clickable(enabled = false) {}
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 3.dp)
                        .background(colors.border),
                )

                // Band tiêu đề — cùng họ với band section trên màn Queue
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ChuText("▌", style = typography.headline, color = colors.accent)
                        ChuText("QUICK QUEUE", style = typography.headline.copy(fontFamily = FontFamily.Monospace))
                        ChuText(
                            "· ${summary.totalActive} ACTIVE",
                            style = typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = colors.textMuted,
                        )
                    }

                    ChuButton(
                        onClick = {
                            onDismiss()
                            onOpenInQueue(pinnedTask?.takeIf { it.target.isNotBlank() }?.target)
                        },
                        variant = ChuButtonVariant.Outlined,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        ChuText("[FULLSCREEN ↗]", style = typography.labelSmall, color = colors.accent)
                    }
                }

                if (orderedTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChuText(
                            "▌ NO ACTIVE TASKS · ALL CLEAR",
                            style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = colors.textMuted,
                        )
                    }
                } else {
                    orderedTasks.forEach { task ->
                        QuickPeekTaskItem(
                            task = task,
                            pinned = task.id == summary.activeTaskId,
                            onAction = { action -> onAction(action, task.id) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun QuickPeekTaskItem(
    task: QueueTask,
    pinned: Boolean,
    onAction: (QueueAction) -> Unit,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current
    val taskColor = task.tone.color()

    ChuCard(
        background = colors.surfaceVariant,
        border = if (pinned) colors.accent else colors.border,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            if (pinned) {
                // Rail accent đánh dấu task mà pill đang nhắc tới
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(colors.accent),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (pinned) {
                            ChuText(">", style = typography.label.copy(fontWeight = FontWeight.Bold), color = colors.accent)
                        }
                        ChuText(task.glyph, style = typography.label, color = taskColor)
                        ChuText("#${task.id}", style = typography.label.copy(fontWeight = FontWeight.Bold), color = colors.accent)
                        if (task.target.isNotBlank()) {
                            ChuText("@${task.target}", style = typography.labelSmall, color = colors.textSecondary)
                        }
                    }
                    ChuText(
                        text = task.stateLabel.uppercase(),
                        style = typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                        color = taskColor,
                    )
                }

                ChuText(
                    text = task.text.replace('\n', ' '),
                    style = typography.bodySmall,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Micro-action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        task.actions.forEach { action ->
                            ChuButton(
                                onClick = { onAction(action) },
                                variant = ChuButtonVariant.Ghost,
                                bracketed = true,
                                borderColor = if (action.danger) colors.error else colors.border,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                ChuText(
                                    action.label,
                                    style = typography.labelSmall,
                                    color = if (action.danger) colors.error else colors.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
