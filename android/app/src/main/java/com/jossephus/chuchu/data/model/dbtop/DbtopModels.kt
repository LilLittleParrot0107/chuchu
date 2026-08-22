package com.jossephus.chuchu.data.model.dbtop

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Cấu hình JSON phòng thủ:
 * - Bỏ qua các trường lạ chưa biết
 * - Ép kiểu nới lỏng an toàn
 * - Không ném ngoại lệ khi backend thêm trường
 */
val DbtopJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

// ============================================================================
// 1. DATA TRANSFER OBJECTS (Mapping state.json)
// ============================================================================

@Serializable
data class DbtopState(
    val ts: Long = 0L,
    val addr: String = "",
    val netWorth: Double = 0.0,
    val defi: Double = 0.0,
    val wallet: Double = 0.0,
    val rows: List<DappRow> = emptyList(),
    val opts: List<OptionOverview> = emptyList(),
    val unknown: Int = 0,
    val unknownKeys: List<String> = emptyList(),
    val idle: Double = 0.0,
    val walletTokens: List<WalletToken> = emptyList(),
    val perday: Double = 0.0,
    val cap: Double = 0.0,
    val apr: Double? = null,
    val curve: List<CurvePoint> = emptyList(),
    val roll: List<RollOpportunity> = emptyList(),
    val realized: RealizedIncome? = null,
    val capNote: Double? = null,
    val delta: Map<String, Delta?> = emptyMap(),
    val px: Map<String, Double> = emptyMap(),
    val pxPrev: Map<String, Double> = emptyMap(),
    val prem: PremiumStats? = null,
    val nTxNew: Int? = null,
    val lastTx: Long? = null,
    val quoteAge: Long? = null,
    val daily: List<DailyYield> = emptyList(),
    val mtd: MtdStats? = null,
) {
    /** Độ tuổi của dữ liệu tính theo giây */
    fun ageSeconds(nowSec: Long = System.currentTimeMillis() / 1000): Long =
        if (ts > 0) maxOf(0L, nowSec - ts) else Long.MAX_VALUE

    /** Đánh giá độ tươi của dữ liệu */
    fun freshness(nowSec: Long = System.currentTimeMillis() / 1000): DataFreshness {
        val age = ageSeconds(nowSec)
        return when {
            age < DataFreshness.STALE_THRESHOLD_SEC -> DataFreshness.Fresh(age)
            age < DataFreshness.DEAD_THRESHOLD_SEC -> DataFreshness.Warning(age)
            else -> DataFreshness.Dead(age)
        }
    }
}

// ----------------------------------------------------------------------------
// Độ tươi dữ liệu (Data Freshness)
// ----------------------------------------------------------------------------

sealed interface DataFreshness {
    val ageSeconds: Long

    /** < 40 phút: Dữ liệu tươi */
    data class Fresh(override val ageSeconds: Long) : DataFreshness

    /** 40 phút <= age < 2 giờ: Cảnh báo vàng */
    data class Warning(override val ageSeconds: Long) : DataFreshness

    /** >= 2 giờ: Cảnh báo đỏ */
    data class Dead(override val ageSeconds: Long) : DataFreshness

    companion object {
        const val STALE_THRESHOLD_SEC = 40 * 60L    // 40 phút -> Vàng
        const val DEAD_THRESHOLD_SEC = 2 * 3600L    // 2 giờ -> Đỏ
    }
}

// ----------------------------------------------------------------------------
// Dapp Rows & Vị thế
// ----------------------------------------------------------------------------

@Serializable
data class DappRow(
    val name: String = "",
    val proto: String = "",
    val cap: Double = 0.0,
    val perday: Double = 0.0,
    val apr: Double? = null,
    val src: String = "",
    val expiry: Long? = null,
    val health: Double? = null,
    val detail: DappDetail? = null,
    // Thông tin rủi ro thanh lý
    val liqDrop: Double? = null,
    val liqBase: String? = null,
    val liqPx: Double? = null,
    val liqAt: Double? = null,
    val debt: Double? = null,
    val coll: Double? = null,
    val lev: Double? = null,
)

