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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import com.jossephus.chuchu.ui.components.BlockBar
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
internal fun MachineStrip(
    state: MachineUiState,
    modifier: Modifier = Modifier,
    onUsageVisible: (Boolean) -> Unit = {},
) {
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
            MachineStripPages(readout, alpha) { page -> onUsageVisible(page == 1) }
        }
        // Thu gọn thì thôi luôn: không ai nhìn USAGE nữa.
        if (!expanded) LaunchedEffect(Unit) { onUsageVisible(false) }
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
private fun MachineStripPages(
    readout: MachineReadout,
    alpha: Float,
    onPageChange: (Int) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val pager = rememberPagerState(pageCount = { 2 })
    // Hai trang PHẢI cao bằng nhau, nếu không lướt qua lại là giật (user chốt
    // 3/9). Lấy theo trang nhiều dòng hơn; trang ngắn hơn thì chừa chỗ trống.
    val rows = maxOf(machineRowCount(readout), usageRowCount(readout))
    val pageHeight = (rows * ROW_HEIGHT_DP).dp

    // Trang USAGE chỉ được LÀM MỚI khi người dùng trượt tới (user chốt 3/9):
    // đọc cache quota thì gần như miễn phí, nhưng làm mới nó tốn 5s + 380MB
    // cho một tiến trình claude — không đáng chạy khi không ai nhìn.
    LaunchedEffect(pager.currentPage) { onPageChange(pager.currentPage) }

    Column(Modifier.fillMaxWidth().background(colors.surfaceVariant)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().height(pageHeight),
            verticalAlignment = Alignment.Top,
        ) { page ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (page == 0) MachinePage(readout, alpha) else UsagePage(readout, alpha)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(2) { i ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(5.dp)
                        .background(if (pager.currentPage == i) colors.accent else colors.border),
                )
            }
            ChuText(
                if (pager.currentPage == 0) "  MACHINE" else "  USAGE",
                style = type.labelSmall,
                color = colors.textMuted,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun rowStyle() = ChuTypography.current.labelSmall.copy(
    fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")

@Composable
private fun MachinePage(readout: MachineReadout, alpha: Float) {
    val colors = ChuColors.current
    val s = readout.snapshot
    BlockBar("CPU", readout.cpuPct?.div(100.0), pct(readout.cpuPct), colors.accentSecondary,
        tail = s.tempCpu?.let { "$it°C" } ?: "", alpha = alpha)
    BlockBar("RAM", s.memPct / 100.0, pct(s.memPct), colors.accent,
        tail = "${g(s.memUsedKb)}/${g(s.memTotalKb)}G", alpha = alpha)
    s.gpu?.let {
        BlockBar("GPU", it.util / 100.0, "${it.util}%", colors.success, tail = "${it.temp}°C", alpha = alpha)
        BlockBar("VRA", it.memPct / 100.0, pct(it.memPct), colors.success,
            tail = "${gMb(it.memUsedMb)}/${gMb(it.memTotalMb)}G", alpha = alpha)
    }
    BlockBar("DSK", s.diskPct / 100.0, pct(s.diskPct), colors.warning,
        tail = "${g(s.diskUsedKb)}/${g(s.diskTotalKb)}G", alpha = alpha)
    val load = s.load.firstOrNull() ?: 0.0
    BlockBar("LOAD", load / s.ncpu.coerceAtLeast(1), String.format(Locale.US, "%.2f", load),
        colors.accentSecondary, tail = "${s.ncpu} core", alpha = alpha)
    // Tiến trình không có "phần trăm của cái gì" nên đừng ép vào khuôn bar —
    // bản trước làm thế khiến tên bị cắt còn "clau".
    readout.topRam.take(2).forEach { p ->
        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
            ChuText(p.comm, style = rowStyle(), color = colors.textSecondary.copy(alpha = alpha),
                maxLines = 1, modifier = Modifier.weight(1f))
            ChuText("${g(p.rssKb)}G ×${p.n}", style = rowStyle(),
                color = colors.textMuted.copy(alpha = alpha), maxLines = 1)
        }
    }
}

@Composable
private fun UsagePage(readout: MachineReadout, alpha: Float) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val s = readout.snapshot
    if (s.claude == null && s.agy == null) {
        ChuText("Loading quota…", style = rowStyle(), color = colors.textMuted)
        return
    }
    // MỌI thanh đều đo phần ĐÃ DÙNG. Bản trước để Claude đếm "đã dùng" còn agy
    // đếm "còn lại" rồi ghi chú ở đuôi — hai thanh dài bằng nhau, cùng màu, mà
    // một cái là tin tốt một cái là báo động (user chỉ ra 3/9). Chú thích không
    // cứu được khi hình vẽ đã nói ngược.
    s.claude?.session?.let {
        BlockBar("5H", it.usedPct / 100.0, "${it.usedPct}%", usedColor(it.usedPct, colors),
            tail = it.resetsAt?.substringAfter(", ")?.let { t -> "→ $t" } ?: "used", alpha = alpha)
    }
    s.claude?.week?.let {
        BlockBar("WK", it.usedPct / 100.0, "${it.usedPct}%", usedColor(it.usedPct, colors),
            tail = it.resetsAt?.substringBefore(",")?.let { d -> "→ $d" } ?: "used", alpha = alpha)
    }
    // Mỗi tài khoản MỘT dòng, lấy cửa sổ căng nhất — thứ đáng biết là "con nào
    // sắp cạn", không phải sáu con số. Tên để nguyên, không cắt còn "c1".
    s.agy?.accounts?.filter { it.configured }?.forEach { a ->
        val remaining = listOfNotNull(a.pct5h, a.pctWeek).minOrNull() ?: return@forEach
        val used = 100.0 - remaining
        val binding = if (a.pctWeek != null && (a.pct5h == null || a.pctWeek <= a.pct5h)) "week" else "5h"
        val label = if (a.id == s.agy?.current) "▸${a.id.removePrefix("acc")}" else a.id.removePrefix("acc")
        BlockBar(label, used / 100.0, "${used.toInt()}%", usedColor(used.toInt(), colors),
            tail = binding, alpha = alpha)
    }
}

/** Cao xấp xỉ một hàng BlockBar (chữ 11sp + đệm 2dp). */
private const val ROW_HEIGHT_DP = 18

private fun machineRowCount(r: MachineReadout): Int =
    4 + (if (r.snapshot.gpu != null) 2 else 0) + r.topRam.take(2).size

private fun usageRowCount(r: MachineReadout): Int {
    val c = r.snapshot.claude
    val q = r.snapshot.agy
    var n = (if (c?.session != null) 1 else 0) + (if (c?.week != null) 1 else 0)
    q?.accounts?.filter { it.configured }?.forEach {
        if (it.pct5h != null || it.pctWeek != null) n++
    }
    return maxOf(n, 1)
}

private fun pct(v: Double?): String = v?.let { String.format(Locale.US, "%.0f%%", it) } ?: "—"

private fun gMb(mb: Long): String = String.format(Locale.US, "%.1f", mb / 1024.0)

/** Màu cho bốn số trên dải thu gọn. */
private fun tone(v: Double, warn: Double, crit: Double, c: com.jossephus.chuchu.ui.theme.ChuColorPalette): Color =
    when {
        v >= crit -> c.error
        v >= warn -> c.warning
        else -> c.textPrimary
    }

/** Thang màu chung cho MỌI dòng USAGE, vì mọi dòng giờ đều đo phần đã dùng. */
private fun usedColor(usedPct: Int, c: com.jossephus.chuchu.ui.theme.ChuColorPalette): Color = when {
    usedPct >= 90 -> c.error
    usedPct >= 70 -> c.warning
    else -> c.accent
}

/** ≥100 thì bỏ phần thập phân — "95.1/467.7G" vừa khít, "95.1/1024.3G" thì không. */
private fun g(kb: Long): String {
    val v = kb / 1048576.0
    return if (v >= 100) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)
}
private fun age(s: Long): String = if (s < 3600) "${s / 60}m ago" else "${s / 3600}h ago"
