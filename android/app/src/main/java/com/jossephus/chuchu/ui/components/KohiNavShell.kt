package com.jossephus.chuchu.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

/**
 * 4 tab gốc của app: HOSTS · FILES · DASHBOARD · QUEUE.
 *
 * Ngữ pháp trung tính kiểu Google Photos/Drive — KHÔNG có màu accent trong
 * bar: không chọn = icon outlined + textSecondary; đang chọn = icon filled +
 * textPrimary + viên pill surfaceVariant sau lưng. Cả bar chỉ dùng đúng ba
 * màu của palette (surface / surfaceVariant / hai tầng text) để không bao
 * giờ xung đột với theme đang bật.
 */
enum class KohiTab(val route: String, val contentDescription: String) {
    HOSTS("servers", "Hosts"),
    FILES("web", "Files"),
    DASHBOARD("dashboard", "Dashboard"),
    QUEUE("queue", "Queue"),
}

/** Đường net-worth thu nhỏ làm icon DASHBOARD — vẽ tay, không dùng glyph. */
@Composable
private fun CurveDashboardIcon(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val pts = arrayOf(
            Offset(0.04f * w, 0.68f * h),
            Offset(0.30f * w, 0.42f * h),
            Offset(0.55f * w, 0.58f * h),
            Offset(0.78f * w, 0.22f * h),
            Offset(0.96f * w, 0.34f * h),
        )
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun VectorIcon(vec: ImageVector, tint: Color) {
    // foundation.Image nhan ImageVector + colorTint: app thuan foundation,
    // khong co composable Icon cua material2/3.
    Image(
        imageVector = vec,
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(22.dp),
    )
}

/** Folder outline vẽ tay — material-icons-core khong co Folder. */
@Composable
private fun FolderIcon(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round)
        val p = Path().apply {
            // tab thu muc
            moveTo(0.10f * w, 0.30f * h)
            lineTo(0.34f * w, 0.30f * h)
            lineTo(0.42f * w, 0.40f * h)
            // than muc
            lineTo(0.90f * w, 0.40f * h)
            lineTo(0.90f * w, 0.74f * h)
            lineTo(0.10f * w, 0.74f * h)
            close()
        }
        drawPath(p, color = tint, style = stroke)
    }
}

@Composable
private fun tabIcon(tab: KohiTab, selected: Boolean, tint: Color) {
    when (tab) {
        KohiTab.HOSTS ->
            if (selected) VectorIcon(Icons.Filled.Home, tint) else VectorIcon(Icons.Outlined.Home, tint)
        KohiTab.FILES ->
            FolderIcon(tint)
        KohiTab.DASHBOARD -> CurveDashboardIcon(tint)
        KohiTab.QUEUE ->
            if (selected) VectorIcon(Icons.AutoMirrored.Filled.List, tint) else VectorIcon(Icons.AutoMirrored.Outlined.List, tint)
    }
}

/**
 * Một ô tab: viên pill (surfaceVariant) sau lưng khi đang chọn, icon phía
 * trên, badge số việc ở góc khi có. Kích thước 56x34dp gọn gàng, bo góc 10dp.
 */
