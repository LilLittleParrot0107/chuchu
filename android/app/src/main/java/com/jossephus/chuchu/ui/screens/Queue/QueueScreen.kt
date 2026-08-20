package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.ChuTextField
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

/**
 * Màn hình hàng đợi — dựng theo đúng khung của `qq` (bản TUI chạy trên máy chủ),
 * không phải một danh sách thẻ tự nghĩ ra.
 *
 * Bốn thứ mượn nguyên của qq, vì chúng là lý do qq dùng được:
 *  1. **Việc lọc theo agent đang chọn.** Đổ phẳng cả hàng đợi thì không trả lời
 *     được câu hỏi duy nhất hay hỏi: "thằng này đang phải làm gì".
 *  2. **Mỗi việc đúng một dòng**, có chữ cái a/b/c… như qq — liếc là đếm được.
 *  3. **Vạch `▌` bên trái** đánh dấu dòng đang trỏ, thay cho khung viền.
 *  4. **Hai thanh nền đậm** (đỉnh và mục) chia màn hình, thay cho tiêu đề rời.
 *
 * Bảng ký hiệu và màu vẫn do qsrv gửi xuống, không nằm trong Kotlin — xem
 * QueueModels.kt.
 */

// Bảng màu lấy đúng của qq để hai bên nhìn như một. Cố ý KHÔNG dùng theme app:
// đây là cùng một công cụ ở hai màn hình, đổi màu là đổi cảm giác.
private val QQ_FG = Color(0xFFE5E5E5)
private val QQ_DIM = Color(0xFF7A7A7A)
private val QQ_ACC = Color(0xFF6FBCF7)
private val QQ_WARN = Color(0xFFE58A2B)
private val QQ_ERR = Color(0xFFE05C5C)
private val QQ_GOLD = Color(0xFFF6C17E)
private val QQ_BAND = Color(0xFF1E1E24)
private val QQ_SEL = Color(0xFF243447)

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
    var configOpen by remember { mutableStateOf(false) }
    var selectedPane by remember { mutableStateOf<String?>(null) }
    var selectedTask by remember { mutableStateOf<Int?>(null) }
    val showConfig = configOpen || ui.needsSetup

    val agents = ui.state.agents
    // Chưa chọn thì lấy agent đầu; agent biến mất thì tự rơi về agent đầu.
    val pane = selectedPane?.takeIf { p -> agents.any { it.pane == p } }
        ?: agents.firstOrNull()?.pane
    val mine = ui.state.tasks.filter { it.target == pane }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            // Thiếu cái này thì màn hình chui lên dưới đồng hồ và thanh thông báo.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        TopBand(ui, agents.size, onAction, onRefresh) { configOpen = !configOpen }

        if (showConfig) {
            ConfigPanel(
                currentUrl = currentUrl,
                currentToken = currentToken,
                onSave = { u, t -> onSaveConfig(u, t); configOpen = false },
                onDismiss = if (ui.needsSetup) null else ({ configOpen = false }),
            )
            return@Column
        }

        Spacer(Modifier.height(6.dp))

        agents.forEach { a ->
            AgentRow(
                agent = a,
                count = ui.state.tasks.count { it.target == a.pane },
                selected = a.pane == pane,
                onClick = { selectedPane = a.pane; selectedTask = null },
            )
        }
        if (agents.isEmpty() && ui.everLoaded) {
            ChuText(
                "  không thấy agent nào",
                style = type.labelSmall,
                color = QQ_DIM,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(6.dp))

        // ---------- thanh mục ----------
        Band {
            ChuText(
                agents.firstOrNull { it.pane == pane }?.name ?: "chưa có agent",
                style = type.label,
                color = QQ_FG,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ChuText("${mine.size} việc", style = type.labelSmall, color = QQ_DIM)
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                mine.isEmpty() && ui.everLoaded -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ChuText("chưa có việc nào", style = type.bodySmall, color = QQ_DIM)
                    ChuText("gõ nội dung rồi bấm gửi", style = type.labelSmall, color = QQ_DIM)
                }
                mine.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ChuText("đang tải…", color = QQ_DIM)
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(mine, key = { _, t -> t.id }) { k, task ->
                        TaskRow(
                            task = task,
                            letter = if (k < 26) ('a' + k).toString() else "·",
                            selected = task.id == selectedTask,
                            busyOps = ui.busyOps,
                            onClick = {
                                selectedTask = if (selectedTask == task.id) null else task.id
                            },
                            onAction = onAction,
                        )
                    }
                }
            }
        }

        AddBar { text -> onAdd(text, pane) }
    }
}

