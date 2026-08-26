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
// 2. RISK THRESHOLDS
// ============================================================================

// 25/8/2026 review: RiskTier + evaluate*/DeFiMathEngine (~120 dong) da bi xoa
// — 0 reference trong app, chi con test tu soi guong. Nguong HF thi UI dung that.
object RiskEvaluator {
    const val HP_BAD = 1.15
    const val HP_WARN = 1.25
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
     * Định dạng giá token theo độ sâu chữ số (chuẩn dbtop): $79,475.57, $80.83, $0.02784
     */
    fun formatTokenPrice(price: Double?): String {
        if (price == null || price == 0.0) return "$0"
        val absVal = abs(price)
        val sign = if (price < 0) "-" else ""
        return when {
            absVal >= 100.0 -> String.format(Locale.US, "%s$%,.2f", sign, absVal)
            absVal >= 1.0 -> {
                val intDigits = absVal.toLong().toString().length
                val decimals = maxOf(2, 4 - intDigits)
                String.format(Locale.US, "%s$%,.${decimals}f", sign, absVal)
            }
            else -> {
                // Với token giá < 1: tự động hiển thị đủ chữ số có nghĩa theo log10
                val log10 = kotlin.math.log10(absVal)
                val decimals = (3 - kotlin.math.floor(log10).toInt()).coerceIn(4, 8)
                String.format(Locale.US, "%s$%,.${decimals}f", sign, absVal)
            }
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



}
