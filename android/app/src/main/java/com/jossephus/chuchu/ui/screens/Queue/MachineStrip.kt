package com.jossephus.chuchu.ui.screens.Queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jossephus.chuchu.data.model.machine.MachineReadout
import com.jossephus.chuchu.data.model.machine.AgyAccount
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.noRippleClickable
import com.jossephus.chuchu.ui.screens.Files.MachineUiState
import com.jossephus.chuchu.ui.theme.CHU_HAIRLINE_ALPHA
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import java.util.Locale

/** Quá ngưỡng này thì số coi như nguội — làm mờ, ghi tuổi thật, không vẽ như số sống. */
private const val STALE_AFTER_S = 15L

/**
 * Số liếc của dải máy: bốn con + tuổi + vạch màu trái. Tách riêng vì hai nơi
 * dùng: dải preview trên compose box terminal và đầu trang MACHINE (25/9).
 */
private data class Glance(
    val ram: Double,
    val cpu: Double?,
    val h5: Int?,
    val wk: Int?,
    val ageS: Long,
    val stale: Boolean,
    val alpha: Float,
    val edge: Color,
)

@Composable
private fun glanceOf(readout: MachineReadout): Glance {
    val colors = ChuColors.current
    val s = readout.snapshot
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
    return Glance(ram, cpu, h5, wk, ageS, stale, alpha, edge)
}

/**
 * Trạng thái máy cho Queue.
 *
 * (25/9, user: "mang bảng usage/machine ra riêng"): bảng USAGE + MACHINE rời
 * khỏi đáy màn Queue sang tab MACHINE của pager — [asPage] = true. Dải preview
 * trên compose box terminal giữ nguyên. Dải ghim mở rộng (▾/▴ + panel đóng/mở
 * theo bàn phím) đi theo chỗ cũ — không còn nơi nào treo nó lên ô gõ nữa.
 */
@Composable
internal fun MachineStrip(
    state: MachineUiState,
    modifier: Modifier = Modifier,
    onUsageVisible: (Boolean) -> Unit = {},
    onRefreshUsage: () -> Unit = {},
    onSwitchAgyAccount: (String) -> Unit = {},
    /**
     * Chỉ xem nhanh: không caret, không bấm (dải trên compose box của terminal,
     * user chốt 4/9). Chưa có số thì vẫn vẽ hàng gạch "—" để compose box không
     * nhảy xuống 30dp lúc số về.
     */
    preview: Boolean = false,
    /**
     * Tab MACHINE của Queue (25/9): bảng USAGE + MACHINE luôn mở, xếp dọc,
     * cuộn được — không còn đóng/mở theo bàn phím, tab là bề mặt XEM.
     */
    asPage: Boolean = false,
) {
    val readout = state.readout
    if (readout == null) {
        when {
            asPage -> MachinePagePlaceholder(modifier)
            preview -> PreviewPlaceholder(modifier)
        }
        return
    }
    val glance = glanceOf(readout)
    if (asPage) {
        MachineTabPage(
            readout = readout,
            glance = glance,
            onUsageVisible = onUsageVisible,
            onRefreshUsage = onRefreshUsage,
            onSwitchAgyAccount = onSwitchAgyAccount,
            modifier = modifier,
        )
        return
    }
    GlanceRow(glance = glance, expandable = false, open = false, onToggle = {}, modifier = modifier)
}

/**
 * Bốn số liếc + tuổi số + vạch màu trái. [expandable] = false thì bỏ caret ▾/▴
 * và không bắt chạm (dải preview terminal, đầu trang MACHINE).
 */
