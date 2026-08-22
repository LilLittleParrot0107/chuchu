package com.jossephus.chuchu.ui.screens.Queue

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuCard
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.ChuTextField
import com.jossephus.chuchu.ui.components.TuiBadge
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import kotlin.math.roundToInt

// =========================================================================
// 1. DESIGN SYSTEM COLOR TOKENS DÀNH CHO QUEUE SCREEN (PHASE 1)
// =========================================================================
object QueuePalette {
    val FgPrimary = Color(0xFFF1F5F9)         // Slate 100
    val FgSecondary = Color(0xFF94A3B8)       // Slate 400
    val FgMuted = Color(0xFF64748B)           // Slate 500

    // Status Colors (Phân cấp quang phổ rõ rệt)
    val Working = Color(0xFF38BDF8)           // Cyan Neon (Đang chạy - 0.5s glance)
    val WorkingGlow = Color(0xFF0284C7)       // Glow cyan
    val Blocked = Color(0xFFFF9100)           // Amber Vivid (Cần duyệt quyền / "cần anh")
    val Error = Color(0xFFFF5252)             // Red Crimson (Lỗi/Thất bại)
    val Pending = Color(0xFF94A3B8)           // Slate Grey (Đang chờ - phân biệt rõ với Xong)
    val Done = Color(0xFF475569)              // Muted Slate (Đã hoàn tất - de-emphasized)
    val Gold = Color(0xFFF6C17E)              // Gold accent

    // Surfaces & Elevations
    val BandBg = Color(0xFF161B26)            // Nền header/footer band
    val SurfaceIdle = Color.Transparent       // Nền hàng bình thường
    val SurfaceActive = Color(0xFF1E293B)     // Nền nổi tầng 1 (Card Active)
    val SurfaceTray = Color(0xFF0F172A)       // Nền lồng tầng 2 (Action Tray)
    val BorderActive = Color(0xFF38BDF8)      // Viền sáng khi active
    val BorderIdle = Color(0xFF242E42)        // Viền hairline ngăn cách nhẹ
    val LogBg = Color(0xFF0F1117)
}

// Bảng màu tương thích ngược
private val QQ_FG = QueuePalette.FgPrimary
private val QQ_DIM = QueuePalette.FgSecondary
private val QQ_ACC = QueuePalette.Working
private val QQ_WARN = QueuePalette.Blocked
private val QQ_ERR = QueuePalette.Error
private val QQ_GOLD = QueuePalette.Gold
private val QQ_BAND = QueuePalette.BandBg
private val QQ_SEL = QueuePalette.SurfaceActive
private val QQ_LOG_BG = QueuePalette.LogBg

/** Chuyển đổi QueueTone sang Color Token chuẩn */
fun QueueTone.resolveColor(stateStr: String? = null): Color {
    val s = stateStr?.lowercase() ?: ""
    return when {
        s.contains("block") || s.contains("wait") || s.contains("perm") || this == QueueTone.Warn -> QueuePalette.Blocked
        s.contains("work") || s.contains("run") || s.contains("sent") || s.contains("sending") || this == QueueTone.Accent -> QueuePalette.Working
        s.contains("done") || s.contains("comp") || this == QueueTone.Ok -> QueuePalette.Done
        s.contains("fail") || s.contains("err") || this == QueueTone.Error -> QueuePalette.Error
        else -> QueuePalette.Pending
    }
}

private fun QueueTone.color(): Color = resolveColor()

// =========================================================================
// 2. ENUMS & DATA MODELS LỌC TRẠNG THÁI (PHASE 1)
// =========================================================================
enum class TaskStatusFilter(
    val label: String,
    val targetStates: Set<String>,
) {
    ALL("Tất cả", emptySet()),
    RUNNING("Đang chạy", setOf("sent", "sending", "working", "busy")),
    BLOCKED("Cần duyệt", setOf("blocked", "unsure", "perm", "waiting")),
    PENDING("Đang chờ", setOf("pending")),
    DONE("Đã xong", setOf("done", "completed")),
    FAILED("Thất bại", setOf("failed", "error"));

    fun matches(taskState: String): Boolean =
        this == ALL || taskState.lowercase() in targetStates
}

// =========================================================================
// 3. SMART PROMPT SYNTAX HIGHLIGHTER & SEARCH HIGHLIGHT (PHASE 4)
// =========================================================================
object PromptSyntaxHighlighter {
    private val ACTION_PATTERN = Pattern.compile(
        "\\b(?i)(fix|feat|test|build|refactor|add|rm|delete|update|chore|perf|docs|revert|clean|deploy|run)(?:\\([^)]+\\))?:?",
        Pattern.CASE_INSENSITIVE
    )

    private val FILE_PATH_PATTERN = Pattern.compile(
        "(?:^|[\\s\"'`(])((?:[./~\\w\\-]+\\/[\\w.\\-]+)|(?:[\\w.\\-]+\\.(?:kt|java|ts|tsx|js|jsx|py|zig|rs|go|c|cpp|h|json|yml|yaml|md|toml|sql|sh|xml|gradle)))(?=[\\s\"'`)]|$)"
    )

    private val BASH_COMMAND_PATTERN = Pattern.compile(
        "`([^`]+)`|\\b(git|gradlew|./gradlew|npm|npx|cargo|docker|pytest|zig|make|curl)\\s+([-\\w./]+)"
    )

