package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.noRippleClickable
import com.jossephus.chuchu.ui.theme.AgentKind
import com.jossephus.chuchu.ui.theme.CHU_HAIRLINE_ALPHA
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import com.jossephus.chuchu.ui.theme.sessionColor

/**
 * Dải chuyển agent NGAY TRONG CHAT (28/9): đang đọc một phiên vẫn nhảy sang
 * phiên khác mà không cần back ra HỘI THOẠI. Cảm hứng từ tab browser.
 *
 * Màn hẹp: hàng ngang cuộn được, đặt ngay TRÊN dải machine (dưới vùng chat,
 * trên machine + composer). Màn rộng (máy gập mở, ≥ 600dp): cùng dải đó xoay
 * dọc TRÁI full-height, kiểu KohiSideRail. Tab active gạch màu session
 * (dưới khi ngang, trái khi dọc) + nền nhẹ; chấm trạng thái và chấm "tin mới"
 * cùng luật với hàng HỘI THOẠI nên mắt không phải học lại.
 */
internal enum class AgentRailOrientation { Horizontal, Vertical }

/** Rộng mỗi tab ngang — CỐ ĐỊNH cho mọi phiên (28/9, user: không show hết tên,
 *  tab nào cũng bằng nhau kiểu browser tab); tên dài tự cắt … . 29/9: 112 → 140
 *  cho thấy thêm tên. */
private val RAIL_TAB_WIDTH = 140.dp

/** Alpha tên phiên chưa mở — vẫn nhận ra màu session nhưng biết không phải tab hiện tại. */
private const val RAIL_IDLE_ALPHA = 0.45f

@Composable
internal fun QueueAgentRail(
    agents: List<QueueAgent>,
    currentPane: String?,
    chatSeen: Map<String, String>,
    onSwitch: (String) -> Unit,
    orientation: AgentRailOrientation,
    modifier: Modifier = Modifier,
) {
    if (agents.isEmpty()) return
    val listState = rememberLazyListState()
    // Animation đưa tab đang mở về MÉP TRÁI (dệt theo prototype đã duyệt 29/9):
    // mượt thay vì giật như scrollToItem cũ. Chạy khi đổi tab HOẶC khi tab đổi vị
    // trí trong list (reorder) — key gồm cả thứ tự panes. Tab đang mở mà đã hiện
    // rõ thì animate cũng chỉ trượt nhẹ (hoặc đứng yên nếu đã ở mép).
    val orderKey = agents.joinToString { it.pane }
    LaunchedEffect(currentPane, orderKey) {
        val idx = agents.indexOfFirst { it.pane == currentPane }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }
    when (orientation) {
        AgentRailOrientation.Horizontal -> LazyRow(
            modifier = modifier.fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(agents, key = { _, a -> a.pane }) { i, agent ->
                if (i > 0) RailVDivider()
                AgentRailTab(
                    agent = agent,
                    active = agent.pane == currentPane,
                    hasNew = agent.chatRev != null && agent.chatRev != chatSeen[agent.pane],
                    onClick = { if (agent.pane != currentPane) onSwitch(agent.pane) },
                )
            }
        }
        AgentRailOrientation.Vertical -> LazyColumn(
            modifier = modifier,
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            itemsIndexed(agents, key = { _, a -> a.pane }) { i, agent ->
                if (i > 0) RailHDivider()
                AgentRailSideItem(
                    agent = agent,
                    active = agent.pane == currentPane,
                    hasNew = agent.chatRev != null && agent.chatRev != chatSeen[agent.pane],
                    onClick = { if (agent.pane != currentPane) onSwitch(agent.pane) },
                )
            }
        }
    }
}

/** Vạch đứng phân cách 2 tab ngang (28/9, user) — hairline như mọi nét kẻ app. */
@Composable
private fun RailVDivider() {
    val colors = ChuColors.current
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(18.dp)
            .background(colors.border.copy(alpha = CHU_HAIRLINE_ALPHA)),
    )
}

/** Vạch ngang phân cách 2 hàng dọc. */
@Composable
private fun RailHDivider() {
    val colors = ChuColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(1.dp)
            .background(colors.border.copy(alpha = CHU_HAIRLINE_ALPHA)),
    )
}

/** Một tab ngang kiểu browser: chấm trạng thái + tên phiên + chấm tin mới. */
@Composable
private fun AgentRailTab(
    agent: QueueAgent,
    active: Boolean,
    hasNew: Boolean,
    onClick: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val kColor = AgentKind.of(agent.agent).sessionColor(agent.name)
    Row(
        modifier = Modifier
            .width(RAIL_TAB_WIDTH)
            .noRippleClickable(onClick = onClick)
            .background(if (active) colors.surfaceVariant else colors.background)
            // Gạch chân màu session cho tab đang mở — tab kia trong suốt giữ chỗ.
            .drawBehind {
                if (active) {
                    val stroke = 2.dp.toPx()
                    drawLine(kColor, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
                }
            }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuText(
            runtimeDot(agent),
            style = type.labelSmall,
            color = sessionStatusColor(agent),
        )
        Spacer(Modifier.width(6.dp))
        ChuText(
            agent.name,
            style = type.label,
            // 30/9 (user): tên phiên luôn mang màu session — chưa mở thì nhạt
            // (alpha), đang mở mới đúng màu.
            color = if (active) kColor else kColor.copy(alpha = RAIL_IDLE_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (hasNew) {
            Spacer(Modifier.width(5.dp))
            ChuText("●", style = type.labelSmall.copy(fontSize = 7.5.sp), color = colors.accent)
        }
    }
}

/** Một hàng dọc trái: chấm + tên (cắt …) + chấm tin mới; active vạch trái. */
@Composable
private fun AgentRailSideItem(
    agent: QueueAgent,
    active: Boolean,
    hasNew: Boolean,
    onClick: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val kColor = AgentKind.of(agent.agent).sessionColor(agent.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick)
            .background(if (active) colors.surfaceVariant else colors.background)
            .drawBehind {
                if (active) {
                    val stroke = 2.dp.toPx()
                    drawLine(kColor, Offset(stroke / 2, 0f), Offset(stroke / 2, size.height), stroke)
                }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuText(
            runtimeDot(agent),
            style = type.labelSmall,
            color = sessionStatusColor(agent),
        )
        Spacer(Modifier.width(7.dp))
        ChuText(
            agent.name,
            style = type.label,
            // 30/9 (user): như tab ngang — nhạt khi chưa mở, đúng màu khi mở.
            color = if (active) kColor else kColor.copy(alpha = RAIL_IDLE_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (hasNew) {
            ChuText("●", style = type.labelSmall.copy(fontSize = 7.5.sp), color = colors.accent)
        }
    }
}
