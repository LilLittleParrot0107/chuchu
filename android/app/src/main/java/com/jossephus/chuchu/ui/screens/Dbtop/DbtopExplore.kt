package com.jossephus.chuchu.ui.screens.Dbtop

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import com.jossephus.chuchu.data.model.explorer.ExplorerBuzz
import com.jossephus.chuchu.data.model.explorer.ExplorerCoin
import com.jossephus.chuchu.data.model.explorer.ExplorerProject
import com.jossephus.chuchu.data.model.explorer.ExplorerState
import com.jossephus.chuchu.data.model.explorer.ExplorerYield
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiBottomSheet
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.KohiSectionBand
import com.jossephus.chuchu.ui.components.KohiSelectableRow
import com.jossephus.chuchu.ui.components.RemoteLogo
import com.jossephus.chuchu.ui.theme.ChuColorPalette
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import java.util.Locale

/**
 * Hai tab khám phá trong Dashboard (26/9): PROJECTS gộp cả YIELD (user chốt
 * "gộp yield vào project"), BUZZ là X BUZZ. Dữ liệu từ out/explorer.json
 * (mkt/explorer.py) — màn chỉ vẽ, chạm hàng mở tấm chi tiết.
 */
@Composable
internal fun ProjectsView(explorer: ExplorerState?) {
    val colors = ChuColors.current
    if (explorer == null) {
        DashboardEmpty("NO EXPLORER DATA (SCAN PENDING)")
        return
    }
    var projectSheet by remember { mutableStateOf<ExplorerProject?>(null) }
    var yieldSheet by remember { mutableStateOf<ExplorerYield?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "market") {
            MarketStrip(explorer.market)
        }
        if (explorer.projects.isNotEmpty()) {
            item(key = "projects_band") {
                KohiSectionBand(
                    label = "NEW PROJECTS · TVL RISING",
                    meta = "UPDATED ${explorer.tsnp}",
                    containerColor = colors.background,
                )
            }
            items(explorer.projects, key = { it.slug.ifBlank { it.name } }) { p ->
                ProjectRow(project = p, onClick = { projectSheet = p })
            }
        }
        if (explorer.yields.isNotEmpty()) {
            item(key = "yields_band") {
                KohiSectionBand(
                    label = "YIELD · WORTH A LOOK",
                    meta = "UPDATED ${explorer.tssc}",
                    containerColor = colors.background,
                )
            }
            items(explorer.yields, key = { "${it.chain}|${it.project}|${it.name}" }) { y ->
                YieldRow(yield = y, onClick = { yieldSheet = y })
            }
        }
        if (explorer.projects.isEmpty() && explorer.yields.isEmpty()) {
            item(key = "empty") {
                EmptyPane("NO PROJECT / YIELD DATA · CHECK PIPELINE")
            }
        }
    }

    projectSheet?.let { p ->
        ProjectSheet(project = p, onDismiss = { projectSheet = null })
    }
    yieldSheet?.let { y ->
        YieldSheet(yield = y, onDismiss = { yieldSheet = null })
    }
}

@Composable
internal fun BuzzView(explorer: ExplorerState?) {
    val colors = ChuColors.current
    if (explorer == null) {
        DashboardEmpty("NO EXPLORER DATA (SCAN PENDING)")
        return
    }
    var buzzSheet by remember { mutableStateOf<ExplorerBuzz?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "buzz_band") {
            KohiSectionBand(
                label = "X BUZZ · RISING ACCOUNTS",
                meta = "UPDATED ${explorer.tsdc}",
                containerColor = colors.background,
            )
        }
        items(explorer.x, key = { it.name }) { b ->
            BuzzRow(buzz = b, onClick = { buzzSheet = b })
        }
        if (explorer.x.isEmpty()) {
            item(key = "empty") {
                EmptyPane("NO X DATA · CHECK PIPELINE")
            }
        }
    }

    buzzSheet?.let { b ->
        BuzzSheet(buzz = b, onDismiss = { buzzSheet = null })
    }
}

