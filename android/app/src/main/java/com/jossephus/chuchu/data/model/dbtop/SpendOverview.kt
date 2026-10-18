package com.jossephus.chuchu.data.model.dbtop

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Dữ liệu đã join cho tab SPEND — tách khỏi Compose để test bằng JVM và để
 * SpendingView chỉ còn việc vẽ. Trước đây filter/fallback/month-union nằm rải
 * trong composable, mỗi bug lại thêm một guard.
 */
data class SpendMonthCell(
    val month: String,
    /** null = tháng chỉ có flow, không có spending. */
    val spendUsd: Double?,
    val flow: FlowMonth?,
)

data class SpendDayCell(val day: String, val usd: Double)

data class SpendOverview(
    val month: String,
    val isCurrentMonth: Boolean,
    val year: String,
    val monthUsd: Double,
    val yearSpendUsd: Double,
    val months: List<SpendMonthCell>,
    val days: List<SpendDayCell>,
    val currentFlow: FlowMonth?,
    val flowDays: List<Pair<String, FlowDay>>,
)

/**
 * Join spending.json + flow.json thành model thuần.
 *
 * Quy tắc:
 *  - tháng của dashboard do spending.month quyết định (flow là phụ);
 *  - lưới tháng là HỢP spending + flow, tháng chỉ có flow vẫn hiện (spend=null);
 *  - flow tháng ưu tiên by_month; fallback days chỉ khi cửa sổ phủ từ mùng 1;
 *  - ngày chi tiêu/flow lọc đúng tháng đang xem.
 */
fun buildSpendOverview(
    spending: SpendingState?,
    flow: FlowState?,
    nowMonth: String,
): SpendOverview? {
    if (spending == null || !isValidFlowMonth(spending.month)) return null
    val year = spending.month.take(4)
    val flowMonths = flowMonthTotals(flow, year)
    val months = (spending.byMonth.keys + flowMonths.keys)
        .filter { it.startsWith(year) && isValidFlowMonth(it) }
        .distinct()
        .sortedDescending()
        .map { month ->
            SpendMonthCell(
                month = month,
                spendUsd = spending.byMonth[month],
                flow = flowMonths[month],
            )
        }
    val days = spending.byDay.entries
        .filter { it.key.startsWith(spending.month) }
        .sortedByDescending { it.key }
        .map { SpendDayCell(it.key, it.value) }
    val flowDays = flow?.byDay?.entries.orEmpty()
        .filter { it.key.startsWith(spending.month) }
        .sortedByDescending { it.key }
        .map { it.key to it.value }
    val yearSpend = spending.byMonth
        .filterKeys { it.startsWith(year) && isValidFlowMonth(it) }
        .values
        .sum()
    return SpendOverview(
        month = spending.month,
        isCurrentMonth = spending.month == nowMonth,
        year = year,
        monthUsd = spending.monthUsd,
        yearSpendUsd = yearSpend,
        months = months,
        days = days,
        currentFlow = flowMonths[spending.month],
        flowDays = flowDays,
    )
}

/** Tháng hiện tại theo múi giờ server (+07) — dùng chung với flow/spending scan. */
fun currentMonthKey(nowMillis: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy-MM", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh") }
        .format(Date(nowMillis))
