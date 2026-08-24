package com.jossephus.chuchu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

@Composable
fun ChuDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Cancel",
    // QueueDialogs cần khoá nút confirm khi validation fail thay vì tự dựng dialog.
    confirmEnabled: Boolean = true,
    // Slot thay thế title dạng string — cho phép title giàu (badge, counter)
    // mà không phải fork cả dialog.
    titleContent: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current
    val shape = RectangleShape

    Dialog(
        onDismissRequest = onDismiss,
        properties = properties,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .imePadding()
                // surface (không phải surfaceVariant) để thống nhất skin với 2
                // dialog tự viết; panel bên trong vẫn surfaceVariant nên vẫn contrast.
                .background(colors.surface, shape)
                .border(BorderStroke(1.dp, colors.border), shape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (titleContent != null) {
                titleContent()
            } else {
                ChuText(text = title, style = typography.title)
            }
            content()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                ChuButton(
                    onClick = onDismiss,
                    variant = ChuButtonVariant.Ghost,
                    bracketed = true,
                    borderColor = colors.textMuted,
                ) {
                    ChuText(dismissLabel, style = typography.label, color = colors.textMuted)
                }
                ChuButton(
                    onClick = onConfirm,
                    // Forward enabled để caller (QueueDialogs) gate confirm theo validation.
                    enabled = confirmEnabled,
                    bracketed = true,
                ) {
                    ChuText(confirmLabel, style = typography.label, color = colors.onAccent)
                }
            }
        }
    }
}