@Composable
private fun KohiNavItem(
    tab: KohiTab,
    selected: Boolean,
    badge: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pillColor: Color? = null,
) {
    val colors = ChuColors.current
    val tint = if (selected) colors.textPrimary else colors.textSecondary
    val targetBgColor = if (selected) (pillColor ?: colors.surfaceVariant) else Color.Transparent
    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(200, easing = LinearOutSlowInEasing),
        label = "pillBgColor",
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = tab.contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(animatedBgColor),
            contentAlignment = Alignment.Center,
        ) {
            tabIcon(tab, selected, tint)
            if (badge != null && badge > 0) {
                // Badge số việc: nền border (trung tính), chữ primary — không accent.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.border)
                        .padding(horizontal = 3.dp),
                ) {
                    ChuText(
                        "$badge",
                        style = ChuTypography.current.labelSmall,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}

/** Rail trái cho màn rộng/ngang: icon dọc, cùng pill grammar. */
@Composable
private fun KohiSideRail(
    selectedRoute: String,
    queueBadge: Int?,
    onSelect: (KohiTab) -> Unit,
) {
    val colors = ChuColors.current
    Column(
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight()
            // Nền rail giữ nguyên CÙNG MÀU theme (background) khi mở rộng màn hình
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        KohiTab.entries.forEach { tab ->
            KohiNavItem(
                tab = tab,
                selected = selectedRoute == tab.route,
                badge = if (tab == KohiTab.QUEUE) queueBadge else null,
                onClick = { onSelect(tab) },
                pillColor = colors.surface,
                modifier = Modifier.size(width = 64.dp, height = 44.dp),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

/**
 * Vỏ điều hướng: width >= 600dp dùng rail trái, nhỏ hơn dùng bar dưới.
 * Nội dung (NavHost) luôn lấp phần còn lại.
 */
@Composable
fun KohiNavShell(
    selectedRoute: String?,
    queueBadge: Int?,
    onSelect: (KohiTab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Terminal va queue-mo-tu-terminal deu la fullscreen modal: an toan bo
    // chrome tab de back tu session-queue ve dung terminal va tab-switch khong
    // xen vao giua cap man hinh nay.
    val isFullscreenTerminal =
        selectedRoute?.startsWith("terminal") == true ||
            selectedRoute?.startsWith("session-queue") == true

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            if (isFullscreenTerminal) {
                // Fullscreen cho Terminal trên màn hình rộng / máy gập mở: Terminal chiếm 100% diện tích
                Box(modifier = Modifier.fillMaxSize()) { content() }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    KohiSideRail(
                        selectedRoute = selectedRoute ?: "",
                        queueBadge = queueBadge,
                        onSelect = onSelect,
                    )
                    Box(modifier = Modifier.weight(1f)) { content() }
                }
            }
        } else {
            if (isFullscreenTerminal) {
                // Fullscreen 100% cho Terminal trên màn hình điện thoại / compact:
                // Ẩn hoàn toàn Bottom Tab Bar, loại bỏ đệm đáy để Terminal vẽ tràn viền
                Box(modifier = Modifier.fillMaxSize()) { content() }
            } else {
                val density = LocalDensity.current
                val imeBottomPx = WindowInsets.ime.getBottom(density)
                val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
                val tabBarHeightPx = with(density) { 54.dp.roundToPx() }
                val closedBottomInsetPx = tabBarHeightPx + navBarBottomPx
                // Cơ chế Inset liên tục: bottom inset luôn là max(tabBar + navBar, imeBottom).
                // Khi bàn phím trượt lên/xuống, chiều cao di chuyển mượt mà liên tục, không bị
                // giật/khựng reflow layout do gắn/tháo view đột ngột.
                val effectiveBottomInsetPx = maxOf(closedBottomInsetPx, imeBottomPx)
                val effectiveBottomInsetDp = with(density) { effectiveBottomInsetPx.toDp() }

                val barAlpha by animateFloatAsState(
                    targetValue = if (imeBottomPx > closedBottomInsetPx) 0f else 1f,
                    animationSpec = tween(50, easing = LinearOutSlowInEasing),
                    label = "tabBarAlpha",
                )

                val stripColors = ChuColors.current

                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. Content chiếm toàn màn hình, được đẩy đáy theo effectiveBottomInsetDp
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = effectiveBottomInsetDp)
                            .consumeWindowInsets(PaddingValues(bottom = effectiveBottomInsetDp)),
                    ) { content() }

                    // 2. Thanh tab dưới đổi sang màu surface (khớp với accessory bar),
                    // fade mượt mà theo alpha khi bàn phím mở/đóng.
                    if (barAlpha > 0f) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .alpha(barAlpha)
                                .background(stripColors.surface)
                                .navigationBarsPadding(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KohiTab.entries.forEach { tab ->
                                    KohiNavItem(
                                        tab = tab,
                                        selected = selectedRoute == tab.route,
                                        badge = if (tab == KohiTab.QUEUE) queueBadge else null,
                                        onClick = { onSelect(tab) },
                                        pillColor = stripColors.surfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
