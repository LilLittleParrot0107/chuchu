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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import com.jossephus.chuchu.data.model.explorer.ExplorerBuzz
import com.jossephus.chuchu.data.model.explorer.ExplorerGain
import com.jossephus.chuchu.data.model.explorer.ExplorerProject
import com.jossephus.chuchu.data.model.explorer.ExplorerState
import com.jossephus.chuchu.data.network.GeminiTranslator
import com.jossephus.chuchu.data.repository.BuzzTranslationStore
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.KohiBottomSheet
import com.jossephus.chuchu.ui.components.KohiCompactAction
import com.jossephus.chuchu.ui.components.KohiSelectableRow
import com.jossephus.chuchu.ui.components.RemoteLogo
import com.jossephus.chuchu.ui.theme.CHU_HAIRLINE_ALPHA
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
 * PROJ: 3 sub-tab BUZZ · PROJECTS · YIELD là 3 TRANG của pager phẳng (28/9 BUZZ lên
 * đầu) — dải sub-tab do DbtopScreen vẽ, ở đây chỉ còn nội dung theo `sub`.
 * Dữ liệu từ out/explorer.json (mkt/explorer.py).
 */
@Composable
internal fun ProjectsSubPane(
    sub: Int,
    explorer: ExplorerState?,
    geminiKey: String,
    hiddenFollows: Set<String> = emptySet(),
    onHideFollow: (String) -> Unit = {},
    onUnhideAllFollows: () -> Unit = {},
) {
    if (explorer == null) {
        DashboardEmpty("NO EXPLORER DATA (SCAN PENDING)")
        return
    }
    when (sub.coerceIn(0, 2)) {
        0 -> BuzzPane(explorer, geminiKey)
        1 -> FollowPane(
            explorer = explorer,
            hidden = hiddenFollows,
            onHide = onHideFollow,
            onUnhideAll = onUnhideAllFollows,
        )
        else -> ProjectsPane(explorer)
    }
}

