package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.noRippleClickable
import com.jossephus.chuchu.ui.components.KohiSelectableRow
import com.jossephus.chuchu.ui.components.LinkifiedText
import com.jossephus.chuchu.ui.components.MiniMarkdownText
import com.jossephus.chuchu.ui.theme.AgentKind
import com.jossephus.chuchu.ui.theme.CHU_HAIRLINE_ALPHA
import com.jossephus.chuchu.ui.theme.ChatTone
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import com.jossephus.chuchu.ui.theme.chatTone
import com.jossephus.chuchu.ui.theme.sessionColor

internal const val ALL_AGENTS = "ALL"

/**
 * Preview HỘI THOẠI là một dòng chữ trơn: lột `**` và backtick cho đọc trôi (học
 * từ prototype sleek terminal, user 17/9). Bản đầy đủ có markdown đã nằm trong
 * thread khi mở — hàng danh sách chỉ cần nội dung, không cần cú pháp.
 */
internal fun stripPreviewMarkdown(text: String): String =
    text.replace("**", "").replace("`", "").replace('\n', ' ').trim()

/**
 * Giới hạn hiển thị tin trên DÒNG THỜI GIAN (user đòi 18/9): tin dài chỉ hiện
 * tối đa ~260 ký tự hoặc 5 dòng, có nút "· xem thêm ▾" / "▴ thu gọn".
 * TUYỆT ĐỐI KHÔNG bung 100% hay xoá bỏ [ParasFold] — chỉ thu gọn tin nhắn cuối
 * (m.text), các tin/đoạn trước (m.paras) vẫn giữ gập độc lập.
 */
internal const val FEED_MAX_CHARS = 260
internal const val FEED_MAX_LINES = 5

internal fun shouldCollapseFeed(
    text: String,
    maxChars: Int = FEED_MAX_CHARS,
    maxLines: Int = FEED_MAX_LINES,
): Boolean {
    if (text.length > maxChars) return true
    var lines = 1
    for (i in text.indices) {
        if (text[i] == '\n') {
            lines++
            if (lines > maxLines) return true
        }
    }
    return false
}

internal fun truncateFeedText(
    text: String,
    maxChars: Int = FEED_MAX_CHARS,
    maxLines: Int = FEED_MAX_LINES,
): String {
    if (!shouldCollapseFeed(text, maxChars, maxLines)) return text
    val rawLines = text.lines()
    val limitedByLines = if (rawLines.size > maxLines) {
        rawLines.take(maxLines).joinToString("\n")
    } else {
        text
    }
    val cut = if (limitedByLines.length > maxChars) {
        val sub = limitedByLines.substring(0, maxChars)
        val lastWs = sub.lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
        if (lastWs >= maxChars - 40) {
            sub.substring(0, lastWs)
        } else {
            sub
        }
    } else {
        limitedByLines
    }
    return cut.trimEnd().trimEnd('.', ',', ';', ':', '!', '?', '-', '`', '*') + "…"
}

/**
 * Dot chi the hien RUNTIME STATUS cua agent — tuyet doi khong dung de bieu thi
 * selection (selection = background + border + cursor '>' ben trai). Truoc day
 * glyph server ('●' cho working, '·' cho idle) lam agent dang chay nhin giong
 * dang duoc chon.
 */
private fun runtimeDot(agent: QueueAgent): String = when (agent.state) {
    // Kẹt/chờ duyệt = chấm ĐỎ như sidebar herdr (user chốt 21/9), không còn tam giác vàng.
    AgentState.Working, AgentState.Blocked -> "●"
    AgentState.Idle, AgentState.Done -> "○"
    else -> agent.glyph.ifBlank { "?" }   // unsure '?' giữ nguyên
}

/** Hai chế độ của màn Queue (user chốt G1, 16/9): đọc dòng thời gian ↔ quản hội thoại. */
internal enum class QueueMode { Timeline, Threads }

