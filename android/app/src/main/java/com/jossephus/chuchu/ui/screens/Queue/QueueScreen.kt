package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.ChuTextField
import com.jossephus.chuchu.ui.components.TuiBadge
import com.jossephus.chuchu.ui.theme.ChuColorPalette
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

/**
 * Màn hình hàng đợi task — không trạng thái, chỉ vẽ những gì [QueueUiState] đưa
 * cho. Biểu tượng, màu, nhãn, danh sách nút đều từ qsrv xuống; ở đây tuyệt đối
 * không có `when (state) { "pending" -> ... }`. Xem chú thích ở QueueModels.kt.
 */
@Composable
fun QueueScreen(
    ui: QueueUiState,
    onAction: (QueueAction, Int?) -> Unit,
    onAdd: (String, String?) -> Unit,
    onRefresh: () -> Unit,
    currentUrl: String,
    currentToken: String,
    onSaveConfig: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    // Chua cau hinh -> bat buoc hien bang cau hinh; da cau hinh -> mo bang nut.
    var configOpen by remember { mutableStateOf(false) }
    val showConfig = configOpen || ui.needsSetup

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ChuText("Hàng đợi", style = type.title)
                if (ui.state.summary.isNotBlank()) {
                    ChuText(ui.state.summary, style = type.labelSmall, color = colors.textSecondary)
                }
            }
            ui.state.globalActions.forEach { action ->
                ChuButton(
                    onClick = { onAction(action, null) },
                    variant = ChuButtonVariant.Outlined,
                    bracketed = true,
                    enabled = "${action.op}:-" !in ui.busyOps,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) { ChuText(action.label, style = type.labelSmall) }
                Spacer(Modifier.width(6.dp))
            }
            ChuButton(
                onClick = onRefresh,
                variant = ChuButtonVariant.Ghost,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                contentDescription = "Tải lại hàng đợi",
            ) { ChuText("⟳", style = type.label, color = colors.textSecondary) }
            ChuButton(
                onClick = { configOpen = !configOpen },
                variant = ChuButtonVariant.Ghost,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                contentDescription = "Cấu hình hàng đợi",
            ) { ChuText("⚙", style = type.label, color = colors.textSecondary) }
        }

        ui.state.banner?.let { Notice(it.text, it.tone.color(colors)) }
        ui.error?.let { Notice(it, colors.error) }

        if (ui.state.agents.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ui.state.agents.forEach { a ->
                    TuiBadge("${a.glyph} ${a.name}", a.tone.color(colors))
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                showConfig -> ConfigPanel(
                    currentUrl = currentUrl,
                    currentToken = currentToken,
                    onSave = { u, t -> onSaveConfig(u, t); configOpen = false },
                    onDismiss = if (ui.needsSetup) null else ({ configOpen = false }),
                )
                ui.state.tasks.isEmpty() && ui.everLoaded ->
                    Centered("Hàng đợi trống", colors.textMuted)
                ui.state.tasks.isEmpty() && ui.loading ->
                    Centered("Đang tải…", colors.textMuted)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    items(ui.state.tasks, key = { it.id }) { task ->
                        TaskRow(task, ui.busyOps, onAction)
                    }
                }
            }
        }

        if (!showConfig) AddBar(enabled = true, onAdd = onAdd)
    }
}

@Composable
private fun TaskRow(
    task: QueueTask,
    busyOps: Set<String>,
    onAction: (QueueAction, Int?) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val tone = task.tone.color(colors)

    ChuCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp)) {
            ChuText(task.glyph, style = type.body, color = tone)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                ChuText(task.text, style = type.body, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChuText(task.stateLabel, style = type.labelSmall, color = tone)
                    if (task.sub.isNotBlank()) {
                        ChuText(
                            "  ${task.sub}",
                            style = type.labelSmall,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            ChuText("#${task.id}", style = type.labelSmall, color = colors.textMuted)
        }
        if (task.actions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                task.actions.forEach { action ->
                    ChuButton(
                        onClick = { onAction(action, task.id) },
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        enabled = "${action.op}:${task.id}" !in busyOps,
                        borderColor = if (action.danger) colors.error else colors.border,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        ChuText(
                            action.label,
                            style = type.labelSmall,
                            color = if (action.danger) colors.error else colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddBar(enabled: Boolean, onAdd: (String, String?) -> Unit) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .border(1.dp, colors.border, RectangleShape)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuTextField(
            value = text,
            onValueChange = { text = it },
            label = "",
            placeholder = "Thêm việc cho agent…",
            singleLine = true,
            showLabel = false,
            modifier = Modifier.weight(1f),
            // Đừng tự bật bàn phím khi mở màn hình — vào đây thường là để xem
            // hàng đợi, không phải để gõ.
            autoFocus = false,
            verticalPadding = 7.dp,
        )
        Spacer(Modifier.width(8.dp))
        ChuButton(
            onClick = { onAdd(text, null); text = "" },
            enabled = enabled && text.isNotBlank(),
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) { ChuText("Thêm", style = type.labelSmall, color = colors.onAccent) }
    }
}

@Composable
private fun Notice(text: String, tone: Color) {
    val type = ChuTypography.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(tone.copy(alpha = 0.10f))
            .border(1.dp, tone.copy(alpha = 0.5f), RectangleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        ChuText(text, style = type.labelSmall, color = tone)
    }
}

@Composable
private fun Centered(text: String, color: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ChuText(text, color = color)
    }
}

@Composable
private fun ConfigPanel(
    currentUrl: String,
    currentToken: String,
    onSave: (String, String) -> Unit,
    onDismiss: (() -> Unit)?,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    var token by remember(currentToken) { mutableStateOf(currentToken) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChuText("Cấu hình hàng đợi", style = type.title)
        ChuText(
            "Token nằm ở ~/.config/qsrv.token trên máy chủ. " +
                "Địa chỉ phải là đường tailnet — qsrv chỉ nghe loopback nên " +
                "ngoài tailnet không gọi tới được.",
            style = type.bodySmall,
            color = colors.textSecondary,
        )
        ChuTextField(
            value = url,
            onValueChange = { url = it },
            label = "Địa chỉ qsrv",
            placeholder = "https://may.tailnet.ts.net/q",
            singleLine = true,
            autoFocus = false,
        )
        ChuTextField(
            value = token,
            onValueChange = { token = it },
            label = "Token",
            placeholder = "dán token vào đây",
            singleLine = true,
            autoFocus = false,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChuButton(
                onClick = { onSave(url, token) },
                enabled = url.isNotBlank() && token.isNotBlank(),
                bracketed = true,
            ) { ChuText("Lưu", color = colors.onAccent) }
            if (onDismiss != null) {
                ChuButton(onClick = onDismiss, variant = ChuButtonVariant.Ghost, bracketed = true) {
                    ChuText("Đóng", color = colors.textSecondary)
                }
            }
        }
    }
}

private fun QueueTone.color(c: ChuColorPalette): Color = when (this) {
    QueueTone.Accent -> c.accent
    QueueTone.Ok -> c.success
    QueueTone.Warn -> c.warning
    QueueTone.Error -> c.error
    QueueTone.Dim -> c.textMuted
}
