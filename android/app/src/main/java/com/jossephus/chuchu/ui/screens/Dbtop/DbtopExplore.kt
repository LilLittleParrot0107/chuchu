package com.jossephus.chuchu.ui.screens.Dbtop

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import com.jossephus.chuchu.data.model.explorer.ExplorerBuzz
import com.jossephus.chuchu.data.model.explorer.ExplorerGain
import com.jossephus.chuchu.data.model.explorer.ExplorerProject
import com.jossephus.chuchu.data.model.explorer.ExplorerState
import com.jossephus.chuchu.data.model.explorer.ExplorerTrend
import com.jossephus.chuchu.data.model.explorer.ExplorerYield
import com.jossephus.chuchu.data.network.GeminiTranslator
import com.jossephus.chuchu.data.repository.BuzzTranslationStore
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiBottomSheet
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.components.KohiSelectableRow
import com.jossephus.chuchu.ui.components.KohiSubTabs
import com.jossephus.chuchu.ui.components.RemoteLogo
import com.jossephus.chuchu.ui.theme.ChuColorPalette
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tab PROJ (27/9, mock chốt proto-build.html): 3 sub-tab PROJECTS · YIELD · BUZZ.
 * Dữ liệu từ out/explorer.json (mkt/explorer.py); đổi sub-tab bằng chạm — không
 * vuốt ngang vì pane nằm trong HorizontalPager của màn (pager sẽ nuốt cú vuốt).
 */
@Composable
internal fun ProjectsView(explorer: ExplorerState?, geminiKey: String) {
    if (explorer == null) {
        DashboardEmpty("NO EXPLORER DATA (SCAN PENDING)")
        return
    }
    var sub by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        KohiSubTabs(
            tabs = listOf(
                "PROJECTS" to explorer.projects.size,
                "YIELD" to explorer.yields.size,
                "BUZZ" to explorer.x.size,
            ),
            selectedIndex = sub.coerceIn(0, 2),
            onSelect = { sub = it },
        )
        Box(modifier = Modifier.weight(1f)) {
            when (sub.coerceIn(0, 2)) {
                0 -> ProjectsPane(explorer)
                1 -> YieldsPane(explorer)
                else -> BuzzPane(explorer, geminiKey)
            }
        }
    }
}

@Composable
private fun ProjectsPane(explorer: ExplorerState) {
    val colors = ChuColors.current
    var projectSheet by remember { mutableStateOf<ExplorerProject?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "band") {
            // 26/9 user: "project mới nó cứ vậy mấy ngày nay" — pipeline sắp theo ngày
            // niêm yết mới nhất trước; 27/9 bỏ đuôi "· NEWEST LISTED" (thừa, user chốt).
            KohiSectionBand(
                label = "NEW PROJECTS",
                meta = "UPDATED ${explorer.tsnp}",
                containerColor = colors.background,
            )
        }
        items(explorer.projects, key = { it.slug.ifBlank { it.name } }) { p ->
            ProjectRow(project = p, onClick = { projectSheet = p })
        }
        if (explorer.projects.isEmpty()) {
            item(key = "empty") { EmptyPane("NO PROJECT DATA · CHECK PIPELINE") }
        }
    }

    projectSheet?.let { p ->
        ProjectSheet(project = p, onDismiss = { projectSheet = null })
    }
}

@Composable
private fun YieldsPane(explorer: ExplorerState) {
    val colors = ChuColors.current
    var yieldSheet by remember { mutableStateOf<ExplorerYield?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "band") {
            KohiSectionBand(
                label = "YIELD",
                meta = "UPDATED ${explorer.tssc}",
                containerColor = colors.background,
            )
        }
        items(explorer.yields, key = { "${it.chain}|${it.project}|${it.name}" }) { y ->
            YieldRow(yield = y, onClick = { yieldSheet = y })
        }
        if (explorer.yields.isEmpty()) {
            item(key = "empty") { EmptyPane("NO YIELD DATA · CHECK PIPELINE") }
        }
    }

    yieldSheet?.let { y ->
        YieldSheet(yield = y, onDismiss = { yieldSheet = null })
    }
}

