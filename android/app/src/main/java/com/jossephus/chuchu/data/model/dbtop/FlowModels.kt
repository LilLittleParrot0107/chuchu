package com.jossephus.chuchu.data.model.dbtop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * flow.json — dòng USDC/USDT VÀO / RA của ví chính, do `flow-scan` trên Legion sinh (user 22/9).
 * Chỉ transfer thật: tiền tới 2 ví spending (đã là chi tiêu) và swap không tính. App chỉ đọc,
 * không cần biết gửi đi đâu / nhận từ đâu — chỉ số tiền theo ngày và tháng.
 */
@Serializable
data class FlowState(
    val ts: Long = 0L,
    val month: String = "",
    @SerialName("month_in") val monthIn: Double = 0.0,
    @SerialName("month_out") val monthOut: Double = 0.0,
    @SerialName("month_net") val monthNet: Double = 0.0,
    @SerialName("month_count") val monthCount: Int = 0,
    /** Cửa sổ ngày gần nhất (flow-scan giữ đủ năm), chỉ ngày có tiền qua lại; `out` dương. */
    @SerialName("by_day") val byDay: Map<String, FlowDay> = emptyMap(),
    /** Từng lệnh theo ngày (mới nhất trước), `amount` có dấu — cho tấm chi tiết ngày. */
    val days: Map<String, List<FlowTx>> = emptyMap(),
    /**
     * Tổng ĐỦ THÁNG từ sổ flow-scan (18/10) — nguồn cho dòng flow ở lưới tháng.
     * Trước đây app cộng `days` (cửa sổ 45 ngày): tháng cũ thiếu ngày ở đầu tháng
     * và tự "nhảy số" khi cửa sổ trượt (Sep 3 rơi đúng 18/10, lệch $779.97).
     * Rỗng với qsrv cũ → app fallback cộng `days` như trước.
     */
    @SerialName("by_month") val byMonth: Map<String, FlowMonth> = emptyMap(),
)

/** Một tháng trong [FlowState.byMonth]: `in` là từ khoá Kotlin nên map qua [inUsd]. */
@Serializable
data class FlowMonth(
    @SerialName("in") val inUsd: Double = 0.0,
    val out: Double = 0.0,
    val net: Double = 0.0,
    val count: Int = 0,
)

@Serializable
data class FlowTx(val ts: Long = 0L, val token: String = "", val amount: Double = 0.0)

@Serializable
data class FlowDay(
    @SerialName("in") val inUsd: Double = 0.0,
    val out: Double = 0.0,
)

/** Một lệnh trong tấm chi tiết ngày của FLOW (user chốt 22/9): [usd] có dấu, dương = nhận. */
data class DayTx(val ts: Long, val token: String, val usd: Double)

private val FLOW_MONTH_KEY = Regex("^\\d{4}-\\d{2}$")

fun isValidFlowMonth(month: String): Boolean = FLOW_MONTH_KEY.matches(month)

/**
 * Tổng flow theo tháng cho lưới năm, ưu tiên `by_month` (đủ tháng).
 *
 * `days`/`by_day` chỉ là cửa sổ gần đây. Với flow.json cũ (45 ngày) tháng đầu
 * cửa sổ bị thiếu ngày, nên chỉ fallback khi cửa sổ thực sự phủ từ mùng 1 —
 * thà ẩn dòng flow còn hơn hiện một tháng thiếu tiền (Sep 2026 lệch $779.97).
 */
fun flowMonthTotals(flow: FlowState?, year: String? = null): Map<String, FlowMonth> {
    if (flow == null) return emptyMap()
    val out = LinkedHashMap<String, FlowMonth>()
    flow.byMonth.forEach { (month, value) ->
        if (isValidFlowMonth(month) && (year == null || month.startsWith(year))) {
            out[month] = value
        }
    }

    // flow.json đời đầu có by_day nhưng chưa có days; đời sau có days. Chọn
    // nguồn chi tiết nhất đang có, không trộn hai cửa sổ.
    val txsByDay: Map<String, List<FlowTx>>
    val aggByDay: Map<String, FlowDay>
    if (flow.days.isNotEmpty()) {
        txsByDay = flow.days
        aggByDay = emptyMap()
    } else {
        txsByDay = emptyMap()
        aggByDay = flow.byDay
    }
    val dayKeys = (txsByDay.keys + aggByDay.keys).filter { it.length == 10 }.sorted()
    val firstDay = dayKeys.firstOrNull() ?: return out
    val buckets = LinkedHashMap<String, MutableList<FlowTx>>()
    val aggregates = LinkedHashMap<String, FlowDay>()

    txsByDay.forEach { (day, txs) ->
        if (day.length != 10) return@forEach
        val month = day.take(7)
        if (!isValidFlowMonth(month) || month in out || (year != null && !month.startsWith(year))) return@forEach
        // Cửa sổ phải bắt đầu trước hoặc đúng mùng 1 của tháng đó.
        if (firstDay > "$month-01") return@forEach
        buckets.getOrPut(month) { mutableListOf() } += txs
    }
    aggByDay.forEach { (day, value) ->
        if (day.length != 10) return@forEach
        val month = day.take(7)
        if (!isValidFlowMonth(month) || month in out || (year != null && !month.startsWith(year))) return@forEach
        if (firstDay > "$month-01") return@forEach
        val prev = aggregates[month] ?: FlowDay()
        aggregates[month] = FlowDay(prev.inUsd + value.inUsd, prev.out + value.out)
    }

    buckets.forEach { (month, txs) ->
        if (txs.isEmpty()) return@forEach
        val inUsd = txs.filter { it.amount > 0 }.sumOf { it.amount }
        val outUsd = -txs.filter { it.amount < 0 }.sumOf { it.amount }
        out[month] = FlowMonth(inUsd, outUsd, inUsd - outUsd, txs.size)
    }
    aggregates.forEach { (month, value) ->
        if (month !in out) out[month] = FlowMonth(value.inUsd, value.out, value.inUsd - value.out, 0)
    }
    return out
}

/** Lệnh chuyển thuần của [day], mới nhất trước. Không đối tác, không mũi tên — dấu và màu là đủ. */
fun flowDayRows(day: String, flow: FlowState?): List<DayTx> =
    flow?.days?.get(day).orEmpty().map { DayTx(it.ts, it.token, it.amount) }.sortedByDescending { it.ts }
