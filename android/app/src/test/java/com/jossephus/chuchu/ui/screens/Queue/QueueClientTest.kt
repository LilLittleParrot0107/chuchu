package com.jossephus.chuchu.ui.screens.Queue

import com.jossephus.chuchu.data.network.normalizeQueueBaseUrl
import com.jossephus.chuchu.testing.LoopbackHttpServer
import com.jossephus.chuchu.testing.RecordedHttpRequest
import com.jossephus.chuchu.testing.StubHttpResponse
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueClientTest {

    @Test
    fun `url copy tu browser hoac api duoc dua ve goc qsrv`() {
        assertEquals("https://host.example/q", normalizeQueueBaseUrl(" https://host.example/q/ui/ "))
        assertEquals(
            "https://host.example/q",
            normalizeQueueBaseUrl("https://host.example/q/state?view=app"),
        )
        assertEquals("https://host.example/q", normalizeQueueBaseUrl("https://host.example/q/"))
    }

    @Test
    fun `client dung duoc url ui cu va van goi dung state route`() {
        val paths = Collections.synchronizedList(mutableListOf<String>())
        withServer(handler = { request ->
            paths += request.path
            StubHttpResponse(200, """{"rev":"r-ui","agents":[],"tasks":[]}""")
        }) { baseUrl ->
            val result = QueueClient("$baseUrl/ui", "", 1_000, 1_000).fetch(null)

            assertTrue(result is QueueClient.Fetch.Fresh)
            assertEquals(listOf("/state?view=app"), paths.toList())
        }
    }

    @Test
    fun `phan hoi 200 khong co rev khong duoc coi la da scan`() {
        withServer(handler = {
            StubHttpResponse(200, """{"agents":[],"tasks":[]}""")
        }) { baseUrl ->
            val result = QueueClient(baseUrl, "", 1_000, 1_000).fetch(null)

            assertTrue(result is QueueClient.Fetch.Failed)
            assertTrue((result as QueueClient.Fetch.Failed).message.contains("/q"))
        }
    }

    @Test
    fun `token cu bi 401 thi thu lai bang tailscale identity`() {
        val authHeaders = Collections.synchronizedList(mutableListOf<String?>())
        withServer(handler = { request ->
            val auth = request.headers["authorization"]
            authHeaders += auth
            if (auth == null) {
                StubHttpResponse(200, """{"rev":"r1","agents":[],"tasks":[]}""")
            } else {
                StubHttpResponse(401, """{"error":"bad token"}""")
            }
        }) { baseUrl ->
            val client = QueueClient(baseUrl, "stale-token", 1_000, 1_000)
            val result = client.fetch(null)

            assertTrue(result is QueueClient.Fetch.Fresh)
            assertEquals(listOf("Bearer stale-token", null), authHeaders.toList())
            assertTrue(client.recoveredWithoutToken)
        }
    }

    @Test
    fun `token rong khong gui authorization header`() {
        val authHeaders = Collections.synchronizedList(mutableListOf<String?>())
        withServer(handler = { request ->
            authHeaders += request.headers["authorization"]
            StubHttpResponse(200, """{"rev":"r2"}""")
        }) { baseUrl ->
            val client = QueueClient(baseUrl, "", 1_000, 1_000)
            val result = client.fetch(null)

            assertTrue(result is QueueClient.Fetch.Fresh)
            assertEquals(listOf<String?>(null), authHeaders.toList())
            assertFalse(client.recoveredWithoutToken)
        }
    }

    @Test
    fun `post bi 401 duoc thu lai dung mot lan voi cung payload`() {
        val payloads = Collections.synchronizedList(mutableListOf<String>())
        withServer(handler = { request ->
            payloads += request.body
            if (request.headers["authorization"] == null) {
                StubHttpResponse(200, """{"note":"added","rev":"r3"}""")
            } else {
                StubHttpResponse(401, """{"error":"bad token"}""")
            }
        }) { baseUrl ->
            val client = QueueClient(baseUrl, "stale-token", 1_000, 1_000)
            val result = client.add("run tests", "w3:p1")

            assertTrue(result is QueueClient.Act.Ok)
            assertEquals(null, (result as QueueClient.Act.Ok).taskId)
            assertEquals(2, payloads.size)
            assertEquals(payloads[0], payloads[1])
            assertTrue(client.recoveredWithoutToken)
        }
    }

    @Test
    fun `add doc duoc task id de ui bao dung la moi xep hang`() {
        withServer(handler = {
            StubHttpResponse(200, """{"id":51,"rev":"r51","dedup":false}""")
        }) { baseUrl ->
            val result = QueueClient(baseUrl, "", 1_000, 1_000).add("do work", "w3:p12")

            assertTrue(result is QueueClient.Act.Ok)
            assertEquals(51, (result as QueueClient.Act.Ok).taskId)
            assertEquals("r51", result.rev)
        }
    }

    private fun withServer(
        handler: (RecordedHttpRequest) -> StubHttpResponse,
        block: (String) -> Unit,
    ) {
        LoopbackHttpServer(handler).use { server -> block(server.baseUrl) }
    }
}
