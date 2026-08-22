package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import com.jossephus.chuchu.ui.components.TuiBadge
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

/**
 * FAB nhỏ gọn đặt tại góc màn hình Terminal.
 */
@Composable
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

    val infiniteTransition = rememberInfiniteTransition(label = "queue_fab_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (summary.isAnyBlocked) 600 else 1200,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_alpha",
    )

    val borderColor = when {
        summary.isAnyBlocked -> colors.warning.copy(alpha = pulseAlpha)
        summary.hasError -> colors.error
        summary.isAnyWorking -> colors.accent.copy(alpha = pulseAlpha)
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
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
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
 */
@Composable
fun QueueAmbientTickerPill(
    summary: QueueAmbientSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current
    var dismissedManually by remember(summary.activeTaskId) { mutableStateOf(false) }

    val shouldShow = (summary.isAnyWorking || summary.isAnyBlocked || summary.isPaused) && !dismissedManually

    AnimatedVisibility(
        visible = shouldShow,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        val pillColor = when {
            summary.isAnyBlocked -> colors.warning
            summary.isPaused -> colors.textMuted
            else -> colors.accent
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
                    .border(BorderStroke(1.dp, pillColor.copy(alpha = 0.7f)), RoundedCornerShape(4.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val glyph = when {
                    summary.isAnyBlocked -> "▲"
                    summary.isPaused -> "⏸"
                    else -> "●"
                }
                ChuText(glyph, style = typography.labelSmall, color = pillColor)

                val message = buildString {
                    if (!summary.primaryAgentName.isNullOrBlank()) {
                        append("@${summary.primaryAgentName}: ")
                    }
                    if (summary.activeTaskId != null) {
                        append("#${summary.activeTaskId} ")
                    }
                    append(summary.statusText)
                    if (summary.pendingCount > 0) {
                        append(" · ${summary.pendingCount} chờ")
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
                        .clickable { dismissedManually = true }
                        .padding(start = 4.dp),
                )
            }
        }
    }
}

/**
 * Micro-Queue Quick Peek Bottom Sheet.
 */
@Composable
fun QueueQuickPeekBottomSheet(
    summary: QueueAmbientSummary,
    onAction: (QueueAction, Int) -> Unit,
    onOpenFullQueue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current

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

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ChuText("⚡", style = typography.headline, color = colors.accent)
                        ChuText("quick queue", style = typography.headline)
                        TuiBadge("${summary.totalActive}", colors.accent)
                    }

                    ChuButton(
                        onClick = {
                            onDismiss()
                            onOpenFullQueue()
                        },
                        variant = ChuButtonVariant.Outlined,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        ChuText("fullscreen ↗", style = type.labelSmall, color = colors.accent)
                    }
                }

                // Top tasks
                if (summary.topTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChuText("queue is empty", style = typography.bodySmall, color = colors.textMuted)
                    }
                } else {
                    summary.topTasks.forEach { task ->
                        QuickPeekTaskItem(
                            task = task,
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
    onAction: (QueueAction) -> Unit,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current
    val isRunning = task.state == "sent" || task.state == "sending" || task.state == "working"
    val taskColor = task.tone.color()

    ChuCard(
        background = colors.surfaceVariant,
        border = if (isRunning) colors.accent else colors.border,
        modifier = Modifier.fillMaxWidth(),
    ) {
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
                    ChuText(task.glyph, style = typography.label, color = taskColor)
                    ChuText("#${task.id}", style = typography.label.copy(fontWeight = FontWeight.Bold), color = colors.accent)
                    if (task.target.isNotBlank()) {
                        ChuText("@${task.target}", style = typography.labelSmall, color = colors.textSecondary)
                    }
                }
                TuiBadge(task.stateLabel, taskColor)
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
