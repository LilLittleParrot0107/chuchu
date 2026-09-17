package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.jossephus.chuchu.ui.theme.AgentKind
import com.jossephus.chuchu.ui.theme.ChatTone
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import com.jossephus.chuchu.ui.theme.chatTone
import com.jossephus.chuchu.ui.theme.rosterColor
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Màn CHAT của một agent trong tab Queue (16/9, prototype kohi-queue-chat-prototype.html,
 * user chốt "mở hoàn toàn"): đọc lại cuộc chat mà không cần terminal.
 *
 * Danh sách là LazyColumn thường — cuộn không vẽ lại terminal, không đi qua SSH. Tin của
 * anh có vạch vàng, tin của agent là markdown; một lượt bị cắt nhiều đoạn được gộp về MỘT
 * bubble (đoạn dẫn giấu sau "· N earlier"), tool thành công lùi về dòng `⚙ ✓` mờ, suy nghĩ
 * ẩn mặc định — toàn bộ nằm ở [collapseAssistantTurns]. Tin mới nhất ở dưới cùng; về sau
 * khi có tin mới và đang ở đáy thì tự trôi theo.
 */
@Composable
internal fun QueueChatView(
    chat: ChatUiState,
    onLoadOlder: () -> Unit,
    /**
     * Danh sách ĐÃ qua [collapseAssistantTurns] (gộp lượt assistant, ẩn think) —
     * QueueScreen tính một lần để dòng đếm và chip dùng chung.
     */
    messages: List<ChatMessage>,
    /** Cỡ chữ (sp) cho tin của anh và của agent; ≤ 0 = cỡ body của theme. */
    fontSizeSp: Float = 0f,
    /** Loại agent của phiên — tin của agent tô màu như terminal từng tool (user chốt 16/9, 2B). */
    kind: AgentKind = AgentKind.OTHER,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val tone = remember(kind) { kind.chatTone() }
    val textSize = if (fontSizeSp > 0f) fontSizeSp.sp else type.body.fontSize
    // Như terminal: cỡ chữ Settings, dãn dòng tự nhiên của font, không thêm leading.
    val bodyStyle = type.body.copy(fontSize = textSize, lineHeight = TextUnit.Unspecified)

    // Tự trôi xuống đáy: lần tải đầu, và khi có tin mới mà người dùng đang ở đáy.
    var pinnedToBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }.collect { info ->
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            pinnedToBottom = info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
        }
    }
    // Bản gộp giữ key ĐẦU của lượt để không đổi chỗ, nên phải theo cả ts (đổi mỗi
    // đoạn mới về) thì tin mới của đúng lượt cuối vẫn kéo được đáy đang ghim.
    LaunchedEffect(messages.lastOrNull()?.key, messages.lastOrNull()?.ts, messages.size) {
        if (messages.isNotEmpty() && pinnedToBottom) listState.scrollToItem(messages.size)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            chat.loading && messages.isEmpty() -> Center("LOADING CHAT…")
            chat.error != null && messages.isEmpty() -> Center("▌ ${chat.error}")
            messages.isEmpty() -> Center("no messages yet")
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "older") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                        when {
                            chat.loadingOlder -> ChuText("loading…", style = type.labelSmall, color = colors.textMuted)
                            chat.hasMore -> ChuText(
                                "load $CHAT_OLDER_LABEL older messages",
                                style = type.labelSmall,
                                color = colors.accent,
                                modifier = Modifier.clickable(onClick = onLoadOlder).padding(6.dp),
                            )
                            else -> ChuText("start of chat", style = type.labelSmall, color = colors.textMuted)
                        }
                    }
                }
                items(messages, key = ChatMessage::key) { m ->
                    when (m.role) {
                        "user" -> UserRow(m, bodyStyle)
                        "assistant" -> AssistantRow(m, textSize, tone = tone, kind = kind)
                        "tool" -> ToolRow(m, tone = tone)
                        // think/tin rỗng đã bị collapseAssistantTurns bỏ ở tầng đọc.
                        else -> Unit
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
private fun UserRow(m: ChatMessage, bodyStyle: androidx.compose.ui.text.TextStyle) {
    val colors = ChuColors.current
    // GIỮ BẢN CŨ (user ra lệnh revert 17/9 "tin nhắn đợi như cũ"): nền vàng 14% +
    // vạch 3dp + ❯, full-width. Bubble phải chỉ thử ở prototype, không duyệt cho đây.
    // Cách khối trên 10dp thêm: tin của anh mở một lượt mới, không dính tool/markdown.
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
                ChuText("❯ ", style = bodyStyle, color = colors.accent)
                LinkifiedText(m.text, style = bodyStyle, color = colors.textPrimary, modifier = Modifier.weight(1f))
                TimeStamp(m.ts)
            }
        }
    }
}

