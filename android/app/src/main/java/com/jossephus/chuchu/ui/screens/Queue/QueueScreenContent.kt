package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuSegmentedControl
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.components.KohiSelectableRow
import com.jossephus.chuchu.ui.components.LinkifiedText
import com.jossephus.chuchu.ui.components.MiniMarkdownText
import com.jossephus.chuchu.ui.theme.AgentKind
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import com.jossephus.chuchu.ui.theme.chatTone
import com.jossephus.chuchu.ui.theme.rosterColor

internal const val ALL_AGENTS = "ALL"

/**
 * Dot chi the hien RUNTIME STATUS cua agent — tuyet doi khong dung de bieu thi
 * selection (selection = background + border + cursor '>' ben trai). Truoc day
 * glyph server ('●' cho working, '·' cho idle) lam agent dang chay nhin giong
 * dang duoc chon.
 */
private fun runtimeDot(agent: QueueAgent): String = when (agent.label.lowercase()) {
    "working", "busy", "sending" -> "●"
    "idle", "done" -> "○"
    else -> agent.glyph.ifBlank { "?" }   // blocked '▲', unsure '?' giữ nguyên
}

/** Hai chế độ của màn Queue (user chốt G1, 16/9): đọc dòng thời gian ↔ quản hội thoại. */
internal enum class QueueMode { Timeline, Threads }

@Composable
internal fun QueueModeSwitch(
    mode: QueueMode,
    onSelect: (QueueMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    ChuSegmentedControl(
        options = listOf(QueueMode.Timeline, QueueMode.Threads),
        labels = mapOf(
            QueueMode.Timeline to "TIMELINE",
            QueueMode.Threads to "CONVERSATIONS",
        ),
        selected = mode,
        onSelect = onSelect,
        modifier = modifier.padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Session đang chạy (theo label) — dùng cho chấm trạng thái, không phải selection. */
private fun sessionWorking(agent: QueueAgent): Boolean =
    agent.label.trim().lowercase() in setOf("working", "busy", "sending", "running")

/**
 * Chấm trạng thái một session (user chốt 17/9: "để cho t mấy chấm show status thanh lịch"):
 * đỏ/vàng khi CẦN ANH, accent khi đang chạy, xanh khi vừa xong, xám khi rảnh — thay hẳn
 * các tag chữ "new"/"N waiting" rẻ tiền.
 */
@Composable
private fun sessionStatusColor(agent: QueueAgent): Color {
    val colors = ChuColors.current
    return if (sessionWorking(agent)) colors.accent else agent.tone.color()
}

/**
 * Vị trí cuộn khi (quay lại) dòng thời gian — hàm thuần, có test riêng.
 *
 * Neo theo KEY tin đầu đang thấy, không theo index: feed cắt tin cũ ở đầu nên index
 * trôi còn key thì không. Đang ở đáy, chưa có neo, hoặc neo đã rơi khỏi cửa sổ feed
 * (quá nhiều tin mới trong lúc rời màn) → về điểm mới nhất (user chốt 17/9).
 * `keys.size` là cuộn quá tin cuối — LazyListState tự kẹp về tin mới nhất.
 */
internal fun feedRestoreIndex(keys: List<String>, pinned: Boolean, anchorKey: String?): Int {
    if (keys.isEmpty() || pinned || anchorKey == null) return keys.size
    val i = keys.indexOf(anchorKey)
    return if (i >= 0) i else keys.size
}

/**
 * DÒNG THỜI GIAN (G1): tin cuối các session đang động, gộp theo giờ, tin mới ở dưới.
 * Chạm một tin = nhắm luôn phiên đó cho ô gõ ngay dưới (user chốt 17/9) — không nhảy
 * sang HỘI THOẠI như bản G1 đầu.
 */
@Composable
internal fun QueueFeedView(
    feed: FeedUiState,
    onPick: (FeedMessage) -> Unit,
    listState: LazyListState,
    pinned: Boolean,
    onPinnedChange: (Boolean) -> Unit,
    anchorKey: String?,
    onAnchorChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    // Vị trí cuộn do QueueScreen giữ (rememberSaveable) — mode/hội thoại quay lại vẫn y chỗ.
    val curFeed by rememberUpdatedState(feed)
    // Đã khôi phục vị trí cho lần vào hiện tại chưa. Collector bên dưới chỉ ghi
    // pinned/neo SAU khi khôi phục xong: layout đầu tiên lúc vào có thể còn ở vị
    // trí cũ trong khi neo đã lưu là tin khác (feed cắt đầu làm index trôi) —
    // ghi sớm là đè mất đúng cái neo cần khôi phục.
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }.collect { info ->
            if (info.totalItemsCount > 0 && restored) {
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                onPinnedChange(last >= info.totalItemsCount - 2)
                // Neo = KEY tin đầu đang thấy: feed cắt tin ở đầu nên index trôi, key ổn định.
                val first = info.visibleItemsInfo.firstOrNull()?.index ?: -1
                if (first >= 0) onAnchorChange(curFeed.messages.getOrNull(first)?.key)
            }
        }
    }
    // Vào lần đầu / quay lại: dừng đúng tin cuối đã thấy; neo rơi khỏi cửa sổ feed
    // (hoặc đang ở đáy) thì về điểm mới nhất (user chốt 17/9).
    LaunchedEffect(feed.messages.isNotEmpty(), pinned, anchorKey) {
        if (restored || feed.messages.isEmpty()) return@LaunchedEffect
        restored = true
        listState.scrollToItem(
            feedRestoreIndex(feed.messages.map(FeedMessage::key), pinned, anchorKey)
        )
    }
    LaunchedEffect(feed.messages.lastOrNull()?.key, feed.messages.size, feed.pane) {
        if (feed.messages.isNotEmpty() && pinned) listState.scrollToItem(feed.messages.size)
    }
    Box(modifier = modifier.fillMaxSize()) {
        when {
            feed.messages.isEmpty() && feed.loading -> CenterNote("LOADING TIMELINE…")
            feed.messages.isEmpty() && feed.error != null -> CenterNote("▌ ${feed.error}")
            feed.messages.isEmpty() -> CenterNote("no messages yet — the house is quiet · switch to CONVERSATIONS to open a session")
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(feed.messages, key = FeedMessage::key) { m ->
                    FeedRow(m, onPick = onPick, bodySize = type.body.fontSize)
                }
            }
        }
    }
}

