package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.ChuTextField
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.components.KohiSelectableRow
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

internal const val ALL_AGENTS = "ALL"

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
            KohiCompactAction(
                label = if (selectedPane == ALL_AGENTS) "FOCUS" else "ALL",
                enabled = agents.isNotEmpty(),
                onClick = {
                    if (selectedPane == ALL_AGENTS) agents.firstOrNull()?.pane?.let(onSelect)
                    else onSelect(ALL_AGENTS)
                },
            )
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
                    .heightIn(max = 146.dp),
            ) {
                items(agents, key = QueueAgent::pane) { agent ->
                    val selected = selectedPane == agent.pane
                    val taskCount = tasks.count { it.target == agent.pane && !it.isCompleted }
                    KohiSelectableRow(
                        selected = selected,
                        tone = agent.tone.color(),
                        onClick = { onSelect(agent.pane) },
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
                    ) {
                        ChuText(agent.glyph, style = type.label, color = agent.tone.color())
                        Spacer(Modifier.width(7.dp))
                        ChuText(
                            agent.name,
                            style = type.label.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                            color = if (selected) colors.textPrimary else colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        ChuText(
                            agent.label.uppercase(),
                            style = type.labelSmall,
                            color = agent.tone.color(),
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(8.dp))
                        ChuText(
                            if (taskCount > 0) taskCount.toString() else "·",
                            style = type.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (taskCount > 0) colors.accent else colors.textMuted,
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
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp),
    ) {
        ChuText(task.glyph, style = type.labelSmall, color = task.tone.color())
        Spacer(Modifier.width(6.dp))
        ChuText(
            "#${task.id}",
            style = type.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
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
        Spacer(Modifier.width(8.dp))
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
        Spacer(Modifier.width(8.dp))
        ChuText(
            if (task.isRunning) "ACTIVE" else task.stateLabel.uppercase(),
            style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = task.tone.color(),
            maxLines = 1,
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
                .padding(horizontal = 10.dp, vertical = 7.dp),
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
                horizontalArrangement = Arrangement.spacedBy(5.dp),
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

@Composable
internal fun EmptyQueueMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ChuText(text, style = ChuTypography.current.body, color = ChuColors.current.textMuted)
    }
}

@Composable
internal fun QueueHintBand(text: String) {
    ChuText(
        text,
        style = ChuTypography.current.labelSmall,
        color = ChuColors.current.textMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .background(ChuColors.current.background)
            .padding(horizontal = 11.dp, vertical = 4.dp),
    )
}

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
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ChuText("›", style = type.headline, color = colors.accent)
        ChuTextField(
            value = value,
            onValueChange = onValueChange,
            label = "",
            placeholder = agent?.let { "Send a task to @${it.name}…" } ?: "Select an agent before sending…",
            singleLine = true,
            showLabel = false,
            modifier = Modifier.weight(1f),
        )
        ChuButton(
            onClick = onSend,
            enabled = canSend,
            variant = ChuButtonVariant.Filled,
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
        ) {
            ChuText(
                if (sending) "SENDING" else "SEND",
                style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (canSend) colors.onAccent else colors.disabledText,
            )
        }
    }
}
