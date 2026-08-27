package com.jossephus.chuchu.ui.components

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.jossephus.chuchu.ui.theme.ChuColors

// 300ms vao / 220ms ra, giam toc — user 27/8: "nhanh qua bi giat minh",
// can nhip du cham de mat kip doan truoc chuyen dong.
private const val SHEET_IN_MS = 300
private const val SHEET_OUT_MS = 220

// KHONG float sheet len khoi day man (thu 27/8, user che "nhin thay day
// la do"): sheet lien day, phan ne navbar la padding TRONG nen surface.
// Chong "sheet dai bi cat day" da co window anchor MATCH_PARENT+BOTTOM.

/**
 * Bottom sheet chung cho detail dashboard/queue: scrim tu ve fade cung nhip,
 * sheet truot len tu day, tap scrim / back = dong CO animation roi moi
 * dismiss that. Inset navbar do tu root window (mien nhiem moi tang nuot
 * inset — bai hoc "lem day" 3 lan ngay 26-27/8).
 */
@Composable
fun KohiBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = ChuColors.current
    val navBottom = rememberRootNavBottomDp()
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val visible = remember { MutableTransitionState(false) }
    val dismissing = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible.targetState = true }
    fun startDismiss() {
        dismissing.value = true
        visible.targetState = false
    }
    // Exit anim chay xong roi moi dismiss that — dong cung phai muot.
    LaunchedEffect(visible.currentState, visible.targetState) {
        if (dismissing.value && !visible.targetState && visible.isIdle) currentOnDismiss()
    }

    Dialog(
        onDismissRequest = { startDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Cua so Dialog mac dinh WRAP_CONTENT + gravity CENTER — WindowManager
        // cua OEM (OriginOS) co the dat no lech xuong so voi day man that, la
        // thu inset KHONG bat duoc (nghi pham vu "chim day" lan 5). Ep window
        // phu DUNG ca man + neo day: BottomCenter cua Box = day man that.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            dialogWindow?.setGravity(Gravity.BOTTOM)
        }

        val scrimAlpha by animateFloatAsState(
            targetValue = if (visible.targetState) 0.45f else 0f,
            animationSpec = tween(SHEET_IN_MS),
            label = "sheet-scrim",
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { startDismiss() },
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInVertically(tween(SHEET_IN_MS, easing = FastOutSlowInEasing)) { it } +
                    fadeIn(tween(SHEET_IN_MS)),
                exit = slideOutVertically(tween(SHEET_OUT_MS)) { it } +
                    fadeOut(tween(SHEET_OUT_MS)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                        .background(colors.surface)
                        .border(1.dp, colors.border)
                        // "Dem phan trang len" (user 28/8, lan 6): navBottom
                        // + 24dp dai trong HY SINH ben trong day sheet — he
                        // thong co an mat dai day thi chi an khoang trong,
                        // khong bao gio an chu. Khong duoc rut mong lai.
                        .padding(bottom = navBottom + 24.dp),
                ) {
                    content()
                }
            }
        }
    }
}
