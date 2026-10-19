package com.jossephus.chuchu.ui.bubble

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.jossephus.chuchu.MainActivity
import com.jossephus.chuchu.data.repository.SettingsRepository
import com.jossephus.chuchu.service.agents.AgentNotifier
import com.jossephus.chuchu.service.agents.BubblePresence
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.screens.Queue.QueueViewModel
import com.jossephus.chuchu.ui.security.VerificationResult
import com.jossephus.chuchu.ui.security.requireUserVerification
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTheme
import com.jossephus.chuchu.ui.theme.ChuTypography
import com.jossephus.chuchu.ui.theme.GhosttyThemeRegistry
import com.jossephus.chuchu.ui.theme.resolveActiveThemeName
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Danh tính của MỘT cửa sổ bubble, dùng làm token cho [BubblePresence].
 *
 * Để trong ViewModel vì nó phải sống qua config change: activity dựng lại thì token
 * giữ nguyên, nên `opened(token)` lần hai là idempotent thay vì thành "cửa sổ thứ hai"
 * (đếm theo token, không theo pane — cùng pane có thể có hai instance, xem [BubblePresence]).
 */
internal class BubbleWindowToken : ViewModel() {
    val id: String = UUID.randomUUID().toString()
}

/** Vì sao cửa sổ bubble đang nằm ở màn khoá. */
private enum class UnlockProblem {
    /** User huỷ prompt, hoặc xác thực sai — bấm lại được. */
    Failed,

    /** Máy không có credential nào dùng được (chưa đặt khoá màn hình) — bấm lại vô ích. */
    Unavailable,
}

/**
 * Cửa sổ bubble gắn thông báo (BubbleMetadata → activity này): chỉ dựng
 * [BubbleShell] với QueueViewModel riêng, KHÔNG mở cả app-shell (NavHost, khóa
 * app, tab…) — bubble là chat + money, còn mọi thứ khác nằm ở MainActivity.
 *
 * `documentLaunchMode="always"` + `taskAffinity=""` (manifest): mỗi pane là một
 * bubble/task độc lập, không đè lên task chính; ⤢ mở app đầy đủ ở đúng phiên.
 *
 * Vòng đời của cửa sổ này được báo cho [BubblePresence] và [AgentNotifier]: hễ
 * cửa sổ còn sống thì notification của pane là CÁI NEO, không ai được xoá nó —
 * xoá là SystemUI dỡ luôn bubble (xem [BubblePresence]).
 */
class AgentBubbleActivity : FragmentActivity() {

    private var pane: String? = null
    private var queueVm: QueueViewModel? = null

    /** Token của cửa sổ này; xem [BubbleWindowToken]. */
    private val windowToken: String
        get() = ViewModelProvider(this)[BubbleWindowToken::class.java].id

    /** Hết khoá chưa. Đọc trong composition nên đổi là UI đổi ngay. */
    private var unlocked by mutableStateOf(true)

    /** Trục trặc của lần xác thực gần nhất — quyết định màn khoá hiện gì. */
    private var unlockProblem by mutableStateOf<UnlockProblem?>(null)

