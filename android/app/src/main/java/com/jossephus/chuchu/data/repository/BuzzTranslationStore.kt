package com.jossephus.chuchu.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * Cache bản dịch buzz theo tweet (27/9, user chốt "cache theo tweet" trong mock):
 * dịch một lần là giữ luôn, không gọi Gemini lại cho tới khi app bị xoá dữ liệu.
 * Khoá = url bài (fallback tên + ngày) — headline đổi cũng không lẫn bản dịch cũ.
 */
object BuzzTranslationStore {

    private const val PREFS = "kohi_buzz_vi"

    fun get(context: Context, key: String): String? =
        prefs(context).getString(key, null)?.takeIf { it.isNotBlank() }

    fun put(context: Context, key: String, vi: String) {
        prefs(context).edit().putString(key, vi).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