@Composable
private fun GlanceRow(
    glance: Glance,
    expandable: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    /** Đầu trang MACHINE: bỏ nét kẻ trên — user 25/9, "khe trắng" dưới hàng tab. */
    topHairline: Boolean = true,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Sleek terminal (user 17/9): bỏ dải nền tối cộm — số vẫn nguyên,
            // phân tách bằng hairline trên như mock; vạch màu trái giữ vì nó
            // là tín hiệu liếc mắt, không phải trang trí.
            .background(colors.background)
            .then(
                if (topHairline) {
                    Modifier.drawBehind {
                        val stroke = 1.dp.toPx()
                        drawLine(
                            colors.border.copy(alpha = CHU_HAIRLINE_ALPHA),
                            Offset(0f, stroke / 2),
                            Offset(size.width, stroke / 2),
                            stroke,
                        )
                    }
                } else {
                    Modifier
                },
            )
            .then(if (expandable) Modifier.clickable(onClick = onToggle) else Modifier)
            .defaultMinSize(minHeight = 30.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).height(30.dp).background(glance.edge))
        Cell("RAM", pct(glance.ram), tone(glance.ram, 70.0, 85.0, colors), glance.alpha)
        Cell("CPU", glance.cpu?.let { pct(it) } ?: "—", tone(glance.cpu ?: 0.0, 70.0, 85.0, colors), glance.alpha)
        Cell("5H", glance.h5?.let { "$it%" } ?: "—", leftTone((glance.h5 ?: 100).toDouble(), colors), glance.alpha)
        Cell("WEEK", glance.wk?.let { "$it%" } ?: "—", leftTone((glance.wk ?: 100).toDouble(), colors), glance.alpha)
        Box(Modifier.weight(1f))
        ChuText(
            if (glance.stale) age(glance.ageS) else "${glance.ageS}s",
            style = type.labelSmall,
            color = (if (glance.stale) colors.warning else colors.textMuted).copy(alpha = glance.alpha),
        )
        if (expandable) {
            ChuText(
                if (open) " ▴" else " ▾",
                style = type.labelSmall,
                color = colors.accent,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

/**
 * Tab MACHINE (25/9): bảng USAGE + MACHINE xếp dọc, luôn mở hết. Hai trang
 * ngày trước (USAGE | MACHINE, vuốt qua lại) đổi thành hai mục — tab là chỗ
 * xem trọn bảng, không việc gì phải che một nửa sau một cú vuốt, và không
 * nhận vuốt ngang của pager ngoài.
 */
@Composable
private fun MachineTabPage(
    readout: MachineReadout,
    glance: Glance,
    onUsageVisible: (Boolean) -> Unit,
    onRefreshUsage: () -> Unit,
    onSwitchAgyAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    // Số quota chỉ làm mới khi CÓ AI NHÌN: tốn 5s + 380MB cho một tiến trình
    // claude. Tab mở là USAGE hiện nguyên — muốn số; rời tab thì thôi (user chốt
    // 3/9 cho trang USAGE, giữ nguyên ý đó khi dải lên tab).
    DisposableEffect(Unit) {
        onUsageVisible(true)
        onDispose { onUsageVisible(false) }
    }
    Column(modifier.fillMaxSize().background(colors.background)) {
        GlanceRow(glance = glance, expandable = false, open = false, onToggle = {}, topHairline = false)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = PAGE_PAD_DP.dp),
        ) {
            SectionLabel("USAGE")
            UsagePage(readout, glance.alpha)
            Spacer(Modifier.height(8.dp))
            SectionLabel("MACHINE")
            MachinePage(readout, glance.alpha)
        }
        // Làm mới / chuyển acc như chân trang panel cũ, ép sát mép phải.
        Box(Modifier.fillMaxWidth().height(FOOTER_HEIGHT_DP.dp)) {
            UsageFooterActions(
                readout = readout,
                onRefreshUsage = onRefreshUsage,
                onSwitchAgyAccount = onSwitchAgyAccount,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
            )
        }
    }
}

/** Nhãn mục của tab MACHINE — chữ mờ + hairline kéo hết bề ngang. */
@Composable
private fun SectionLabel(text: String) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ChuText(text, style = type.labelSmall.copy(letterSpacing = 0.6.sp), color = colors.textMuted)
        Box(Modifier.weight(1f).height(1.dp).background(colors.border.copy(alpha = CHU_HAIRLINE_ALPHA)))
    }
}

/** Chưa có số lúc vừa mở tab MACHINE: ghi rõ đang chờ thay vì màn trống. */
@Composable
private fun MachinePagePlaceholder(modifier: Modifier) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Box(modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        ChuText("LOADING MACHINE…", style = type.label, color = colors.textMuted)
    }
}

/** Hàng gạch cùng cỡ với dải thật, để chỗ sẵn trong lúc chờ nhịp poll đầu. */
@Composable
private fun PreviewPlaceholder(modifier: Modifier) {
    val colors = ChuColors.current
    Row(
        modifier = modifier.fillMaxWidth().background(colors.background)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    colors.border.copy(alpha = CHU_HAIRLINE_ALPHA),
                    Offset(0f, stroke / 2),
                    Offset(size.width, stroke / 2),
                    stroke,
                )
            }
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
private fun UsageFooterActions(
    readout: MachineReadout,
    onRefreshUsage: () -> Unit,
    onSwitchAgyAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val s = readout.snapshot
    val agy = s.agy
    val configuredAccs = agy?.accounts?.filter { it.configured } ?: emptyList()
    val curAcc = configuredAccs.firstOrNull { it.id == agy?.current }
    // Tài khoản đáng chuyển sang: quota khả dụng (min của 5h và tuần) cao hơn tài khoản hiện
    // tại rõ rệt. Hoà hoặc hơn không đáng kể thì giữ nguyên — đổi tài khoản là reload phiên.
    val switchTo = betterAgyAccount(configuredAccs, curAcc)

    val qts = readout.snapshot.claude?.dataTs ?: 0L
    val qAge = if (qts > 0) System.currentTimeMillis() / 1000 - qts else -1
    var refreshTapped by remember { mutableStateOf(false) }
    var switchTapped by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTapped) {
        if (refreshTapped) {
            kotlinx.coroutines.delay(6000)
            refreshTapped = false
        }
    }
    LaunchedEffect(switchTapped) {
        if (switchTapped) {
            kotlinx.coroutines.delay(3000)
            switchTapped = false
        }
    }

    val monoStyle = type.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        fontSize = 11.sp,
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Hai trạng thái rõ: tài khoản hiện tại đã tốt nhất → "acc2 ✓" mờ, KHÔNG bấm được
        // (bản trước bấm vào là gửi toggle mù → nhảy sang tài khoản kém hơn); có tài khoản
        // tốt hơn → "→ acc3 72%" vàng, bấm là chuyển đúng tới nó.
        if (curAcc != null || switchTo != null) {
            when {
                switchTapped -> ChuText("switching…", style = monoStyle, color = colors.accent)
                switchTo != null -> {
                    val pct = switchTo.effectivePct?.toInt()
                    ChuText(
                        text = if (pct != null && pct >= 0) "→ ${switchTo.id} $pct%" else "→ ${switchTo.id}",
                        style = monoStyle,
                        color = colors.accent,
                        modifier = Modifier.noRippleClickable {
                            switchTapped = true
                            onSwitchAgyAccount(switchTo.id)
                        },
                    )
                }
                else -> ChuText("${curAcc?.id ?: ""} ✓", style = monoStyle, color = colors.textMuted)
            }
            ChuText("·", style = monoStyle, color = colors.border)
        }

        ChuText(
            text = if (refreshTapped) "refreshing…" else "${quotaAge(qAge)} ⟳",
            style = monoStyle,
            color = colors.accent,
            modifier = Modifier.noRippleClickable(enabled = !refreshTapped) {
                refreshTapped = true
                onRefreshUsage()
            },
        )
    }
}

