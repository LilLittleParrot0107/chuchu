package com.jossephus.chuchu.ui.screens.Dbtop

import com.jossephus.chuchu.ui.components.chart.CashflowEngine
import com.jossephus.chuchu.ui.components.chart.NetRatePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * GROSS APR 30d chỉ tính ngày đo đủ (coverage >= MIN_COVERAGE = 0.05); ngày coverage
 * tí xíu có grossRate là số thô (không quy về ngày đầy) nên KHÔNG được lọt vào trung bình
 * (bug im lặng 3/10: lọc > 0.0).
 */
class GrossApr30Test {
    private fun pt(date: String, gross: Double, coverage: Double, grossRate: Double) =
        NetRatePoint(
            date = date,
            gross = gross,
            spend = 0.0,
            coverage = coverage,
            grossRate = grossRate,
            trailGross = grossRate,
            trailSpend = 0.0,
            trailNet = grossRate,
        )

    @Test
    fun `ngay coverage duoi nguong bi loai`() {
        val points = listOf(
            pt("2026-10-01", gross = 1.0, coverage = 0.01, grossRate = 1.0),
            pt("2026-10-02", gross = 6.0, coverage = 0.60, grossRate = 10.0),
        )
        // cap 3650 USD: chỉ ngày thứ hai (grossRate 10) => 10*365/3650*100 = 100%
        assertEquals(100.0, CashflowEngine.grossApr30(points, cap = 3650.0)!!, 0.001)
    }

    @Test
    fun `khong ngay do duoc thi null`() {
        val points = listOf(pt("2026-10-01", 1.0, 0.01, 1.0))
        assertNull(CashflowEngine.grossApr30(points, cap = 3650.0))
    }

    @Test
    fun `cap khong duong thi null`() {
        val points = listOf(pt("2026-10-02", 6.0, 0.60, 10.0))
        assertNull(CashflowEngine.grossApr30(points, cap = 0.0))
    }
}
