package com.jossephus.chuchu.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * BackHandler gắn với vòng đời của ACTIVITY thay vì của màn (NavBackStackEntry) — dùng thay
 * `androidx.activity.compose.BackHandler` ở mọi màn của app.
 *
 * Vì sao (24/9, "lần đầu back từ chat sau khi mở app nhảy về home"): dispatcher gỡ rồi gắn lại
 * mọi callback theo vòng đời mỗi lần app quay lại từ nền. NavHost (navigation-compose 2.7.7)
 * tự đặt một BackHandler lùi ngăn xếp, gắn với vòng đời Activity; BackHandler của màn gắn với
 * vòng đời màn. Khi app lên lại, NavController đánh thức màn TRƯỚC rồi observer của NavHost
 * mới chạy → callback của NavHost được gắn SAU, nằm trên cùng, ăn lần back đầu (Queue → host
 * list) thay vì đóng chat. Gắn mọi callback của app vào cùng vòng đời Activity thì chúng được
 * gắn lại theo đúng thứ tự đăng ký (NavHost trước, màn sau, lớp trong cùng sau cùng).
 */
@Composable
fun KohiBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val callback = remember {
        object : OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() = currentOnBack()
        }
    }
    SideEffect { callback.isEnabled = enabled }
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher ?: return
    val owner = LocalContext.current.componentActivity() ?: LocalLifecycleOwner.current
    DisposableEffect(dispatcher, owner) {
        dispatcher.addCallback(owner, callback)
        onDispose { callback.remove() }
    }
}

private tailrec fun Context.componentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.componentActivity()
    else -> null
}