@Composable
private fun MarketStrip(coins: List<ExplorerCoin>) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    if (coins.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        coins.forEach { coin ->
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ChuText(coin.sym, style = type.labelSmall, color = colors.textMuted, maxLines = 1)
                ChuText(
                    DeFiFormatter.formatTokenPrice(coin.px),
                    style = type.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.textPrimary,
                    maxLines = 1,
                )
                coin.c24?.let {
                    ChuText(
                        DeFiFormatter.formatPercent(it, decimals = 1),
                        style = type.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontFeatureSettings = "tnum",
                        ),
                        color = if (it >= 0) colors.success else colors.error,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(
    project: ExplorerProject,
    onClick: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val actionColor = when (project.action) {
        "ENTRY" -> colors.success
        "SKIP" -> colors.error
        else -> colors.accent
    }
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
                project.action?.let {
                    ExTag(it, actionColor)
                    Spacer(Modifier.width(4.dp))
                }
                ChuText(
                    buildList {
                        project.cat?.let { add(it) }
                        project.age?.let { add("${it}D") }
                        project.chains.take(2).forEach { add(it) }
                        project.flags.take(2).forEach { add(it) }
                    }.joinToString(" · "),
                    style = type.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
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
    val lane = laneLabel(yield.lane)
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
                ExTag(lane, colors.accentSecondary)
                Spacer(Modifier.width(4.dp))
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

@Composable
private fun BuzzRow(
    buzz: ExplorerBuzz,
    onClick: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val likes = buzz.likes?.let { if (it >= 1000) String.format(Locale.US, "%.1fk", it / 1000.0) else "$it" }
    KohiSelectableRow(
        selected = false,
        tone = colors.accent,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        RemoteLogo(url = buzz.img, fallback = "@", tint = colors.accent)
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText(
                    buzz.name,
                    style = type.label.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                buzz.kind?.let {
                    ExTag(it.uppercase(), colors.textSecondary)
                }
                likes?.let {
                    Spacer(Modifier.width(4.dp))
                    ChuText(
                        "♥ $it",
                        style = type.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontFeatureSettings = "tnum",
                        ),
                        color = colors.textMuted,
                        maxLines = 1,
                    )
                }
            }
            val text = buzz.vi.ifBlank { buzz.head }
            if (text.isNotBlank()) {
                ChuText(
                    text,
                    style = type.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChuText(
                    "${buzz.n ?: 0} ACCOUNTS",
                    style = type.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (buzz.launch) {
                    ExTag("LAUNCH", colors.success)
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
            )
        )
        project.why?.takeIf { it.isNotBlank() }?.let { why ->
            SheetSection("CALL")
            Row(verticalAlignment = Alignment.CenterVertically) {
                project.action?.let {
                    ExTag(it, colors.accent)
                    Spacer(Modifier.width(4.dp))
                }
                ChuText(why, style = type.bodySmall, color = colors.textSecondary)
            }
        }
        if (project.flags.isNotEmpty() || project.nPools != null) {
            SheetSection("FLAGS")
            ChuText(
                buildList {
                    addAll(project.flags)
                    project.nPools?.let { add("$it pools ≥10%") }
                }.joinToString("  ·  "),
                style = type.bodySmall,
                color = colors.textSecondary,
            )
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
            )
        )
        if (yield.why.isNotBlank()) {
            SheetSection("DETAILS")
            ChuText(yield.why, style = type.bodySmall, color = colors.textSecondary)
        }
        if (yield.flags.isNotEmpty()) {
            SheetSection("FLAGS")
            ChuText(
                (yield.flags + listOfNotNull(laneLabel(yield.lane), yield.kind)).joinToString("  ·  "),
                style = type.bodySmall,
                color = colors.textSecondary,
            )
        }
        if (yield.url != null) {
            SheetSection("LINKS")
            ChuText(yield.url, style = type.bodySmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun BuzzSheet(
    buzz: ExplorerBuzz,
    onDismiss: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    SheetFrame(
        title = buzz.name,
        subtitle = listOfNotNull(buzz.kind, "♥ ${buzz.likes ?: 0}", "${buzz.n ?: 0} accounts").joinToString(" · "),
        onDismiss = onDismiss,
    ) {
        SheetSection("POST")
        if (buzz.vi.isNotBlank()) {
            ChuText(buzz.vi, style = type.bodySmall, color = colors.textSecondary)
            ChuText(buzz.head, style = type.labelSmall, color = colors.textMuted)
        } else {
            ChuText(buzz.head, style = type.bodySmall, color = colors.textSecondary)
        }
        if (buzz.by.isNotEmpty()) {
            SheetSection("MENTIONED BY (${buzz.by.size})")
            ChuText(buzz.by.joinToString("  "), style = type.bodySmall, color = colors.textSecondary)
        }
        if (buzz.yields.isNotEmpty()) {
            SheetSection("YIELDS")
            ChuText(buzz.yields.joinToString("  ·  "), style = type.bodySmall, color = colors.textSecondary)
        }
        if (buzz.launch) {
            ExTag("LAUNCH", colors.success)
        }
        if (buzz.url != null) {
            SheetSection("LINKS")
            ChuText(buzz.url, style = type.bodySmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun SheetFrame(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
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
                    ChuText(
                        title,
                        style = type.title.copy(fontWeight = FontWeight.Bold),
                        color = colors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
private fun ExTag(text: String, color: Color) {
    val type = ChuTypography.current
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        ChuText(text, style = type.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
private fun EmptyPane(text: String) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ChuText(text, style = type.labelSmall, color = colors.textMuted)
    }
}

private fun laneLabel(lane: String?): String = when (lane) {
    "carry" -> "CARRY"
    "stable" -> "STABLE"
    "lp" -> "LP"
    "new" -> "NEW"
    else -> lane?.uppercase() ?: ""
}

private fun pctColor(value: Double?, colors: ChuColorPalette): Color = when {
    value == null -> colors.textMuted
    value >= 0 -> colors.success
    else -> colors.error
}
