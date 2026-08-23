package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.components.KohiSelectableRow
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

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

@Composable
internal fun QueueAgentRoster(
    agents: List<QueueAgent>,
    tasks: List<QueueTask>,
    selectedPane: String,
    onSelect: (String) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current

    Column(modifier = Modifier.fillMaxWidth()) {
        KohiSectionBand(label = "AGENTS", meta = "${agents.size} LIVE") {
            val spreadMode = selectedPane == ALL_AGENTS
            ChuButton(
                onClick = {
                    if (spreadMode) agents.firstOrNull()?.pane?.let(onSelect)
                    else onSelect(ALL_AGENTS)
                },
                enabled = agents.isNotEmpty(),
                variant = ChuButtonVariant.Ghost,
                bracketed = true,
                borderColor = if (spreadMode) colors.accent else colors.border,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                minHeight = 26.dp,
            ) {
                // Ten nut noi ra HANH DONG se lam (focus mot agent / tra ve all),
                // mau accent khi dang o che do lan tay de che do nay khong bi lan
                // voi trang thai binh thuong.
                ChuText(
                    if (spreadMode) "FOCUS" else "ALL",
                    style = type.labelSmall,
                    color = when {
                        agents.isEmpty() -> colors.textMuted
                        spreadMode -> colors.accent
                        else -> colors.textSecondary
                    },
                )
            }
        }
        if (agents.isEmpty()) {
            ChuText(
                "  NO AGENTS FOUND · CHECK HERDR/QSRV",
                style = type.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 232.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(agents, key = QueueAgent::pane) { agent ->
                    val selected = selectedPane == agent.pane
                    val taskCount = tasks.count { it.target == agent.pane && !it.isCompleted }
                    val working = runtimeDot(agent) == "●"
                    KohiSelectableRow(
                        selected = selected,
                        tone = colors.accent,      // rail + border theo ACCENT, khong theo tone
                        onClick = { onSelect(agent.pane) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        // Slot con tro '>' co dinh: selection nhan mat ngay ca khi
                        // qua mau sac khong doc duoc.
                        ChuText(
                            if (selected) ">" else "",
                            style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.accent,
                            modifier = Modifier.width(11.dp),
                        )
                        ChuText(
                            runtimeDot(agent),
                            style = type.labelSmall,
                            color = if (working) colors.success else agent.tone.color(),
                        )
                        Spacer(Modifier.width(6.dp))
                        ChuText(
                            agent.name,
                            style = type.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                            color = if (selected) colors.textPrimary else colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        // Dot ●○ da noi ro trang thai — tu trang thai dac biet
                        // (▲ blocked, ? unknown) moi can chu di kem.
                        if (runtimeDot(agent) !in listOf("●", "○")) {
                            ChuText(
                                agent.label.uppercase(),
                                style = type.labelSmall,
                                color = colors.textMuted,
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        // Chi dem task KHI co viec; khong thay bang dau '.' de ruot
                        // phai khong con dau trang tri du thua.
                        ChuText(
                            if (taskCount > 0) taskCount.toString() else "",
                            style = type.labelSmall,
                            color = colors.accent,
                            modifier = Modifier.width(14.dp),
                        )
                    }
                }
            }
        }
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
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // Con tro '>' dong nhat voi roster: task dang mo detail pane.
        ChuText(
            if (selected) ">" else "",
            style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.accent,
            modifier = Modifier.width(11.dp),
        )
        ChuText(task.glyph, style = type.labelSmall, color = task.tone.color())
        Spacer(Modifier.width(6.dp))
        ChuText(
            "#${task.id}",
            style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
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
                modifier = Modifier.weight(0.28f, fill = false),
            )
        }
        Spacer(Modifier.width(6.dp))
        ChuText(
            task.text.replace('\n', ' '),
            style = type.bodySmall.copy(
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = if (task.isCompleted) colors.textMuted else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QueueTaskDetailPane(
    task: QueueTask,
    busyOps: Set<String>,
    onInspect: () -> Unit,
    onCopy: () -> Unit,
    onAction: (QueueAction) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val busy = task.actions.any { it.operationKey(task.id) in busyOps }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 176.dp)
            .background(colors.surface),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(task.tone.color()),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText(
                    "#${task.id} · ${task.stateLabel.uppercase()}",
                    style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = task.tone.color(),
                )
                if (task.sub.isNotBlank()) {
                    ChuText(
                        task.sub,
                        style = type.labelSmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            ChuText(
                task.text,
                style = type.bodySmall,
                color = colors.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                KohiCompactAction(
                    label = if (task.hasResp || task.isCompleted) "VIEW RESPONSE" else "VIEW PROMPT",
                    onClick = onInspect,
                )
                KohiCompactAction(label = "COPY", onClick = onCopy)
                task.actions.forEach { action ->
                    val actionBusy = action.operationKey(task.id) in busyOps
                    KohiCompactAction(
                        label = if (actionBusy) "WAIT" else action.label.uppercase(),
                        enabled = !busy,
                        danger = action.danger,
                        onClick = { onAction(action) },
                    )
                }
            }
        }
    }
}

/**
 * Empty state HUU ICH thay cho mot dong "QUEUE IS EMPTY" giua khoang trong:
 * hien inspector cua agent dang chon (status, so viec, hoat dong gan nhat) va
 * huong dan buoc tiep theo. Van giu nguyen pha terminal: key-value monospace.
 */
@Composable
internal fun EmptyQueueInspector(
    agent: QueueAgent?,
    scopeLabel: String,
    tasks: List<QueueTask>,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val pending = tasks.count { !it.isCompleted }
    val done = tasks.count { it.isCompleted }
    val recent = tasks.lastOrNull { it.isCompleted }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (agent != null) {
                ChuText(runtimeDot(agent), style = type.label, color = agent.tone.color())
                Spacer(Modifier.width(7.dp))
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
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val canSend = agent != null && value.isNotBlank() && !sending

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
                .defaultMinSize(minHeight = 40.dp)
                .background(colors.surfaceVariant)
                .border(1.dp, if (canSend) colors.accent.copy(alpha = 0.45f) else colors.border)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = if (value.contains('\n')) Alignment.Top else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            agent?.let {
                ChuText(
                    "@${it.name}",
                    style = type.labelSmall,
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
                modifier = Modifier.weight(1f)
                    .padding(vertical = 8.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            ChuText(
                                agent?.let { "Describe the task…" } ?: "Pick an agent first…",
                                style = type.body,
                                color = colors.disabledText,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.width(6.dp))
        // Send la hanh dong van ban trong terminal, KHONG phai block rieng;
        // disabled thi moi mo di chu khong bien thanh nut "co ve bi liet".
        ChuButton(
            onClick = onSend,
            enabled = canSend,
            variant = ChuButtonVariant.Ghost,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            minHeight = 26.dp,
        ) {
            ChuText(
                if (sending) "[…]" else "[SEND]",
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