    fun highlight(text: String, isDark: Boolean = true): AnnotatedString {
        return buildAnnotatedString {
            append(text)

            val verbColor = if (isDark) Color(0xFFFAB387) else Color(0xFFD97706) // Cam
            val pathColor = if (isDark) Color(0xFF89B4FA) else Color(0xFF2563EB) // Xanh dương
            val cmdColor = if (isDark) Color(0xFFA6E3A1) else Color(0xFF059669)  // Xanh lá
            val codeBgColor = if (isDark) Color(0x3345475A) else Color(0x1A000000)

            // 1. Highlight Action Verbs
            val verbMatcher = ACTION_PATTERN.matcher(text)
            while (verbMatcher.find()) {
                addStyle(
                    SpanStyle(color = verbColor, fontWeight = FontWeight.Bold),
                    verbMatcher.start(),
                    verbMatcher.end(),
                )
            }

            // 2. Highlight File Paths
            val pathMatcher = FILE_PATH_PATTERN.matcher(text)
            while (pathMatcher.find()) {
                val start = pathMatcher.start(1)
                val end = pathMatcher.end(1)
                if (start in 0..end && end <= text.length) {
                    addStyle(
                        SpanStyle(color = pathColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium),
                        start,
                        end,
                    )
                }
            }

            // 3. Highlight Bash Commands
            val cmdMatcher = BASH_COMMAND_PATTERN.matcher(text)
            while (cmdMatcher.find()) {
                addStyle(
                    SpanStyle(color = cmdColor, background = codeBgColor, fontFamily = FontFamily.Monospace),
                    cmdMatcher.start(),
                    cmdMatcher.end(),
                )
            }
        }
    }
}

/** Dựng AnnotatedString tự động highlight các cụm từ trùng khớp với search query */
fun buildHighlightAnnotatedString(
    text: String,
    query: String,
    baseColor: Color,
    highlightColor: Color = QueuePalette.Gold,
    highlightBackground: Color = QueuePalette.Gold.copy(alpha = 0.28f),
): AnnotatedString {
    if (query.isBlank()) {
        return AnnotatedString(text, SpanStyle(color = baseColor))
    }

    val trimmedQuery = query.trim()
    val pattern = Pattern.compile(Pattern.quote(trimmedQuery), Pattern.CASE_INSENSITIVE)
    val matcher = pattern.matcher(text)

    return buildAnnotatedString {
        var lastIndex = 0
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            if (start > lastIndex) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(text.substring(lastIndex, start))
                }
            }

            withStyle(
                SpanStyle(
                    color = highlightColor,
                    background = highlightBackground,
                    fontWeight = FontWeight.Bold,
                )
            ) {
                append(text.substring(start, end))
            }
            lastIndex = end
        }

        if (lastIndex < text.length) {
            withStyle(SpanStyle(color = baseColor)) {
                append(text.substring(lastIndex))
            }
        }
    }
}

@Composable
fun HighlightedText(
    text: String,
    query: String,
    style: TextStyle,
    baseColor: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val annotatedString = remember(text, query, baseColor) {
        buildHighlightAnnotatedString(
            text = text,
            query = query,
            baseColor = baseColor,
            highlightColor = QueuePalette.Gold,
            highlightBackground = QueuePalette.Gold.copy(alpha = 0.28f),
        )
    }

    BasicText(
        text = annotatedString,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

// =========================================================================
// 4. REAL-TIME FEEDBACK: LIVE TIMER & PROGRESS SHIMMER (PHASE 3)
// =========================================================================
@Composable
fun rememberLiveElapsedSeconds(
    startEpochSeconds: Long?,
    isRunning: Boolean,
): State<Long> {
    val elapsed = remember(startEpochSeconds, isRunning) {
        mutableLongStateOf(
            if (startEpochSeconds != null && startEpochSeconds > 0) {
                maxOf(0L, (System.currentTimeMillis() / 1000L) - startEpochSeconds)
            } else 0L,
        )
    }

    LaunchedEffect(startEpochSeconds, isRunning) {
        if (!isRunning || startEpochSeconds == null || startEpochSeconds <= 0L) {
            return@LaunchedEffect
        }
        while (isActive) {
            val nowSec = System.currentTimeMillis() / 1000L
            elapsed.longValue = maxOf(0L, nowSec - startEpochSeconds)
            delay(1000L)
        }
    }

    return elapsed
}

fun formatElapsedDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    val h = m / 60
    return if (h > 0) {
        val remM = m % 60
        String.format("%02d:%02d:%02ds", h, remM, s)
    } else {
        String.format("%02d:%02ds", m, s)
    }
}

@Composable
fun ShimmerProgressBar(
    modifier: Modifier = Modifier,
    height: Dp = 2.5.dp,
    trackColor: Color = Color(0xFF1E1E2E),
    highlightColor: Color = QueuePalette.Working,
) {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawWithCache {
                val brush = Brush.linearGradient(
                    colors = listOf(
                        trackColor,
                        highlightColor.copy(alpha = 0.85f),
                        trackColor,
                    ),
                    start = Offset(x = translateAnim * size.width - size.width, y = 0f),
                    end = Offset(x = translateAnim * size.width, y = 0f),
                )
                onDrawWithContent {
                    drawRect(color = trackColor)
                    drawRect(brush = brush)
                }
            },
    )
}

