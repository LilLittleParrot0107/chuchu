package com.jossephus.chuchu

import android.content.Intent
import android.net.Uri
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.jossephus.chuchu.data.repository.SettingsRepository
import com.jossephus.chuchu.ui.ApplicationNavController
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTheme
import com.jossephus.chuchu.ui.theme.GhosttyThemeRegistry
import com.jossephus.chuchu.ui.theme.resolveActiveThemeName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hand-off channel for deep links (Telegram notification links):
 * https://<tailnet>/kohi-open?host=<profile name>. MainActivity writes the
 * requested host name here; ApplicationNavController consumes it once the
 * app (and any app-lock) is ready.
 */
object DeepLinkBus {
    val pendingHostName = MutableStateFlow<String?>(null)

    /** herdr pane id (e.g. "w4:p1") to jump to once the terminal attaches. */
    val pendingPane = MutableStateFlow<String?>(null)
}

class MainActivity : FragmentActivity() {

    private fun captureDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val isKohiScheme = data.scheme == "kohi"
        val path = data.path.orEmpty()
        val isHttpsPath = path.startsWith("/kohi-open") || path.startsWith("/kohi-focus/go")
        if (!isKohiScheme && !isHttpsPath) return
        // Telegram sometimes leaves the href's "&amp;" un-escaped, so the
        // second parameter arrives named "amp;pane" (seen in the hook's
        // access log). Accept both spellings.
        fun param(name: String) =
            (data.getQueryParameter(name) ?: data.getQueryParameter("amp;$name"))
                ?.trim()?.ifEmpty { null }
        val pane = param("pane")
        DeepLinkBus.pendingPane.value = pane
        DeepLinkBus.pendingHostName.value = param("host")
        // A verified App Link hands the URL straight to us, so the hook page
        // that used to focus the herdr tab on its way through the browser
        // never runs — call the hook's /focus endpoint ourselves. (kohi://
        // links come FROM that page, which already did it.)
        if (isHttpsPath && pane != null) {
            val focusUrl = "${data.scheme}://${data.host}/kohi-focus/focus?pane=" + Uri.encode(pane)
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    val conn = URL(focusUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    try {
                        conn.responseCode
                    } finally {
                        conn.disconnect()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureDeepLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureDeepLink(intent)
        val settings = SettingsRepository.getInstance(this)
        lifecycleScope.launch {
            settings.hideScreenContents.collect { hideScreenContents ->
                if (hideScreenContents) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0x00000000),
            navigationBarStyle = SystemBarStyle.dark(0x00000000),
        )
        setContent {
            AppRoot()
        }
    }
}

@Composable
fun AppRoot() {
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
            ApplicationNavController()
        }
    }
}
