package com.jossephus.chuchu.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

/**
 * Kohi Brand Action Menu (Portal Popup Dialog).
 *
 * Xuất hiện khi người dùng chạm vào "$ kohi" tại ServerListScreen hoặc TerminalScreen.
 * Cung cấp 2 lựa chọn: Trình duyệt File (Dufs) và Dashboard DeFi (dbtop).
 */
@Composable
fun KohiActionMenu(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onOpenFileBrowser: () -> Unit,
    onOpenDashboard: () -> Unit,
    modifier: Modifier = Modifier,
    defiSummary: String = "$83.3k · 29.3% APR",
) {
    if (!isOpen) return

    val colors = ChuColors.current
    val typography = ChuTypography.current
    val shape = RoundedCornerShape(8.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surfaceVariant)
                .border(BorderStroke(1.dp, colors.border), shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: $ kohi portal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ChuText("$ ", style = typography.title, color = colors.textMuted)
                    ChuText("kohi portal", style = typography.title, color = colors.accent)
                }
                TuiBadge(text = "SERVICES", color = colors.accentSecondary)
            }

            // Divider Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border),
            )

            // Option 1: Trình duyệt File (Dufs)
            KohiMenuOptionCard(
                icon = "📁",
                badge = "dufs",
                title = "Trình duyệt File",
                description = "Quản lý file, xem log, tải APK, media stream",
                onClick = {
                    onDismiss()
                    onOpenFileBrowser()
                },
            )

            // Option 2: Dashboard DeFi (dbtop)
            KohiMenuOptionCard(
                icon = "📊",
                badge = "dbtop",
                title = "Dashboard DeFi (dbtop)",
                description = "Theo dõi danh mục, APR yield, options & lending",
                highlightMetric = defiSummary,
                onClick = {
                    onDismiss()
                    onOpenDashboard()
                },
            )

            // Divider Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border),
            )

            // Nút [ ✕ Đóng ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuButton(
                    onClick = onDismiss,
                    variant = ChuButtonVariant.Ghost,
                    bracketed = true,
                    borderColor = colors.textMuted,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    ChuText("✕ Đóng", style = typography.label, color = colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun KohiMenuOptionCard(
    icon: String,
    badge: String,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlightMetric: String? = null,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.border), shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChuText(icon, style = typography.title)
                    ChuText(title, style = typography.title, color = colors.textPrimary)
                }
                TuiBadge(text = badge, color = colors.accent)
            }

            ChuText(
                text = description,
                style = typography.bodySmall,
                color = colors.textSecondary,
            )

            if (highlightMetric != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.surfaceVariant)
                        .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChuText("●", style = typography.labelSmall, color = colors.accent)
                    ChuText(
                        text = highlightMetric,
                        style = typography.labelSmall,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}
