package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.ChuTextField
import com.jossephus.chuchu.ui.components.KohiBottomSheet
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.noRippleClickable
import com.jossephus.chuchu.ui.theme.AgentKind
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import com.jossephus.chuchu.ui.theme.rosterColor

/** Bốn con agent qsrv biết mở (POST /launch) — thứ tự như roster. */
private val LAUNCH_AGENTS = listOf("claude", "opencode", "agy", "codex")

/**
 * Tấm NEW SESSION (user chốt 23/9: tấm trượt đáy, không mẫu lệnh): chọn agent (chip màu họ), thư mục
 * (cwd các phiên đang chạy + lịch sử mở, hoặc gõ tay), gõ lệnh đầu (tuỳ chọn) → START. qsrv mở tab herdr,
 * chạy agent, gửi lệnh khi nó sẵn sàng; phiên hiện trong CONVERSATIONS sau vài giây. Cùng khung
 * [KohiBottomSheet] với các tấm chi tiết khác.
 */
@Composable
internal fun QueueLaunchSheet(
    dirs: List<LaunchDir>,
    onDismiss: () -> Unit,
    onStart: (agent: String, cwd: String, prompt: String) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    var agent by rememberSaveable { mutableStateOf("claude") }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var other by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    val cwd = other.trim().ifBlank { picked ?: "" }
    KohiBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(colors.surface)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ChuText("NEW SESSION", style = type.title.copy(fontWeight = FontWeight.Bold), color = colors.accent)
                Spacer(Modifier.weight(1f))
                KohiCompactAction(label = "CLOSE", onClick = onDismiss)
            }

            SheetLabel("AGENT")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LAUNCH_AGENTS.forEach { kind ->
                    val on = kind == agent
                    val tone = AgentKind.of(kind).rosterColor()
                    Row(
                        modifier = Modifier
                            .background(if (on) colors.accent.copy(alpha = 0.08f) else Color.Transparent, BoxShape)
                            .border(1.dp, if (on) colors.accent else colors.border, BoxShape)
                            .noRippleClickable { agent = kind }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChuText("● ", style = type.labelSmall.copy(fontSize = 9.sp), color = tone)
                        ChuText(kind, style = type.label, color = if (on) colors.textPrimary else colors.textSecondary)
                    }
                }
            }

            SheetLabel("FOLDER · RECENT")
            if (dirs.isEmpty()) {
                ChuText("no running sessions yet — type a folder below", style = type.labelSmall, color = colors.textMuted)
            }
            dirs.forEach { d ->
                val on = other.isBlank() && picked == d.path
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (on) colors.accent.copy(alpha = 0.08f) else Color.Transparent, BoxShape)
                        .border(1.dp, if (on) colors.accent else Color.Transparent, BoxShape)
                        .noRippleClickable { picked = d.path; other = "" }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChuText(d.short, style = type.label, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (d.agent.isNotBlank() || d.name.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        ChuText(listOf(d.agent, d.name).filter { it.isNotBlank() }.joinToString(" · "), style = type.labelSmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            ChuTextField(
                value = other,
                onValueChange = { other = it },
                label = "",
                placeholder = "other folder… (~/path)",
                singleLine = true,
                showLabel = false,
                autoFocus = false,
                modifier = Modifier.fillMaxWidth(),
            )

            SheetLabel("PROMPT · OPTIONAL")
            ChuTextField(
                value = text,
                onValueChange = { text = it },
                label = "",
                placeholder = "what should it do first?",
                singleLine = false,
                showLabel = false,
                autoFocus = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            )

            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                KohiCompactAction(label = "▸ START", enabled = cwd.isNotBlank(), onClick = { onStart(agent, cwd, text.trim()) })
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    ChuText(text, style = type.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp), color = colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
}
