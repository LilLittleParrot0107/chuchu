package com.jossephus.chuchu.ui.screens.Dbtop

import com.jossephus.chuchu.data.model.dbtop.DappRow
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ẩn vị thế < $10 khỏi POS (user 4/10) — ngưỡng đúng 10$ vẫn giữ. */
class PositionsVisibilityTest {
    @Test
    fun `an vi the duoi nguong, giu dung nguong`() {
        val rows = listOf(
            DappRow(name = "big", cap = 2500.0),
            DappRow(name = "exact", cap = 10.0),
            DappRow(name = "just-below", cap = 9.99),
            DappRow(name = "dust", cap = 0.5),
        )
        assertEquals(listOf("big", "exact"), visiblePositions(rows).map { it.name })
    }

    @Test
    fun `tat ca duoi nguong thi rong`() {
        val rows = listOf(DappRow(name = "a", cap = 5.0), DappRow(name = "b", cap = 0.0))
        assertEquals(emptyList<String>(), visiblePositions(rows).map { it.name })
    }
}