@Composable
private fun FeedRow(m: FeedMessage, onPick: (FeedMessage) -> Unit, bodySize: TextUnit) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val kind = AgentKind.of(m.agent)
    val tone = remember(kind) { kind.chatTone() }
    when (m.role) {
        "user" -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPick(m) }
                .height(IntrinsicSize.Min)
                .background(colors.accent.copy(alpha = 0.10f)),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(colors.accent.copy(alpha = 0.7f)))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).padding(vertical = 5.dp, horizontal = 0.dp)) {
                ChuText(
                    "${m.name} · ${chatClock(m.ts)}",
                    style = type.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinkifiedText(m.text, style = type.body, color = colors.textPrimary, modifier = Modifier.fillMaxWidth())
            }
        }
        else -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPick(m) }
                .padding(top = 4.dp, end = 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChuText(
                    m.name,
                    style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = kind.rosterColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                // Nhãn chữ ("working"/"done"…) thu thành một chấm màu (user 17/9:
                // "để cho t mấy chấm show status") — cùng ngôn ngữ với HỘI THOẠI.
                if (m.label.isNotBlank()) {
                    ChuText("●", style = type.labelSmall, color = m.tone.color())
                    Spacer(Modifier.width(6.dp))
                }
                ChuText(chatClock(m.ts), style = type.labelSmall, color = colors.textMuted)
            }
            MiniMarkdownText(m.text, fontSize = bodySize, tone = tone)
        }
    }
}

