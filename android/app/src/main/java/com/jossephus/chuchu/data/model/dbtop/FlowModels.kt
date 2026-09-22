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
    /** 45 ngày gần nhất, chỉ ngày có tiền qua lại; `out` dương. */
    @SerialName("by_day") val byDay: Map<String, FlowDay> = emptyMap(),
    /** Từng lệnh theo ngày (mới nhất trước), `amount` có dấu — cho tấm chi tiết ngày. */
    val days: Map<String, List<FlowTx>> = emptyMap(),
)

@Serializable
data class FlowTx(val ts: Long = 0L, val token: String = "", val amount: Double = 0.0)

@Serializable
data class FlowDay(
    @SerialName("in") val inUsd: Double = 0.0,
    val out: Double = 0.0,
)

/** Một hàng bảng BY DAY của tab SPEND: ngày có chi tiêu hoặc có flow trong tháng đang xem. */
data class DayFlowRow(val day: String, val spend: Double, val inUsd: Double, val out: Double)

/**
 * Gộp chi tiêu theo ngày (spending.json) với flow theo ngày (flow.json) thành các hàng của
 * tháng đang xem, mới nhất trước. flow lệch tháng (server chưa quét tới) thì coi như không có
 * flow — hàng vẫn ra, cột in/out bằng 0 — chứ không trộn số tháng khác vào.
 */
fun dayFlowRows(spending: SpendingState, flow: FlowState?): List<DayFlowRow> {
    val month = spending.month
    val flowDays = flow?.takeIf { it.month == month }?.byDay.orEmpty()
    // sortedDescending trên List: SortedSet.reversed() bị resolve vào member JDK 21, runtime 17/Android không có.
    val days = (spending.byDay.keys + flowDays.keys).filter { it.startsWith(month) }.distinct().sortedDescending()
    return days.map { d ->
        DayFlowRow(
            day = d,
            spend = spending.byDay[d] ?: 0.0,
            inUsd = flowDays[d]?.inUsd ?: 0.0,
            out = flowDays[d]?.out ?: 0.0,
        )
    }
}

/** Một lệnh trong tấm chi tiết ngày (user chốt B, 22/9): [kind] = "in" | "out" | "spend", [usd] luôn dương. */
data class DayTx(val ts: Long, val kind: String, val token: String, val usd: Double)

/** Lệnh của [day] từ cả hai nguồn, mới nhất trước. Không đối tác — user chỉ cần gửi/nhận bao nhiêu. */
fun dayTransactions(day: String, spending: SpendingState, flow: FlowState?): List<DayTx> {
    val fromFlow = flow?.days?.get(day).orEmpty().map {
        DayTx(it.ts, if (it.amount >= 0) "in" else "out", it.token, kotlin.math.abs(it.amount))
    }
    val fromSpend = spending.days[day].orEmpty().map { DayTx(it.ts, "spend", it.token, it.usd) }
    return (fromFlow + fromSpend).sortedByDescending { it.ts }
}
