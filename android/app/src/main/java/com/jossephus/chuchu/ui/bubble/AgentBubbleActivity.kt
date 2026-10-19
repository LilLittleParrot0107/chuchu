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
import com.jossephus.chuchu.ui.theme.GhosttyThemeRegistry
import com.jossephus.chuchu.ui.theme.resolveActiveThemeName
import kotlinx.coroutines.launch

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

    /** Hết khoá chưa. Đọc trong composition nên đổi là UI đổi ngay. */
    private var unlocked by mutableStateOf(true)

    /** Xác thực hỏng/bị huỷ → hiện nút thử lại (không tự bật lại prompt). */
    private var unlockBlocked by mutableStateOf(false)

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

        val resolvedPane = intent?.getStringExtra(AgentNotifier.EXTRA_PANE)?.trim()?.ifEmpty { null }
        pane = resolvedPane
        // Đăng ký TRƯỚC khi dựng UI: từ giây phút này notification là neo, mọi
        // nhịp poll (Blocked→Working sau khi user trả lời…) phải giữ nó lại.
        resolvedPane?.let(BubblePresence::opened)

        val appLock = SettingsRepository.getInstance(this).appLockEnabled.value
        unlocked = !appLock

        // 19/10: LUÔN dựng UI trước, kể cả khi đang khoá. Bản cũ xin xác thực ngay
        // trong onCreate rồi finish() khi thất bại — cửa sổ bubble mở ra rồi tắt
        // trong cùng một nhịp, user chỉ thấy "bấm vào không ra gì".
        showBubble()
        if (appLock) requestUnlock()
    }

    override fun onStart() {
        super.onStart()
        pane?.let(BubblePresence::shown)
    }

    override fun onStop() {
        pane?.let(BubblePresence::hidden)
        super.onStop()
    }

    /**
     * Bubble head của pane A có thể được hệ thống mở lại bằng intent của pane B
     * (cùng activity, `documentLaunchMode="always"`). Không xử lý thì cửa sổ đang
     * mở sẽ hiển thị nhầm hội thoại.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val next = intent.getStringExtra(AgentNotifier.EXTRA_PANE)?.trim()?.ifEmpty { null } ?: return
        if (next == pane) return
        pane?.let(BubblePresence::closed)
        pane = next
        BubblePresence.opened(next)
        queueVm?.openChat(next)
    }

    override fun onDestroy() {
        val closing = pane
        pane = null
        if (closing != null) {
            BubblePresence.closed(closing)
            // Config change thì cửa sổ vẫn còn (activity mới mở lại ngay) — đừng dọn neo.
            if (!isChangingConfigurations) {
                AgentNotifier.onBubbleClosed(applicationContext, closing)
            }
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
                    unlockBlocked = false
                }
                VerificationResult.Failed, VerificationResult.Unavailable -> unlockBlocked = true
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
                unlockBlocked = unlockBlocked,
                onRetryUnlock = {
                    unlockBlocked = false
                    requestUnlock()
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
    unlockBlocked: Boolean,
    onRetryUnlock: () -> Unit,
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
                BubbleLocked(blocked = unlockBlocked, onRetry = onRetryUnlock)
            }
        }
    }
}

/**
 * Màn khoá của bubble — KHÔNG phải overlay mờ trên nội dung: bubble là surface
 * riêng, che nửa vời là lộ chat ở sau. Chỉ một dòng chữ và (khi đã hỏng) nút mở
 * khoá, đúng tinh thần `LockedGate` của app-shell.
 */
@Composable
private fun BubbleLocked(blocked: Boolean, onRetry: () -> Unit) {
    val colors = ChuColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ChuText("kohi đang khoá", color = colors.textPrimary)
            if (blocked) {
                Spacer(Modifier.height(12.dp))
                ChuButton(onClick = onRetry, bracketed = true) {
                    ChuText("Mở khoá", color = colors.onAccent)
                }
            }
        }
    }
}