@Composable
private fun AssistantRow(m: ChatMessage, textSize: TextUnit, tone: ChatTone?, kind: AgentKind) {
    val colors = ChuColors.current
    // Bubble bên TRÁI, fill = 3% màu agent (liều user chốt — gần như trắng, chỉ để
    // phân biệt vùng), viền đậm 2,5dp 70% màu agent; giờ trong góc dưới trái.
    val bubbleColor = kind.rosterColor()
    Column(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .background(bubbleColor.copy(alpha = 0.03f), BubbleShape)
            .border(2.5.dp, bubbleColor.copy(alpha = 0.7f), BubbleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        ParasFold(m.key, m.paras, textSize, tone)
        MiniMarkdownText(m.text, fontSize = textSize, tone = tone)
        TimeStamp(m.ts, color = tone?.meta ?: colors.textMuted)
    }
}

/** Bo 5dp dùng cho cả bubble hai phía (F1·B). */
private val BubbleShape = RoundedCornerShape(5.dp)

/**
 * "· N earlier" — các đoạn dẫn trước của cùng lượt (mục 5, duyệt 17/9). Mặc định
 * bubble chỉ giữ đoạn CUỐI (thứ thật sự còn giá trị đọc); chạm một lần để mở ngược
 * các đoạn đó ngay trên. Dùng chung cho cả màn CHAT và DÒNG THỜI GIAN.
 */
@Composable
internal fun ParasFold(key: String, paras: List<String>, textSize: TextUnit, tone: ChatTone?) {
    if (paras.isEmpty()) return
    val colors = ChuColors.current
    val type = ChuTypography.current
    var open by remember(key) { mutableStateOf(false) }
    ChuText(
        text = if (open) "▴ ${paras.size} earlier" else "· ${paras.size} earlier ▾",
        style = type.labelSmall,
        color = colors.textMuted,
        modifier = Modifier.clickable { open = !open }.padding(vertical = 2.dp),
    )
    if (open) paras.forEach { MiniMarkdownText(it, fontSize = textSize, tone = tone) }
}

/**
 * Một lần gọi tool — mục 4 (duyệt 17/9): tool THÀNH CÔNG chỉ còn một dòng nhỏ
 * `⚙ ✓ tên`, mô tả/kết quả ẩn cho tới khi chạm; tool LỖI nổi bật màu đỏ và hiện
 * mô tả luôn vì đó là thứ cần đọc ngay. (Mặc định qsrv lọc hết tool — dòng này
 * chỉ xuất hiện khi bật `tools=true`.)
 */
@Composable
private fun ToolRow(m: ChatMessage, tone: ChatTone?) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    var open by remember(m.key) { mutableStateOf(false) }
    val canOpen = !m.res.isNullOrEmpty() || m.desc.isNotBlank()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp)
            .then(if (open) Modifier.border(1.dp, colors.border).background(colors.surfaceVariant).padding(6.dp) else Modifier)
            .clickable(enabled = canOpen) { open = !open },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChuText(if (m.err) "└ ⚙ ✕ " else "└ ⚙ ✓ ", style = type.labelSmall, color = if (m.err) colors.error else (tone?.meta ?: colors.textMuted))
            ChuText(
                m.toolName,
                style = type.labelSmall.copy(fontWeight = FontWeight.Medium),
                // Lỗi thì đỏ để nhảy mắt; thành công lùi về chữ mờ vì nó là nhiễu nền.
                color = if (m.err) colors.error else colors.textMuted,
            )
            if (m.desc.isNotBlank() && (open || m.err)) {
                ChuText(
                    " · ${m.desc}",
                    style = type.labelSmall,
                    color = if (m.err) colors.error else (tone?.meta ?: colors.textMuted),
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
                ChuText("… ${m.resLen - (m.res?.length ?: 0)} more chars", style = type.labelSmall, color = colors.accentSecondary)
            }
        }
    }
}

@Composable
private fun TimeStamp(ts: String, color: androidx.compose.ui.graphics.Color? = null) {
    val colors = ChuColors.current
    val label = remember(ts) { chatClock(ts) }
    if (label.isNotEmpty()) {
        ChuText(label, style = ChuTypography.current.labelSmall, color = color ?: colors.textMuted)
    }
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
        s < 60 -> "updated ${s}s ago"
        s < 3600 -> "updated ${s / 60}m ago"
        else -> "updated ${s / 3600}h ago"
    }
}

/**
 * Giờ ngắn cho hàng HỘI THOẠI (UI G1): "vừa xong" · "12′" · "20:59" (hôm nay) · "3d" ·
 * "16/9". Cùng nguồn ISO UTC với chatClock; chuỗi lạ thì rỗng (không đoán).
 */
internal fun chatWhen(ts: String, now: Long = System.currentTimeMillis()): String {
    if (ts.length < 19) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val date = parser.parse(ts.substring(0, 19)) ?: return ""
        val age = now - date.time
        when {
            age < 60_000L -> "just now"
            age < 3_600_000L -> "${age / 60_000L}′"
            age < 86_400_000L -> SimpleDateFormat("HH:mm", Locale.US).format(date)
            age < 7 * 86_400_000L -> "${age / 86_400_000L}d"
            else -> SimpleDateFormat("d/M", Locale.US).format(date)
        }
    } catch (e: Exception) {
        ""
    }
}
