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

/** Một lệnh trong tấm chi tiết ngày của FLOW (user chốt 22/9): [usd] có dấu, dương = nhận. */
data class DayTx(val ts: Long, val token: String, val usd: Double)

/** Lệnh chuyển thuần của [day], mới nhất trước. Không đối tác, không mũi tên — dấu và màu là đủ. */
fun flowDayRows(day: String, flow: FlowState?): List<DayTx> =
    flow?.days?.get(day).orEmpty().map { DayTx(it.ts, it.token, it.amount) }.sortedByDescending { it.ts }
