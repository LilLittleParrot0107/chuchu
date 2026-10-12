package com.jossephus.chuchu.ui.bubble

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.noRippleClickable
import com.jossephus.chuchu.ui.screens.Dbtop.DbtopViewModel
import com.jossephus.chuchu.ui.screens.Dbtop.WatchlistMergedPane
import com.jossephus.chuchu.ui.screens.Dbtop.buildWatchlist
import com.jossephus.chuchu.ui.screens.Dbtop.formatMoney
import com.jossephus.chuchu.ui.screens.Queue.AgentRailOrientation
import com.jossephus.chuchu.ui.screens.Queue.ChatThreadView
import com.jossephus.chuchu.ui.screens.Queue.QueueAgentRail
import com.jossephus.chuchu.ui.screens.Queue.QueueComposer
import com.jossephus.chuchu.ui.screens.Queue.QueueViewModel
import com.jossephus.chuchu.ui.screens.Queue.collapseAssistantTurns
import com.jossephus.chuchu.ui.theme.AgentKind
import com.jossephus.chuchu.ui.theme.CHU_HAIRLINE_ALPHA
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import com.jossephus.chuchu.ui.theme.sessionColor
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Bubble shell (Android 17 app-bubbles / cửa sổ đa nhiệm thấp) — bản gọn của app:
 * màn CHAT (band + transcript + rail phiên + composer) và màn MONEY (thu nhập +
 * WATCH gộp token/gainers). Không phải chế độ màn mới của Queue/Dashboard: chỉ là
 * composition riêng, tái dùng NGUYÊN các mảnh có sẵn (ChatThreadView, QueueAgentRail,
 * QueueComposer, WatchlistMergedPane) nên hành vi chat/blocked/WATCH không lệch bản đầy đủ.
 *
 * Điều kiện vào bubble: cửa sổ thấp (xem [bubbleWindowEligible]) — Android 17 mở app
 * thành bubble là rơi vào đây; split-screen hẹp cũng vậy (chấp nhận, cùng một trải nghiệm).
 * ⤢ trong band = thoát chế độ gọn, quay lại UI đầy đủ (nút ⤡ nổi ở góc để quay về).
 */
@Composable
fun BubbleShell(
    queueVm: QueueViewModel,
    onExpand: () -> Unit,
) {
    val colors = ChuColors.current
    val ui by queueVm.ui.collectAsStateWithLifecycle()
    val chat by queueVm.chat.collectAsStateWithLifecycle()
    val chatSeen by queueVm.chatSeen.collectAsStateWithLifecycle()
    var segment by rememberSaveable { mutableStateOf(SEG_CHAT) }

    val chatAgent = ui.state.agents.firstOrNull { it.pane == chat.pane }
    // Badge trên tab CHAT: số phiên có tin mới (cùng luật chấm "tin mới" của rail/roster).
    val needsYou = ui.state.agents.count { it.chatRev != null && it.chatRev != chatSeen[it.pane] }
    val title = chatAgent?.name?.uppercase(Locale.US)?.take(16) ?: "KOHI"
    val titleColor = chatAgent
        ?.let { AgentKind.of(it.agent).sessionColor(it.name) }
        ?: colors.textPrimary

    // Chat chỉ chạy khi "queue đang hiện" + online — cùng hợp đồng với màn Queue đầy đủ.
    LifecycleResumeEffect(queueVm) {
        queueVm.setQueueVisible(true)
        onPauseOrDispose { queueVm.setQueueVisible(false) }
    }
    DisposableEffect(queueVm) {
        queueVm.setOnline(true)
        onDispose {}
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            // Bubble thật thường không có system bar (insets = 0); split-screen thì có —
            // thiếu cái này là band chui dưới status bar / composer dưới thanh gesture.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        BubbleBand(
            title = title,
            titleColor = titleColor,
            segment = segment,
            needsYou = needsYou,
            onSegment = { segment = it },
            onExpand = onExpand,
        )
        when (segment) {
            SEG_MONEY -> BubbleMoneyPane(Modifier.fillMaxWidth().weight(1f))
            else -> BubbleChatPane(queueVm, chatSeen, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/** Cửa sổ có được vào chế độ bubble không. Tách khỏi Compose để unit-test được. */
internal fun bubbleWindowEligible(screenHeightDp: Int, inMultiWindow: Boolean): Boolean =
    (inMultiWindow && screenHeightDp in 1..639) || screenHeightDp in 1..479

private const val SEG_CHAT = "chat"
private const val SEG_MONEY = "money"

@Composable
private fun BubbleBand(
    title: String,
    titleColor: Color,
    segment: String,
    needsYou: Int,
    onSegment: (String) -> Unit,
    onExpand: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Nhóm trái co giãn: cửa sổ siêu hẹp (bubble ~267dp) title cắt ellipsis,
        // segmented + ⤢ LUÔN giữ chỗ — trước đây title cứng 16 ký tự đẩy 2 thứ đó ra ngoài mép.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            ChuText("●", style = type.labelSmall, color = titleColor)
            Spacer(Modifier.width(5.dp))
            ChuText(
                title,
                style = type.label.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.5.sp),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(5.dp))
            ChuText(
                "· ${if (segment == SEG_MONEY) "MONEY" else "CHAT"}",
                style = type.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(6.dp))
        BubbleSegment(segment, needsYou, onSegment)
        Spacer(Modifier.width(6.dp))
        KohiCompactAction(label = "⤢", onClick = onExpand)
    }
}

/** Segmented CHAT|MONEY — S1 đã chốt: 1 chạm, không cử chỉ ẩn. */
@Composable
private fun BubbleSegment(segment: String, needsYou: Int, onSelect: (String) -> Unit) {
    val colors = ChuColors.current
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .background(colors.surfaceVariant, shape)
            .border(1.dp, colors.border.copy(alpha = 0.7f), shape),
    ) {
        SegmentCell("CHAT", segment == SEG_CHAT, needsYou.takeIf { it > 0 }) { onSelect(SEG_CHAT) }
        SegmentCell("MONEY", segment == SEG_MONEY, null) { onSelect(SEG_MONEY) }
    }
}