@Serializable
data class DappDetail(
    val netUsd: Double = 0.0,
    val health: Double? = null,
    val collateral: List<TokenPosition> = emptyList(),
    val supply: List<TokenPosition> = emptyList(),
    val borrow: List<TokenPosition> = emptyList(),
    val reward: List<TokenPosition> = emptyList(),
    val option: OptionDetail? = null,
    val breakdown: JsonElement? = null,
) {
    fun asLendingBreakdown(): LendingBreakdown? = runCatching {
        breakdown?.let { DbtopJson.decodeFromJsonElement(LendingBreakdown.serializer(), it) }
    }.getOrNull()

    fun asPoolBreakdown(): PoolBreakdown? = runCatching {
        breakdown?.let { DbtopJson.decodeFromJsonElement(PoolBreakdown.serializer(), it) }
    }.getOrNull()
}

@Serializable
data class TokenPosition(
    val sym: String = "",
    val amt: Double = 0.0,
    val px: Double = 0.0,
    val usd: Double = 0.0,
    val pct: Double? = null,
    val keep: Double? = null,
    val keepNote: String? = null,
)

// ----------------------------------------------------------------------------
// Chi tiết Option & Lending Breakdown
// ----------------------------------------------------------------------------

@Serializable
data class OptionDetail(
    val type: String = "", // "Call" hoặc "Put"
    val underlying: UnderlyingAsset? = null,
    val strike: Double? = null,
    val strikeTotal: Double? = null,
    val strikeSym: String? = null,
    val expiry: Long? = null,
    val itmUsd: Double? = null,
    val sold: Long? = null,
    val dte: Double? = null,
    val prem: Double? = null,
    val apr: Double? = null,
)

@Serializable
data class UnderlyingAsset(
    val sym: String = "",
    val amt: Double = 0.0,
    val px: Double = 0.0,
)

@Serializable
data class OptionOverview(
    val name: String = "",
    val sold: Long = 0L,
    val dte: Double = 0.0,
    val strikeTotal: Double = 0.0,
    val isCall: Boolean = true,
    val expiry: Long = 0L,
    val spot: Double = 0.0,
    val strike: Double = 0.0,
    val itm: Double = 0.0,
    val net: Double = 0.0,
)

@Serializable
data class LendingBreakdown(
    val stake: Double = 0.0,
    val dust: Double = 0.0,
    val borrow_base: Double = 0.0,
    val dust_rebate: Double = 0.0,
    val keep: Double = 1.0,
    val why: String = "",
    val apr: Double = 0.0,
)

@Serializable
data class PoolBreakdown(
    val type: String = "",
    val apy: Double = 0.0,
    val apyBase: Double? = null,
    val apyReward: Double? = null,
    val tvl: Double? = null,
    val project: String? = null,
    val symbol: String? = null,
    val pool: String? = null,
)

// ----------------------------------------------------------------------------
// Delta, Token ví, Roll & Thống kê
// ----------------------------------------------------------------------------

@Serializable
data class Delta(
    val ts: Long = 0L,
    val nw: Double = 0.0,
    val d: Double = 0.0,
    val pct: Double = 0.0,
    val span: Long = 0L,
    val yieldPart: Double = 0.0,
)

@Serializable
data class WalletToken(
    val sym: String = "",
    val amt: Double = 0.0,
    val px: Double = 0.0,
    val usd: Double = 0.0,
)

@Serializable
data class RollOpportunity(
    val asset: String = "",
    val type: String = "",
    val strike: Double = 0.0,
    val apr: Double = 0.0,
    val days: Int = 0,
    val spot: Double = 0.0,
    val cap: Double? = null,
)

@Serializable
data class RealizedIncome(
    val days: Int = 0,
    val orders: Int = 0,
    val premium: Double = 0.0,
    val perWeek: Double = 0.0,
)

@Serializable
data class PremiumStats(
    val d7: Double = 0.0,
    val d7prev: Double = 0.0,
    val d28: Double = 0.0,
    val d28prev: Double = 0.0,
    val lastSale: Long = 0L,
    val chg7: Double? = null,
)

