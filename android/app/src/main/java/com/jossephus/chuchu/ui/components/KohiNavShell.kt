package com.jossephus.chuchu.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Canvas(modifier = Modifier.size(24.dp)) {
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
        modifier = Modifier.size(24.dp),
    )
}

/** Folder outline vẽ tay — material-icons-core khong co Folder. */
@Composable
private fun FolderIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
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
            if (selected) VectorIcon(Icons.Filled.List, tint) else VectorIcon(Icons.Outlined.List, tint)
    }
}

/**
 * Một ô tab: viên pill (surfaceVariant) sau lưng khi đang chọn, icon phía
 * trên, badge số việc ở góc khi có. Icon-box 56x40dp chuẩn M3.
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
    Box(
        modifier = modifier
            .size(width = 56.dp, height = 40.dp)
            .semantics { contentDescription = tab.contentDescription }
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) (pillColor ?: colors.surfaceVariant) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        tabIcon(tab, selected, tint)
        if (badge != null && badge > 0) {
            // Badge số việc: nền border (trung tính), chữ primary — không accent.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.border)
                    .padding(horizontal = 4.dp),
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

/** Bar dưới cho màn compact: 4 ô dàn đều, surface phủ cả gesture nav phía dưới. */
@Composable
private fun KohiBottomBar(
    selectedRoute: String,
    queueBadge: Int?,
    onSelect: (KohiTab) -> Unit,
) {
    val colors = ChuColors.current
    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KohiTab.entries.forEach { tab ->
                KohiNavItem(
                    tab = tab,
                    selected = selectedRoute == tab.route,
                    badge = if (tab == KohiTab.QUEUE) queueBadge else null,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(2.dp),
        )
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
            .width(72.dp)
            .fillMaxHeight()
            // Nen rail CUNG MAU theme (background), khong sang hon content —
            // thu vien pill moi la lop sang (surface = bac tren mocha).
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        KohiTab.entries.forEach { tab ->
            KohiNavItem(
                tab = tab,
                selected = selectedRoute == tab.route,
                badge = if (tab == KohiTab.QUEUE) queueBadge else null,
                onClick = { onSelect(tab) },
                pillColor = colors.surface,
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
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                KohiSideRail(
                    selectedRoute = selectedRoute ?: "",
                    queueBadge = queueBadge,
                    onSelect = onSelect,
                )
                Box(modifier = Modifier.weight(1f)) { content() }
            }
        } else {
            // IME mo -> an bar: neu khong, composer/terminal bi day cao THUA
            // dung chieu cao bar (ime inset do tu day manh, content lai bi
            // cat tai dinh bar) = dung dai trang giua content va keyboard.
            // Con navigationBars luon duoc tieu thu o content: bar tu xu ly
            // khi hien, keyboard de len khi an.
            val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .consumeWindowInsets(WindowInsets.navigationBars),
                ) { content() }
                if (!imeVisible) {
                    KohiBottomBar(
                        selectedRoute = selectedRoute ?: "",
                        queueBadge = queueBadge,
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}
