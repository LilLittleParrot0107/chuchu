package com.jossephus.chuchu.ui.screens.Dbtop

import com.jossephus.chuchu.data.model.dbtop.DappDetail
import com.jossephus.chuchu.data.model.dbtop.DappRow
import com.jossephus.chuchu.data.model.dbtop.DataFreshness
import com.jossephus.chuchu.data.model.dbtop.DbtopState
import com.jossephus.chuchu.data.model.dbtop.OptionDetail
import com.jossephus.chuchu.data.model.dbtop.TokenPosition
import com.jossephus.chuchu.data.model.dbtop.WalletToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DbtopUiStateTest {

    private val state = DbtopState(
        rows = listOf(
            DappRow(name = "Call BTC", detail = DappDetail(option = OptionDetail())),
            DappRow(name = "Lending position", health = 1.5),
            DappRow(name = "Stable Pool"),
            DappRow(name = "Unclassified"),
        ),
    )

    @Test
    fun `dashboard defaults to the positions view without stale selection`() {
        val ui = DbtopUiState(state = state)

        assertEquals(DbtopView.POSITIONS, ui.selectedView)
        assertNull(ui.selectedPositionKey)
    }

    @Test
    fun `position selection key includes protocol and source`() {
        val first = DappRow(name = "Vault", proto = "alpha", src = "wallet-a")
        val sameName = DappRow(name = "Vault", proto = "beta", src = "wallet-b")

        assertNotEquals(first.positionKey(), sameName.positionKey())
    }

    @Test
    fun `risk banner selects the lowest health factor`() {
        val ui = DbtopUiState(
            state = DbtopState(
                rows = listOf(
                    DappRow(name = "warning", health = 1.22),
                    DappRow(name = "critical", health = 1.08),
                    DappRow(name = "safe", health = 1.8),
                ),
            ),
        )

        assertEquals("critical", ui.criticalLendingRow?.name)
    }

    @Test
    fun `per day bo option da dao han nhu dbtop`() {
        val ui = DbtopUiState(
            state = DbtopState(
                rows = listOf(
                    DappRow(name = "expired", perday = 12.0, expiry = 999L),
                    DappRow(name = "live", perday = 5.5, expiry = 2_000L),
                    DappRow(name = "lending", perday = 3.0),
                ),
            ),
            freshness = DataFreshness.Fresh(ageSeconds = 30L),
            // currentPerDay an yield khi chua everLoaded (23/8) — test nay
            // kiem tra logic tru/bo option het han nen phai gia lap da load.
            everLoaded = true,
        )

        assertEquals(8.5, ui.currentPerDay(nowSec = 1_000L)!!, 0.001)
    }

    @Test
    fun `snapshot chet thi an yield nhu dbtop`() {
        val ui = DbtopUiState(
            state = DbtopState(rows = listOf(DappRow(perday = 99.0))),
            freshness = DataFreshness.Dead(ageSeconds = 8_000L),
        )

        assertEquals(null, ui.currentPerDay(nowSec = 1_000L))
    }

    @Test
    fun `buildWatchlist always includes and pins BTC first even with zero holdings`() {
        val testState = DbtopState(
            px = mapOf("ETH" to 2500.0, "BTC" to 77000.0, "MON" to 0.025),
            walletTokens = listOf(
                WalletToken(sym = "ETH", amt = 1.0, usd = 2500.0, px = 2500.0),
            ),
        )

        val watchlist = testState.buildWatchlist()
        assertEquals(2, watchlist.size)
        assertEquals("BTC", watchlist[0].symbol)
        assertEquals(77000.0, watchlist[0].price, 0.01)
        assertEquals(0.0, watchlist[0].totalUsd, 0.01)
        assertEquals("ETH", watchlist[1].symbol)
    }

    @Test
    fun `buildWatchlist subtracts borrowed tokens from totalUsd`() {
        val testState = DbtopState(
            px = mapOf("BTC" to 70000.0),
            rows = listOf(
                DappRow(
                    proto = "Lending",
                    detail = DappDetail(
                        supply = listOf(TokenPosition(sym = "BTC", amt = 2.0, px = 70000.0, usd = 140000.0)),
                        borrow = listOf(TokenPosition(sym = "BTC", amt = 0.5, px = 70000.0, usd = 35000.0)),
                    ),
                ),
            ),
        )
        val watchlist = testState.buildWatchlist()
        assertEquals(1, watchlist.size)
        assertEquals("BTC", watchlist[0].symbol)
        assertEquals(105000.0, watchlist[0].totalUsd, 0.01)
    }

    @Test
    fun `buildWatchlist respects dynamic benchmarks from state`() {
        val testState = DbtopState(
            benchmarks = listOf("SOL"),
            px = mapOf("SOL" to 150.0, "BTC" to 70000.0),
        )
        val watchlist = testState.buildWatchlist()
        assertEquals(1, watchlist.size)
        assertEquals("SOL", watchlist[0].symbol)
    }

    @Test
    fun `buildWatchlist normalizes FBTC to BTC and aggregates`() {
        val testState = DbtopState(
            px = mapOf("BTC" to 70000.0),
            walletTokens = listOf(
                WalletToken(sym = "FBTC", amt = 0.1, px = 70000.0, usd = 7000.0),
            ),
        )
        val watchlist = testState.buildWatchlist()
        assertEquals(1, watchlist.size)
        assertEquals("BTC", watchlist[0].symbol)
        assertEquals(7000.0, watchlist[0].totalUsd, 0.01)
    }

    @Test
    fun `buildWatchlist retains net debt positions exceeding 100 USD threshold`() {
        val testState = DbtopState(
            benchmarks = listOf("BTC"),
            px = mapOf("BTC" to 70000.0, "ETH" to 2500.0),
            rows = listOf(
                DappRow(
                    proto = "Aave",
                    detail = DappDetail(
                        borrow = listOf(TokenPosition(sym = "ETH", amt = 2.0, px = 2500.0, usd = 5000.0)),
                    ),
                ),
            ),
        )
        val watchlist = testState.buildWatchlist()
        assertEquals(2, watchlist.size)
        assertEquals("BTC", watchlist[0].symbol)
        assertEquals("ETH", watchlist[1].symbol)
        assertEquals(-5000.0, watchlist[1].totalUsd, 0.01)
    }

    @Test
    fun `buildWatchlist resolves mixed-case aliases in px24 such as cbBTC`() {
        val testState = DbtopState(
            benchmarks = listOf("BTC"),
            px = mapOf("BTC" to 70000.0),
        )
        val watchlist = testState.buildWatchlist(px24 = mapOf("cbBTC" to 68000.0))
        assertEquals(1, watchlist.size)
        assertEquals("BTC", watchlist[0].symbol)
        assertEquals(2.94, watchlist[0].changePct24h!!, 0.01)
    }

    @Test
    fun `buildWatchlist filters out NaN or non-finite prices`() {
        val testState = DbtopState(
            benchmarks = listOf("BTC"),
            px = mapOf("BTC" to Double.NaN),
        )
        val watchlist = testState.buildWatchlist()
        assertEquals(0, watchlist.size)
    }
}