@Composable
internal fun QueueModeSwitch(
    mode: QueueMode,
    threadsCount: Int,
    onSelect: (QueueMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Hairline chân hàng tab như prototype: phân tách bằng nét mảnh,
            // không dựng lại hộp viền kín.
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    colors.border.copy(alpha = CHU_HAIRLINE_ALPHA),
                    Offset(0f, size.height - stroke / 2),
                    Offset(size.width, size.height - stroke / 2),
                    stroke,
                )
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        QueueModeTab(
            label = "TIMELINE",
            meta = null,
            active = mode == QueueMode.Timeline,
            onClick = { onSelect(QueueMode.Timeline) },
        )
        QueueModeTab(
            label = "CONVERSATIONS",
            meta = threadsCount.toString(),
            active = mode == QueueMode.Threads,
            onClick = { onSelect(QueueMode.Threads) },
        )
    }
}

/**
 * Tab kiểu con trỏ CLI (học từ prototype queue-sleek-terminal, user 17/9): không
 * hộp viền kín, tab đang mở đánh dấu bằng '›' + accent + đậm. Tab kia vẫn giữ
 * đúng một ô cho '›' (vẽ trong suốt) để đổi chế độ không nhảy ngang chữ.
 */
@Composable
private fun QueueModeTab(
    label: String,
    meta: String?,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(
        modifier = Modifier.noRippleClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tab to hơn labelSmall (user chốt 17/9: "tăng kích thước timeline vs
        // conversations") — 13sp, vẫn giữ dáng con trỏ CLI.
        ChuText("›", style = type.label, color = if (active) colors.accent else Color.Transparent)
        Spacer(Modifier.width(4.dp))
        ChuText(
            label,
            style = type.label.copy(
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.6.sp,
            ),
            color = if (active) colors.accent else colors.textMuted,
        )
        if (meta != null) {
            Spacer(Modifier.width(5.dp))
            ChuText(
                meta,
                style = type.label,
                color = if (active) colors.accent else colors.textMuted.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * Chấm trạng thái một session (user chốt 17/9: "để cho t mấy chấm show status thanh lịch"):
 * đỏ/vàng khi CẦN ANH, accent khi đang chạy, xanh khi vừa xong, xám khi rảnh — thay hẳn
 * các tag chữ "new"/"N waiting" rẻ tiền.
 */
@Composable
private fun sessionStatusColor(agent: QueueAgent): Color {
    val colors = ChuColors.current
    return when (agent.state) {
        AgentState.Blocked -> colors.error      // đỏ, khớp herdr
        AgentState.Working -> colors.accent
        else -> agent.tone.color()
    }
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
 * DÒNG THỜI GIAN kiểu T2 (user chốt 22/9, prototype kohi-timeline-t2-shades): chia ô 30 phút có
 * vạch giờ mờ; trong ô, tin của cùng phiên gom một KHỐI có thanh màu trái và tem một lần (tên ·
 * pha · giờ); tin của anh là bọt phải nền accent, tin agent bọt trái nền màu PHIÊN (họ màu loại
 * agent lệch theo tên — [sessionColor]); đoạn assistant liên tiếp đã gộp ở [collapseFeedTurns].
 * Vạch MỚI ngăn phần chưa xem từ lần rời màn trước ([FeedUiState.sinceTs]); phiên đang kẹt ghim
 * khối ở đáy, không trôi theo ô giờ. Chạm tin = nhắm phiên cho ô gõ (giữ luật 17/9). Vị trí cuộn
 * do QueueScreen giữ (rememberSaveable) — đổi mode/hội thoại quay lại vẫn y chỗ.
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
    val messages = remember(feed.messages) { collapseFeedTurns(feed.messages) }
    val items = remember(messages, feed.sinceTs) { feedTimelineItems(messages, feed.sinceTs) }
    val keys = remember(items) { items.map { it.key } }
    val pinnedBlocks = remember(messages) { blockedBlocks(messages) }
    val curKeys by rememberUpdatedState(keys)
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
                // Neo = KEY mục đầu đang thấy: feed cắt tin ở đầu nên index trôi, key ổn định.
                val first = info.visibleItemsInfo.firstOrNull()?.index ?: -1
                if (first >= 0) onAnchorChange(curKeys.getOrNull(first))
            }
        }
    }
    // Vào lần đầu / quay lại: dừng đúng chỗ đã thấy; neo rơi khỏi cửa sổ feed
    // (hoặc đang ở đáy) thì về điểm mới nhất (user chốt 17/9).
    LaunchedEffect(items.isNotEmpty(), pinned, anchorKey) {
        if (restored || items.isEmpty()) return@LaunchedEffect
        restored = true
        listState.scrollToItem(feedRestoreIndex(keys, pinned, anchorKey))
    }
    LaunchedEffect(messages.lastOrNull()?.key, messages.lastOrNull()?.ts, items.size) {
        if (items.isNotEmpty() && pinned) listState.scrollToItem(items.size)
    }
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                messages.isEmpty() && feed.loading -> CenterNote("LOADING TIMELINE…")
                messages.isEmpty() && feed.error != null -> CenterNote("▌ ${feed.error}")
                messages.isEmpty() -> CenterNote("no messages yet — the house is quiet · switch to CONVERSATIONS to open a session")
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { it.key }) { item ->
                        when (item) {
                            is FeedItem.Hour -> FeedHourDivider(epochClock(item.startSec))
                            is FeedItem.New -> FeedNewDivider("new · since " + epochClock(item.sinceSec))
                            is FeedItem.Block -> FeedBlockView(item.block, onPick = onPick, bodySize = type.body.fontSize)
                        }
                    }
                }
            }
        }
        // Phiên đang kẹt: ghim sát ô gõ tới khi anh trả lời — không trôi lên theo ô giờ.
        pinnedBlocks.forEach { b ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 6.dp)
                    .background(colors.error.copy(alpha = 0.07f))
                    .border(1.dp, colors.error.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                FeedBlockView(b, onPick = onPick, bodySize = type.body.fontSize)
            }
        }
    }
}