@Composable
private fun BuzzPane(explorer: ExplorerState, geminiKey: String) {
    val colors = ChuColors.current
    val context = LocalContext.current
    // Bản dịch nằm trong store theo tweet; tick này ép hàng đọc lại sau khi sheet dịch xong.
    var viTick by remember { mutableIntStateOf(0) }
    var buzzSheet by remember { mutableStateOf<ExplorerBuzz?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "band") {
            KohiSectionBand(
                // 26/9: buzz sắp mới → cũ theo ngày đăng; 27/9 bỏ đuôi "· NEWEST FIRST".
                label = "X BUZZ",
                meta = "UPDATED ${explorer.tsdc}",
                containerColor = colors.background,
            )
        }
        items(explorer.x, key = { it.name }) { b ->
            val vi = remember(b.translationKey(), viTick) {
                BuzzTranslationStore.get(context, b.translationKey())
            }
            BuzzCard(buzz = b, vi = vi, onClick = { buzzSheet = b })
        }
        if (explorer.x.isEmpty()) {
            item(key = "empty") { EmptyPane("NO X DATA · CHECK PIPELINE") }
        }
    }

    buzzSheet?.let { b ->
        BuzzSheet(
            buzz = b,
            geminiKey = geminiKey,
            onTranslated = { viTick++ },
            onDismiss = { buzzSheet = null },
        )
    }
}

/** Pane TRENDING của WATCH (mock 27/9): thứ tự dòng = hạng trending CoinGecko. */
@Composable
internal fun TrendingPane(explorer: ExplorerState) {
    val colors = ChuColors.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "band") {
            KohiSectionBand(
                label = "TRENDING",
                meta = explorer.tstr.takeIf { it.isNotBlank() }?.let { "UPDATED $it" },
                containerColor = colors.background,
            )
        }
        itemsIndexed(explorer.trend, key = { i, t -> "$i|${t.sym}" }) { _, t ->
            TrendRow(trend = t)
        }
        if (explorer.trend.isEmpty()) {
            item(key = "empty") { EmptyPane("NO TRENDING DATA · CHECK PIPELINE") }
        }
    }
}

/** Pane GAINERS của WATCH: 24h trước, phần chỉ lọt top 7 ngày xếp sau (pipeline sắp). */
@Composable
internal fun GainersPane(explorer: ExplorerState) {
    val colors = ChuColors.current
    var gainSheet by remember { mutableStateOf<ExplorerGain?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "band") {
            KohiSectionBand(
                label = "TOP GAINERS · 24H",
                meta = explorer.gmin?.let { "VOL ≥ " + DeFiFormatter.formatUsdCompact(it) } ?: "VOL ≥ $5M",
                containerColor = colors.background,
            )
        }
        items(explorer.gain, key = { it.asset ?: it.sym }) { g ->
            GainRow(gain = g, onClick = { gainSheet = g })
        }
        if (explorer.gain.isEmpty()) {
            item(key = "empty") { EmptyPane("NO GAINER DATA · CHECK PIPELINE") }
        }
    }

    gainSheet?.let { g ->
        GainerSheet(gain = g, onDismiss = { gainSheet = null })
    }
}