/**
 * Tài khoản agy đáng chuyển sang, hoặc null nếu tài khoản hiện tại đã tốt nhất (hoặc chưa
 * có gì để so). "Tốt hơn" = quota khả dụng ([AgyAccount.effectivePct], cửa sổ thắt nút giữa
 * 5h và tuần) cao hơn hiện tại ít nhất [minGainPct] điểm — đổi tài khoản là reload phiên, không
 * đáng vì vài phần trăm. Không có tài khoản hiện tại thì lấy tài khoản khả dụng nhất.
 */
internal fun betterAgyAccount(configured: List<AgyAccount>, current: AgyAccount?, minGainPct: Double = 5.0): AgyAccount? {
    val best = configured.filter { it.effectivePct != null }.maxByOrNull { it.effectivePct!! } ?: return null
    if (current == null) return best
    if (best.id == current.id) return null
    val cur = current.effectivePct ?: -1.0
    return if (best.effectivePct!! >= cur + minGainPct) best else null
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
    if (s.claude == null && s.agy == null && s.bai == null) {
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
        val left = a.effectivePct ?: return@forEach
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
    // b.ai (17/9): chi co SO DU, khong co han muc — khong co tong thi khong ve
    // duoc thanh (thanh can mot cai de so sanh). Nen day la dong CHU nhu topRam,
    // nhung van nam trong luoi cua BlockBar: nhan chiem cot nhan, so canh phai
    // dung cot phai. So khong rut gon: credit la tien that, doc duoc tung con.
    s.bai?.let { b ->
        Row(rowMod().fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            ChuText("bai·credit", style = rowStyle(), color = colors.accentSecondary.copy(alpha = alpha),
                maxLines = 1, modifier = Modifier.weight(1f))
            ChuText(
                if (b.ok) "${credits(b.balance)} cr" else "—",
                style = rowStyle().copy(textAlign = TextAlign.End),
                color = (if (b.ok) colors.textPrimary else colors.textMuted).copy(alpha = alpha),
                maxLines = 1, modifier = Modifier.width(PANEL_RIGHT_W.dp),
            )
        }
    }
}

/** Chiều cao MỖI hàng, khoá cứng qua [rowMod] — không còn "xấp xỉ" (13/9). */
// Chữ 13sp / bar 11sp / hàng 22dp — to hơn bản đầu ~20% (user chốt 3/9).
private const val ROW_HEIGHT_DP = 22
/** Đệm dọc quanh bảng trong tab MACHINE. */
private const val PAGE_PAD_DP = 6
/** Chân trang (nút làm mới / chuyển acc): labelSmall lineHeight 16 + đệm dọc 2×2. */
private const val FOOTER_HEIGHT_DP = 20
/** Cột số + đuôi của BlockBar, tính cả hai khoảng đệm: 6 + 46 + 4 + 92. */
private const val PANEL_RIGHT_W = 6 + 46 + 4 + 92

private fun rowMod(): Modifier = Modifier.height(ROW_HEIGHT_DP.dp)
private const val PANEL_TEXT_SP = 13
private const val PANEL_BAR_SP = 11
/** Đủ cho nhãn dài nhất "cl·week" (7 ô monospace ở 13sp). */
private const val PANEL_LABEL_W = 56

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

/** 14275165 -> "14,275,165" — so Credit that, khong rut gon kieu 14.3M (17/9). */
internal fun credits(v: Long): String = String.format(Locale.US, "%,d", v)

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