@Composable
fun LiveTerminalPeek(
    lines: List<String>,
    isLoading: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = ChuTypography.current
    val recentLines = remember(lines, isExpanded) {
        if (isExpanded) lines.takeLast(8) else lines.takeLast(2)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor_alpha",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
            .background(Color(0xFF0C0E14))
            .border(1.dp, QueuePalette.Working.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ChuText(">", style = type.labelSmall, color = Color(0xFFA6E3A1))
                ChuText(
                    if (isExpanded) "terminal output (expanded)" else "live stream",
                    style = type.labelSmall.copy(fontSize = 10.sp),
                    color = QueuePalette.FgMuted,
                )
                if (isLoading) {
                    ChuText("⟳", style = type.labelSmall, color = QueuePalette.Gold)
                }
            }
            ChuText(
                if (isExpanded) "▲ thu gọn" else "▼ mở rộng",
                style = type.labelSmall.copy(fontSize = 9.sp),
                color = QueuePalette.Working,
            )
        }

        Spacer(Modifier.height(4.dp))

        if (recentLines.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChuText(
                    "Đang kết nối pty daemon...",
                    style = type.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    ),
                    color = QueuePalette.FgMuted,
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(11.dp)
                        .background(QueuePalette.Working.copy(alpha = cursorAlpha)),
                )
            }
        } else {
            recentLines.forEachIndexed { index, line ->
                val isLast = index == recentLines.lastIndex
                Row(modifier = Modifier.fillMaxWidth()) {
                    ChuText(
                        line,
                        style = type.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        ),
                        color = when {
                            line.contains("ERROR", true) || line.contains("fail", true) -> QueuePalette.Error
                            line.contains("WARN", true) -> QueuePalette.Blocked
                            line.contains("✓") || line.contains("DONE", true) -> Color(0xFFA6E3A1)
                            line.startsWith("$") || line.startsWith(">") -> QueuePalette.Working
                            else -> QueuePalette.FgSecondary
                        },
                        maxLines = if (isExpanded) 4 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isLast && !isExpanded) {
                        Spacer(Modifier.width(3.dp))
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(11.dp)
                                .background(QueuePalette.Working.copy(alpha = cursorAlpha))
                                .align(Alignment.CenterVertically),
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 5. SWIPE-TO-ACTION GESTURES (PHASE 4)
// =========================================================================
@Composable
fun SwipeableTaskContainer(
    taskId: Int,
    enabled: Boolean = true,
    onSwipeTop: () -> Unit,
    onSwipeDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val maxSwipePx = with(density) { 110.dp.toPx() }
    val thresholdPx = with(density) { 68.dp.toPx() }

    val swipeOffsetX = remember(taskId) { Animatable(0f) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(QueuePalette.BandBg),
    ) {
        val currentOffset = swipeOffsetX.value
        val isSwipingRight = currentOffset > 0
        val isSwipingLeft = currentOffset < 0
        val isOverThreshold = kotlin.math.abs(currentOffset) >= thresholdPx

        if (isSwipingRight) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(if (isOverThreshold) QueuePalette.Working else QueuePalette.Working.copy(alpha = 0.45f))
                    .padding(start = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChuText("▲", style = ChuTypography.current.label, color = Color.Black)
                    ChuText(
                        if (isOverThreshold) "LÊN ĐẦU HÀNG" else "Ưu tiên",
                        style = ChuTypography.current.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black,
                    )
                }
            }
        } else if (isSwipingLeft) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(if (isOverThreshold) QueuePalette.Error else QueuePalette.Error.copy(alpha = 0.45f))
                    .padding(end = 14.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChuText(
                        if (isOverThreshold) "XOÁ TÁC VỤ" else "Xoá",
                        style = ChuTypography.current.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    ChuText("✕", style = ChuTypography.current.label, color = Color.White)
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .pointerInput(taskId, enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val next = (swipeOffsetX.value + dragAmount).coerceIn(-maxSwipePx, maxSwipePx)
                            val willBeOver = kotlin.math.abs(next) >= thresholdPx
                            if (willBeOver && !hasTriggeredHaptic) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                hasTriggeredHaptic = true
                            } else if (!willBeOver && hasTriggeredHaptic) {
                                hasTriggeredHaptic = false
                            }
                            scope.launch { swipeOffsetX.snapTo(next) }
                        },
                        onDragEnd = {
                            val finalOffset = swipeOffsetX.value
                            scope.launch {
                                if (finalOffset >= thresholdPx) {
                                    onSwipeTop()
                                } else if (finalOffset <= -thresholdPx) {
                                    onSwipeDelete()
                                }
                                hasTriggeredHaptic = false
                                swipeOffsetX.animateTo(0f, animationSpec = tween(150))
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                hasTriggeredHaptic = false
                                swipeOffsetX.animateTo(0f, animationSpec = tween(150))
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}

// =========================================================================
// 6. UI COMPONENTS: FILTER CHIPS BAR & ANIMATED BADGE (PHASE 1)
// =========================================================================
@Composable
fun QueueFilterChipsBar(
    selectedFilter: TaskStatusFilter,
    onFilterSelected: (TaskStatusFilter) -> Unit,
    statusCounts: Map<TaskStatusFilter, Int>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val type = ChuTypography.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(QueuePalette.BandBg)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskStatusFilter.entries.forEach { filter ->
            val count = statusCounts[filter] ?: 0
            val isSelected = filter == selectedFilter
            val activeColor = when (filter) {
                TaskStatusFilter.ALL -> QueuePalette.FgPrimary
                TaskStatusFilter.RUNNING -> QueuePalette.Working
                TaskStatusFilter.BLOCKED -> QueuePalette.Blocked
                TaskStatusFilter.PENDING -> QueuePalette.Gold
                TaskStatusFilter.DONE -> QueuePalette.Done
                TaskStatusFilter.FAILED -> QueuePalette.Error
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isSelected) activeColor.copy(alpha = 0.18f) else Color.Transparent)
                    .border(
                        width = if (isSelected) 1.dp else 0.5.dp,
                        color = if (isSelected) activeColor else QueuePalette.BorderIdle,
                        shape = RoundedCornerShape(3.dp),
                    )
                    .clickable { onFilterSelected(filter) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ChuText(
                        text = filter.label,
                        style = type.labelSmall.copy(fontSize = 11.sp),
                        color = if (isSelected) activeColor else QueuePalette.FgSecondary,
                    )
                    if (count > 0 || filter == TaskStatusFilter.ALL) {
                        ChuText(
                            text = "($count)",
                            style = type.labelSmall.copy(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = if (isSelected) activeColor else QueuePalette.FgMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedTuiBadge(
    text: String,
    tone: QueueTone,
    stateString: String? = null,
    modifier: Modifier = Modifier,
) {
    val typography = ChuTypography.current
    val baseColor = tone.resolveColor(stateString)
    val isWorking = baseColor == QueuePalette.Working
    val isBlocked = baseColor == QueuePalette.Blocked
    val isDone = baseColor == QueuePalette.Done

    val transition = rememberInfiniteTransition(label = "badgeAnim")

    val glowAlpha by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    val pulseBorderAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseBorder",
    )

    val bgAlpha = when {
        isWorking -> glowAlpha
        isBlocked -> (pulseBorderAlpha * 0.25f)
        isDone -> 0.05f
        else -> 0.12f
    }

    val borderAlpha = when {
        isWorking -> 0.8f
        isBlocked -> pulseBorderAlpha
        isDone -> 0.3f
        else -> 0.6f
    }

    val iconPrefix = when {
        isBlocked -> "⚠ "
        isWorking -> "● "
        isDone -> "✔ "
        else -> "○ "
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(baseColor.copy(alpha = bgAlpha))
            .border(
                width = if (isBlocked) 1.5.dp else 1.dp,
                color = baseColor.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(3.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ChuText(
                iconPrefix + text.uppercase(),
                style = typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = if (isBlocked || isWorking) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.4.sp,
                ),
                color = if (isDone) QueuePalette.Done else baseColor,
            )
        }
    }
}

// =========================================================================
// 7. MAIN QUEUE SCREEN (PHASES 1, 2, 3, 4)
// =========================================================================
@Composable
fun QueueScreen(
    ui: QueueUiState,
    onAction: (QueueAction, Int?) -> Unit,
    onAdd: (String, String?, String?) -> Unit,
    onClearDone: (String?) -> Unit,
    onRefresh: () -> Unit,
    onFetchLogs: (Int) -> Unit = {},
    onConsumeToast: () -> Unit = {},
    currentUrl: String,
    currentToken: String,
    onSaveConfig: (String, String) -> Unit,
    onFetchResponse: (suspend (Int) -> String?)? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val context = LocalContext.current

    var configOpen by remember { mutableStateOf(false) }
    var logsOpen by remember { mutableStateOf(false) }
    var inspectedTask by remember { mutableStateOf<QueueTask?>(null) }
    var selectedPane by remember { mutableStateOf<String?>(null) }
    var selectedStatusFilter by remember { mutableStateOf(TaskStatusFilter.ALL) }
    var selectedTask by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var localToast by remember { mutableStateOf<String?>(null) }

    val showConfig = configOpen || ui.needsSetup
    val agents = ui.state.agents

    val pane = selectedPane?.takeIf { p -> p == "ALL" || agents.any { it.pane == p } }
        ?: agents.firstOrNull()?.pane ?: "ALL"

    val paneBaseTasks = remember(ui.state.tasks, pane) {
        if (pane == "ALL") ui.state.tasks else ui.state.tasks.filter { it.target == pane }
    }

    val statusCounts = remember(paneBaseTasks) {
        TaskStatusFilter.entries.associateWith { filter ->
            if (filter == TaskStatusFilter.ALL) paneBaseTasks.size
            else paneBaseTasks.count { filter.matches(it.state) }
        }
    }

    val activeTasks = remember(paneBaseTasks, selectedStatusFilter, searchQuery) {
        var list = paneBaseTasks
        if (selectedStatusFilter != TaskStatusFilter.ALL) {
            list = list.filter { selectedStatusFilter.matches(it.state) }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter { t ->
                t.text.lowercase().contains(q) ||
                    t.target.lowercase().contains(q) ||
                    "#${t.id}".contains(q) ||
                    t.stateLabel.lowercase().contains(q) ||
                    t.sub.lowercase().contains(q)
            }
        }
        list
    }

    val doneCount = remember(paneBaseTasks) {
        paneBaseTasks.count { it.state.equals("done", ignoreCase = true) || it.state.equals("completed", ignoreCase = true) }
    }

    val activeAgent = agents.firstOrNull { it.pane == pane }

    fun copyToClipboard(text: String, label: String = "Đã copy vào clipboard") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Queue Prompt", text))
        localToast = label
    }

    LaunchedEffect(ui.toast) {
        val t = ui.toast
        if (!t.isNullOrBlank()) {
            localToast = t
            onConsumeToast()
        }
    }

    LaunchedEffect(localToast) {
        if (localToast != null) {
            delay(2600)
            localToast = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Thanh tiêu đề đỉnh
            TopBand(
                ui = ui,
                onAction = onAction,
                onRefresh = onRefresh,
                onLogs = {
                    onFetchLogs(80)
                    logsOpen = true
                },
                onConfig = { configOpen = !configOpen },
                onToggleSearch = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) searchQuery = ""
                },
                isSearchActive = isSearchActive,
                onBack = onBack,
            )

            if (showConfig) {
                ConfigPanel(
                    currentUrl = currentUrl,
                    currentToken = currentToken,
                    onSave = { u, t ->
                        onSaveConfig(u, t)
                        configOpen = false
                    },
                    onDismiss = if (ui.needsSetup) null else ({ configOpen = false }),
                )
                return@Column
            }

            // Thanh tìm kiếm nhanh dạng inline nếu bật
            if (isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(QueuePalette.BandBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChuText("🔍", style = type.labelSmall, color = QueuePalette.Working)
                    Spacer(Modifier.width(6.dp))
                    ChuTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "",
                        placeholder = "Lọc việc theo nội dung, #id, trạng thái…",
                        singleLine = true,
                        showLabel = false,
                        autoFocus = true,
                        verticalPadding = 4.dp,
                        modifier = Modifier.weight(1f),
                    )
                    if (searchQuery.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        ChuButton(
                            onClick = { searchQuery = "" },
                            variant = ChuButtonVariant.Ghost,
                            bracketed = true,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            ChuText("x", style = type.labelSmall, color = QueuePalette.FgSecondary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ==================== DANH SÁCH AGENT THEO CHIỀU DỌC ====================
            val totalTaskCount = ui.state.tasks.size
            AgentRow(
                glyph = "✦",
                name = "Tất cả các agent",
                tone = QueueTone.Accent,
                word = "",
                count = totalTaskCount,
                selected = pane == "ALL",
                onClick = {
                    selectedPane = "ALL"
                    selectedTask = null
                },
            )

            agents.forEach { a ->
                AgentRow(
                    glyph = a.glyph,
                    name = a.name,
                    tone = a.tone,
                    word = a.word,
                    count = ui.state.tasks.count { it.target == a.pane },
                    selected = a.pane == pane,
                    onClick = {
                        selectedPane = a.pane
                        selectedTask = null
                    },
                )
            }

            if (agents.isEmpty() && ui.everLoaded) {
                ChuText(
                    "  không thấy agent nào",
                    style = type.labelSmall,
                    color = QueuePalette.FgMuted,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(4.dp))

            // ==================== THANH MỤC & NÚT XOÁ ĐÃ XONG ====================
            Band {
                ChuText(
                    if (pane == "ALL") "Tất cả tác vụ" else (activeAgent?.name ?: "chưa có agent"),
                    style = type.label.copy(fontWeight = FontWeight.SemiBold),
                    color = QueuePalette.FgPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                ChuText(
                    "${paneBaseTasks.size} việc",
                    style = type.labelSmall,
                    color = QueuePalette.FgSecondary,
                )

                // Nút XOÁ SẠCH CÁC VIỆC ĐÃ XONG (1 chạm)
                if (doneCount > 0) {
                    ChuButton(
                        onClick = { onClearDone(if (pane == "ALL") null else pane) },
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        borderColor = QueuePalette.Error,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        ChuText(
                            "✕ dọn $doneCount xong",
                            style = type.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = QueuePalette.Error,
                        )
                    }
                }
            }

            // ==================== FILTER CHIPS BAR ====================
            QueueFilterChipsBar(
                selectedFilter = selectedStatusFilter,
                onFilterSelected = { selectedStatusFilter = it },
                statusCounts = statusCounts,
            )

            // ==================== DANH SÁCH VIỆC (CHIỀU DỌC) ====================
            Box(modifier = Modifier.weight(1f)) {
                when {
                    activeTasks.isEmpty() && ui.everLoaded -> Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ChuText("không có việc nào trong mục này", style = type.bodySmall, color = QueuePalette.FgSecondary)
                        if (selectedStatusFilter != TaskStatusFilter.ALL) {
                            Spacer(Modifier.height(4.dp))
                            ChuButton(
                                onClick = { selectedStatusFilter = TaskStatusFilter.ALL },
                                variant = ChuButtonVariant.Ghost,
                                bracketed = true,
                            ) {
                                ChuText("Hiện tất cả việc", style = type.labelSmall, color = QueuePalette.Working)
                            }
                        } else {
                            ChuText("gõ nội dung bên dưới rồi bấm gửi", style = type.labelSmall, color = QueuePalette.FgMuted)
                        }
                    }
                    activeTasks.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        ChuText("đang tải dữ liệu…", color = QueuePalette.FgSecondary)
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        itemsIndexed(activeTasks, key = { _, t -> t.id }) { k, task ->
                            val letter = if (k < 26) ('a' + k).toString() else "·"
                            val topAction = task.actions.firstOrNull { it.op == "top" } ?: QueueAction("top", "Lên đầu", false, false)
                            val delAction = task.actions.firstOrNull { it.op == "del" || it.op == "rm" || it.danger } ?: QueueAction("del", "Xoá", false, true)

                            SwipeableTaskContainer(
                                taskId = task.id,
                                onSwipeTop = { onAction(topAction, task.id) },
                                onSwipeDelete = { onAction(delAction, task.id) },
                            ) {
                                TaskRow(
                                    task = task,
                                    letter = letter,
                                    searchQuery = searchQuery,
                                    liveLogs = if (task.id == selectedTask) ui.logs else emptyList(),
                                    isLogLoading = ui.logsLoading,
                                    selected = task.id == selectedTask,
                                    busyOps = ui.busyOps,
                                    onClick = {
                                        if (selectedTask == task.id) {
                                            selectedTask = null
                                        } else {
                                            selectedTask = task.id
                                            onFetchLogs(30)
                                        }
                                    },
                                    onAction = onAction,
                                    onCopy = { copyToClipboard(task.text, "Đã sao chép prompt #${task.id}") },
                                    onInspect = { inspectedTask = task },
                                )
                            }
                        }
                    }
                }
            }

            // ==================== THANH SOẠN PROMPT Ở ĐÁY ====================
            AddBar(
                targetAgentName = if (pane == "ALL") (agents.firstOrNull()?.name ?: "agent") else (activeAgent?.name ?: "agent"),
                onAdd = { text ->
                    val effectiveTarget = if (pane == "ALL") agents.firstOrNull()?.pane else pane
                    onAdd(text, effectiveTarget, null)
                },
            )
        }

        // Dialog xem chi tiết task
        inspectedTask?.let { task ->
            TaskDetailDialog(
                task = task,
                onDismiss = { inspectedTask = null },
                onCopy = { copyToClipboard(task.text, "Đã sao chép prompt #${task.id}") },
                onAction = { a ->
                    onAction(a, task.id)
                    inspectedTask = null
                },
                onFetchResponse = onFetchResponse,
            )
        }

        // Dialog xem live daemon logs
        if (logsOpen) {
            QueueLogsDialog(
                logs = ui.logs,
                loading = ui.logsLoading,
                error = ui.logsError,
                onRefresh = { onFetchLogs(100) },
                onDismiss = { logsOpen = false },
            )
        }

        // Toast thông báo
        AnimatedVisibility(
            visible = localToast != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
        ) {
            localToast?.let { msg ->
                ChuCard(
                    background = colors.surfaceVariant,
                    border = colors.accent,
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) {
                    ChuText(
                        msg,
                        style = type.labelSmall,
                        color = colors.accent,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Một thanh nền đậm chạy hết bề ngang — qq gọi là band. */
@Composable
private fun Band(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(QueuePalette.BandBg)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun TopBand(
    ui: QueueUiState,
    onAction: (QueueAction, Int?) -> Unit,
    onRefresh: () -> Unit,
    onLogs: () -> Unit,
    onConfig: () -> Unit,
    onToggleSearch: () -> Unit,
    isSearchActive: Boolean,
    onBack: () -> Unit,
) {
    val type = ChuTypography.current
    val pending = ui.state.tasks.count { it.state == "pending" }
    Band {
        ChuText("HÀNG ĐỢI", style = type.label.copy(fontWeight = FontWeight.Bold), color = QueuePalette.FgPrimary)

        val notice = ui.error ?: ui.state.banner?.text
        if (notice != null) {
            val tone = if (ui.error != null) QueueTone.Error else ui.state.banner?.tone ?: QueueTone.Warn
            ChuText(
                notice,
                style = type.labelSmall,
                color = tone.resolveColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
        } else {
            Spacer(Modifier.weight(1f))
            if (pending > 0) {
                ChuText("$pending chờ", style = type.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = QueuePalette.Gold)
            }
        }

        ui.state.globalActions.forEach { a ->
            ChuButton(
                onClick = { onAction(a, null) },
                variant = ChuButtonVariant.Ghost,
                bracketed = true,
                borderColor = if (a.danger) QueuePalette.Error else QueuePalette.BorderIdle,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            ) {
                ChuText(a.label, style = type.labelSmall, color = if (a.danger) QueuePalette.Error else QueuePalette.FgPrimary)
            }
        }

        ChuText(
            if (isSearchActive) "✕" else "🔍",
            style = type.label,
            color = if (isSearchActive) QueuePalette.Working else QueuePalette.FgSecondary,
            modifier = Modifier.clickable(onClick = onToggleSearch),
        )

        ChuText(
            "log",
            style = type.labelSmall,
            color = QueuePalette.FgSecondary,
            modifier = Modifier.clickable(onClick = onLogs),
        )

        ChuText(
            "⟳",
            style = type.label,
            color = QueuePalette.FgSecondary,
            modifier = Modifier.clickable(onClick = onRefresh),
        )

        ChuText(
            "⚙",
            style = type.label,
            color = QueuePalette.FgSecondary,
            modifier = Modifier.clickable(onClick = onConfig),
        )

        ChuText(
            "✕",
            style = type.label,
            color = QueuePalette.FgSecondary,
            modifier = Modifier.clickable(onClick = onBack),
        )
    }
}

@Composable
private fun AgentRow(
    glyph: String,
    name: String,
    tone: QueueTone,
    word: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = ChuTypography.current
    val agentColor = tone.resolveColor()
    val isAttention = word.isNotBlank()

    val surfaceBg = if (selected) QueuePalette.SurfaceActive else QueuePalette.SurfaceIdle
    val borderStroke = if (selected) {
        BorderStroke(1.dp, QueuePalette.BorderActive.copy(alpha = 0.4f))
    } else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(surfaceBg)
            .then(if (borderStroke != null) Modifier.border(borderStroke, RoundedCornerShape(4.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (selected) QueuePalette.Working else Color.Transparent),
            )

            Spacer(Modifier.width(8.dp))

            ChuText(
                glyph,
                style = type.label.copy(fontWeight = FontWeight.Bold),
                color = agentColor,
            )

            Spacer(Modifier.width(8.dp))

            ChuText(
                name,
                style = type.label.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (selected) QueuePalette.FgPrimary else QueuePalette.FgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (isAttention) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(QueuePalette.Blocked.copy(alpha = 0.2f))
                        .border(0.8.dp, QueuePalette.Blocked.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    ChuText(
                        word,
                        style = type.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = QueuePalette.Blocked,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }

            if (count > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (selected) QueuePalette.Working.copy(alpha = 0.2f) else QueuePalette.BandBg)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    ChuText(
                        "$count",
                        style = type.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = if (selected) QueuePalette.Working else QueuePalette.FgSecondary,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun TaskRow(
    task: QueueTask,
    letter: String,
    searchQuery: String,
    liveLogs: List<String>,
    isLogLoading: Boolean,
    selected: Boolean,
    busyOps: Set<String>,
    onClick: () -> Unit,
    onAction: (QueueAction, Int?) -> Unit,
    onCopy: () -> Unit,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = ChuTypography.current
    val isRunning = task.state.equals("sent", true) || task.state.equals("sending", true) || task.state.equals("working", true) || task.state.equals("busy", true)
    val isDone = task.state.equals("done", ignoreCase = true) || task.state.equals("completed", ignoreCase = true)
    val taskColor = task.tone.resolveColor(task.state)
    var terminalExpanded by remember { mutableStateOf(false) }

    val elapsedSeconds by rememberLiveElapsedSeconds(
        startEpochSeconds = if (isRunning) (System.currentTimeMillis() / 1000L - 15L) else null,
        isRunning = isRunning,
    )

    val cardBg = if (selected) QueuePalette.SurfaceActive else Color.Transparent
    val cardBorder = if (selected) {
        BorderStroke(1.dp, QueuePalette.BorderActive.copy(alpha = 0.5f))
    } else {
        BorderStroke(0.5.dp, QueuePalette.BorderIdle)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(cardBg)
            .border(cardBorder, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(QueuePalette.BandBg),
                contentAlignment = Alignment.Center,
            ) {
                ChuText(
                    letter,
                    style = type.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = if (selected) QueuePalette.Working else QueuePalette.FgMuted,
                )
            }

            Spacer(Modifier.width(8.dp))

            ChuText(
                task.glyph,
                style = type.label.copy(fontWeight = FontWeight.Bold),
                color = taskColor,
            )

            Spacer(Modifier.width(8.dp))

            if (isRunning) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(QueuePalette.Gold.copy(alpha = 0.15f))
                        .border(0.8.dp, QueuePalette.Gold.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    ChuText(
                        formatElapsedDuration(elapsedSeconds),
                        style = type.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                        color = QueuePalette.Gold,
                    )
                }
                Spacer(Modifier.width(6.dp))
            }

            if (searchQuery.isNotBlank()) {
                HighlightedText(
                    text = task.text.replace('\n', ' '),
                    query = searchQuery,
                    style = type.label.copy(
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    baseColor = if (isDone) QueuePalette.Done else if (selected) QueuePalette.FgPrimary else QueuePalette.FgSecondary,
                    maxLines = if (selected) 5 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                val highlightedText = remember(task.text, selected) {
                    if (selected) PromptSyntaxHighlighter.highlight(task.text)
                    else AnnotatedString(task.text.replace('\n', ' '))
                }
                BasicText(
                    text = highlightedText,
                    style = type.label.copy(
                        color = if (isDone) QueuePalette.Done else if (selected) QueuePalette.FgPrimary else QueuePalette.FgSecondary,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    maxLines = if (selected) 5 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.width(8.dp))

            if (task.target.isNotBlank() && !selected) {
                ChuText(
                    "@${task.target}",
                    style = type.labelSmall.copy(fontSize = 10.sp),
                    color = QueuePalette.FgMuted,
                )
            }
        }

        if (isRunning) {
            ShimmerProgressBar(
                modifier = Modifier
                    .padding(start = 28.dp, end = 12.dp, top = 1.dp, bottom = 4.dp),
            )
        }

        if (selected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(QueuePalette.SurfaceTray)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChuText(
                        "#${task.id}${if (task.target.isNotBlank()) " · target: @${task.target}" else ""}",
                        style = type.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = QueuePalette.FgMuted,
                    )
                    AnimatedTuiBadge(
                        text = task.stateLabel,
                        tone = task.tone,
                        stateString = task.state,
                    )
                }

                if (isRunning || liveLogs.isNotEmpty()) {
                    LiveTerminalPeek(
                        lines = liveLogs,
                        isLoading = isLogLoading,
                        isExpanded = terminalExpanded,
                        onToggleExpand = { terminalExpanded = !terminalExpanded },
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    task.actions.forEach { action ->
                        val isDanger = action.danger
                        val isBusy = "${action.op}:${task.id}" in busyOps
                        ChuButton(
                            onClick = { onAction(action, task.id) },
                            variant = ChuButtonVariant.Ghost,
                            bracketed = true,
                            enabled = !isBusy,
                            borderColor = if (isDanger) QueuePalette.Error else QueuePalette.BorderIdle,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            ChuText(
                                action.label,
                                style = type.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isDanger) QueuePalette.Error else QueuePalette.FgPrimary,
                            )
                        }
                    }

                    ChuButton(
                        onClick = onCopy,
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        borderColor = QueuePalette.BorderIdle,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        ChuText("copy", style = type.labelSmall, color = QueuePalette.FgSecondary)
                    }

                    ChuButton(
                        onClick = onInspect,
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        borderColor = QueuePalette.Working,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        ChuText("chi tiết", style = type.labelSmall, color = QueuePalette.Working)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddBar(
    targetAgentName: String,
    onAdd: (String) -> Unit,
) {
    val type = ChuTypography.current
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(QueuePalette.BandBg)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuTextField(
            value = text,
            onValueChange = { text = it },
            label = "",
            placeholder = "Giao việc cho @$targetAgentName…",
            singleLine = true,
            showLabel = false,
            modifier = Modifier.weight(1f),
            autoFocus = false,
            verticalPadding = 6.dp,
        )
        Spacer(Modifier.width(6.dp))
        ChuButton(
            onClick = {
                if (text.isNotBlank()) {
                    onAdd(text.trim())
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
            ChuText("gửi", style = type.labelSmall, color = ChuColors.current.onAccent)
        }
    }
}

@Composable
private fun TaskDetailDialog(
    task: QueueTask,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onAction: (QueueAction) -> Unit,
    onFetchResponse: (suspend (Int) -> String?)? = null,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val context = LocalContext.current
    var responseText by remember { mutableStateOf<String?>(null) }
    var isLoadingResponse by remember { mutableStateOf(false) }

    LaunchedEffect(task.id) {
        if (task.hasResp || task.state.equals("done", ignoreCase = true)) {
            if (onFetchResponse != null) {
                isLoadingResponse = true
                responseText = onFetchResponse(task.id)
                isLoadingResponse = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChuText("#${task.id}", style = type.headline, color = QueuePalette.Working)
                    TuiBadge(task.stateLabel, task.tone.color())
                }
                ChuButton(
                    onClick = onDismiss,
                    variant = ChuButtonVariant.Ghost,
                    bracketed = true,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    ChuText("x", style = type.label, color = colors.textMuted)
                }
            }

            ChuText("Nội dung prompt:", style = type.labelSmall, color = colors.textMuted)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (responseText != null) 140.dp else 280.dp)
                    .background(colors.surface)
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val highlighted = remember(task.text) { PromptSyntaxHighlighter.highlight(task.text) }
                BasicText(
                    text = highlighted,
                    style = type.body.copy(color = colors.textPrimary),
                )
            }

            if (isLoadingResponse) {
                ChuText("⏳ Đang tải kết quả từ agent…", style = type.labelSmall, color = QueuePalette.Working)
            } else if (!responseText.isNullOrBlank()) {
                ChuText("Kết quả từ Agent:", style = type.labelSmall, color = QueuePalette.Working)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .background(QueuePalette.LogBg)
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    BasicText(
                        text = buildAnnotatedString {
                            append(responseText.orEmpty())
                        },
                        style = type.bodySmall.copy(color = QueuePalette.FgPrimary, fontFamily = FontFamily.Monospace),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChuButton(
                        onClick = onCopy,
                        variant = ChuButtonVariant.Outlined,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        ChuText("📋 Prompt", style = type.labelSmall, color = colors.textSecondary)
                    }
                    if (!responseText.isNullOrBlank()) {
                        ChuButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Agent Response", responseText))
                            },
                            variant = ChuButtonVariant.Outlined,
                            bracketed = true,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            ChuText("📋 Kết quả", style = type.labelSmall, color = QueuePalette.Working)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.actions.forEach { a ->
                        ChuButton(
                            onClick = { onAction(a) },
                            variant = ChuButtonVariant.Outlined,
                            bracketed = true,
                            borderColor = if (a.danger) QueuePalette.Error else colors.border,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            ChuText(
                                a.label,
                                style = type.labelSmall,
                                color = if (a.danger) QueuePalette.Error else QueuePalette.Working,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueLogsDialog(
    logs: List<String>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.scrollToItem(logs.size - 1)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChuText("$ ", style = type.headline, color = colors.textMuted)
                    ChuText("daemon logs", style = type.headline)
                    if (loading) {
                        ChuText("…", style = type.labelSmall, color = QueuePalette.Gold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChuButton(
                        onClick = onRefresh,
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        ChuText("⟳", style = type.label, color = QueuePalette.Working)
                    }
                    ChuButton(
                        onClick = onDismiss,
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        ChuText("x", style = type.label, color = colors.textMuted)
                    }
                }
            }

            if (error != null) {
                ChuText(error, style = type.bodySmall, color = QueuePalette.Error)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 320.dp)
                    .background(QueuePalette.LogBg)
                    .padding(8.dp),
            ) {
                if (logs.isEmpty() && !loading) {
                    ChuText(
                        "Không có log gần đây",
                        style = type.bodySmall,
                        color = QueuePalette.FgSecondary,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(logs) { _, line ->
                            ChuText(
                                line,
                                style = type.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                ),
                                color = when {
                                    line.contains("ERROR", ignoreCase = true) || line.contains("fail", ignoreCase = true) -> QueuePalette.Error
                                    line.contains("WARN", ignoreCase = true) -> QueuePalette.Blocked
                                    line.contains("START", ignoreCase = true) || line.contains("DONE", ignoreCase = true) -> QueuePalette.Working
                                    else -> QueuePalette.FgPrimary
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigPanel(
    currentUrl: String,
    currentToken: String,
    onSave: (String, String) -> Unit,
    onDismiss: (() -> Unit)?,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    var token by remember(currentToken) { mutableStateOf(currentToken) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChuText(
            "Chỉ cần địa chỉ. Đi qua tailnet thì máy chủ đã biết chắc là bạn, " +
                "không cần token. Ô token bên dưới chỉ dùng khi chạy qsrv " +
                "không nằm sau tailscale serve.",
            style = type.bodySmall,
            color = colors.textSecondary,
        )
        ChuTextField(
            value = url,
            onValueChange = { url = it },
            label = "Địa chỉ qsrv",
            placeholder = "https://may.tailnet.ts.net/q",
            singleLine = true,
            autoFocus = false,
        )
        ChuTextField(
            value = token,
            onValueChange = { token = it },
            label = "Token (không bắt buộc)",
            placeholder = "để trống nếu dùng qua tailnet",
            singleLine = true,
            autoFocus = false,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChuButton(
                onClick = { onSave(url, token) },
                enabled = url.isNotBlank(),
                bracketed = true,
            ) { ChuText("Lưu", color = colors.onAccent) }
            if (onDismiss != null) {
                ChuButton(onClick = onDismiss, variant = ChuButtonVariant.Ghost, bracketed = true) {
                    ChuText("Đóng", color = colors.textSecondary)
                }
            }
        }
    }
}