@Composable
private fun ProjectRow(
    project: ExplorerProject,
    onClick: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    KohiSelectableRow(
        selected = false,
        tone = colors.accent,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        RemoteLogo(url = project.img, fallback = "◆", tint = colors.accent)
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 26/9 user (mock chốt): dự án ≤7 ngày tuổi gắn nhãn NEW.
                project.age?.takeIf { it <= 7 }?.let {
                    Box(
                        modifier = Modifier
                            .background(colors.warning, RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChuText(
                            "NEW",
                            style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.onAccent,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                ChuText(
                    project.name,
                    style = type.label.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                ChuText(
                    DeFiFormatter.formatPercent(project.g7, decimals = 1),
                    style = type.label.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.Bold,
                    ),
                    color = pctColor(project.g7, colors),
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Dòng 2 chỉ còn chain + loại: bỏ tag action/age/flags (26/9) —
                // mấy thứ đó nằm trong tấm chi tiết, hàng để sleek.
                ChuText(
                    buildList {
                        project.chains.take(2).forEach { add(it) }
                        project.cat?.let { add(it) }
                    }.joinToString(" · "),
                    style = type.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Tuổi dự án (mock: "Flare · Risk Curators · 18D") — màu warning
                // để mắt bắt được cái mới, TVL vẫn nằm ngoài cùng phải.
                project.age?.let { age ->
                    Spacer(Modifier.width(6.dp))
                    ChuText(
                        "${age}D",
                        style = type.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontFeatureSettings = "tnum",
                            fontWeight = FontWeight.Bold,
                        ),
                        color = colors.warning,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(6.dp))
                ChuText(
                    "TVL " + DeFiFormatter.formatUsdCompact(project.tvl),
                    style = type.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                    ),
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun YieldRow(
    yield: ExplorerYield,
    onClick: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    KohiSelectableRow(
        selected = false,
        tone = colors.accentSecondary,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        RemoteLogo(url = yield.img, fallback = "⟡", tint = colors.accentSecondary)
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText(
                    yield.name,
                    style = type.label.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                ChuText(
                    yield.net?.let { "${String.format(Locale.US, "%.1f", it)}%" } ?: "—",
                    style = type.label.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.success,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText(
                    listOfNotNull(yield.chain, yield.project).joinToString(" · "),
                    style = type.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                ChuText(
                    yield.risk?.let { String.format(Locale.US, "safety %.2f", it) } ?: "",
                    style = type.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                    ),
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Dòng TRENDING (mock chốt 27/9): thứ tự = hạng trending, KHÔNG gắn #1/#2;
 * dòng 2 "#hạng vốn hoá · vol" — hạng là hạng vốn hoá CoinGecko, không phải vị trí dòng.
 */
@Composable
private fun TrendRow(trend: ExplorerTrend) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    KohiSelectableRow(
        selected = false,
        tone = colors.accent,
        onClick = null,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        RemoteLogo(url = trend.img, fallback = "◆", tint = colors.accent)
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText(
                    trend.sym,
                    style = type.label.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    maxLines = 1,
                )
                if (trend.name.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    ChuText(
                        trend.name,
                        style = type.labelSmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            ChuText(
                "#${trend.mcRank ?: "—"} · vol ${
                    trend.vol?.let { DeFiFormatter.formatUsdCompact(it) } ?: "—"
                }",
                style = type.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                ),
                color = colors.textMuted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            ChuText(
                DeFiFormatter.formatTokenPrice(trend.px),
                style = type.label.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.textPrimary,
                maxLines = 1,
            )
            ChuText(
                trend.chg?.let { DeFiFormatter.formatPercent(it, decimals = 1) } ?: "—",
                style = type.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Bold,
                ),
                color = pctColor(trend.chg, colors),
                maxLines = 1,
            )
        }
    }
}

/** Dòng GAINERS — cùng khuôn TRENDING (mock chốt: uniform, vol24 luôn ở dòng 2). */
@Composable
private fun GainRow(
    gain: ExplorerGain,
    onClick: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val line2 = buildString {
        append("#").append(gain.rank ?: "—")
        append(" · vol ").append(gain.vol24?.let { DeFiFormatter.formatUsdCompact(it) } ?: "—")
        gain.volx?.let { append(" · ×").append(String.format(Locale.US, "%.1f", it)) }
    }
    KohiSelectableRow(
        selected = false,
        tone = colors.accent,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        RemoteLogo(url = gain.img, fallback = "◆", tint = colors.accent)
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText(
                    gain.sym,
                    style = type.label.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    maxLines = 1,
                )
                if (gain.name.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    ChuText(
                        gain.name,
                        style = type.labelSmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            ChuText(
                line2,
                style = type.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                ),
                color = colors.textMuted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            ChuText(
                gain.chg24?.let { DeFiFormatter.formatPercent(it, decimals = 1) } ?: "—",
                style = type.label.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Bold,
                ),
                color = pctColor(gain.chg24, colors),
                maxLines = 1,
            )
            ChuText(
                gain.mcap?.let { DeFiFormatter.formatUsdCompact(it) } ?: "—",
                style = type.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                ),
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

/**
 * Thẻ BUZZ phẳng như row của app (user chốt 27/9: bỏ viền màu cạnh trái của mock đầu),
 * nền surface + viền hairline. Có bản dịch thì thân chuyển xanh + chip BẢN GỐC.
 */
@Composable
private fun BuzzCard(
    buzz: ExplorerBuzz,
    vi: String?,
    onClick: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val likes = buzz.likes?.let { if (it >= 1000) String.format(Locale.US, "%.1fk", it / 1000.0) else "$it" }
    val meta = listOfNotNull(buzzWhen(buzz.postTs), likes?.let { "♥ $it" }).joinToString(" · ")
    val body = vi ?: buzz.head
    ChuCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteLogo(url = buzz.img, fallback = "@", tint = colors.accent, size = 28.dp)
                Spacer(Modifier.width(8.dp))
                ChuText(
                    buzz.name,
                    style = type.label.copy(fontWeight = FontWeight.Bold),
                    color = colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (meta.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    ChuText(
                        meta,
                        style = type.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontFeatureSettings = "tnum",
                        ),
                        color = colors.textMuted,
                        maxLines = 1,
                    )
                }
            }
            if (body.isNotBlank()) {
                ChuText(
                    body,
                    style = type.bodySmall,
                    color = if (vi != null) colors.success else colors.textSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText(
                    "${buzz.n ?: 0} accounts" + if (buzz.launch) " · launch" else "",
                    style = type.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                )
                // Chip chỉ là dấu hiệu — chạm cả thẻ mở sheet, nút DỊCH thật nằm trong đó.
                Box(
                    modifier = Modifier
                        .border(1.dp, colors.accent, RoundedCornerShape(2.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    ChuText(
                        if (vi != null) "BẢN GỐC" else "DỊCH",
                        style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.accent,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectSheet(
    project: ExplorerProject,
    onDismiss: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    SheetFrame(
        title = project.name,
        subtitle = listOfNotNull(project.cat, project.chains.joinToString(" · ").ifBlank { null }).joinToString(" · "),
        onDismiss = onDismiss,
    ) {
        SheetGrid(
            listOfNotNull(
                "TVL" to DeFiFormatter.formatUsdCompact(project.tvl),
                "7D" to DeFiFormatter.formatPercent(project.g7, decimals = 1),
                "30D" to DeFiFormatter.formatPercent(project.g30, decimals = 1),
                "AGE" to (project.age?.let { "${it}D" } ?: "—"),
                "UP DAYS/14" to (project.up14?.let { "${kotlin.math.round(it * 14).toInt()}" } ?: "—"),
                "AUDITS" to (project.audits?.toString() ?: "—"),
            ),
        )
        project.why?.takeIf { it.isNotBlank() }?.let { why ->
            SheetSection("CALL")
            ChuText(why, style = type.bodySmall, color = colors.textSecondary)
        }
        if (project.desc.isNotBlank()) {
            SheetSection("ABOUT")
            ChuText(project.desc, style = type.bodySmall, color = colors.textSecondary)
        }
        if (project.points.isNotBlank()) {
            SheetSection("POINTS / NEWS")
            ChuText(project.points, style = type.bodySmall, color = colors.textSecondary)
        }
        if (project.tw != null || project.url != null) {
            SheetSection("LINKS")
            ChuText(
                listOfNotNull(project.tw?.let { "x.com/$it" }, project.url).joinToString("  ·  "),
                style = type.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun YieldSheet(
    yield: ExplorerYield,
    onDismiss: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    SheetFrame(
        title = yield.name,
        subtitle = listOfNotNull(yield.chain, yield.project).joinToString(" · "),
        onDismiss = onDismiss,
    ) {
        SheetGrid(
            listOfNotNull(
                "NET/YR" to (yield.net?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—"),
                "SAFETY" to (yield.risk?.let { String.format(Locale.US, "%.2f", it) } ?: "—"),
                "TVL" to DeFiFormatter.formatUsdCompact(yield.tvl),
                yield.lltv?.let { "LLTV" to "${kotlin.math.round(it * 100).toInt()}%" },
                yield.lev?.let { "LEVERAGE" to "${String.format(Locale.US, "%.1f", it)}×" },
                yield.bnet?.let { "BORROW NET" to "${String.format(Locale.US, "%.1f", it)}%" },
                yield.cy?.let { "COLL YIELD" to "${String.format(Locale.US, "%.1f", it)}%" },
            ),
        )
        if (yield.why.isNotBlank()) {
            SheetSection("DETAILS")
            ChuText(yield.why, style = type.bodySmall, color = colors.textSecondary)
        }
        if (yield.url != null) {
            SheetSection("LINKS")
            ChuText(yield.url, style = type.bodySmall, color = colors.textMuted)
        }
    }
}

/** Tấm chi tiết gainer (mock 27/9): grid 5 ô + DRIVER / VÌ SAO / HỌ, nút COINGECKO ↗. */
@Composable
private fun GainerSheet(
    gain: ExplorerGain,
    onDismiss: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val context = LocalContext.current
    SheetFrame(
        title = "${gain.sym} · ${gain.name}",
        subtitle = "#${gain.rank ?: "—"} · mcap ${DeFiFormatter.formatUsdCompact(gain.mcap)}",
        onDismiss = onDismiss,
    ) {
        SheetGrid(
            listOf(
                "VOL 24H" to (gain.vol24?.let { DeFiFormatter.formatUsdCompact(it) } ?: "—"),
                "VOL ×30D" to (gain.volx?.let { String.format(Locale.US, "×%.1f", it) } ?: "—"),
                "24H" to (gain.chg24?.let { DeFiFormatter.formatPercent(it, decimals = 1) } ?: "—"),
                "7D" to (gain.chg7d?.let { DeFiFormatter.formatPercent(it, decimals = 1) } ?: "—"),
                "30D" to (gain.chg30d?.let { DeFiFormatter.formatPercent(it, decimals = 1) } ?: "—"),
            ),
        )
        gain.driver?.takeIf { it.isNotBlank() }?.let { d ->
            SheetSection("DRIVER")
            ChuText(d, style = type.bodySmall, color = colors.textSecondary)
        }
        if (gain.why.isNotBlank()) {
            SheetSection("VÌ SAO")
            ChuText(gain.why, style = type.bodySmall, color = colors.textSecondary)
        }
        if (gain.family.isNotBlank()) {
            SheetSection("HỌ / PEER")
            ChuText(gain.family, style = type.bodySmall, color = colors.textSecondary)
        }
        gain.asset?.let { asset ->
            ChuButton(
                onClick = { openUrl(context, "https://www.coingecko.com/en/coins/$asset") },
                variant = ChuButtonVariant.Outlined,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                ChuText("COINGECKO ↗", style = type.label.copy(fontWeight = FontWeight.Bold), color = colors.accent)
            }
        }
    }
}

/**
 * Tấm chi tiết buzz (mock 27/9): POST 14/21, DỊCH ▸ → ĐANG DỊCH… → VI (badge xanh)
 * → BẢN GỐC; bản dịch cache theo tweet (BuzzTranslationStore), gọi Gemini bằng khoá
 * dán trong Settings. Bấm MỞ X mở bài gốc.
 */
@Composable
private fun BuzzSheet(
    buzz: ExplorerBuzz,
    geminiKey: String,
    onTranslated: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transKey = buzz.translationKey()
    var vi by remember(transKey) { mutableStateOf(BuzzTranslationStore.get(context, transKey)) }
    var showVi by remember(transKey) { mutableStateOf(vi != null) }
    var busy by remember(transKey) { mutableStateOf(false) }
    var failed by remember(transKey) { mutableStateOf(false) }

    SheetFrame(
        title = buzz.name,
        subtitle = listOfNotNull(
            buzzWhen(buzz.postTs),
            "♥ ${buzz.likes ?: 0}",
            "${buzz.n ?: 0} accounts",
            if (buzz.launch) "launch" else null,
        ).joinToString(" · "),
        badge = if (vi != null) "VI" else null,
        onDismiss = onDismiss,
    ) {
        val viet = if (showVi) vi else null
        if (viet != null) {
            SheetSection("POST · TIẾNG VIỆT")
            ChuText(viet, style = type.body, color = colors.success)
            ChuText(buzz.head, style = type.labelSmall, color = colors.textMuted)
        } else {
            SheetSection("POST")
            ChuText(buzz.head, style = type.body, color = colors.textSecondary)
        }
        if (buzz.by.isNotEmpty()) {
            SheetSection("MENTIONED BY (${buzz.by.size})")
            ChuText(buzz.by.joinToString("  "), style = type.bodySmall, color = colors.textSecondary)
        }
        if (buzz.yields.isNotEmpty()) {
            SheetSection("YIELDS")
            ChuText(buzz.yields.joinToString("  ·  "), style = type.bodySmall, color = colors.textSecondary)
        }
        if (buzz.url != null) {
            SheetSection("LINKS")
            ChuText(buzz.url, style = type.bodySmall, color = colors.textMuted)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChuButton(
                onClick = {
                    when {
                        busy -> Unit
                        vi != null -> showVi = !showVi
                        // Chưa có khoá: hint "CẦN KHOÁ GEMINI" ngay dưới hàng nút.
                        geminiKey.isBlank() -> failed = false
                        else -> scope.launch {
                            busy = true
                            failed = false
                            val out = withContext(Dispatchers.IO) { GeminiTranslator.translate(geminiKey, buzz.head) }
                            busy = false
                            if (out != null) {
                                BuzzTranslationStore.put(context, transKey, out)
                                vi = out
                                showVi = true
                                onTranslated()
                            } else {
                                failed = true
                            }
                        }
                    }
                },
                variant = ChuButtonVariant.Outlined,
                modifier = Modifier.weight(1f),
                enabled = !busy,
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                ChuText(
                    when {
                        busy -> "ĐANG DỊCH…"
                        vi != null && showVi -> "BẢN GỐC"
                        else -> "DỊCH ▸"
                    },
                    style = type.label.copy(fontWeight = FontWeight.Bold),
                    color = if (busy) colors.textMuted else colors.accent,
                )
            }
            ChuButton(
                onClick = { openUrl(context, buzz.url) },
                variant = ChuButtonVariant.Filled,
                modifier = Modifier.weight(1f),
                enabled = buzz.url != null,
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                ChuText(
                    "MỞ X ↗",
                    style = type.label.copy(fontWeight = FontWeight.Bold),
                    color = colors.onAccent,
                )
            }
        }
        if (geminiKey.isBlank()) {
            ChuText("CẦN KHOÁ GEMINI · SETTINGS", style = type.labelSmall, color = colors.warning)
        }
        if (failed) {
            ChuText("DỊCH LỖI — THỬ LẠI", style = type.labelSmall, color = colors.error)
        }
    }
}

@Composable
private fun SheetFrame(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    KohiBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(colors.surface)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChuText(
                            title,
                            style = type.title.copy(fontWeight = FontWeight.Bold),
                            color = colors.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (badge != null) {
                            Spacer(Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .background(colors.success, RoundedCornerShape(2.dp))
                                    .padding(horizontal = 3.dp),
                            ) {
                                ChuText(
                                    badge,
                                    style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onAccent,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        ChuText(
                            subtitle,
                            style = type.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                KohiCompactAction(label = "CLOSE", onClick = onDismiss)
            }
            content()
        }
    }
}

@Composable
private fun SheetSection(label: String) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    ChuText(
        label,
        style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = colors.accent,
        modifier = Modifier.padding(top = 3.dp),
    )
}

@Composable
private fun SheetGrid(cells: List<Pair<String, String>>) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    if (cells.isEmpty()) return
    ChuCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cells.chunked(3).forEach { rowCells ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowCells.forEach { (label, value) ->
                        Column(modifier = Modifier.weight(1f)) {
                            ChuText(label, style = type.labelSmall, color = colors.textMuted, maxLines = 1)
                            ChuText(
                                value,
                                style = type.label.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontFeatureSettings = "tnum",
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = colors.textPrimary,
                                maxLines = 1,
                            )
                        }
                    }
                    repeat(3 - rowCells.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun EmptyPane(text: String) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ChuText(text, style = type.labelSmall, color = colors.textMuted)
    }
}

/** Epoch giây (explorer.json "when") → "26/09 08:59" theo giờ máy. */
private fun buzzWhen(ts: Long?): String? =
    ts?.let { SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(it * 1000L)) }

private fun pctColor(value: Double?, colors: ChuColorPalette): Color = when {
    value == null -> colors.textMuted
    value >= 0 -> colors.success
    else -> colors.error
}

/** Khoá cache bản dịch — url bài là định danh bền nhất. */
private fun ExplorerBuzz.translationKey(): String =
    url ?: "$name|${postTs ?: 0L}"

private fun openUrl(context: Context, url: String?) {
    if (url.isNullOrBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
