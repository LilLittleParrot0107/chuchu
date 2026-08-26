package com.jossephus.chuchu.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Inset navbar doc tu DECORVIEW CUA ACTIVITY — nguon goc nhat, luon attached
 * va luon co inset, bat ke goi tu dau (ke ca trong cua so Dialog). Cac nguon
 * truoc deu co canh hong: WindowInsets composition bi NavShell consume ve 0,
 * LocalView trong dialog khong nhan inset, view chua attach tra null (chuoi
 * bug "lem day" 26-27/8). San 12dp: du inset that = 0 (an thanh dieu huong)
 * van chua hoi tho cho canh duoi sheet.
 */
@Composable
fun rememberRootNavBottomDp(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    val activity = remember(context) { context.findActivity() }
    val px = activity?.window?.decorView
        ?.let { ViewCompat.getRootWindowInsets(it)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom }
        ?: 0
    return with(density) { maxOf(px.toDp(), 12.dp) }
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