/** Một thanh nền đậm chạy hết bề ngang — qq gọi là band. */
@Composable
private fun Band(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(QQ_BAND)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun TopBand(
    ui: QueueUiState,
    agentCount: Int,
    onAction: (QueueAction, Int?) -> Unit,
    onRefresh: () -> Unit,
    onConfig: () -> Unit,
) {
    val type = ChuTypography.current
    val pending = ui.state.tasks.count { it.state == "pending" }
    Band {
        ChuText("HÀNG ĐỢI", style = type.label, color = QQ_FG)
        Box(Modifier.weight(1f))
        // Chỗ này qq dành cho thứ CẦN ĐẾN NGƯỜI — báo động đè lên số đếm,
        // vì số đếm lúc nào cũng đúng còn báo động thì không được lỡ.
        val notice = ui.error ?: ui.state.banner?.text
        if (notice != null) {
            ChuText(
                "⚠ $notice",
                style = type.labelSmall,
                color = if (ui.state.paused) QQ_WARN else QQ_ERR,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(3f),
            )
        } else {
            ChuText("$agentCount agent · ", style = type.labelSmall, color = QQ_DIM)
            ChuText("$pending", style = type.labelSmall, color = QQ_ACC)
            ChuText(" chờ", style = type.labelSmall, color = QQ_DIM)
        }
        ui.state.globalActions.forEach { a ->
            ChuText(
                if (a.op == "pause") " ‖" else " ▶",
                style = type.label,
                // Đang tạm dừng thì nút chạy tiếp phải nổi lên, không được mờ
                // như mọi thứ khác — đó là thứ người dùng đang đi tìm.
                color = if (ui.state.paused) QQ_WARN else QQ_DIM,
                modifier = Modifier.clickable { onAction(a, null) },
            )
        }
        ChuText("⟳", style = type.label, color = QQ_DIM, modifier = Modifier.clickable(onClick = onRefresh))
        ChuText("⚙", style = type.label, color = QQ_DIM, modifier = Modifier.clickable(onClick = onConfig))
    }
}

@Composable
private fun AgentRow(agent: QueueAgent, count: Int, selected: Boolean, onClick: () -> Unit) {
    val type = ChuTypography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) QQ_SEL else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuText("▌", style = type.label, color = if (selected) QQ_ACC else Color.Transparent)
        Spacer(Modifier.width(6.dp))
        ChuText(agent.glyph, style = type.label, color = agent.tone.color())
        Spacer(Modifier.width(8.dp))
        ChuText(
            agent.name,
            style = type.label,
            color = if (selected) QQ_FG else QQ_DIM,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (agent.word.isNotBlank()) {
            ChuText(agent.word, style = type.labelSmall, color = QQ_WARN)
            Spacer(Modifier.width(8.dp))
        }
        ChuText(
            if (count > 0) "$count" else " ",
            style = type.labelSmall,
            color = if (count > 0) QQ_ACC else QQ_DIM,
        )
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun TaskRow(
    task: QueueTask,
    letter: String,
    selected: Boolean,
    busyOps: Set<String>,
    onClick: () -> Unit,
    onAction: (QueueAction, Int?) -> Unit,
) {
    val type = ChuTypography.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) QQ_SEL else Color.Transparent)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChuText("▌", style = type.label, color = if (selected) QQ_ACC else Color.Transparent)
            ChuText(letter, style = type.labelSmall, color = QQ_DIM)
            Spacer(Modifier.width(6.dp))
            ChuText(task.glyph, style = type.label, color = task.tone.color())
            Spacer(Modifier.width(8.dp))
            ChuText(
                task.text.replace('\n', ' '),
                style = type.label,
                // qq: việc xong thì mờ đi, nó không còn đòi gì ở người đọc nữa.
                color = if (task.state == "done") QQ_DIM else QQ_FG,
                maxLines = if (selected) 4 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
        }
        // Nút chỉ hiện cho dòng đang chọn. Bày nút trên MỌI dòng là thứ làm
        // danh sách rối nhất — mỗi việc ba nút thì mười việc là ba mươi ô bấm.
        if (selected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 26.dp, end = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText("#${task.id} ${task.stateLabel}", style = type.labelSmall, color = QQ_DIM)
                task.actions.forEach { a ->
                    ChuButton(
                        onClick = { onAction(a, task.id) },
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        enabled = "${a.op}:${task.id}" !in busyOps,
                        borderColor = if (a.danger) QQ_ERR else QQ_DIM,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        ChuText(
                            a.label,
                            style = type.labelSmall,
                            color = if (a.danger) QQ_ERR else QQ_FG,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddBar(onAdd: (String) -> Unit) {
    val type = ChuTypography.current
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(QQ_BAND)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuTextField(
            value = text,
            onValueChange = { text = it },
            label = "",
            placeholder = "giao việc cho agent đang chọn…",
            singleLine = true,
            showLabel = false,
            modifier = Modifier.weight(1f),
            autoFocus = false,
            verticalPadding = 6.dp,
        )
        Spacer(Modifier.width(6.dp))
        ChuButton(
            onClick = { onAdd(text); text = "" },
            enabled = text.isNotBlank(),
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) { ChuText("gửi", style = type.labelSmall, color = ChuColors.current.onAccent) }
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChuText(
            "Chỉ cần địa chỉ. Đi qua tailnet thì máy chủ đã biết chắc là bạn, " +
                "không cần token. Ô token bên dưới chỉ dùng khi chạy qsrv " +
                "không nằm sau tailscale serve.",
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
            label = "Token (không bắt buộc)",
            placeholder = "để trống nếu dùng qua tailnet",
            singleLine = true,
            autoFocus = false,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChuButton(
                onClick = { onSave(url, token) },
                enabled = url.isNotBlank(),
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

/** qq dùng vàng cho "đang chạy", mờ cho "xong" — giữ đúng nghĩa đó. */
private fun QueueTone.color(): Color = when (this) {
    QueueTone.Accent -> QQ_GOLD
    QueueTone.Ok -> QQ_DIM
    QueueTone.Warn -> QQ_WARN
    QueueTone.Error -> QQ_ERR
    QueueTone.Dim -> QQ_DIM
}
