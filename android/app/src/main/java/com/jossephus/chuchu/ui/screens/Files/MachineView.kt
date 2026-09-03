package com.jossephus.chuchu.ui.screens.Files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.data.model.machine.MachineReadout
import com.jossephus.chuchu.ui.components.BlockBar
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import java.util.Locale

/** Qua nguong nay thi so coi nhu nguoi — danh dau, khong ve nhu so song. */
private const val STALE_AFTER_S = 15L

@Composable
internal fun MachineView(state: MachineUiState, modifier: Modifier = Modifier) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val readout = state.readout

    if (readout == null) {
        Column(modifier = modifier.fillMaxSize().padding(14.dp)) {
            ChuText(
                state.error ?: "Reading machine state…",
                style = type.bodySmall,
                color = if (state.error != null) colors.error else colors.textMuted,
            )
            if (state.error != null) {
                ChuText(
                    "\nThe Queue tab and this one share the same address and login — " +
                        "if Queue works, this should too.",
                    style = type.labelSmall,
                    color = colors.textMuted,
                )
            }
        }
        return
    }

    val s = readout.snapshot
    val ageS = (System.currentTimeMillis() / 1000 - s.ts).coerceAtLeast(0)
    val stale = ageS > STALE_AFTER_S

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "host") {
            KohiSectionBand(
                label = s.host.ifBlank { "MACHINE" },
                meta = "↑${uptime(s.uptimeS)} · ${age(ageS)}",
                containerColor = colors.background,
                accent = if (stale) colors.warning else colors.success,
            )
            if (state.error != null) {
                ChuText(
                    "↯ ${state.error}",
                    style = type.labelSmall,
                    color = colors.error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
            ChuCard(modifier = cardModifier()) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    val a = if (stale) 0.45f else 1f
                    BlockBar("CPU", readout.cpuPct?.div(100.0), pctOf(readout.cpuPct),
                        colors.accentSecondary, tail = s.tempCpu?.let { "$it°C" } ?: "", alpha = a)
                    BlockBar("RAM", s.memPct / 100.0, pctOf(s.memPct), colors.accent,
                        tail = "${gb(s.memUsedKb)}/${gb(s.memTotalKb)}G", alpha = a)
                    s.gpu?.let { g ->
                        BlockBar("GPU", g.util / 100.0, "${g.util}%", colors.success,
                            tail = "${g.temp}°C", alpha = a)
                        BlockBar("VRA", g.memPct / 100.0, pctOf(g.memPct), colors.success,
                            tail = "${mb(g.memUsedMb)}/${mb(g.memTotalMb)}G", alpha = a)
                    }
                    BlockBar("DSK", s.diskPct / 100.0, pctOf(s.diskPct), colors.warning,
                        tail = "${gb(s.diskUsedKb)}/${gb(s.diskTotalKb)}G", alpha = a)
                    Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                        ChuText("NET", style = monoStyle(type), color = colors.textSecondary,
                            modifier = Modifier.width(30.dp))
                        ChuText(
                            "↓${rate(readout.netRxPerSec)} ↑${rate(readout.netTxPerSec)}",
                            style = monoStyle(type),
                            color = if (readout.netRxPerSec == null) colors.textMuted else colors.textPrimary,
                        )
                        Box(Modifier.weight(1f))
                        ChuText(
                            "load ${String.format(Locale.US, "%.2f", s.load.firstOrNull() ?: 0.0)}",
                            style = monoStyle(type), color = colors.textMuted,
                        )
                    }
                    s.battery?.let { b ->
                        if (b.discharging) {
                            ChuText(
                                "ON BATTERY ${b.pct}% — POWER CUT?",
                                style = monoStyle(type).copy(fontWeight = FontWeight.Bold),
                                color = colors.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        s.claude?.let { q ->
            item(key = "claude") {
                val meta = q.session?.resetsEpoch?.let { "resets in ${until(it)}" } ?: q.error
                KohiSectionBand("CLAUDE", meta, containerColor = colors.background,
                    accent = if (q.ok) colors.accent else colors.warning)
                ChuCard(modifier = cardModifier()) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        // Claude tra % DA DUNG, agy tra % CON LAI. Doi Claude
                        // sang con lai de moi thanh trong app cung mot chieu:
                        // day = con nguyen, can = da tieu het.
                        q.session?.let {
                            val left = 100 - it.usedPct
                            BlockBar("5H", left / 100.0, "$left%",
                                quotaColor(left, colors), tail = it.resetsAt ?: "left")
                        }
                        q.week?.let {
                            val left = 100 - it.usedPct
                            BlockBar("WK", left / 100.0, "$left%",
                                quotaColor(left, colors), tail = it.resetsAt ?: "left")
                        }
                    }
                }
            }
        }

        s.agy?.let { q ->
            item(key = "agy") {
                KohiSectionBand("AGY QUOTA", q.current, containerColor = colors.background)
                ChuCard(modifier = cardModifier()) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        q.accounts.forEach { a ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                ChuText(
                                    if (a.id == q.current) "●" else "○",
                                    style = monoStyle(type),
                                    color = if (a.id == q.current) colors.success else colors.textMuted,
                                    modifier = Modifier.width(16.dp),
                                )
                                ChuText(a.id, style = monoStyle(type),
                                    color = if (a.id == q.current) colors.textPrimary else colors.textSecondary,
                                    modifier = Modifier.width(48.dp))
                                if (!a.configured) {
                                    ChuText("not set", style = monoStyle(type), color = colors.textMuted)
                                } else if (a.error != null) {
                                    ChuText(a.error, style = monoStyle(type), color = colors.error, maxLines = 1)
                                } else {
                                    // agy tra phan tram CON LAI — cang thap cang gan het.
                                    QuotaCell("5h", a.pct5h, colors, type, Modifier.weight(1f))
                                    QuotaCell("W", a.pctWeek, colors, type, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (readout.topCpu.isNotEmpty()) {
            item(key = "topcpu") {
                KohiSectionBand("TOP CPU", "${readout.topCpu.size}", containerColor = colors.background)
                ChuCard(modifier = cardModifier()) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        readout.topCpu.forEach {
                            ProcRow(it.comm, String.format(Locale.US, "%.1f%%", it.pct), it.n,
                                colors.accentSecondary, type)
                        }
                    }
                }
            }
        }

        item(key = "topram") {
            KohiSectionBand("TOP RAM", "${readout.topRam.size}", containerColor = colors.background)
            ChuCard(modifier = cardModifier()) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    readout.topRam.forEach {
                        ProcRow(it.comm, "${gb(it.rssKb)}G", it.n, colors.accent, type)
                    }
                }
            }
        }
    }
}

@Composable
private fun cardModifier(): Modifier =
    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)

@Composable
private fun monoStyle(type: com.jossephus.chuchu.ui.theme.ChuTypeScale) =
    type.body.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")

/** Thanh do. [pct] null = chua co hai mau de tinh -> hien "—", khong ve 0%. */
@Composable
private fun Gauge(label: String, pct: Double?, color: Color, tail: String = "", dim: Boolean = false) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val alpha = if (dim) 0.45f else 1f
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        ChuText(label, style = monoStyle(type), color = colors.textSecondary.copy(alpha = alpha),
            modifier = Modifier.width(30.dp))
        Box(
            Modifier.weight(1f).height(9.dp)
                .background(colors.border.copy(alpha = 0.35f * alpha)),
        ) {
            val f = ((pct ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
            if (f > 0f) {
                Box(Modifier.fillMaxWidth(f).height(9.dp).background(color.copy(alpha = alpha)))
            }
        }
        ChuText(
            pct?.let { String.format(Locale.US, "%3.0f%%", it) } ?: "  —",
            style = monoStyle(type),
            color = (if (pct == null) colors.textMuted else colors.textPrimary).copy(alpha = alpha),
            modifier = Modifier.padding(start = 8.dp).width(46.dp),
        )
        ChuText(tail, style = monoStyle(type), color = colors.textMuted.copy(alpha = alpha), maxLines = 1)
    }
}

@Composable
private fun QuotaCell(
    label: String,
    remaining: Double?,
    colors: com.jossephus.chuchu.ui.theme.ChuColorPalette,
    type: com.jossephus.chuchu.ui.theme.ChuTypeScale,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ChuText(label, style = monoStyle(type), color = colors.textMuted)
        ChuText(
            remaining?.let { String.format(Locale.US, "%3.0f%%", it) } ?: "  ?",
            style = monoStyle(type),
            color = when {
                remaining == null -> colors.textMuted
                remaining <= 10 -> colors.error
                remaining <= 30 -> colors.warning
                else -> colors.success
            },
        )
    }
}