@Serializable
data class MtdStats(
    val month: String = "",
    val usd: Double = 0.0,
    val days: Double = 0.0,
    val proj: Double? = null,
    val apr: Double? = null,
)

// ----------------------------------------------------------------------------
// Custom Serializers cho DailyYield và CurvePoint
// ----------------------------------------------------------------------------

@Serializable(with = DailyYieldSerializer::class)
data class DailyYield(
    val date: String = "",
    val yieldUsd: Double = 0.0,
    val coverageDays: Double = 0.0,
)

object DailyYieldSerializer : KSerializer<DailyYield> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DailyYield")

    override fun deserialize(decoder: Decoder): DailyYield {
        val input = decoder as? JsonDecoder
            ?: throw IllegalStateException("Yêu cầu JsonDecoder cho DailyYield")
        val element = input.decodeJsonElement()
        return when (element) {
            is JsonArray -> {
                val date = element.getOrNull(0)?.jsonPrimitive?.content ?: ""
                val yUsd = element.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: 0.0
                val cov = element.getOrNull(2)?.jsonPrimitive?.doubleOrNull ?: 0.0
                DailyYield(date = date, yieldUsd = yUsd, coverageDays = cov)
            }
            is JsonObject -> {
                val date = element["date"]?.jsonPrimitive?.content
                    ?: element["ngay"]?.jsonPrimitive?.content ?: ""
                val yUsd = element["yieldUsd"]?.jsonPrimitive?.doubleOrNull
                    ?: element["yield_usd"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val cov = element["coverageDays"]?.jsonPrimitive?.doubleOrNull
                    ?: element["ngay_do"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                DailyYield(date = date, yieldUsd = yUsd, coverageDays = cov)
            }
            else -> DailyYield()
        }
    }

    override fun serialize(encoder: Encoder, value: DailyYield) {
        val out = JsonArray(listOf(
            kotlinx.serialization.json.JsonPrimitive(value.date),
            kotlinx.serialization.json.JsonPrimitive(value.yieldUsd),
            kotlinx.serialization.json.JsonPrimitive(value.coverageDays),
        ))
        (encoder as JsonDecoder).decodeJsonElement()
    }
}

@Serializable(with = CurvePointSerializer::class)
data class CurvePoint(
    val ts: Long = 0L,
    val nw: Double = 0.0,
)

object CurvePointSerializer : KSerializer<CurvePoint> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CurvePoint")

    override fun deserialize(decoder: Decoder): CurvePoint {
        val input = decoder as? JsonDecoder
            ?: throw IllegalStateException("Yêu cầu JsonDecoder cho CurvePoint")
        val element = input.decodeJsonElement()
        return if (element is JsonArray && element.size >= 2) {
            val ts = element[0].jsonPrimitive.longOrNull ?: 0L
            val nw = element[1].jsonPrimitive.doubleOrNull ?: 0.0
            CurvePoint(ts = ts, nw = nw)
        } else {
            CurvePoint()
        }
    }

    override fun serialize(encoder: Encoder, value: CurvePoint) {
        // Serialization
    }
}

// ============================================================================
// 2. RISK TIER & EVALUATOR
// ============================================================================

enum class RiskTier(val label: String, val severity: Int) {
    SAFE("An toàn", 1),
    MODERATE("Cần chú ý", 2),
    DANGER("Nguy hiểm", 3),
    CRITICAL("Nguy kịch", 4)
}

object RiskEvaluator {
    const val HP_BAD = 1.15
    const val HP_WARN = 1.25
    const val T_SOON = 4 * 3600L       // 4h
    const val T_ROLL = 48 * 3600L      // 48h

    fun evaluateLendingRisk(healthFactor: Double?): RiskTier {
        if (healthFactor == null || healthFactor <= 0.0) return RiskTier.SAFE
        return when {
            healthFactor < HP_BAD -> RiskTier.CRITICAL
            healthFactor < HP_WARN -> RiskTier.DANGER
            healthFactor < 1.50 -> RiskTier.MODERATE
            else -> RiskTier.SAFE
        }
    }