@Composable
private fun ProjectsPane(explorer: ExplorerState) {
    var projectSheet by remember { mutableStateOf<ExplorerProject?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
private fun BuzzPane(explorer: ExplorerState, geminiKey: String) {
    val context = LocalContext.current
    // Bản dịch nằm trong store theo tweet; tick này ép hàng đọc lại sau khi sheet dịch xong.
    var viTick by remember { mutableIntStateOf(0) }
    var buzzSheet by remember { mutableStateOf<ExplorerBuzz?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // 30/9 review: key theo name crash "Key was already used" khi 2 buzz cùng
        // account — key theo translationKey (url, fallback name|ts).
        items(explorer.x, key = { it.translationKey() }) { b ->
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

/**
 * FOLLOW (29/9, user): account MỚI toanh trên For You + user chưa follow + được ≥2
 * account user follow repost/nhắc (pipeline explorer.py `_follow`).
 * 30/9 (user chốt B): thẻ hiện bio luôn (pipeline scrape profile) — ấn AVATAR → X
 * để follow tay, thân thẻ không bấm (app không tự follow, chỉ đọc).
 */
@Composable
internal fun FollowPane(
    explorer: ExplorerState,
    hidden: Set<String> = emptySet(),
    onHide: (String) -> Unit = {},
    onUnhideAll: () -> Unit = {},
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val context = LocalContext.current
    // Ẩn theo handle thường (29/9 user: acc to không muốn follow) — pref local, khớp
    // cả @Handle lẫn handle.
    val norm = { h: String -> h.trim().lowercase().removePrefix("@") }
    // 30/9 review: normalize CẢ set ẩn (handle lưu thô @Foo/hoa là ẩn không dính) +
    // distinctBy chống pipeline trả trùng handle văng app.
    val hiddenNorm = remember(hidden) { hidden.map(norm).toSet() }
    val visible = remember(explorer.follow, hiddenNorm) {
        explorer.follow.filter { norm(it.handle) !in hiddenNorm }
            .distinctBy { norm(it.handle) }
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (hidden.isNotEmpty()) {
            item(key = "unhide") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onUnhideAll)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChuText(
                        "đang ẩn ${hidden.size} · BỎ ẨN",
                        style = type.labelSmall,
                        color = colors.textMuted,
                    )
                }
            }
        }
        items(visible, key = { it.handle }) { f ->
            val endorsers = f.endorsers.take(3).joinToString(" · ")
            // 30/9 review: handle rỗng thì avatar không bấm (trước rơi về x.com/).
            val goX = { openUrl(context, f.url?.takeIf { it.isNotBlank() } ?: xUrl(f.handle)) }
            val canX = f.handle.isNotBlank()
            // Tên hiển thị = display name pipeline scrape (rỗng trên data cũ → handle).
            val showName = f.name.takeIf { it.isNotBlank() && !it.equals(f.handle, ignoreCase = true) }
            KohiSelectableRow(
                selected = false,
                tone = colors.accent,
                onClick = null,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = if (canX) Modifier.clickable(onClick = goX) else Modifier,
                            contentAlignment = Alignment.Center,
                        ) {
                            RemoteLogo(url = f.img, fallback = "@", tint = colors.accent, size = 30.dp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (showName != null) {
                                ChuText(
                                    showName,
                                    style = type.label.copy(fontWeight = FontWeight.Bold),
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            ChuText(
                                "@${f.handle}",
                                style = if (showName != null) type.labelSmall else type.label.copy(fontWeight = FontWeight.Bold),
                                color = if (showName != null) colors.textMuted else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        ChuText(
                            "✕",
                            style = type.label.copy(fontWeight = FontWeight.Bold),
                            color = colors.textMuted,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clickable { onHide(f.handle) },
                        )
                    }
                    if (f.bio.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        ChuText(
                            f.bio,
                            style = type.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val stats = buildList {
                        if (f.followers.isNotBlank()) add("${f.followers} followers")
                        if (endorsers.isNotBlank()) add("via $endorsers")
                        if (f.nPosts > 0) add("${f.nPosts} posts")
                    }.joinToString(" · ")
                    if (stats.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        ChuText(
                            stats,
                            style = type.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontFeatureSettings = "tnum",
                            ),
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            item(key = "empty") { EmptyPane(if (explorer.follow.isEmpty()) "NO NEW ACCOUNTS · CHECK PIPELINE" else "ĐÃ ẨN HẾT · BỎ ẨN Ở TRÊN") }
        }
    }
}

/**
 * Pane WATCH (5/10, user: "gộp gainer vs token vào làm 1 tab, hiện hết token của mình
 * xong nối xuống gainer luôn"): MỘT LazyColumn — token của mình trước, dải mảnh
 * "GAINERS" làm mốc, rồi tới gainer; cuộn liền một mạch (không lồng LazyColumn).
 * Sheet chi tiết gainer giữ nguyên như pane GAINERS cũ.
 */
@Composable
internal fun WatchlistMergedPane(
    items: List<WatchlistTokenItem>,
    explorer: ExplorerState?,
    moneyDisplay: MoneyDisplay = MoneyDisplay.USD,
    vndRate: Double = 0.0,
) {
    if (items.isEmpty() && explorer == null) {
        DashboardEmpty("NO TOKENS IN WATCHLIST")
        return
    }
    val type = ChuTypography.current
    val colors = ChuColors.current
    var gainSheet by remember { mutableStateOf<ExplorerGain?>(null) }
    val gains = explorer?.gain ?: emptyList()
    val imgs = explorer?.imgs ?: emptyMap()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(items, key = { "tok:" + it.symbol }) { token ->
            WatchlistTokenRow(
                token = token,
                img = imgs[token.symbol],
                hold = if (token.totalUsd > 0.0) {
                    formatMoney(token.totalUsd, moneyDisplay, vndRate, compact = true)
                } else {
                    "—"
                },
            )
        }
        if (items.isEmpty()) {
            item(key = "empty-tok") { EmptyPane("NO TOKENS IN WATCHLIST") }
        }
        item(key = "gain-head") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ChuText("GAINERS", style = type.labelSmall, color = colors.textMuted)
                Box(
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(colors.border.copy(alpha = CHU_HAIRLINE_ALPHA)),
                )
            }
        }
        items(gains, key = { "gain:" + (it.asset ?: it.sym) }) { g ->
            GainRow(gain = g, onClick = { gainSheet = g })
        }
        if (gains.isEmpty()) {
            item(key = "empty-gain") {
                EmptyPane(if (explorer == null) "NO EXPLORER DATA (SCAN PENDING)" else "NO GAINER DATA · CHECK PIPELINE")
            }
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
            // 28/9 (user: gainer show cả giá, không chỉ %): giá lên đầu như TRENDING.
            ChuText(
                gain.px?.let { DeFiFormatter.formatTokenPrice(it) } ?: "—",
                style = type.label.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.textPrimary,
                maxLines = 1,
            )
            ChuText(
                gain.chg24?.let { DeFiFormatter.formatPercent(it, decimals = 1) } ?: "—",
                style = type.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Bold,
                ),
                color = pctColor(gain.chg24, colors),
                maxLines = 1,
            )
        }
    }
}

/**
 * Thẻ BUZZ nền trong suốt như các hàng khác, phân cách bằng vạch mỏng (user chốt 27/9:
 * "để cùng màu, phân cách bằng vạch mỏng như mấy cái kia" — trước là ChuCard nền surface
 * + viền). Có bản dịch thì thân chuyển xanh + chip BẢN GỐC.
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                // 28/9 (user: content buzz to lên xíu) bodySmall 12 -> body 14.
                // 1/10 (user: buzz detail dễ đọc hơn): $ticker xanh lá, @mention xanh dương.
                BasicText(
                    text = highlightBuzz(body, colors.success, colors.accentSecondary),
                    style = type.body.copy(color = if (vi != null) colors.success else colors.textSecondary),
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border.copy(alpha = CHU_HAIRLINE_ALPHA)),
        )
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
        val tw = project.tw?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
        val url = project.url?.takeIf { it.isNotBlank() }
        if (tw != null || url != null) {
            SheetSection("LINKS")
            tw?.let { LinkLine("x.com/$it", xUrl(it)) }
            url?.let { LinkLine(it, httpUrl(it)) }
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
            BasicText(
                text = highlightBuzz(viet, colors.success, colors.accentSecondary),
                style = type.body.copy(color = colors.success),
            )
            ChuText(buzz.head, style = type.labelSmall, color = colors.textMuted)
        } else {
            SheetSection("POST")
            BasicText(
                text = highlightBuzz(buzz.head, colors.success, colors.accentSecondary),
                style = type.body.copy(color = colors.textSecondary),
            )
        }
        if (buzz.by.isNotEmpty()) {
            SheetSection("MENTIONED BY (${buzz.by.size})")
            BasicText(
                text = highlightBuzz(buzz.by.joinToString("  "), colors.success, colors.accentSecondary),
                style = type.bodySmall.copy(color = colors.textSecondary),
            )
        }
        if (buzz.yields.isNotEmpty()) {
            SheetSection("YIELDS")
            ChuText(buzz.yields.joinToString("  ·  "), style = type.bodySmall, color = colors.textSecondary)
        }
        if (buzz.url != null) {
            SheetSection("LINKS")
            LinkLine(buzz.url, buzz.url)
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
                // 560.dp riêng từng sheet, đừng gộp const chung.
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

/** Dòng link bấm được trong tấm chi tiết (user 27/9: "link phải bấm đc"). */
@Composable
private fun LinkLine(label: String, url: String) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openUrl(context, url) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ChuText("↗", style = type.bodySmall, color = colors.accent)
        ChuText(
            label,
            style = type.bodySmall,
            color = colors.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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

/**
 * Tô màu thực thể trong text buzz (1/10 user: dễ đọc hơn): $ticker xanh lá,
 * @mention xanh dương, còn lại giữ màu style gọi. Cùng họ với highlightName của portal.
 */
internal fun highlightBuzz(text: String, ticker: Color, mention: Color): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    val marks = arrayOfNulls<Color>(text.length)
    val tickRe = Regex("\\\$[A-Za-z][A-Za-z0-9]{1,14}")
    val menRe = Regex("@[A-Za-z0-9_]{3,15}")
    for (m in tickRe.findAll(text)) {
        for (j in m.range) marks[j] = ticker
    }
    for (m in menRe.findAll(text)) {
        for (j in m.range) if (marks[j] == null) marks[j] = mention
    }
    return buildAnnotatedString {
        var start = 0
        while (start < text.length) {
            val c = marks[start]
            var end = start
            while (end < text.length && marks[end] == c) end++
            val seg = text.substring(start, end)
            if (c != null) withStyle(SpanStyle(color = c)) { append(seg) } else append(seg)
            start = end
        }
    }
}

private fun openUrl(context: Context, url: String?) {
    if (url.isNullOrBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Link thiếu scheme (vd "bittensor.com") vẫn phải mở được — thêm https. */
private fun httpUrl(raw: String): String {
    val t = raw.trim()
    return if (Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(t)) t else "https://$t"
}

/** handle X (explorer.json không kèm @) → link; lỡ là URL đầy đủ thì giữ nguyên. */
private fun xUrl(handle: String): String {
    val h = handle.trim().removePrefix("@")
    return if (h.startsWith("http://") || h.startsWith("https://")) h else "https://x.com/$h"
}
