package com.jossephus.chuchu.data.model.dbtop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * spending.json — tổng chi tiêu, do `spending-scan` trên Legion sinh từ các
 * transfer chảy VÀO 2 ví đích spending (user chốt 26/8: gửi tiền về 2 ví đó
 * nghĩa là đã tiêu). App chỉ đọc và hiển thị, không tự tính gì thêm.
 */
@Serializable
data class SpendingState(
    val ts: Long = 0L,
    @SerialName("total_usd") val totalUsd: Double = 0.0,
    @SerialName("month_usd") val monthUsd: Double = 0.0,
    val month: String = "",
    val count: Int = 0,
    @SerialName("by_month") val byMonth: Map<String, Double> = emptyMap(),
    /** Chi tieu theo ngay cua THANG HIEN TAI — chi ngay co chi tieu. */
    @SerialName("by_day") val byDay: Map<String, Double> = emptyMap(),
    /**
     * Gia token ~24h truoc (tu snapshot debank gan moc now-24h). App tinh
     * % 24h so voi px hien tai cua state.json. KHONG dung pxPrev cua
     * state.json cho viec nay: do la gia cua lan quet truoc (30 phut).
     */
    val px24: Map<String, Double> = emptyMap(),
    val recent: List<SpendingEntry> = emptyList(),
)

@Serializable
data class SpendingEntry(
    val ts: Long = 0L,
    val token: String = "",
    val amount: Double = 0.0,
    val usd: Double = 0.0,
)