    /** Đã xin xác thực lần nào chưa (chỉ tự hỏi MỘT lần mỗi cửa sổ; sau đó là nút bấm). */
    private var unlockAsked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Compose IME insets đáng tin từ API 30; Android 10 (minSdk 24) với
        // adjustNothing sẽ để bàn phím che composer. Fallback resize cho máy cũ.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0x00000000),
            navigationBarStyle = SystemBarStyle.dark(0x00000000),
        )
        // Cùng privacy flag với MainActivity: bubble từng là lỗ hổng screenshot
        // khi user bật "hide screen contents".
        lifecycleScope.launch {
            SettingsRepository.getInstance(this@AgentBubbleActivity).hideScreenContents.collect { hide ->
                if (hide) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        // Khoá app bị TẮT trong lúc bubble đang mở: mở luôn. Đừng để user kẹt ở màn khoá
        // của một tính năng vừa bị tắt (19/10 — rà soát bubble). Bật khoá thì không tự
        // khoá lại cửa sổ đang mở (user đang gõ dở); lần mở bubble sau đã phải xác thực.
        lifecycleScope.launch {
            SettingsRepository.getInstance(this@AgentBubbleActivity).appLockEnabled.collect { enabled ->
                if (!enabled) {
                    unlocked = true
                    unlockProblem = null
                }
            }
        }

        val resolvedPane = intent?.getStringExtra(AgentNotifier.EXTRA_PANE)?.trim()?.ifEmpty { null }
        pane = resolvedPane
        // Đăng ký TRƯỚC khi dựng UI: từ giây phút này notification là neo, mọi
        // nhịp poll (Blocked→Working sau khi user trả lời…) phải giữ nó lại.
        resolvedPane?.let { BubblePresence.opened(it, windowToken) }

        val appLock = SettingsRepository.getInstance(this).appLockEnabled.value
        unlocked = !appLock

        // 19/10: LUÔN dựng UI trước, kể cả khi đang khoá. Bản cũ xin xác thực ngay
        // trong onCreate rồi finish() khi thất bại — cửa sổ bubble mở ra rồi tắt
        // trong cùng một nhịp, user chỉ thấy "bấm vào không ra gì".
        showBubble()
        // Prompt xác thực KHÔNG gọi ở đây: BiometricPrompt chỉ hiện khi activity đã
        // resumed, gọi trong onCreate là có máy không bao giờ thấy prompt. [onResume] lo.
    }

    override fun onResume() {
        super.onResume()
        if (!unlockAsked && !unlocked) {
            unlockAsked = true
            requestUnlock()
        }
    }

    override fun onStart() {
        super.onStart()
        pane?.let { BubblePresence.shown(it, windowToken) }
    }

    override fun onStop() {
        pane?.let { BubblePresence.hidden(it, windowToken) }
        super.onStop()
    }

    /**
     * Bubble head của pane A có thể được hệ thống mở lại bằng intent của pane B
     * (cùng activity, `documentLaunchMode="always"`). Không xử lý thì cửa sổ đang
     * mở sẽ hiển thị nhầm hội thoại.
     *
     * Đổi pane phải dọn CẢ HAI đầu: sổ presence (token cũ) và notification neo của
     * pane cũ — bỏ sót là neo mồ côi nằm lại trong shade, kèm một đầu bubble không
     * còn cửa sổ nào.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val next = intent.getStringExtra(AgentNotifier.EXTRA_PANE)?.trim()?.ifEmpty { null } ?: return
        val previous = pane
        if (next == previous) return
        pane = next
        BubblePresence.opened(next, windowToken)
        // Cửa sổ đang hiện thì pane mới cũng đang hiện — không báo thì [AgentNotifier]
        // tưởng user không nhìn và làm ồn ngay trước mặt họ.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            BubblePresence.shown(next, windowToken)
        }
        queueVm?.openChat(next)
        if (previous != null && !isChangingConfigurations) {
            BubblePresence.closed(previous, windowToken)
            AgentNotifier.onBubbleClosed(applicationContext, previous)
        }
    }

    override fun onDestroy() {
        val closing = pane
        pane = null
        // Config change: cửa sổ VẪN CÒN (activity mới dựng lại ngay, cùng token) — báo
        // đóng ở đây là tự tay dỡ cái neo đang giữ cửa sổ. 19/10: trước đây chỉ
        // onBubbleClosed được bảo vệ, còn presence thì bị đóng vô điều kiện, nên giữa hai
        // instance có một khe mà vòng poll nền (Dispatchers.IO) có thể xoá notification.
        if (closing != null && !isChangingConfigurations) {
            BubblePresence.closed(closing, windowToken)
            AgentNotifier.onBubbleClosed(applicationContext, closing)
        }
        super.onDestroy()
    }

    /**
     * Xin xác thực cho app lock. Thất bại (kể cả [VerificationResult.Unavailable]
     * — máy chưa cài vân tay/mặt, hoặc khoá màn hình vừa bị gỡ) thì GIỮ cửa sổ lại
     * và mời thử lại, tuyệt đối không `finish()`: bubble là surface duy nhất user
     * vừa bấm vào, tự đóng nó là "không có gì xảy ra".
     */
    private fun requestUnlock() {
        requireUserVerification(
            context = this,
            title = "Unlock kohi",
            subtitle = "Authenticate to open this conversation",
        ) { result ->
            if (isFinishing || isDestroyed) return@requireUserVerification
            when (result) {
                VerificationResult.Success -> {
                    unlocked = true
                    unlockProblem = null
                }
                VerificationResult.Failed -> unlockProblem = UnlockProblem.Failed
                // Máy không có credential nào dùng được: bấm lại cũng hỏng, nên màn khoá
                // phải mời một lối khác thay vì một nút chết (19/10 — rà soát bubble).
                VerificationResult.Unavailable -> unlockProblem = UnlockProblem.Unavailable
            }
        }
    }

    private fun showBubble() {
        val vm = ViewModelProvider(this, QueueViewModel.factory(application))
            .get(QueueViewModel::class.java)
        queueVm = vm
        pane?.let(vm::openChat)
        setContent {
            BubbleRoot(
                queueVm = vm,
                unlocked = unlocked,
                unlockProblem = unlockProblem,
                onRetryUnlock = {
                    unlockProblem = null
                    requestUnlock()
                },
                onOpenApp = {
                    openFullApp(pane)
                    finish()
                },
                onExpand = {
                    openFullApp(pane)
                    finish()
                },
            )
        }
    }

    private fun openFullApp(pane: String?) {
        val intent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        if (pane != null) intent.putExtra(AgentNotifier.EXTRA_PANE, pane)
        startActivity(intent)
    }
}

