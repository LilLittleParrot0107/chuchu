package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onRefreshUsage: () -> Unit = {},
    collapse: Boolean = false,
    /**
     * Chỉ xem nhanh: không caret, không bấm, không bao giờ bung panel (dải
     * trên compose box của terminal, user chốt 4/9). Chưa có số thì vẫn vẽ
     * hàng gạch "—" để compose box không nhảy xuống 30dp lúc số về.
     */
    preview: Boolean = false,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val readout = state.readout
    if (readout == null) {
        if (preview) PreviewPlaceholder(modifier)
        return
    }
    val s = readout.snapshot
    var expanded by remember { mutableStateOf(false) }
    // Panel mở CHỈ KHI người dùng muốn VÀ bàn phím đang đóng. Bản trước chỉ thu
    // lại đúng lúc [collapse] đổi giá trị, nên mở panel trong khi đang gõ thì nó
    // cứ thế bung ra: cột dọc không cuộn được, panel + ô nhập cao hơn phần màn
    // còn lại, và ô nhập bị đẩy tụt xuống dưới bàn phím (user báo 4/9).
    // Giữ nguyên ý định của người dùng trong [expanded] để đóng bàn phím là
    // panel trở lại như cũ, không phải mở tay lần nữa.
    val open = expanded && !collapse && !preview

    // Tuổi tính theo đồng hồ chạy 5s, không theo lúc compose: đường lỗi copy(error=…)
    // trùng giá trị thì StateFlow không emit, dải hiện "3s" alpha đầy mãi dù qsrv đã
    // chết (audit 4/9 #13).
    val now by rememberTicking()
    val ageS = (now / 1000 - s.ts).coerceAtLeast(0)
    val stale = ageS > STALE_AFTER_S
    val alpha = if (stale) 0.5f else 1f

    val ram = s.memPct
    val cpu = readout.cpuPct
    // CÒN LẠI, không phải đã dùng — xem ghi chú ở UsagePage.
    val h5 = s.claude?.session?.usedPct?.let { 100 - it }
    val wk = s.claude?.week?.usedPct?.let { 100 - it }

    // Vạch bên trái đổi màu theo cái căng nhất — thấy được bằng đuôi mắt mà
    // không phải đọc số.
    val edge = when {
        ram >= 85 || (cpu ?: 0.0) >= 85 -> colors.error
        ram >= 70 || (cpu ?: 0.0) >= 70 || (h5 ?: 100) <= 30 -> colors.warning
        else -> Color.Transparent
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = open) {
            MachineStripPages(readout, alpha, onRefreshUsage) { page -> onUsageVisible(page == 1) }
        }
        // Thu gọn thì thôi luôn: không ai nhìn USAGE nữa.
        if (!open) LaunchedEffect(Unit) { onUsageVisible(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant)
                .then(if (preview) Modifier else Modifier.clickable { expanded = !expanded })
                .defaultMinSize(minHeight = 30.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(2.dp).height(30.dp).background(edge))
            Cell("RAM", pct(ram), tone(ram, 70.0, 85.0, colors), alpha)
            Cell("CPU", cpu?.let { pct(it) } ?: "—", tone(cpu ?: 0.0, 70.0, 85.0, colors), alpha)
            Cell("5H", h5?.let { "$it%" } ?: "—", leftTone((h5 ?: 100).toDouble(), colors), alpha)
            Cell("WEEK", wk?.let { "$it%" } ?: "—", leftTone((wk ?: 100).toDouble(), colors), alpha)
            Box(Modifier.weight(1f))
            ChuText(
                if (stale) age(ageS) else "${ageS}s",
                style = type.labelSmall,
                color = (if (stale) colors.warning else colors.textMuted).copy(alpha = alpha),
            )
            if (!preview) {
                ChuText(
                    if (open) " ▴" else " ▾",
                    style = type.labelSmall,
                    color = colors.accent,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

/** Hàng gạch cùng cỡ với dải thật, để chỗ sẵn trong lúc chờ nhịp poll đầu. */
@Composable
private fun PreviewPlaceholder(modifier: Modifier) {
    val colors = ChuColors.current
    Row(
        modifier = modifier.fillMaxWidth().background(colors.surfaceVariant)
            .defaultMinSize(minHeight = 30.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).height(30.dp))
        for (k in listOf("RAM", "CPU", "5H", "WEEK")) Cell(k, "—", colors.textMuted, 0.6f)
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
    onRefreshUsage: () -> Unit,
    onPageChange: (Int) -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val pager = rememberPagerState(pageCount = { 2 })
    // Hai trang PHẢI cao bằng nhau, nếu không lướt qua lại là giật (user chốt
    // 3/9). Lấy theo trang nhiều dòng hơn; trang ngắn hơn thì chừa chỗ trống.
    val rows = maxOf(machineRowCount(readout), usageRowCount(readout))
    // + đệm dọc của Column bên trong: hàng đã khoá cứng thì khung phải chứa đủ,
    // không thì trang đủ 8 dòng bị xén 6dp mỗi đầu.
    val pageHeight = (rows * ROW_HEIGHT_DP + 2 * PAGE_PAD_DP).dp

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
            // Trang ít dòng hơn: khối CĂN GIỮA, giãn dòng y hệt trang kia (13/9, user
            // giao em chọn). Dàn đều (4/9) làm USAGE 5 dòng cách 35dp còn MACHINE 22dp,
            // hai trang nhìn như hai bảng khác nhau; dồn lên trên thì để hố trống dưới
            // đáy, chính cái user chê 4/9. Mỗi hàng khoá cứng ROW_HEIGHT_DP nên chiều
            // cao khung = số dòng × 22 chính xác, không còn ước lượng dư vài dp.
            Column(
                Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = PAGE_PAD_DP.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (page == 0) MachinePage(readout, alpha) else UsagePage(readout, alpha)
            }
        }
        // Chấm trang nằm ĐÚNG giữa, nút làm mới ép sát mép phải. Trước đây cả
        // hai xếp chung một hàng canh giữa nên nút lơ lửng giữa chừng, và mỗi
        // lần đổi trang nó xuất hiện/biến mất là chấm bị kéo lệch theo.
        Box(Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
            Row(Modifier.align(Alignment.Center)) {
                repeat(2) { i ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(5.dp)
                            .background(if (pager.currentPage == i) colors.accent else colors.border),
                    )
                }
            }
            // Trang USAGE có nút làm mới THẤY ĐƯỢC, kèm tuổi của số quota —
            // trước đây trigger chạy ngầm nên không ai biết nó có ăn hay không.
            if (pager.currentPage == 1) {
                val qts = readout.snapshot.claude?.dataTs ?: 0L
                val qAge = if (qts > 0) System.currentTimeMillis() / 1000 - qts else -1
                var tapped by remember { mutableStateOf(false) }
                LaunchedEffect(tapped) { if (tapped) { kotlinx.coroutines.delay(6000); tapped = false } }
                ChuText(
                    if (tapped) "refreshing…" else "${quotaAge(qAge)} ⟳",
                    style = type.labelSmall,
                    color = colors.accent,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable { tapped = true; onRefreshUsage() }
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun rowStyle() = ChuTypography.current.labelSmall.copy(
    fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum",
    fontSize = PANEL_TEXT_SP.sp, lineHeight = (PANEL_TEXT_SP * 1.45f).sp)

@Composable
private fun MachinePage(readout: MachineReadout, alpha: Float) {
    val colors = ChuColors.current
    val s = readout.snapshot
    BlockBar("CPU", readout.cpuPct?.div(100.0), pct(readout.cpuPct), colors.accentSecondary,
        tail = s.tempCpu?.let { "$it°C" } ?: "", alpha = alpha, labelWidth = PANEL_LABEL_W, reserveTail = true, modifier = rowMod(), fontSize = PANEL_BAR_SP, textSize = PANEL_TEXT_SP)
    BlockBar("RAM", s.memPct / 100.0, pct(s.memPct), colors.accent,
        tail = "${g(s.memUsedKb)}/${g(s.memTotalKb)}G", alpha = alpha, labelWidth = PANEL_LABEL_W, reserveTail = true, modifier = rowMod(), fontSize = PANEL_BAR_SP, textSize = PANEL_TEXT_SP)
    s.gpu?.let {
        BlockBar("GPU", it.util / 100.0, "${it.util}%", colors.success, tail = "${it.temp}°C", alpha = alpha, labelWidth = PANEL_LABEL_W, reserveTail = true, modifier = rowMod(), fontSize = PANEL_BAR_SP, textSize = PANEL_TEXT_SP)
        BlockBar("VRA", it.memPct / 100.0, pct(it.memPct), colors.success,
            tail = "${gMb(it.memUsedMb)}/${gMb(it.memTotalMb)}G", alpha = alpha, labelWidth = PANEL_LABEL_W, reserveTail = true, modifier = rowMod(), fontSize = PANEL_BAR_SP, textSize = PANEL_TEXT_SP)
    }
    BlockBar("DSK", s.diskPct / 100.0, pct(s.diskPct), colors.warning,
        tail = "${g(s.diskUsedKb)}/${g(s.diskTotalKb)}G", alpha = alpha, labelWidth = PANEL_LABEL_W, reserveTail = true, modifier = rowMod(), fontSize = PANEL_BAR_SP, textSize = PANEL_TEXT_SP)
    val load = s.load.firstOrNull() ?: 0.0
    BlockBar("LOAD", load / s.ncpu.coerceAtLeast(1), String.format(Locale.US, "%.2f", load),
        colors.accentSecondary, tail = "${s.ncpu} core", alpha = alpha, labelWidth = PANEL_LABEL_W, reserveTail = true, modifier = rowMod(), fontSize = PANEL_BAR_SP, textSize = PANEL_TEXT_SP)
    // Tiến trình không có "phần trăm của cái gì" nên đừng ép vào khuôn bar —
    // bản trước làm thế khiến tên bị cắt còn "clau". Nhưng vẫn phải NẰM TRONG LƯỚI
    // của BlockBar: tên chiếm cột nhãn + thanh, số chiếm cột số + đuôi (đúng
    // 6 + 46 + 4 + 92 dp), để mép phải và mép thanh thẳng với các dòng trên (13/9).
    readout.topRam.take(2).forEach { p ->
        Row(rowMod().fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            ChuText(p.comm, style = rowStyle(), color = colors.textSecondary.copy(alpha = alpha),
                maxLines = 1, modifier = Modifier.weight(1f))
            ChuText("${g(p.rssKb)}G ×${p.n}", style = rowStyle().copy(textAlign = TextAlign.End),
                color = colors.textMuted.copy(alpha = alpha), maxLines = 1,
                modifier = Modifier.width(PANEL_RIGHT_W.dp))
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
    // MỌI thanh đều đo phần CÒN LẠI: thanh đầy = còn nguyên credit, thanh cạn =
    // đã tiêu hết (user chốt 3/9). Đọc như bình xăng, không phải như đồng hồ tải.
    // Điều bắt buộc là mọi dòng đo CÙNG một thứ — bản trước Claude đếm "đã dùng"
    // còn agy đếm "còn lại", hai thanh dài bằng nhau mà một cái là tin tốt một
    // cái là báo động. Chú thích không cứu được khi hình vẽ đã nói ngược.
    // Nhãn mang luôn tên nhà cung cấp — "cl·wk", "agy·2" tự nói nó của ai, nên
    // không tốn dòng tiêu đề nào (user chốt phương án B, 3/9). Màu NHÃN phân
    // nhóm, màu THANH vẫn theo mức cạn: hai màu hai việc, không chồng nghĩa.
    s.claude?.session?.let {
        val left = 100 - it.usedPct
        BlockBar("cl·5h", left / 100.0, "$left%", leftColor(left, colors),
            tail = resetIn(it.resetsEpoch, it.resetsAt ?: ""),
            alpha = alpha, labelColor = colors.accent, labelWidth = PANEL_LABEL_W, reserveTail = true, modifier = rowMod(),
            fontSize = PANEL_BAR_SP, textSize = PANEL_TEXT_SP)
    }
    s.claude?.week?.let {
        val left = 100 - it.usedPct
        BlockBar("cl·week", left / 100.0, "$left%", leftColor(left, colors),
            tail = resetIn(it.resetsEpoch, it.resetsAt ?: ""),
            alpha = alpha, labelColor = colors.accent, labelWidth = PANEL_LABEL_W, reserveTail = true, modifier = rowMod(),
            fontSize = PANEL_BAR_SP, textSize = PANEL_TEXT_SP)
    }
    // Mỗi tài khoản MỘT dòng, lấy cửa sổ căng nhất — thứ đáng biết là "con nào
    // sắp cạn", không phải sáu con số.
    s.agy?.accounts?.filter { it.configured }?.forEach { a ->
        // agy trả sẵn phần CÒN LẠI — đúng chiều, không phải đổi.
        val left = listOfNotNull(a.pct5h, a.pctWeek).minOrNull() ?: return@forEach
        // Cửa sổ căng nhất là cửa sổ đang đếm ngược — cùng cách đọc với dòng
        // Claude (user chốt 4/9); không có mốc thì ghi tên cửa sổ như trước.
        val weekBinds = a.pctWeek != null && (a.pct5h == null || a.pctWeek <= a.pct5h)
        val tail = if (weekBinds) resetIn(a.resetWeek, "week") else resetIn(a.reset5h, "5h")
        // Đệm một dấu cách cho dòng KHÔNG phải tài khoản đang dùng, để chữ "agy"
        // của mọi dòng thẳng cột — mũi tên không được đẩy nhãn lệch đi một ô.
        val mark = if (a.id == s.agy?.current) "▸" else " "
        BlockBar("${mark}agy·${a.id.removePrefix("acc")}",
            left / 100.0, "${left.toInt()}%", leftColor(left.toInt(), colors),
            tail = tail, alpha = alpha, labelColor = colors.success, labelWidth = PANEL_LABEL_W, reserveTail = true, modifier = rowMod(),
            fontSize = PANEL_BAR_SP, textSize = PANEL_TEXT_SP)
    }
}

/** Chiều cao MỖI hàng, khoá cứng qua [rowMod] — không còn "xấp xỉ" (13/9). */
// Chữ 13sp / bar 11sp / hàng 22dp — to hơn bản đầu ~20% (user chốt 3/9).
private const val ROW_HEIGHT_DP = 22
/** Đệm dọc của mỗi trang (trên + dưới), cộng vào chiều cao pager. */
private const val PAGE_PAD_DP = 6
/** Cột số + đuôi của BlockBar, tính cả hai khoảng đệm: 6 + 46 + 4 + 92. */
private const val PANEL_RIGHT_W = 6 + 46 + 4 + 92

private fun rowMod(): Modifier = Modifier.height(ROW_HEIGHT_DP.dp)
private const val PANEL_TEXT_SP = 13
private const val PANEL_BAR_SP = 11
/** Đủ cho nhãn dài nhất "cl·week" (7 ô monospace ở 13sp). */
private const val PANEL_LABEL_W = 56

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

/**
 * Còn bao lâu nữa thì cửa sổ quota reset — thứ người dùng thật sự hỏi khi nhìn
 * bảng này ("bao giờ có credit lại"), chứ không phải mốc đồng hồ tuyệt đối:
 * "→ Sep 6" bắt tự trừ trong đầu, "2d4h" thì không.
 */
private fun resetIn(epoch: Long?, fallback: String): String {
    if (epoch == null) return fallback
    val left = epoch - System.currentTimeMillis() / 1000
    if (left <= 0) return "↺ now"
    val d = left / 86400
    val h = (left % 86400) / 3600
    val m = (left % 3600) / 60
    return when {
        d > 0 -> "↺${d}d${h}h"
        h > 0 -> "↺${h}h${m}m"
        else -> "↺${m}m"
    }
}

/** Tuổi của số quota, chữ ngắn để nằm gọn cạnh nút ⟳. */
private fun quotaAge(s: Long): String = when {
    s < 0 -> "no data"
    s < 90 -> "${s}s"
    s < 5400 -> "${s / 60}m"
    else -> "${s / 3600}h"
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

/** Thang màu chung cho MỌI dòng USAGE: càng CÒN ÍT càng gắt. */
private fun leftColor(leftPct: Int, c: com.jossephus.chuchu.ui.theme.ChuColorPalette): Color = when {
    leftPct <= 10 -> c.error
    leftPct <= 30 -> c.warning
    else -> c.accent
}

/** Như [leftColor] nhưng cho bốn số trên dải thu gọn (chữ, không phải thanh). */
private fun leftTone(leftPct: Double, c: com.jossephus.chuchu.ui.theme.ChuColorPalette): Color = when {
    leftPct <= 10 -> c.error
    leftPct <= 30 -> c.warning
    else -> c.textPrimary
}

/** ≥100 thì bỏ phần thập phân — "95.1/467.7G" vừa khít, "95.1/1024.3G" thì không. */
private fun g(kb: Long): String {
    val v = kb / 1048576.0
    return if (v >= 100) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)
}
private fun age(s: Long): String = if (s < 3600) "${s / 60}m ago" else "${s / 3600}h ago"

/** Mốc ms hiện tại, tự làm mới mỗi [periodMs] khi composable còn trên màn. */
@Composable
internal fun rememberTicking(periodMs: Long = 5_000L): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            kotlinx.coroutines.delay(periodMs)
            now.longValue = System.currentTimeMillis()
        }
    }
    return now
}
