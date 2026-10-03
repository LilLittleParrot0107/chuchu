package com.jossephus.chuchu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember

/** Mốc ms hiện tại, tự làm mới mỗi [periodMs] khi composable còn trên màn. */
@Composable
internal fun rememberTicking(periodMs: Long = 5_000L): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            kotlinx.coroutines.delay(periodMs)
            now.longValue = System.currentTimeMillis()
        }
    }
    return now
}