@Composable
private fun BubbleRoot(
    queueVm: QueueViewModel,
    unlocked: Boolean,
    unlockProblem: UnlockProblem?,
    onRetryUnlock: () -> Unit,
    onOpenApp: () -> Unit,
    onExpand: () -> Unit,
) {
    val context = LocalContext.current
    GhosttyThemeRegistry.init(context)
    val settings = SettingsRepository.getInstance(context)
    val fontName by settings.fontName.collectAsStateWithLifecycle()
    val themeName by settings.themeName.collectAsStateWithLifecycle()
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    val lightThemeName by settings.lightThemeName.collectAsStateWithLifecycle()
    val resolvedThemeName = resolveActiveThemeName(
        themeMode = themeMode,
        darkThemeName = themeName,
        lightThemeName = lightThemeName,
    )
    ChuTheme(themeName = resolvedThemeName, fontName = fontName) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ChuColors.current.background),
        ) {
            if (unlocked) {
                // Bubble là một "màn hình" độc lập: app-active/queue-visible do CHÍNH
                // activity này quyết định (MainActivity có thể đang dừng ở dưới).
                LifecycleResumeEffect(queueVm) {
                    queueVm.setAppActive(true)
                    onPauseOrDispose { queueVm.setAppActive(false) }
                }
                BubbleShell(queueVm = queueVm, onExpand = onExpand)
            } else {
                BubbleLocked(
                    problem = unlockProblem,
                    onRetry = onRetryUnlock,
                    onOpenApp = onOpenApp,
                )
            }
        }
    }
}

/**
 * Màn khoá của bubble — KHÔNG phải overlay mờ trên nội dung: bubble là surface
 * riêng, che nửa vời là lộ chat ở sau. Đúng tinh thần `LockedGate` của app-shell,
 * nhưng có thêm lối thoát: xác thực không dùng được thì mời mở app đầy đủ, không
 * để user đứng trước một nút bấm mãi không qua (19/10 — rà soát bubble).
 */
@Composable
private fun BubbleLocked(
    problem: UnlockProblem?,
    onRetry: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val colors = ChuColors.current
    val type = ChuTypography.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            ChuText("kohi đang khoá", color = colors.textPrimary)
            when (problem) {
                UnlockProblem.Unavailable -> {
                    Spacer(Modifier.height(8.dp))
                    ChuText(
                        "Máy chưa đặt khoá màn hình nên không xác thực được",
                        style = type.labelSmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    ChuButton(onClick = onRetry, bracketed = true) {
                        ChuText("Thử lại", color = colors.onAccent)
                    }
                    Spacer(Modifier.height(8.dp))
                    ChuButton(onClick = onOpenApp) {
                        ChuText("Mở app", color = colors.onAccent)
                    }
                }
                UnlockProblem.Failed -> {
                    Spacer(Modifier.height(12.dp))
                    ChuButton(onClick = onRetry, bracketed = true) {
                        ChuText("Mở khoá", color = colors.onAccent)
                    }
                }
                // null = prompt đang mở; chỉ một dòng chữ, không nút nào để bấm nhầm.
                null -> Unit
            }
        }
    }
}
