package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.noRippleClickable
import com.jossephus.chuchu.ui.components.MiniMarkdownText
import com.jossephus.chuchu.ui.components.LinkifiedText
import com.jossephus.chuchu.ui.theme.AgentKind
import com.jossephus.chuchu.ui.theme.ChatTone
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import com.jossephus.chuchu.ui.theme.chatTone
import com.jossephus.chuchu.ui.theme.sessionColor
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
    /** Thẻ NEEDS YOU (21/9): chạm một lựa chọn của prompt đang chặn pane. */
    onAnswerBlocked: (BlockedOption) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val tone = remember(kind) { kind.chatTone() }
    val sessionColor = kind.sessionColor(chat.name)
    val blocked = chat.blocked
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
    // Thẻ NEEDS YOU là item cuối (sau tin cuối) — hiện ra cũng kéo đáy như một tin mới.
    val lastIndex = messages.size + (if (blocked != null) 1 else 0)
    LaunchedEffect(messages.lastOrNull()?.key, messages.lastOrNull()?.ts, messages.size, blocked?.signature) {
        if (lastIndex > 0 && pinnedToBottom) listState.scrollToItem(lastIndex)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            chat.loading && messages.isEmpty() -> Center("LOADING CHAT…")
            chat.error != null && messages.isEmpty() -> Center("▌ ${chat.error}")
            messages.isEmpty() && blocked == null -> Center("no messages yet")
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "older") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                        when {
                            chat.loadingOlder -> ChuText("loading…", style = type.labelSmall, color = colors.textMuted)
                            chat.hasMore -> ChuText(
                                "load $CHAT_OLDER_LABEL older messages",
                                style = type.labelSmall,
                                color = colors.accent,
                                modifier = Modifier.noRippleClickable(onClick = onLoadOlder).padding(6.dp),
                            )
                            else -> ChuText("start of chat", style = type.labelSmall, color = colors.textMuted)
                        }
                    }
                }
                items(messages, key = ChatMessage::key) { m ->
                    when (m.role) {
                        "user" -> UserRow(m, bodyStyle)
                        "assistant" -> AssistantRow(m, textSize, tone = tone, bubbleColor = sessionColor)
                        "tool" -> ToolRow(m, tone = tone)
                        // think/tin rỗng đã bị collapseAssistantTurns bỏ ở tầng đọc.
                        else -> Unit
                    }
                }
                if (blocked != null) item(key = "blocked") {
                    BlockedCard(
                        prompt = blocked,
                        answered = chat.answered,
                        answering = chat.answering,
                        textSize = textSize,
                        onAnswer = onAnswerBlocked,
                    )
                }
            }
        }
    }
}

/**
 * Thẻ NEEDS YOU (prototype kohi-chat-blocked-prototype.html, user duyệt 21/9): viền/nền đỏ
 * nhạt như chấm trạng thái, tiêu đề + loại prompt, lệnh trong khung xám, câu hỏi, rồi ĐÚNG
 * các lựa chọn Claude đưa với số y như terminal. Chạm = gõ số đó vào pane; ô vừa gửi đổi
 * xanh và thẻ khoá tới khi prompt đổi (ô con trỏ terminal KHÔNG tô — user 21/9). "Type
 * something" / "Chat about this" viền đứt: gửi số xong câu trả lời gõ ở ô dưới.
 */
