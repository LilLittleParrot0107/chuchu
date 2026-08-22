package com.jossephus.chuchu.ui.screens.Queue

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test

/** Opt-in end-to-end check: run with QSRV_LIVE_URL=https://host/q. */
class QueueLiveContractTest {

    @Test
    fun `client doc duoc agent tu qsrv that`() {
        val endpoint = System.getenv("QSRV_LIVE_URL")
        assumeNotNull(endpoint)

        val result = QueueClient(
            baseUrl = endpoint!!,
            token = "",
            connectTimeoutMs = 8_000,
            readTimeoutMs = 12_000,
        ).fetch(sinceRev = null)

        assertTrue("Live Queue fetch failed: $result", result is QueueClient.Fetch.Fresh)
        val state = (result as QueueClient.Fetch.Fresh).state
        assertTrue("Live Queue returned a blank revision", state.rev.isNotBlank())
        assertTrue("Live Queue did not return any agents", state.agents.isNotEmpty())
    }
}
