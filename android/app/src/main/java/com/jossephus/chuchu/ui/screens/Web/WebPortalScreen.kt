package com.jossephus.chuchu.ui.screens.Web

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography

/**
 * In-app browser for the user's dufs file portal (directory listing,
 * video streaming, APK downloads). Deliberately minimal: one WebView,
 * back walks web history first, downloads are handed to the system.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPortalScreen(
    url: String,
    onClose: () -> Unit,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("") }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChuButton(
                onClick = onClose,
                variant = ChuButtonVariant.Outlined,
                bracketed = true,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                ChuText("←", style = typography.label)
            }
            ChuText(
                pageTitle.ifBlank { "web" },
                style = typography.label,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            ChuButton(
                onClick = { webViewRef?.reload() },
                variant = ChuButtonVariant.Outlined,
                bracketed = true,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                ChuText("↻", style = typography.label)
            }
        }

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = WebViewClient()
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            pageTitle = title.orEmpty()
                        }
                    }
                    // Downloads (APKs etc.) go to the system browser/downloader.
                    setDownloadListener { downloadUrl, _, _, _, _ ->
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        }
                    }
                    loadUrl(url)
                    webViewRef = this
                }
            },
        )
    }
}