@Composable
private fun SegmentCell(label: String, active: Boolean, badge: Int?, onClick: () -> Unit) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(
        modifier = Modifier
            .noRippleClickable(onClick = onClick)
            .background(if (active) colors.surface else colors.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuText(
            label,
            style = type.labelSmall.copy(letterSpacing = 0.6.sp),
            color = if (active) colors.accent else colors.textMuted,
            maxLines = 1,
        )
        if (badge != null) {
            Spacer(Modifier.width(5.dp))
            Box(
                Modifier
                    .background(colors.error, CircleShape)
                    .padding(horizontal = 4.dp),
            ) {
                ChuText(
                    badge.toString(),
                    style = type.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                    color = colors.onAccent,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BubbleChatPane(
    vm: QueueViewModel,
    chatSeen: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val fontSize by vm.terminalFontSize.collectAsStateWithLifecycle()
    val agents = ui.state.agents
    val chatAgent = agents.firstOrNull { it.pane == chat.pane }
    val chatMessages = remember(chat.messages) { collapseAssistantTurns(chat.messages) }
    val listState = rememberLazyListState()
    var prompt by rememberSaveable { mutableStateOf("") }
    val composerFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Bubble mở vào thẳng một phiên: ưu tiên phiên CÓ tin mới, không thì phiên đầu.
    LaunchedEffect(chat.pane, agents.size) {
        if (chat.pane == null && agents.isNotEmpty()) {
            val busy = agents.firstOrNull { it.chatRev != null && it.chatRev != chatSeen[it.pane] }
            vm.openChat((busy ?: agents.first()).pane)
        }
    }

    Column(modifier) {
        if (chat.pane != null) {
            ChatThreadView(
                chat = chat,
                chatAgent = chatAgent,
                chatMessages = chatMessages,
                chatFontSizeSp = fontSize,
                chatListState = listState,
                onLoadOlder = vm::loadOlderChat,
                onAnswerBlocked = vm::answerBlocked,
                onSubmitBlocked = vm::answerBlockedMulti,
                composerFocus = composerFocus,
                keyboard = keyboard,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            QueueAgentRail(
                agents = agents,
                currentPane = chat.pane,
                chatSeen = chatSeen,
                onSwitch = { p -> vm.openChat(p) },
                orientation = AgentRailOrientation.Horizontal,
                modifier = Modifier.fillMaxWidth(),
            )
            QueueComposer(
                value = prompt,
                onValueChange = { prompt = it },
                agent = chatAgent,
                sending = chat.sending,
                onSend = {
                    val text = prompt.trim()
                    if (text.isNotEmpty()) {
                        vm.sendChat(text)
                        prompt = ""
                    }
                },
                placeholder = "Reply to ${chatAgent?.name ?: "agent"}…",
                focusRequester = composerFocus,
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ChuText("connecting…", style = ChuTypography.current.labelSmall, color = colors.textMuted)
            }
        }
    }
}

/**
 * MONEY — M3 (thu nhập) + WATCH gộp như tab WATCH thật: token của mình trước, dải
 * "GAINERS", rồi gainer, cuộn liền (WatchlistMergedPane). Số hôm nay = mốc daily mới
 * nhất của đúng ngày UTC (debank chốt ngày theo UTC), không bịa "hôm qua là hôm nay".
 */
@Composable
private fun BubbleMoneyPane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: DbtopViewModel = viewModel(factory = DbtopViewModel.factory(context.applicationContext as Application))
    val ui by vm.ui.collectAsStateWithLifecycle()
    val colors = ChuColors.current
    val type = ChuTypography.current
    val vnd = ui.spending?.usdVnd ?: 0.0
    val perDay = ui.currentPerDay(ui.nowSec)
    val state = ui.state
    val items = remember(state, ui.spending) { state.buildWatchlist(ui.spending?.px24 ?: emptyMap()) }
    val lastDay = state.daily.lastOrNull()
    val utcToday = remember(ui.nowSec) { LocalDate.now(ZoneOffset.UTC).toString() }
    val today = lastDay?.takeIf { it.date == utcToday }

    Column(modifier.padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(6.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ChuText(
                perDay?.let { (if (it >= 0) "+" else "") + formatMoney(it, ui.moneyDisplay, vnd) } ?: "—",
                style = type.label.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                ),
                color = if ((perDay ?: 0.0) >= 0) colors.success else colors.error,
                maxLines = 1,
            )
            ChuText(
                "MỖI NGÀY · ước theo vị thế đang gửi",
                style = type.labelSmall,
                color = colors.textMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Hairline()
        val dayPoint = today ?: lastDay
        val dayLabel = when {
            today != null -> "HÔM NAY (đo ${(today.coverageDays * 24).roundToInt()}h)"
            lastDay != null -> "NGÀY ${lastDay.date.substring(5)} (đo ${(lastDay.coverageDays * 24).roundToInt()}h)"
            else -> "HÔM NAY"
        }
        MiniRow(
            dayLabel,
            dayPoint?.let { (if (it.yieldUsd >= 0) "+" else "") + formatMoney(it.yieldUsd, ui.moneyDisplay, vnd) } ?: "—",
            colors.success,
        )
        state.mtd?.let { mtd ->
            MiniRow(
                "THÁNG ${mtd.month.takeLast(2)} · đã kiếm",
                (if (mtd.usd >= 0) "+" else "") + formatMoney(mtd.usd, ui.moneyDisplay, vnd),
                colors.success,
            )
        }
        MiniRow("NET WORTH", formatMoney(state.netWorth, ui.moneyDisplay, vnd), colors.accent)
        Spacer(Modifier.height(4.dp))
        Hairline()
        Spacer(Modifier.height(6.dp))
        ChuText(
            "WATCH · của anh + GAINERS · cuộn liền",
            style = type.labelSmall.copy(letterSpacing = 0.6.sp),
            color = colors.textMuted,
        )
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            WatchlistMergedPane(
                items = items,
                explorer = ui.explorer,
                moneyDisplay = ui.moneyDisplay,
                vndRate = vnd,
            )
        }
    }
}

@Composable
private fun MiniRow(label: String, value: String, valueColor: Color) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuText(label, style = type.labelSmall, color = colors.textMuted, maxLines = 1)
        ChuText(
            value,
            style = type.label.copy(
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum",
            ),
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ChuColors.current.border.copy(alpha = CHU_HAIRLINE_ALPHA)),
    )
}
