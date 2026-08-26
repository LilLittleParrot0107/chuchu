package com.jossephus.chuchu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Inset navbar doc tu ROOT WINDOW — mien nhiem voi consumeWindowInsets cua
 * NavShell va voi viec cua so Dialog khong nhan inset (hai thu da lam bottom
 * sheet "lem" day 3 lan trong 26-27/8). Navbar khong doi kich thuoc giua
 * chung phien nen doc mot lan la du.
 */
@Composable
fun rememberRootNavBottomDp(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    val px = ViewCompat.getRootWindowInsets(view.rootView)
        ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    return with(density) { px.toDp() }
}
