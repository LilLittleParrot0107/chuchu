package com.jossephus.chuchu.data.network

import com.jossephus.chuchu.testing.LoopbackHttpServer
import com.jossephus.chuchu.testing.RecordedHttpRequest
import com.jossephus.chuchu.testing.StubHttpResponse
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DbtopClientTest {

    @Test
    fun `query state json khong mang authorization va dung etag cho poll`() {
        val requests = Collections.synchronizedList(mutableListOf<RecordedHttpRequest>())
        val calls = AtomicInteger(0)
        LoopbackHttpServer { request ->
            requests += request
            if (calls.getAndIncrement() == 0) {
                StubHttpResponse(
                    code = 200,
                    body = SNAPSHOT,
                    headers = mapOf(
                        "ETag" to "\"snapshot-r1\"",
                        "Last-Modified" to "Sat, 22 Aug 2026 10:55:42 GMT",
                    ),
                )
            } else {
                StubHttpResponse(code = 304)
            }
        }.use { server ->
            val client = DbtopClient("${server.baseUrl}/home/debank/state.json", 1_000, 1_000)

            val first = client.fetch()
            val second = client.fetch()

            assertTrue(first is DbtopClient.FetchResult.Fresh)
            assertTrue(second is DbtopClient.FetchResult.Unchanged)
            val state = (first as DbtopClient.FetchResult.Fresh).state
            assertEquals(80_278.52, state.netWorth, 0.01)
            assertEquals("neverland · Lending", state.rows.single().name)
            assertEquals("2026-08-22", state.daily.single().date)

            assertEquals(2, requests.size)
            assertEquals("GET", requests[0].method)
            assertEquals("/home/debank/state.json", requests[0].path)
            assertNull(requests[0].headers["authorization"])
            assertNull(requests[1].headers["authorization"])
            assertEquals("\"snapshot-r1\"", requests[1].headers["if-none-match"])
            assertEquals(
                "Sat, 22 Aug 2026 10:55:42 GMT",
                requests[1].headers["if-modified-since"],
            )
        }
    }

    @Test
    fun `manual refresh bo qua conditional cache headers`() {
        val requests = Collections.synchronizedList(mutableListOf<RecordedHttpRequest>())
        LoopbackHttpServer { request ->
            requests += request
            StubHttpResponse(
                code = 200,
                body = SNAPSHOT,
                headers = mapOf("ETag" to "\"snapshot-r2\""),
            )
        }.use { server ->
            val client = DbtopClient("${server.baseUrl}/state.json", 1_000, 1_000)

            assertTrue(client.fetch() is DbtopClient.FetchResult.Fresh)
            assertTrue(client.fetch(forceRefresh = true) is DbtopClient.FetchResult.Fresh)

            assertEquals(2, requests.size)
            assertNull(requests[1].headers["if-none-match"])
            assertNull(requests[1].headers["if-modified-since"])
        }
    }

    @Test
    fun `json rong khong duoc coi la snapshot da tai thanh cong`() {
        LoopbackHttpServer { StubHttpResponse(code = 200, body = "{}") }.use { server ->
            val result = DbtopClient("${server.baseUrl}/state.json", 1_000, 1_000).fetch()

            assertTrue(result is DbtopClient.FetchResult.Failed)
            assertEquals(
                "The response is not a valid dbtop snapshot (missing ts)",
                (result as DbtopClient.FetchResult.Failed).message,
            )
        }
    }

    private companion object {
        val SNAPSHOT = """
            {
              "ts": 1787396737,
              "addr": "0xe0bcc717cd73a85da94099320963dcf0009737d7",
              "netWorth": 80278.526,
              "defi": 80169.581,
              "wallet": 108.945,
              "perday": 67.069,
              "rows": [{
                "name": "neverland · Lending",
                "proto": "neverland",
                "cap": 26124.41,
                "perday": 15.44,
                "apr": 21.57,
                "health": 1.1625,
                "detail": {
                  "netUsd": 26124.41,
                  "supply": [{"sym":"sMON","amt":1112438.6,"px":0.0292,"usd":32578.8}],
                  "borrow": [{"sym":"USDC","amt":27172.1,"px":1.0,"usd":27174.8}]
                }
              }],
              "curve": [[1787395562,80716.96],[1787396737,80278.52]],
              "daily": [["2026-08-22",30.5638,0.4553]]
            }
        """.trimIndent()
    }
}
