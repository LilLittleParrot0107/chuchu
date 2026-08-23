package com.jossephus.chuchu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

/**
 * Kohi service portal.
 *
 * Xuất hiện khi người dùng chạm vào "$ kohi" tại ServerListScreen hoặc TerminalScreen.
 * Mỗi service dùng cùng ngôn ngữ TUI với Queue và Dbtop: phẳng, monospace,
 * ít màu trang trí và ưu tiên trạng thái có ích hơn icon minh hoạ.
 */
@Composable
fun KohiActionMenu(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onOpenFileBrowser: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenQueue: (String?) -> Unit,
    modifier: Modifier = Modifier,
    queueStatus: String = "ready",
    queueStatusColor: Color? = null,
) {
    if (!isOpen) return

    val colors = ChuColors.current
    val typography = ChuTypography.current

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
                .background(colors.background, RectangleShape)
                .border(BorderStroke(1.dp, colors.accent.copy(alpha = 0.7f)), RectangleShape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ChuText("$ ", style = typography.title, color = colors.accent)
                    ChuText("kohi", style = typography.title, color = colors.textPrimary)
                    ChuText(" / portal", style = typography.label, color = colors.textMuted)
                }
                TuiBadge(text = "03 SERVICES", color = colors.accentSecondary)
            }

            PortalDivider()

            KohiServiceRow(
                index = "01",
                glyph = "/",
                badge = "dufs",
                title = "file portal",
                description = "browse files · stream media · download apk",
                tone = colors.accentSecondary,
                onClick = {
                    onDismiss()
                    onOpenFileBrowser()
                },
            )

            KohiServiceRow(
                index = "02",
                glyph = "#",
                badge = "dbtop",
                title = "defi dashboard",
                description = "positions · yield · risk · charts",
                tone = colors.success,
                onClick = {
                    onDismiss()
                    onOpenDashboard()
                },
            )

            KohiServiceRow(
                index = "03",
                glyph = ">",
                badge = "qsrv",
                title = "agent queue",
                description = "tasks · agents · logs · responses",
                status = queueStatus,
                tone = queueStatusColor ?: colors.accent,
                onClick = {
                    onDismiss()
                    onOpenQueue(null)
                },
            )

            PortalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText("tap a service to open", style = typography.labelSmall, color = colors.textMuted)
                ChuButton(
                    onClick = onDismiss,
                    variant = ChuButtonVariant.Ghost,
                    bracketed = true,
                    borderColor = colors.textMuted,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    ChuText("esc · close", style = typography.label, color = colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun PortalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ChuColors.current.border),
    )
}

@Composable
private fun KohiServiceRow(
    index: String,
    glyph: String,
    badge: String,
    title: String,
    description: String,
    tone: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    status: String? = null,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current

    KohiSelectableRow(
        selected = false,
        tone = tone,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
    ) {
        ChuText(index, style = typography.labelSmall, color = colors.textMuted)
        Spacer(Modifier.width(8.dp))
        ChuText(glyph, style = typography.title, color = tone)
        Spacer(Modifier.width(8.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
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
                    ChuText(title, style = typography.title, color = colors.textPrimary)
                    TuiBadge(text = badge, color = tone)
                }
                status?.let {
                    ChuText(it, style = typography.labelSmall, color = tone)
                }
            }

            ChuText(
                text = description,
                style = typography.bodySmall,
                color = colors.textSecondary,
            )
        }

        ChuText("›", style = typography.title, color = colors.textMuted)
    }
}
