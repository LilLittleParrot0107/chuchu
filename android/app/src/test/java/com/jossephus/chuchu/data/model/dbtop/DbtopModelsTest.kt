package com.jossephus.chuchu.data.model.dbtop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DbtopModelsTest {

    private val sampleJson = """
    {
      "ts": 1787331339,
      "addr": "0xe0bcc717cd73a85da94099320963dcf0009737d7",
      "netWorth": 83266.16149807558,
      "defi": 83152.55025845605,
      "wallet": 113.6112396195322,
      "rows": [
        {
          "name": "Call UBTC @65k",
          "proto": "Rysk",
          "cap": 12568.56,
          "perday": 7.25,
          "apr": 21.05,
          "src": "hợp đồng",
          "expiry": 1787904000,
          "detail": {
            "netUsd": 13036.76,
            "option": {
              "type": "Call",
              "strike": 65000.0,
              "strikeTotal": 13000,
              "expiry": 1787904000,
              "itmUsd": 2409.66,
              "dte": 13.94,
              "prem": 101.06,
              "apr": 21.05
            }
          }
        },
        {
          "name": "neverland · Lending",
          "proto": "neverland",
          "cap": 26492.40,
          "perday": 15.20,
          "apr": 20.95,
          "health": 1.129567,
          "liqDrop": 11.47,
          "liqPx": 0.0278,
          "liqAt": 0.0246,
          "debt": 29676.04,
          "coll": 56167.85,
          "lev": 2.12
        }
      ],
      "curve": [
        [1787221669, 74378.8],
        [1787331339, 83266.16]
      ],
      "daily": [
        ["2026-08-20", 49.2486, 0.564],
        ["2026-08-21", 48.556, 0.7053]
      ],
      "roll": [
        {
          "asset": "UBTC",
          "type": "Call",
          "strike": 80000.0,
          "apr": 44.75,
          "days": 14,
          "spot": 77464.84,
          "cap": 0.0
        }
      ]
    }
    """.trimIndent()

    @Test
    fun testParseStateJson() {
        val state = DbtopJson.decodeFromString<DbtopState>(sampleJson)
        assertEquals("0xe0bcc717cd73a85da94099320963dcf0009737d7", state.addr)
        assertEquals(83266.16, state.netWorth, 0.01)
        assertEquals(2, state.rows.size)
        assertEquals("Call UBTC @65k", state.rows[0].name)
        assertEquals("Rysk", state.rows[0].proto)
        assertEquals("Call", state.rows[0].detail?.option?.type)
        assertEquals(13000.0, state.rows[0].detail?.option?.strikeTotal)

        assertEquals("neverland · Lending", state.rows[1].name)
        assertEquals(1.129567, state.rows[1].health!!, 0.0001)
        assertEquals(2.12, state.rows[1].lev!!, 0.01)

        assertEquals(2, state.curve.size)
        assertEquals(1787221669L, state.curve[0].ts)
        assertEquals(74378.8, state.curve[0].nw, 0.1)

        assertEquals(2, state.daily.size)
        assertEquals("2026-08-20", state.daily[0].date)
        assertEquals(49.2486, state.daily[0].yieldUsd, 0.0001)

        assertEquals(1, state.roll.size)
        assertEquals("UBTC", state.roll[0].asset)
        assertEquals(44.75, state.roll[0].apr, 0.01)
    }

    @Test
    fun testDeFiMathEngine() {
        // Liquidation Drop for HF = 1.12957
        val liqDrop = DeFiMathEngine.calculateLiquidationDropPct(1.12957)
        assertEquals(11.47, liqDrop, 0.02)

        // Liquidation Price for Base Spot 0.0278364 and HF 1.12957
        val liqPrice = DeFiMathEngine.calculateLiquidationPrice(0.0278364, 1.12957)
        assertEquals(0.02464, liqPrice, 0.0001)

        // Leverage for Collateral 56167.85 and Debt 29676.04
        val lev = DeFiMathEngine.calculateLeverage(56167.85, 29676.04)
        assertEquals(2.12, lev, 0.01)

        // Option ITM USD for Call 65k vs Spot 77232.10, Amt 0.2 -> (77232.10 - 65000) * 0.2 = 2446.42
        val callItm = DeFiMathEngine.calculateOptionItmUsd(true, 65000.0, 77232.10, 0.2)
        assertEquals(2446.42, callItm, 0.01)

        // Option ITM USD for Put 67 vs Spot 76.114 (OTM)
        val putItm = DeFiMathEngine.calculateOptionItmUsd(false, 67.0, 76.114, 500.0)
        assertEquals(0.0, putItm, 0.001)
    }

    @Test
    fun testDeFiFormatter() {
        assertEquals("$83,266.16", DeFiFormatter.formatUsd(83266.1615))
        assertEquals("$83.3k", DeFiFormatter.formatUsdCompact(83266.16))
        assertEquals("$543", DeFiFormatter.formatUsdCompact(543.15))
        assertEquals("$77,232.10", DeFiFormatter.formatTokenPrice(77232.10))
        assertEquals("$0.02784", DeFiFormatter.formatTokenPrice(0.0278364))
        assertEquals("+10.19%", DeFiFormatter.formatPercent(10.19))
        assertEquals("-0.36%", DeFiFormatter.formatPercent(-0.36))
        assertEquals("13.9 ngày", DeFiFormatter.formatDteDays(13.94))
        assertEquals("@65k", DeFiFormatter.formatStrikeLabel(65000.0))
        assertEquals("@67", DeFiFormatter.formatStrikeLabel(67.0))
    }

    @Test
    fun testRiskEvaluator() {
        assertEquals(RiskTier.CRITICAL, RiskEvaluator.evaluateLendingRisk(1.12))
        assertEquals(RiskTier.DANGER, RiskEvaluator.evaluateLendingRisk(1.20))
        assertEquals(RiskTier.MODERATE, RiskEvaluator.evaluateLendingRisk(1.35))
        assertEquals(RiskTier.SAFE, RiskEvaluator.evaluateLendingRisk(1.85))
    }
}