@Composable
private fun ProcRow(
    name: String,
    value: String,
    count: Int,
    valueColor: Color,
    type: com.jossephus.chuchu.ui.theme.ChuTypeScale,
) {
    val colors = ChuColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        ChuText(name, style = monoStyle(type), color = colors.textPrimary, maxLines = 1,
            modifier = Modifier.weight(1f))
        ChuText(value, style = monoStyle(type), color = valueColor)
        ChuText(if (count > 1) " ×$count" else "", style = monoStyle(type), color = colors.textMuted,
            modifier = Modifier.width(34.dp))
    }
}

private fun quotaColor(leftPct: Int, colors: com.jossephus.chuchu.ui.theme.ChuColorPalette): Color = when {
    leftPct <= 10 -> colors.error
    leftPct <= 30 -> colors.warning
    else -> colors.accent
}

private fun pctOf(v: Double?): String =
    v?.let { String.format(Locale.US, "%.0f%%", it) } ?: "—"

private fun gb(kb: Long): String = String.format(Locale.US, "%.1f", kb / 1048576.0)
private fun mb(mbv: Long): String = String.format(Locale.US, "%.1f", mbv / 1024.0)

private fun rate(bytesPerSec: Double?): String = when {
    bytesPerSec == null -> "—"
    bytesPerSec >= 1048576 -> String.format(Locale.US, "%.1fM/s", bytesPerSec / 1048576.0)
    else -> String.format(Locale.US, "%.0fK/s", bytesPerSec / 1024.0)
}

private fun uptime(s: Long): String {
    val d = s / 86400
    val h = (s % 86400) / 3600
    return if (d > 0) "${d}d${h}h" else "${h}h${(s % 3600) / 60}m"
}

private fun age(s: Long): String = when {
    s < 60 -> "${s}s"
    s < 3600 -> "${s / 60}m"
    else -> "${s / 3600}h"
}

private fun until(epoch: Long): String {
    val left = epoch - System.currentTimeMillis() / 1000
    if (left <= 0) return "now"
    return if (left < 3600) "${left / 60}m" else "${left / 3600}h${(left % 3600) / 60}"
}
