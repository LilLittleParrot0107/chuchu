package com.jossephus.chuchu.ui.screens.Queue

/** Polling cadence derived from app and navigation lifecycle state. */
internal enum class QueuePollingMode {
    Stopped,
    Ambient,
    Foreground,
}

internal fun resolveQueuePollingMode(
    isAppActive: Boolean,
    isQueueVisible: Boolean,
): QueuePollingMode =
    when {
        !isAppActive -> QueuePollingMode.Stopped
        isQueueVisible -> QueuePollingMode.Foreground
        else -> QueuePollingMode.Ambient
    }
