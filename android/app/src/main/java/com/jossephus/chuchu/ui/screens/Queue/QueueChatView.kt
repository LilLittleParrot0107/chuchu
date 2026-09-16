package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.MiniMarkdownText
import com.jossephus.chuchu.ui.components.LinkifiedText
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Màn CHAT của một agent trong tab Queue (16/9, prototype kohi-queue-chat-prototype.html,
 * user chốt "mở hoàn toàn"): đọc lại cuộc chat mà không cần terminal.
 *
 * Danh sách là LazyColumn thường — cuộn không vẽ lại terminal, không đi qua SSH. Tin của
 * anh có vạch vàng, tin của agent là markdown, mỗi lần gọi tool là một dòng xám chạm để mở
 * kết quả, phần suy nghĩ chỉ còn một dòng nhỏ. Tin mới nhất ở dưới cùng; về sau khi có tin
 * mới và đang ở đáy thì tự trôi theo.
 */
@Composable
internal fun QueueChatView(
    chat: ChatUiState,
    onLoadOlder: () -> Unit,
    /** Việc của pane này còn trong hàng đợi taskq (tin gửi lúc agent bận) — hiện ở cuối chat. */
    pendingTasks: List<QueueTask> = emptyList(),
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val messages = chat.messages

    // Tự trôi xuống đáy: lần tải đầu, và khi có tin mới mà người dùng đang ở đáy.
    var pinnedToBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }.collect { info ->
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            pinnedToBottom = info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(messages.lastOrNull()?.key, messages.size, pendingTasks.size) {
        if (messages.isNotEmpty() && pinnedToBottom) listState.scrollToItem(messages.size + pendingTasks.size)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            chat.loading && messages.isEmpty() -> Center("ĐANG TẢI CHAT…")
            chat.error != null && messages.isEmpty() -> Center("▌ ${chat.error}")
            messages.isEmpty() -> Center("chưa có tin nào")
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "older") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                        when {
                            chat.loadingOlder -> ChuText("đang tải…", style = type.labelSmall, color = colors.textMuted)
                            chat.hasMore -> ChuText(
                                "tải thêm $CHAT_OLDER_LABEL tin cũ hơn",
                                style = type.labelSmall,
                                color = colors.accent,
                                modifier = Modifier.clickable(onClick = onLoadOlder).padding(6.dp),
                            )
                            else -> ChuText("đầu cuộc chat", style = type.labelSmall, color = colors.textMuted)
                        }
                    }
                }
                items(messages, key = ChatMessage::key) { m ->
                    when (m.role) {
                        "user" -> UserRow(m)
                        "assistant" -> AssistantRow(m)
                        "tool" -> ToolRow(m)
                        else -> ChuText("✻ suy nghĩ", style = type.labelSmall, color = colors.textMuted, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                // Tin xếp hàng chưa vào transcript: hiện ở cuối để khỏi tưởng mất. Xoá thì về Queue.
                items(pendingTasks, key = { "task:${it.id}" }) { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(colors.border))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f).padding(vertical = 4.dp, horizontal = 0.dp)) {
                            ChuText(
                                if (t.isRunning) "▶ đang gửi · #${t.id}" else "⏳ chờ agent rảnh · #${t.id}",
                                style = type.labelSmall,
                                color = colors.textMuted,
                            )
                            ChuText(t.text, style = type.body, color = colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Center(text: String) {
    val colors = ChuColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ChuText(text, style = ChuTypography.current.labelSmall, color = colors.textMuted)
    }
}

@Composable
private fun UserRow(m: ChatMessage) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    // Cách khối trên 10dp thêm (danh sách chỉ 6dp): tin của anh mở một lượt mới, không dính
    // vào tool/markdown ngay trên (user 16/9 "díu quá").
    // Plan A (user chọn 16/9): nền vàng 14% + vạch 3dp, chữ giữ màu, đệm 6/10dp.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(IntrinsicSize.Min)
            .background(colors.accent.copy(alpha = 0.14f)),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(colors.accent))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(top = 6.dp, bottom = 6.dp, end = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                ChuText("❯ ", style = type.body, color = colors.accent)
                LinkifiedText(m.text, style = type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                TimeStamp(m.ts)
            }
        }
    }
}

@Composable
private fun AssistantRow(m: ChatMessage) {
    Column(Modifier.fillMaxWidth().padding(start = 10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            TimeStamp(m.ts)
        }
        MiniMarkdownText(m.text)
    }
}

/** Một lần gọi tool: dòng xám "⚙ Tên · mô tả"; chạm mở kết quả (đã cắt 2 KB ở server). */
@Composable
private fun ToolRow(m: ChatMessage) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    var open by remember(m.key) { mutableStateOf(false) }
    val canOpen = !m.res.isNullOrEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp)
            .then(if (open) Modifier.border(1.dp, colors.border).background(colors.surfaceVariant).padding(6.dp) else Modifier)
            .clickable(enabled = canOpen) { open = !open },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChuText("⚙ ", style = type.labelSmall, color = if (m.err) colors.error else colors.textMuted)
            ChuText(
                m.toolName,
                style = type.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = colors.textSecondary,
            )
            if (m.desc.isNotBlank()) {
                ChuText(
                    " · ${m.desc}",
                    style = type.labelSmall,
                    color = colors.textMuted,
                    maxLines = if (open) 6 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        if (open && canOpen) {
            BasicText(
                text = m.res.orEmpty(),
                style = type.labelSmall.copy(color = colors.textSecondary),
                maxLines = 40,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (m.resLen > (m.res?.length ?: 0)) {
                ChuText("… còn ${m.resLen - (m.res?.length ?: 0)} ký tự nữa", style = type.labelSmall, color = colors.accentSecondary)
            }
        }
    }
}

@Composable
private fun TimeStamp(ts: String) {
    val colors = ChuColors.current
    val label = remember(ts) { chatClock(ts) }
    if (label.isNotEmpty()) ChuText(label, style = ChuTypography.current.labelSmall, color = colors.textMuted)
}

private const val CHAT_OLDER_LABEL = "50"

/** "2026-09-16T05:04:31.123Z" → "12:04" theo giờ máy; chuỗi lạ thì rỗng. */
internal fun chatClock(ts: String): String {
    if (ts.length < 19) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val date = parser.parse(ts.substring(0, 19)) ?: return ""
        SimpleDateFormat("HH:mm", Locale.US).format(date)
    } catch (e: Exception) {
        ""
    }
}

/** "cập nhật 12 giây trước" cho dòng phụ dưới thanh tiêu đề. */
internal fun chatAge(updatedAt: Long, now: Long = System.currentTimeMillis()): String {
    if (updatedAt <= 0L) return ""
    val s = ((now - updatedAt) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "cập nhật ${s} giây trước"
        s < 3600 -> "cập nhật ${s / 60} phút trước"
        else -> "cập nhật ${s / 3600} giờ trước"
    }
}
