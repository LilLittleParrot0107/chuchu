package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.assertEquals
import org.junit.Test

class QueuePollingPolicyTest {

    @Test
    fun `app paused always stops polling`() {
        assertEquals(
            QueuePollingMode.Stopped,
            resolveQueuePollingMode(isAppActive = false, isQueueVisible = false),
        )
        assertEquals(
            QueuePollingMode.Stopped,
            resolveQueuePollingMode(isAppActive = false, isQueueVisible = true),
        )
    }

    @Test
    fun `active app uses cadence for current destination`() {
        assertEquals(
            QueuePollingMode.Ambient,
            resolveQueuePollingMode(isAppActive = true, isQueueVisible = false),
        )
        assertEquals(
            QueuePollingMode.Foreground,
            resolveQueuePollingMode(isAppActive = true, isQueueVisible = true),
        )
    }
}