@Composable
private fun BlockedCard(
    prompt: BlockedPrompt,
    answered: Int?,
    answering: Boolean,
    textSize: TextUnit,
    onAnswer: (BlockedOption) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val locked = answering || answered != null
    Column(
        Modifier
            .fillMaxWidth(0.96f)
            .background(colors.error.copy(alpha = 0.07f), BoxShape)
            .border(1.dp, colors.error.copy(alpha = 0.55f), BoxShape)
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChuText("● NEEDS YOU", style = type.labelSmall.copy(fontWeight = FontWeight.Bold), color = colors.error)
            Spacer(Modifier.width(8.dp))
            ChuText(
                prompt.title,
                style = type.labelSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ChuText(prompt.kind, style = type.labelSmall, color = colors.textMuted)
        }
        if (prompt.detail.isNotEmpty()) {
            BasicText(
                text = prompt.detail.joinToString("\n"),
                style = type.labelSmall.copy(color = colors.textSecondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .background(colors.surfaceVariant, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (prompt.question.isNotBlank()) {
            ChuText(
                prompt.question,
                style = type.body.copy(fontSize = textSize, lineHeight = TextUnit.Unspecified),
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            prompt.options.forEach { opt ->
                BlockedOptionRow(opt, sent = answered == opt.n, enabled = !locked, onClick = { onAnswer(opt) })
            }
        }
        val foot = when {
            answered != null -> "sent $answered · waiting for the agent…"
            answering -> "sending…"
            else -> "tap = answer on the pane" + prompt.hint.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        }
        ChuText(
            foot,
            style = type.labelSmall,
            color = colors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

@Composable
private fun BlockedOptionRow(opt: BlockedOption, sent: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    // Không tô ô con trỏ terminal đang đứng (opt.selected): trên máy thật viền vàng nhìn như
    // "đã chọn" dù chưa chạm (user 21/9). Chỉ ô ĐÃ GỬI mới đổi xanh; cờ selected vẫn giữ
    // trong model vì qsrv cần nó để điều hướng ←/→ ↑/↓.
    val borderColor = if (sent) colors.success else colors.border
    val desc = opt.desc.ifBlank { if (opt.opensComposer) "type in the box below" else "" }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (sent) colors.success.copy(alpha = 0.10f) else colors.surfaceVariant, BoxShape)
            .then(if (opt.opensComposer && !sent) Modifier.dashedBorder(borderColor) else Modifier.border(1.dp, borderColor, BoxShape))
            .noRippleClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ChuText(
            "${opt.n}",
            style = type.label.copy(fontWeight = FontWeight.Bold),
            color = if (sent) colors.success else colors.accent,
            modifier = Modifier.width(18.dp),
        )
        BasicText(
            text = buildAnnotatedString {
                append(opt.label)
                if (desc.isNotBlank()) {
                    withStyle(SpanStyle(color = colors.textMuted, fontSize = type.labelSmall.fontSize)) { append("  — $desc") }
                }
            },
            style = type.body.copy(color = colors.textPrimary),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Viền đứt 1dp bo 4dp (lựa chọn "gõ tiếp") — Compose không có border kiểu dashed sẵn. */
private fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(4.dp.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
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
    // ② + tint (user chốt 18/9): hộp TÔ MÀU không viền — accent 12%, lề phải, bo 4dp,
    // không tem tên (tin của anh luôn là của anh vì ở bên phải), giờ góc dưới phải.
    TintBox(fillColor = colors.accent.copy(alpha = 0.12f), fraction = 0.88f, alignEnd = true) {
        LinkifiedText(m.text, style = bodyStyle, color = colors.textPrimary, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            TimeStamp(m.ts)
        }
    }
}

@Composable
private fun AssistantRow(m: ChatMessage, textSize: TextUnit, tone: ChatTone?, bubbleColor: androidx.compose.ui.graphics.Color) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    // Tem "● ASSISTANT · giờ" nằm TRÊN khối (hết viền nên hết cắn viền); thân =
    // 10% màu agent, bo 4dp — cùng công thức với tin của anh, khác màu/ bên.
    Column(Modifier.fillMaxWidth(0.94f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 3.dp),
        ) {
            ChuText("●", style = type.labelSmall, color = bubbleColor)
            Spacer(Modifier.width(5.dp))
            ChuText(
                "ASSISTANT",
                style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.textSecondary,
            )
            Spacer(Modifier.width(6.dp))
            TimeStamp(m.ts, color = tone?.meta ?: colors.textMuted)
        }
        TintBox(fillColor = bubbleColor.copy(alpha = 0.10f)) {
            ParasFold(m.key, m.paras, textSize, tone)
            MiniMarkdownText(m.text, fontSize = textSize, tone = tone)
        }
    }
}

/**
 * Khối tô màu KHÔNG viền (kiểu 2 + tint, duyệt 18/9): nền là màu nhận diện
 * (agent = 10% màu của nó, anh = 12% accent), bo 4dp, tem nằm ngoài trên đầu.
 * Dùng chung chat + timeline.
 */
@Composable
internal fun TintBox(
    fillColor: androidx.compose.ui.graphics.Color,
    fraction: Float = 1f,
    alignEnd: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .fillMaxWidth(fraction)
                .background(fillColor, BoxShape)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            content = content,
        )
    }
}

/** Bo góc khối tint — nhỉnh hơn ô nhập (2dp) một chút, vẫn cùng họ app. */
internal val BoxShape = RoundedCornerShape(4.dp)

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
        modifier = Modifier.noRippleClickable { open = !open }.padding(vertical = 2.dp),
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
            .padding(start = 26.dp)
            .then(if (open) Modifier.border(1.dp, colors.border).background(colors.surfaceVariant).padding(6.dp) else Modifier)
            .noRippleClickable(enabled = canOpen) { open = !open },
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

/** epoch giây → "12:04" theo giờ máy (vạch giờ của DÒNG THỜI GIAN). */
internal fun epochClock(sec: Long): String =
    SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(sec * 1000L))

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
