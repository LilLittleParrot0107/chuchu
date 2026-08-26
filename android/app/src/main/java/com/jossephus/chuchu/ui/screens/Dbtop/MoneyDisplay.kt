package com.jossephus.chuchu.ui.screens.Dbtop

import com.jossephus.chuchu.data.model.dbtop.DeFiFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Che do hien thi tien cua dashboard (user chot 27/8): tap vao so NET WORTH
 * xoay vong USD -> VND -> AN. Ap cho Overview + tab Spending; positions va
 * charts giu USD.
 */
enum class MoneyDisplay {
    USD, VND, HIDDEN;

    fun next(): MoneyDisplay = when (this) {
        USD -> VND
        VND -> HIDDEN
        HIDDEN -> USD
    }

    companion object {
        fun fromId(id: String?): MoneyDisplay = entries.find { it.name == id } ?: USD
    }
}

/** Fallback khi spending.json chua co ty gia (server moi dung, mang hong...). */
internal const val DEFAULT_USD_VND = 26_000.0

internal fun formatMoney(
    usd: Double,
    mode: MoneyDisplay,
    vndRate: Double,
    compact: Boolean = false,
): String = when (mode) {
    MoneyDisplay.HIDDEN -> "••••"
    MoneyDisplay.USD -> if (compact) DeFiFormatter.formatUsdCompact(usd) else DeFiFormatter.formatUsd(usd)
    MoneyDisplay.VND -> formatVnd(usd * (vndRate.takeIf { it > 0 } ?: DEFAULT_USD_VND))
}

private fun formatVnd(v: Double): String {
    val a = abs(v)
    return when {
        a >= 1e9 -> String.format(Locale.US, "₫%.2fB", v / 1e9)
        a >= 1e6 -> String.format(Locale.US, "₫%.1fM", v / 1e6)
        a >= 1e3 -> String.format(Locale.US, "₫%.0fK", v / 1e3)
        else -> String.format(Locale.US, "₫%.0f", v)
    }
}
