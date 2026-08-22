package com.jossephus.chuchu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (onBack != null) {
                ChuButton(
                    onClick = onBack,
                    variant = ChuButtonVariant.Ghost,
                    bracketed = true,
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    ChuText("←", style = type.label, color = colors.textSecondary)
                }
            }
            ChuText(
                title.uppercase(),
                style = type.title.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
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
) {
    val colors = ChuColors.current
    ChuButton(
        onClick = onClick,
        enabled = enabled,
        variant = ChuButtonVariant.Ghost,
        bracketed = true,
        borderColor = if (danger) colors.error else colors.border,
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
        modifier = modifier.defaultMinSize(minHeight = 32.dp),
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
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

/**
 * Transient, in-flow feedback used after a user action. Unlike the old
 * floating cards this takes its own layout space, so it never covers an input
 * or a task row.
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(color.copy(alpha = 0.14f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .background(color),
        )
        ChuText(
            text = text,
            style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 9.dp, vertical = 6.dp),
        )
        ChuButton(
            onClick = onDismiss,
            variant = ChuButtonVariant.Ghost,
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
        ) {
            ChuText("×", style = type.labelSmall, color = colors.textMuted)
        }
    }
}

@Composable
fun KohiSectionBand(
    label: String,
    meta: String? = null,
    modifier: Modifier = Modifier,
    accent: Color = ChuColors.current.accent,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
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
            )
            if (!meta.isNullOrBlank()) {
                ChuText("· $meta", style = type.labelSmall, color = colors.textMuted)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = trailing,
        )
    }
}

@Composable
fun KohiSelectableRow(
    selected: Boolean,
    tone: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val colors = ChuColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (selected) colors.surface else colors.surfaceVariant)
            // Selection phai thay bang BACKGROUND + BORDER, khong dung dot/icon —
            // dot chi danh cho runtime status de khong luong tuong selected.
            .border(
                width = 1.dp,
                color = if (selected) tone.copy(alpha = 0.6f) else Color.Transparent,
            )
            .clickable(onClick = onClick),
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            // Divider phai im lang hon selection border — truoc day alpha 0.65
            // canh tranh voi duong vien cua row dang chon.
            .background(colors.border.copy(alpha = 0.32f)),
    )
}