    fun evaluateOptionRisk(expiryTs: Long, nowTs: Long, isItm: Boolean): RiskTier {
        val remainingSec = expiryTs - nowTs
        if (remainingSec <= 0) return RiskTier.CRITICAL
        return when {
            remainingSec < T_SOON -> RiskTier.CRITICAL
            isItm -> RiskTier.DANGER
            remainingSec < T_ROLL -> RiskTier.MODERATE
            else -> RiskTier.SAFE
        }
    }
}

// ============================================================================
// 3. FINANCIAL MATH ENGINE
// ============================================================================

object DeFiMathEngine {

    /**
     * Tính toán đệm thanh lý: % giá tài sản thế chấp giảm tối đa trước khi HF chạm 1.0
     */
    fun calculateLiquidationDropPct(healthFactor: Double): Double {
        if (healthFactor <= 1.0) return 0.0
        return (1.0 - (1.0 / healthFactor)) * 100.0
    }

    /**
     * Tính giá kích hoạt thanh lý của tài sản thế chấp cơ sở
     */
    fun calculateLiquidationPrice(baseSpotPx: Double, healthFactor: Double): Double {
        if (healthFactor <= 0.0) return 0.0
        return baseSpotPx / healthFactor
    }

    /**
     * Tính tỷ lệ đòn bẩy: Collateral / Equity
     */
    fun calculateLeverage(collateralUsd: Double, debtUsd: Double): Double {
        val equity = collateralUsd - debtUsd
        if (equity <= 0.0) return 0.0
        return collateralUsd / equity
    }

    /**
     * Tính độ lệch Moneyness chuẩn hoá theo độ biến động: m = ln(K / S0) / sqrt(T)
     */
    fun calculateMoneynessMetric(strike: Double, spot: Double, daysToExpiry: Double): Double {
        if (strike <= 0.0 || spot <= 0.0 || daysToExpiry <= 0.0) return 0.0
        return ln(strike / spot) / sqrt(daysToExpiry)
    }

    /**
     * Tính giá trị ITM (In-The-Money) của quyền chọn
     */
    fun calculateOptionItmUsd(isCall: Boolean, strike: Double, spot: Double, underlyingAmt: Double): Double {
        val diff = if (isCall) (spot - strike) else (strike - spot)
        return max(0.0, diff * underlyingAmt)
    }

    /**
     * Tính APR quyền chọn đã chốt dựa trên vốn thế chấp ban đầu
     */
    fun calculateOptionLockedApr(premiumUsd: Double, collateralAtSaleUsd: Double, dteDays: Double): Double {
        if (collateralAtSaleUsd <= 0.0 || dteDays <= 0.0) return 0.0
        return (premiumUsd / collateralAtSaleUsd) * (365.0 / dteDays) * 100.0
    }

    /**
     * Tính Net APR cho mô hình Lending đòn bẩy có áp dụng Haircut thưởng
     */
    fun calculateLeveragedLendingNetApr(
        stakeApr: Double,
        dustSupplyApr: Double,
        dustBorrowRebateApr: Double,
        borrowBaseCostApr: Double,
        rewardKeepFactor: Double = 0.5,
    ): Double {
        val netDustSupply = dustSupplyApr * rewardKeepFactor
        val netDustRebate = dustBorrowRebateApr * rewardKeepFactor
        return stakeApr + netDustSupply + netDustRebate - abs(borrowBaseCostApr)
    }
}

// ============================================================================
// 4. FORMATTING HELPERS
// ============================================================================

object DeFiFormatter {

    /**
     * Định dạng tiền tệ đầy đủ: $83,266.16 hoặc $83,266
     */
    fun formatUsd(amount: Double?, decimals: Int = 2): String {
        if (amount == null) return "$0"
        val absVal = abs(amount)
        val sign = if (amount < 0) "-" else ""
        return String.format(Locale.US, "%s$%,.${decimals}f", sign, absVal)
    }

