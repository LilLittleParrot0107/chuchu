package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.data.model.machine.MachineReadout
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.screens.Files.MachineUiState
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import java.util.Locale

/** Quá ngưỡng này thì số coi như nguội — làm mờ, ghi tuổi thật, không vẽ như số sống. */
private const val STALE_AFTER_S = 15L

/**
 * Dải trạng thái máy ghim ngay trên ô nhập của Queue.
 *
 * Bốn số chọn theo NGỮ CẢNH: đứng ở Queue là lúc quyết giao việc, nên RAM/CPU
 * ("máy còn tải nổi không") và quota Claude 5H/tuần ("còn lượt không") mới là
 * thứ đáng nhìn — dung lượng đĩa không ảnh hưởng gì tới quyết định đó, nên nó
 * lui vào phần mở rộng.
 */
@Composable
internal fun MachineStrip(state: MachineUiState, modifier: Modifier = Modifier) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val readout = state.readout ?: return
    val s = readout.snapshot
    var expanded by remember { mutableStateOf(false) }

    val ageS = (System.currentTimeMillis() / 1000 - s.ts).coerceAtLeast(0)
    val stale = ageS > STALE_AFTER_S
    val alpha = if (stale) 0.5f else 1f

    val ram = s.memPct
    val cpu = readout.cpuPct
    val h5 = s.claude?.session?.usedPct
    val wk = s.claude?.week?.usedPct

    // Vạch bên trái đổi màu theo cái căng nhất — thấy được bằng đuôi mắt mà
    // không phải đọc số.
    val edge = when {
        ram >= 85 || (cpu ?: 0.0) >= 85 -> colors.error
        ram >= 70 || (cpu ?: 0.0) >= 70 || (h5 ?: 0) >= 70 -> colors.warning
        else -> Color.Transparent
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = expanded) {
            MachineStripDetail(readout, alpha)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant)
                .clickable { expanded = !expanded }
                .defaultMinSize(minHeight = 30.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(2.dp).height(30.dp).background(edge))
            Cell("RAM", pct(ram), tone(ram, 70.0, 85.0, colors), alpha)
            Cell("CPU", cpu?.let { pct(it) } ?: "—", tone(cpu ?: 0.0, 70.0, 85.0, colors), alpha)
            Cell("5H", h5?.let { "$it%" } ?: "—", tone((h5 ?: 0).toDouble(), 70.0, 90.0, colors), alpha)
            Cell("WK", wk?.let { "$it%" } ?: "—", tone((wk ?: 0).toDouble(), 70.0, 90.0, colors), alpha)
            Box(Modifier.weight(1f))
            ChuText(
                if (stale) age(ageS) else "${ageS}s",
                style = type.labelSmall,
                color = (if (stale) colors.warning else colors.textMuted).copy(alpha = alpha),
            )
            ChuText(
                if (expanded) " ▴" else " ▾",
                style = type.labelSmall,
                color = colors.accent,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

@Composable
private fun Cell(label: String, value: String, valueColor: Color, alpha: Float) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        ChuText(label, style = type.labelSmall, color = colors.textMuted.copy(alpha = alpha))
        ChuText(
            value,
            style = type.labelSmall.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"),
            color = valueColor.copy(alpha = alpha),
        )
    }
}

@Composable
private fun MachineStripDetail(readout: MachineReadout, alpha: Float) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val s = readout.snapshot
    Column(
        Modifier.fillMaxWidth().background(colors.surfaceVariant).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Gauge("CPU", readout.cpuPct, colors.accentSecondary, s.tempCpu?.let { "$it°C" } ?: "", alpha)
        Gauge("RAM", s.memPct, colors.accent,
            "${g(s.memUsedKb)}/${g(s.memTotalKb)}G", alpha)
        s.gpu?.let { Gauge("GPU", it.util.toDouble(), colors.success, "${it.temp}°C", alpha) }
        Gauge("DSK", s.diskPct, colors.warning, "${g(s.diskUsedKb)}/${g(s.diskTotalKb)}G", alpha)
        s.claude?.session?.let {
            Gauge("5H", it.usedPct.toDouble(), colors.accent, it.resetsAt?.let { r -> "→ $r" } ?: "used", alpha)
        }
        s.claude?.week?.let {
            Gauge("WK", it.usedPct.toDouble(), colors.accent, it.resetsAt?.let { r -> "→ $r" } ?: "used", alpha)
        }
        readout.topRam.take(2).forEach {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                ChuText(it.comm, style = type.labelSmall, color = colors.textSecondary.copy(alpha = alpha),
                    maxLines = 1, modifier = Modifier.weight(1f))
                ChuText("${g(it.rssKb)}G ×${it.n}", style = type.labelSmall,
                    color = colors.textMuted.copy(alpha = alpha))
            }
        }
        ChuText(
            "load ${String.format(Locale.US, "%.2f", s.load.firstOrNull() ?: 0.0)} · ${s.host}",
            style = type.labelSmall,
            color = colors.textMuted.copy(alpha = alpha),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun Gauge(label: String, pct: Double?, color: Color, tail: String, alpha: Float) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        ChuText(label, style = type.labelSmall, color = colors.textSecondary.copy(alpha = alpha),
            modifier = Modifier.width(28.dp))
        Box(Modifier.weight(1f).height(7.dp).background(colors.border.copy(alpha = 0.35f * alpha))) {
            val f = ((pct ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
            if (f > 0f) Box(Modifier.fillMaxWidth(f).height(7.dp).background(color.copy(alpha = alpha)))
        }
        ChuText(
            pct?.let { String.format(Locale.US, "%3.0f%%", it) } ?: "  —",
            style = type.labelSmall.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"),
            color = colors.textPrimary.copy(alpha = alpha),
            modifier = Modifier.padding(start = 6.dp).width(42.dp),
        )
        ChuText(tail, style = type.labelSmall, color = colors.textMuted.copy(alpha = alpha), maxLines = 1)
    }
}

private fun tone(v: Double, warn: Double, crit: Double, c: com.jossephus.chuchu.ui.theme.ChuColorPalette): Color =
    when {
        v >= crit -> c.error
        v >= warn -> c.warning
        else -> c.textPrimary
    }

private fun pct(v: Double): String = String.format(Locale.US, "%.0f%%", v)
private fun g(kb: Long): String = String.format(Locale.US, "%.1f", kb / 1048576.0)
private fun age(s: Long): String = if (s < 3600) "${s / 60}m trước" else "${s / 3600}h trước"
