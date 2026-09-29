package com.jossephus.chuchu.ui.screens.Queue

import com.jossephus.chuchu.ui.components.LinkifiedText
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.components.ChuDialog
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.ChuTextField
import com.jossephus.chuchu.ui.components.noRippleClickable
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.TuiBadge
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

@OptIn(ExperimentalLayoutApi::class)
@Composable
/**
 * Chi tiết MỘT task (28/9 làm gọn: user chỉ cần biết task ĐÃ GỬI chưa) — xem câu
 * trả lời của agent đã gỡ hẳn (model hasResp, cache response, fetch, panel cuộn
 * response đều xoá, không để code chết). Còn lại: trạng thái gửi + prompt + actions.
 */
internal fun TaskDetailDialog(
    task: QueueTask,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onAction: (QueueAction) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    var copiedPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(copiedPrompt) {
        if (copiedPrompt) {
            delay(1500)
            copiedPrompt = false
        }
    }
    // Dòng gửi: trạng thái + giờ gửi + lý do (nếu có). Ví dụ "unknown · gửi 03:09 ·
    // mồ côi sau khi qd khởi động lại" — đủ biết có tới agent chưa mà không cần đoán.
    val delivery = buildString {
        append(task.stateLabel.ifBlank { task.state })
        if (task.sentTs != null) append(" · gửi ${epochClock(task.sentTs)}") else append(" · chưa gửi")
        if (task.reason.isNotBlank()) append(" · ${task.reason}")
    }
    // Gioi han dialog theo chieu cao man hinh: header + nut hanh dong LUON thay,
    // prompt dai thi tu cuon — truoc day Column de tran khoi man hinh.
    val maxDialogH = (LocalConfiguration.current.screenHeightDp * 0.86f).dp

    // Bottom sheet chung (scrim + truot + inset): xem KohiBottomSheet.
    com.jossephus.chuchu.ui.components.KohiBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxDialogH)
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

            ChuText(
                delivery,
                style = type.labelSmall,
                color = colors.textMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChuText("PROMPT", style = type.labelSmall, color = colors.textMuted)
                // Prompt thường ngắn — bỏ panel cuộn riêng, outer scroll gánh:
                // còn 1 mức nested scroll thay vì 2.
                // Prompt cua user -10% (27/8: giam 20% xong user keu nho qua,
                // nang lai 10). Scale ca lineHeight,
                // khong lap lai bug chu de nhau ben dashboard.
                // Link trong prompt cũng bấm được (16/9, user: "link bên Queue phải bấm vào được").
                LinkifiedText(
                    text = task.text,
                    style = type.body.copy(
                        fontSize = type.body.fontSize * 0.9f,
                        lineHeight = type.body.lineHeight * 0.9f,
                    ),
                    color = colors.textPrimary,
                )
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

@Composable
internal fun QueueConfigDialog(
    currentUrl: String,
    currentToken: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
    /** Ô trên dải machine (28/9): toggle áp dụng NGAY, không chờ SAVE (như mọi toggle setting). */
    stripCells: List<String> = StripCells.DEFAULT,
    onToggleStripCell: (String) -> Unit = {},
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
            ChuText(
                "STRIP — CELLS ON THE MACHINE STRIP",
                style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.accent,
            )
            StripCells.ALL.forEach { id ->
                val on = id in stripCells
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable { onToggleStripCell(id) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChuText(
                        if (on) "[✔]" else "[ ]",
                        style = type.label.copy(fontWeight = FontWeight.Bold),
                        color = if (on) colors.success else colors.textMuted,
                    )
                    Spacer(Modifier.width(8.dp))
                    ChuText(
                        StripCells.label(id),
                        style = type.label,
                        color = if (on) colors.textPrimary else colors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    ChuText(StripCells.hint(id), style = type.labelSmall, color = colors.textMuted)
                }
            }
        }
    }
}