/**
 * HỘI THOẠI (G1, gọn lại 17/9): danh sách session như hàng đợi thật — việc cần anh lên
 * đầu (thứ tự đã sắp từ /state), một chấm trạng thái + một dòng xem trước; chạm = mở thread.
 */
@Composable
internal fun QueueConversationList(
    agents: List<QueueAgent>,
    selectedPane: String,
    chatSeen: Map<String, String>,
    onOpenChat: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    if (agents.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ChuText("NO AGENTS FOUND · CHECK HERDR/QSRV", style = type.labelSmall, color = colors.textMuted)
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(agents, key = QueueAgent::pane) { agent ->
            val selected = selectedPane == agent.pane
            val hasNew = agent.chatRev != null && agent.chatRev != chatSeen[agent.pane]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) colors.accent.copy(alpha = 0.10f) else Color.Transparent)
                    // Có transcript thì chạm là mở thread; chưa có thì chỉ chọn (ô gõ nhắm vào nó).
                    .clickable { if (agent.chatRev != null) onOpenChat(agent.pane) else onSelect(agent.pane) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ChuText("●", style = type.labelSmall, color = sessionStatusColor(agent), modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChuText(
                            agent.name,
                            style = type.label.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                            color = AgentKind.of(agent.agent).rosterColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.weight(1f))
                        // Chưa đọc = một chấm nhỏ cạnh giờ, thay tag chữ "new" (user 17/9).
                        if (hasNew) {
                            ChuText("●", style = type.labelSmall, color = colors.accent)
                            Spacer(Modifier.width(6.dp))
                        }
                        chatWhen(agent.previewTs).takeIf { it.isNotEmpty() }?.let {
                            ChuText(it, style = type.labelSmall, color = colors.textMuted)
                        }
                    }
                    val preview = agent.preview.ifBlank {
                        if (agent.chatRev != null) "no messages yet" else "no transcript"
                    }
                    ChuText(
                        preview,
                        style = type.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterNote(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ChuText(text, style = ChuTypography.current.labelSmall, color = ChuColors.current.textMuted)
    }
}

@Composable
internal fun QueueTaskRow(
    task: QueueTask,
    selected: Boolean,
    showTarget: Boolean,
    onClick: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    KohiSelectableRow(
        selected = selected,
        tone = task.tone.color(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        // Con tro '>' dong nhat voi roster: task dang mo detail pane.
        ChuText(
            if (selected) ">" else "",
            style = type.label.copy(fontWeight = FontWeight.Bold),
            color = colors.accent,
            modifier = Modifier.width(12.dp),
        )
        ChuText(task.glyph, style = type.label, color = task.tone.color())
        Spacer(Modifier.width(6.dp))
        ChuText(
            "#${task.id}",
            style = type.label.copy(fontWeight = FontWeight.Bold),
            color = colors.accentSecondary,
        )
        if (showTarget && task.target.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            ChuText(
                "@${task.target}",
                style = type.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // ~28% chỗ còn lại cho @target, text giữ phần lớn.
                modifier = Modifier.weight(0.28f, fill = false),
            )
        }
        Spacer(Modifier.width(6.dp))
        ChuText(
            task.text.replace('\n', ' '),
            style = type.body,
            color = if (task.isCompleted) colors.textMuted else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun EmptyQueueInspector(
    agent: QueueAgent?,
    scopeLabel: String,
    allTasks: List<QueueTask>,
    pane: String,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val scoped = if (pane == ALL_AGENTS) allTasks else allTasks.filter { it.target == pane }
    val pending = scoped.count { !it.isCompleted }
    val done = scoped.count { it.isCompleted }
    val recent = scoped.lastOrNull { it.isCompleted }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (agent != null) {
                ChuText(runtimeDot(agent), style = type.label, color = agent.tone.color())
                Spacer(Modifier.width(6.dp))
            }
            ChuText(
                scopeLabel,
                style = type.label.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        InspectorRow("STATUS", agent?.label?.uppercase() ?: "—", colors.textSecondary)
        InspectorRow(
            "QUEUE",
            "$pending QUEUED · $done DONE",
            if (pending > 0) colors.accent else colors.textMuted,
        )
        if (recent != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChuText("RECENT", style = type.labelSmall, color = colors.textMuted, modifier = Modifier.width(52.dp))
                ChuText(
                    "#${recent.id} ${recent.stateLabel.uppercase()} · ${recent.text.replace('\n', ' ')}",
                    style = type.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        ChuText(
            agent?.let { "› type below to send a task to @${it.name}" }
                ?: "› pick an agent above to send work",
            style = type.labelSmall,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InspectorRow(key: String, value: String, valueColor: Color) {
    val type = ChuTypography.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChuText(key, style = type.labelSmall, color = ChuColors.current.textMuted, modifier = Modifier.width(52.dp))
        ChuText(value, style = type.labelSmall, color = valueColor)
    }
}

/**
 * Composer mot dong gan nhu terminal prompt: recipient + '>' va o nhap nam trong
 * CUNG khung, nut send o ben phai trong khung do — bo cuc rieng le lam input
 * cao loi, nut SEND chiem block rieng va dau '>' tach khoi input.
 */
@Composable
internal fun QueueComposer(
    value: String,
    onValueChange: (String) -> Unit,
    agent: QueueAgent?,
    sending: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    /** Màn CHAT truyền "Reply to <agent>…"; null = "Describe the task…" như cũ. */
    placeholder: String? = null,
    sendLabel: String = "[SEND]",
    /** Nút đứng GIỮA ô gõ và nút gửi (màn CHAT: ⊕ đính file). null = không có. */
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val canSend = agent != null && value.isNotBlank() && !sending

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    // Man hep: bo "@ten-agent" di, o nhap moi la thu can cho. Agent dang nhan
    // da hien ro o hang AGENTS ngay tren, khong can nhac lai. Man rong (may gap
    // mo ra) thi con cho, hien lai.
    val wide = maxWidth >= 600.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 56.dp)
                .background(colors.surfaceVariant)
                .border(1.dp, if (canSend) colors.accent.copy(alpha = 0.45f) else colors.border)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (wide) agent?.let {
                ChuText(
                    "@${it.name}",
                    style = type.label,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp),
                )
            }
            ChuText("›", style = type.headline, color = if (canSend) colors.accent else colors.textMuted)
            // Đa dòng như qq: gõ dài thì text WRAP để luôn nhìn thấy toàn bộ,
            // tối đa 4 dòng rồi mới cuộn bên trong. Trước đây singleLine khiến
            // đoạn dài trôi ngang khỏi màn — "typing không thấy snippet".
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = false,
                maxLines = 4,
                textStyle = type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                // IME action Send: Enter tren ban phim mem gui luon (kieu qq)
                // thay vi phai cham nut SEND; Enter vat ly van xuong dong vi
                // singleLine = false.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                modifier = Modifier.weight(1f)
                    .padding(vertical = 10.dp)
                    // Chạm vào ô gõ = bàn phím sắp chiếm nửa màn -> panel máy
                    // phải hạ xuống, không thì nó chắn mất chỗ gõ (user 3/9).
                    .onFocusChanged { onFocusChanged(it.isFocused) },
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            ChuText(
                                agent?.let { placeholder ?: "Describe the task…" } ?: "Pick an agent first…",
                                style = type.body,
                                color = colors.disabledText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.width(6.dp))
        if (trailing != null) {
            trailing()
            Spacer(Modifier.width(2.dp))
        }
        // Send la hanh dong van ban trong terminal, KHONG phai block rieng;
        // disabled thi moi mo di chu khong bien thanh nut "co ve bi liet".
        ChuButton(
            onClick = onSend,
            enabled = canSend,
            variant = ChuButtonVariant.Ghost,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
            minHeight = 34.dp,
        ) {
            ChuText(
                if (sending) "[…]" else sendLabel,
                style = type.label.copy(fontWeight = FontWeight.Bold),
                color = when {
                    sending -> colors.textMuted
                    canSend -> colors.accent
                    else -> colors.disabledText
                },
            )
        }
    }
    }
}