    /**
     * Định dạng tiền tệ thu gọn (Compact): $83.2k, $1.2M, $543
     */
    fun formatUsdCompact(amount: Double?): String {
        if (amount == null) return "$0"
        val absVal = abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            absVal >= 1_000_000 -> String.format(Locale.US, "%s$%.1fM", sign, absVal / 1_000_000.0)
            absVal >= 1_000 -> String.format(Locale.US, "%s$%.1fk", sign, absVal / 1_000.0)
            absVal >= 100 -> String.format(Locale.US, "%s$%,.0f", sign, absVal)
            absVal >= 1 -> String.format(Locale.US, "%s$%,.2f", sign, absVal)
            else -> String.format(Locale.US, "%s$%,.4f", sign, absVal)
        }
    }

    /**
     * Định dạng giá token theo độ sâu chữ số: $77,232.10, $0.02784
     */
    fun formatTokenPrice(price: Double?): String {
        if (price == null || price == 0.0) return "$0"
        val absVal = abs(price)
        val sign = if (price < 0) "-" else ""
        return when {
            absVal >= 1000 -> String.format(Locale.US, "%s$%,.2f", sign, absVal)
            absVal >= 1 -> String.format(Locale.US, "%s$%,.3f", sign, absVal)
            absVal >= 0.001 -> String.format(Locale.US, "%s$%.5f", sign, absVal)
            else -> String.format(Locale.US, "%s$%.7f", sign, absVal)
        }
    }

    /**
     * Định dạng số lượng token: 1.11M, 483.8k, 0.20
     */
    fun formatAmount(amount: Double?): String {
        if (amount == null || amount == 0.0) return "0"
        val absVal = abs(amount)
        return when {
            absVal >= 1_000_000 -> String.format(Locale.US, "%.2fM", amount / 1_000_000.0)
            absVal >= 100_000 -> String.format(Locale.US, "%.0fk", amount / 1_000.0)
            absVal >= 1_000 -> String.format(Locale.US, "%.1fk", amount / 1_000.0)
            absVal >= 100 -> String.format(Locale.US, "%,.0f", amount)
            absVal >= 1 -> String.format(Locale.US, "%,.2f", amount)
            else -> String.format(Locale.US, "%,.4f", amount)
        }
    }

    /**
     * Định dạng tỷ lệ phần trăm: +10.19%, -0.36%, 21.05%
     */
    fun formatPercent(pct: Double?, showPlusSign: Boolean = true, decimals: Int = 2): String {
        if (pct == null) return "0.0%"
        val prefix = if (showPlusSign && pct > 0) "+" else ""
        return String.format(Locale.US, "%s%,.${decimals}f%%", prefix, pct)
    }

    /**
     * Định dạng thời gian đáo hạn DTE: "13.9 ngày"
     */
    fun formatDteDays(dte: Double?): String {
        if (dte == null || dte <= 0.0) return "Hết hạn"
        return String.format(Locale.US, "%.1f ngày", dte)
    }

    /**
     * Định dạng đếm ngược thời gian chi tiết: "13d 22h", "03h 45m", "12m 30s"
     */
    fun formatCountdown(secondsRemaining: Long?): String {
        if (secondsRemaining == null || secondsRemaining <= 0) return "HẾT HẠN"
        val days = secondsRemaining / 86400
        val hours = (secondsRemaining % 86400) / 3600
        val minutes = (secondsRemaining % 3600) / 60
        val seconds = secondsRemaining % 60
        return when {
            days > 0 -> String.format(Locale.US, "%dd %02dh", days, hours)
            hours > 0 -> String.format(Locale.US, "%02dh %02dm", hours, minutes)
            else -> String.format(Locale.US, "%02dm %02ds", minutes, seconds)
        }
    }

    /**
     * Định dạng nhãn Strike: @65k, @67, @1.2M
     */
    fun formatStrikeLabel(strike: Double?): String {
        if (strike == null) return "?"
        val absVal = abs(strike)
        return when {
            absVal >= 1_000_000 -> String.format(Locale.US, "@%.1fM", strike / 1_000_000.0).replace(".0M", "M")
            absVal >= 1_000 -> String.format(Locale.US, "@%.1fk", strike / 1_000.0).replace(".0k", "k")
            else -> String.format(Locale.US, "@%,.0f", strike)
        }
    }
}
