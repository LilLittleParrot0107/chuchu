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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jossephus.chuchu.MainActivity
import com.jossephus.chuchu.data.repository.SettingsRepository
import com.jossephus.chuchu.service.agents.AgentNotifier
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
 */
class AgentBubbleActivity : FragmentActivity() {

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
        val pane = intent?.getStringExtra(AgentNotifier.EXTRA_PANE)?.trim()?.ifEmpty { null }
        val appLock = SettingsRepository.getInstance(this).appLockEnabled.value
        if (appLock) {
            // Bubble là surface đọc/trả lời đầy đủ; không được bypass app lock.
            requireUserVerification(
                context = this,
                title = "Unlock kohi",
                subtitle = "Authenticate to open this conversation",
            ) { result ->
                if (result == VerificationResult.Success && !isFinishing && !isDestroyed) {
                    showBubble(pane)
                } else {
                    finish()
                }
            }
        } else {
            showBubble(pane)
        }
    }

    private fun showBubble(pane: String?) {
        val queueVm = ViewModelProvider(this, QueueViewModel.factory(application))
            .get(QueueViewModel::class.java)
        if (pane != null) {
            queueVm.openChat(pane)
        }
        setContent {
            BubbleRoot(queueVm, onExpand = {
                openFullApp(pane)
                finish()
            })
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
private fun BubbleRoot(queueVm: QueueViewModel, onExpand: () -> Unit) {
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
            // Bubble là một "màn hình" độc lập: app-active/queue-visible do CHÍNH
            // activity này quyết định (MainActivity có thể đang dừng ở dưới).
            LifecycleResumeEffect(queueVm) {
                queueVm.setAppActive(true)
                onPauseOrDispose { queueVm.setAppActive(false) }
            }
            BubbleShell(queueVm = queueVm, onExpand = onExpand)
        }
    }
}
