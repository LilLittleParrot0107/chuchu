package com.jossephus.chuchu.ui.screens.Queue

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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

    // retry đếm thêm lần bấm ↻ — trước đây fetch hỏng thì dialog chết luôn
    // không có đường thử lại.
    var retry by remember { mutableStateOf(0) }
    var copiedPrompt by remember { mutableStateOf(false) }
    var copiedResponse by remember { mutableStateOf(false) }
    LaunchedEffect(copiedPrompt) {
        if (copiedPrompt) {
            delay(1500)
            copiedPrompt = false
        }
    }
    LaunchedEffect(copiedResponse) {
        if (copiedResponse) {
            delay(1500)
            copiedResponse = false
        }
    }
    LaunchedEffect(task.id, task.hasResp, retry) {
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
    // Inset navbar do o composition man hinh, truyen dp cung vao dialog —
    // doc inset trong cua so dialog tra 0 tren may that (bug lem 26-27/8).
    val sheetDensity = androidx.compose.ui.platform.LocalDensity.current
    val navBottom = with(sheetDensity) {
        androidx.compose.foundation.layout.WindowInsets.safeDrawing.getBottom(sheetDensity).toDp()
    }

    // BOTTOM SHEET nhu detail cua dashboard (user chot 27/8): scrim mo phan
    // tren, tap vao vung mo = dong, khung nam sat day man.
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Scrim TU VE: dim cua window dialog co the khong an khi
                // decorFitsSystemWindows=false (user bat 27/8 tren queue) —
                // ModalBottomSheet cua Google cung tu ve scrim vi ly do nay.
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) {}
                .heightIn(max = maxDialogH)
                .background(colors.surface)
                .border(1.dp, colors.border)
                .padding(bottom = navBottom)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChuText("PROMPT", style = type.labelSmall, color = colors.textMuted)
                // Prompt thường ngắn — bỏ panel cuộn riêng, outer scroll gánh:
                // còn 1 mức nested scroll thay vì 2.
                ChuText(
                    text = task.text,
                    style = type.body,
                    color = colors.textPrimary,
                )

                when {
                    loadingResponse -> ChuText("LOADING AGENT RESPONSE…", style = type.labelSmall, color = colors.accent)
                    task.isCompleted && responseText.isNullOrBlank() && !loadingResponse ->
                        KohiCompactAction(label = "↻ RETRY", onClick = { retry++ })
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KohiCompactAction(
                    label = if (copiedPrompt) "COPIED ✓" else "COPY PROMPT",
                    onClick = {
                        onCopy()
                        copiedPrompt = true
                    },
                )
                if (!responseText.isNullOrBlank()) {
                    KohiCompactAction(
                        label = if (copiedResponse) "COPIED ✓" else "COPY RESPONSE",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Agent response", responseText))
                            copiedResponse = true
                        },
                    )
                }
                // DELETE một phát ăn ngay từng là cơn ác mộng — lần đầu chỉ
                // khoá súng ("CONFIRM?"), lần hai mới xoá thật.
                var armedDelete by remember(task.id) { mutableStateOf(false) }
                task.actions.forEach { action ->
                    val isDelete = action.danger
                    KohiCompactAction(
                        label = if (isDelete && armedDelete) "CONFIRM?" else action.label.uppercase(),
                        danger = action.danger,
                        onClick = {
                            if (isDelete && !armedDelete) armedDelete = true else onAction(action)
                        },
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun ScrollableTextPanel(text: String, maxHeight: Int, markdown: Boolean = false) {
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
        if (markdown) {
            MiniMarkdownText(text)
        } else {
            ChuText(text, style = type.body, color = colors.textPrimary)
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
    val colors = ChuColors.current
    val type = ChuTypography.current
    var url by remember { mutableStateOf(currentUrl) }
    var token by remember { mutableStateOf(currentToken) }
    var urlError by remember { mutableStateOf(false) }

    ChuDialog(
        title = "QUEUE SETTINGS",
        confirmLabel = "SAVE",
        dismissLabel = "CANCEL",
        confirmEnabled = url.isNotBlank(),
        // Band ▌ thống nhất ngữ pháp header với LOGS/detail (trước đây title
        // to riêng một kiểu).
        titleContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ChuText("▌", style = type.labelSmall, color = colors.accent)
                ChuText(
                    "QUEUE SETTINGS",
                    style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.textSecondary,
                )
            }
        },
        onConfirm = { onSave(url.trim(), token.trim()) },
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChuTextField(
                value = url,
                onValueChange = {
                    url = it
                    urlError = false
                },
                label = "QSRV URL",
                placeholder = "https://…ts.net/q",
                singleLine = true,
                isError = urlError,
                supportingText = if (urlError) "URL is required" else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Uri),
            )
            ChuTextField(
                value = token,
                onValueChange = { token = it },
                label = "AUTH TOKEN (OPTIONAL)",
                placeholder = "Leave blank when using Tailscale",
                singleLine = true,
                autoFocus = false,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(url.trim(), token.trim()) }),
            )
        }
    }
}

private val LOG_ERROR_RE =
    Regex("\\b(error|fatal|panic|fail(ed|ure)?)s?\\b", RegexOption.IGNORE_CASE)

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
                .border(1.dp, colors.border)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header dung dung ngu phap band cua app: ▌ LABEL · META + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChuText("▌", style = type.labelSmall, color = colors.accent)
                    ChuText(
                        "DAEMON LOGS",
                        style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.textSecondary,
                    )
                    if (logs.isNotEmpty()) {
                        ChuText(
                            "· LAST ${logs.size} LINES",
                            style = type.labelSmall,
                            color = colors.textMuted,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KohiCompactAction(label = "↻", onClick = onRefresh)
                    KohiCompactAction(label = "✕", onClick = onDismiss)
                }
            }
            // Giữ log cũ khi refresh (trước đây loading thay toàn bộ nội dung
            // làm scroll sụp về đầu); log mới nhất nằm cuối -> auto cuộn xuống.
            val scrollState = rememberScrollState()
            LaunchedEffect(logs) {
                if (logs.isNotEmpty()) scrollState.scrollTo(scrollState.maxValue)
            }
            val hScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .background(colors.surfaceVariant)
                    .padding(8.dp)
                    .verticalScroll(scrollState)
                    .horizontalScroll(hScroll),
            ) {
                when {
                    error != null -> ChuText("ERROR: $error", style = type.bodySmall, color = colors.error)
                    logs.isEmpty() && loading -> ChuText("FETCHING LOGS…", style = type.bodySmall, color = colors.textMuted)
                    logs.isEmpty() -> ChuText("NO LOGS AVAILABLE", style = type.bodySmall, color = colors.textMuted)
                    else -> Column {
                        logs.forEach { line ->
                            val isErr = LOG_ERROR_RE.containsMatchIn(line)
                            ChuText(
                                line,
                                style = type.bodySmall,
                                softWrap = false,
                                color = if (isErr) colors.error else colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