@Composable
private fun FeedHourDivider(text: String) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.border.copy(alpha = 0.7f)))
        ChuText(text, style = type.labelSmall, color = colors.textMuted, modifier = Modifier.padding(horizontal = 8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(colors.border.copy(alpha = 0.7f)))
    }
}

@Composable
private fun FeedNewDivider(text: String) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.accent.copy(alpha = 0.45f)))
        ChuText(text.uppercase(), style = type.labelSmall, color = colors.accent, modifier = Modifier.padding(horizontal = 8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(colors.accent.copy(alpha = 0.45f)))
    }
}

/**
 * Một khối phiên: thanh màu phiên bên trái, tem "● tên · pha/giờ" một lần, rồi các bọt. Pha đọc
 * từ nhãn /state của phiên (kẹt = đỏ, đang làm = accent, xong = xanh kèm giờ), không đoán từ chữ.
 */
@Composable
private fun FeedBlockView(block: FeedBlock, onPick: (FeedMessage) -> Unit, bodySize: TextUnit) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val kind = AgentKind.of(block.agent)
    val tone = remember(kind) { kind.chatTone() }
    val color = kind.sessionColor(block.name)
    val state = AgentState.of(block.label)
    val last = block.messages.last()
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val w = 2.dp.toPx()
                drawLine(color, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
            }
            .padding(start = 9.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp).noRippleClickable { onPick(last) },
        ) {
            ChuText("● ", style = type.labelSmall, color = color)
            ChuText(
                block.name,
                style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            when (state) {
                AgentState.Blocked -> ChuText("● NEEDS YOU", style = type.labelSmall.copy(fontWeight = FontWeight.Bold), color = colors.error)
                AgentState.Working -> ChuText("◐ working", style = type.labelSmall, color = colors.accent)
                AgentState.Done -> ChuText("✓ " + chatClock(last.ts), style = type.labelSmall, color = colors.success)
                else -> ChuText(chatClock(last.ts), style = type.labelSmall, color = colors.textMuted)
            }
        }
        block.messages.forEachIndexed { i, m ->
            if (i > 0) Spacer(Modifier.height(4.dp))
            FeedBubble(m, color = color, tone = tone, bodySize = bodySize, onPick = onPick)
        }
    }
}

