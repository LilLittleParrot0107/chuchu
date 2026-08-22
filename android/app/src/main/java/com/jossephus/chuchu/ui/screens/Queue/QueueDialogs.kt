package com.jossephus.chuchu.ui.screens.Queue

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jossephus.chuchu.ui.components.ChuDialog
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.ChuTextField
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.MiniMarkdownText
import com.jossephus.chuchu.ui.components.TuiBadge
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskDetailDialog(
    task: QueueTask,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onAction: (QueueAction) -> Unit,
    onFetchResponse: (suspend (Int) -> String?)? = null,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val context = LocalContext.current
    var responseText by remember { mutableStateOf<String?>(null) }
    var loadingResponse by remember { mutableStateOf(false) }

    LaunchedEffect(task.id) {
        if (task.hasResp || task.isCompleted) {
            loadingResponse = true
            responseText = onFetchResponse?.invoke(task.id)
            loadingResponse = false
        }
    }
    // Gioi han dialog theo chieu cao man hinh: header + nut hanh dong LUON thay,
    // phan giua (prompt + response) tu cuon khi dai — truoc day Column de tran
    // khoi man hinh, response chi duoc 240dp nen doc rat ngop.
    val maxDialogH = (LocalConfiguration.current.screenHeightDp * 0.86f).dp

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxDialogH)
                .background(colors.surface)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
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
                    ChuText("#${task.id}", style = type.headline, color = colors.accent)
                    TuiBadge(task.stateLabel.uppercase(), task.tone.color())
                }
                KohiCompactAction(label = "✕", onClick = onDismiss)
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                ChuText("PROMPT", style = type.labelSmall, color = colors.textMuted)
                ScrollableTextPanel(
                    text = task.text,
                    maxHeight = if (responseText != null) 120 else 240,
                    monospace = false,
                )

                when {
                    loadingResponse -> ChuText("LOADING AGENT RESPONSE…", style = type.labelSmall, color = colors.accent)
                    !responseText.isNullOrBlank() -> {
                        ChuText("AGENT RESPONSE", style = type.labelSmall, color = colors.accent)
                        ScrollableTextPanel(
                            text = responseText.orEmpty(),
                            maxHeight = 380,
                            markdown = true,
                        )
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                KohiCompactAction(label = "COPY PROMPT", onClick = onCopy)
                if (!responseText.isNullOrBlank()) {
                    KohiCompactAction(
                        label = "COPY RESPONSE",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Agent response", responseText))
                        },
                    )
                }
                task.actions.forEach { action ->
                    KohiCompactAction(
                        label = action.label.uppercase(),
                        danger = action.danger,
                        onClick = { onAction(action) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrollableTextPanel(text: String, maxHeight: Int, monospace: Boolean, markdown: Boolean = false) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight.dp)
            .background(colors.surfaceVariant)
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        when {
            markdown -> MiniMarkdownText(text)
            monospace -> BasicText(
                text = buildAnnotatedString { append(text) },
                style = type.bodySmall.copy(color = colors.textPrimary, fontFamily = FontFamily.Monospace),
            )
            else -> ChuText(text, style = type.body, color = colors.textPrimary)
        }
    }
}

@Composable
internal fun QueueConfigDialog(
    currentUrl: String,
    currentToken: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(currentUrl) }
    var token by remember { mutableStateOf(currentToken) }

    ChuDialog(
        title = "QUEUE SETTINGS",
        confirmLabel = "SAVE",
        dismissLabel = "CANCEL",
        onConfirm = { onSave(url.trim(), token.trim()) },
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChuTextField(
                value = url,
                onValueChange = { url = it },
                label = "QSRV URL",
                placeholder = "https://…ts.net/q",
                singleLine = true,
            )
            ChuTextField(
                value = token,
                onValueChange = { token = it },
                label = "AUTH TOKEN (OPTIONAL)",
                placeholder = "Leave blank when using Tailscale",
                singleLine = true,
            )
        }
    }
}

@Composable
internal fun QueueLogsDialog(
    logs: List<String>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText("DAEMON LOGS", style = type.title)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KohiCompactAction(label = "↻", onClick = onRefresh)
                    KohiCompactAction(label = "✕", onClick = onDismiss)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(colors.surfaceVariant)
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    loading -> ChuText("FETCHING LOGS…", style = type.bodySmall, color = colors.textMuted)
                    error != null -> ChuText("ERROR: $error", style = type.bodySmall, color = colors.error)
                    logs.isEmpty() -> ChuText("NO LOGS AVAILABLE", style = type.bodySmall, color = colors.textMuted)
                    else -> Column {
                        logs.forEach { line ->
                            ChuText(
                                line,
                                style = type.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = if (line.contains("error", true) || line.contains("fail", true)) {
                                    colors.error
                                } else {
                                    colors.textSecondary
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
