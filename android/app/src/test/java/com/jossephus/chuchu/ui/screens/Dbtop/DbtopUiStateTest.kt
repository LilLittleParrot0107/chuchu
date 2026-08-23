package com.jossephus.chuchu.ui.screens.Dbtop

import com.jossephus.chuchu.data.model.dbtop.DappDetail
import com.jossephus.chuchu.data.model.dbtop.DappRow
import com.jossephus.chuchu.data.model.dbtop.DataFreshness
import com.jossephus.chuchu.data.model.dbtop.DbtopState
import com.jossephus.chuchu.data.model.dbtop.OptionDetail
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
}