/** Bọt một tin: anh = phải nền accent 12% với ❯; agent = trái nền màu phiên 12%. Dài thì gấp "show more". */
@Composable
private fun FeedBubble(m: FeedMessage, color: androidx.compose.ui.graphics.Color, tone: ChatTone?, bodySize: TextUnit, onPick: (FeedMessage) -> Unit) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val needsCollapse = remember(m.text) { shouldCollapseFeed(m.text) }
    var expanded by remember(m.key) { mutableStateOf(false) }
    val displayText = if (needsCollapse && !expanded) truncateFeedText(m.text) else m.text
    if (m.role == "user") {
        TintBox(
            fillColor = colors.accent.copy(alpha = 0.12f),
            fraction = 0.86f,
            alignEnd = true,
            modifier = Modifier.noRippleClickable { onPick(m) },
        ) {
            LinkifiedText("❯ " + displayText, style = type.body, color = colors.textPrimary, modifier = Modifier.fillMaxWidth())
            if (needsCollapse) {
                ChuText(
                    text = if (expanded) "▴ show less" else "· show more ▾",
                    style = type.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.align(Alignment.End).noRippleClickable { expanded = !expanded }.padding(top = 4.dp, bottom = 2.dp),
                )
            }
        }
    } else {
        TintBox(fillColor = color.copy(alpha = 0.12f), fraction = 0.94f, modifier = Modifier.noRippleClickable { onPick(m) }) {
            ParasFold(m.key, m.paras, bodySize, tone)
            MiniMarkdownText(displayText, fontSize = bodySize, tone = tone)
            if (needsCollapse) {
                ChuText(
                    text = if (expanded) "▴ show less" else "· show more ▾",
                    style = type.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.noRippleClickable { expanded = !expanded }.padding(top = 4.dp, bottom = 2.dp),
                )
            }
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
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(agents, key = QueueAgent::pane) { agent ->
            val hasNew = agent.chatRev != null && agent.chatRev != chatSeen[agent.pane]
            // ③ Gutter Rail + Tint Strip (chốt 18/9): Cột gutter 22dp căn thẳng glyph;
            // thân phải là TintStrip bo 4dp (10% màu agent) đồng bộ với TintBox của Chat.
            // TUYỆT ĐỐI KHÔNG làm sáng vàng cho session đã truy cập và back ra.
            val kColor = AgentKind.of(agent.agent).sessionColor(agent.name)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable {
                        // Có transcript thì chạm là mở thread; chưa có thì chỉ chọn (ô gõ nhắm vào nó).
                        if (agent.chatRev != null) onOpenChat(agent.pane) else onSelect(agent.pane)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Rãnh Gutter 22dp: căn giữa glyph runtime (●/○/▲), mắt quét thẳng trục
                ChuText(
                    runtimeDot(agent),
                    style = type.labelSmall.copy(textAlign = TextAlign.Center),
                    color = sessionStatusColor(agent),
                    modifier = Modifier.width(22.dp),
                )
                // Thân TintStrip: Khối bo 4dp không viền, nền 10% màu agent (khớp TintBox của Chat)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(kColor.copy(alpha = 0.10f), BoxShape)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChuText(
                            agent.name,
                            style = type.label.copy(fontWeight = FontWeight.Bold),
                            color = kColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (hasNew) {
                            Spacer(Modifier.width(6.dp))
                            ChuText("●", style = type.labelSmall.copy(fontSize = 7.5.sp), color = colors.accent)
                        }
                        Spacer(Modifier.width(8.dp))
                        ChuText(
                            chatWhen(agent.previewTs),
                            style = type.labelSmall.copy(textAlign = TextAlign.End),
                            color = colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                    val preview = agent.preview.ifBlank {
                        if (agent.chatRev != null) "no messages yet" else "no transcript"
                    }
                    ChuText(
                        stripPreviewMarkdown(preview),
                        style = type.bodySmall,
                        color = colors.textPrimary.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
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
                ChuText(runtimeDot(agent), style = type.label, color = sessionStatusColor(agent))
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
        if (agent != null && agent.cwd.isNotBlank()) {
            InspectorRow("CWD", agent.cwd.replace("/home/a", "~"), colors.textSecondary)
        }
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
    /** Thẻ NEEDS YOU chọn "Type something" → xin focus ô gõ để trả lời ngay (21/9). */
    focusRequester: FocusRequester? = null,
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
            // Học từ prototype sleek terminal: composer là MỘT lớp — nền theme +
            // hairline, chỉ còn đúng một khung nhập có viền; bỏ dải surface đậm
            // từng làm "hộp trong hộp".
            .background(colors.background)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    colors.border.copy(alpha = CHU_HAIRLINE_ALPHA),
                    Offset(0f, stroke / 2),
                    Offset(size.width, stroke / 2),
                    stroke,
                )
            }
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
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
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
