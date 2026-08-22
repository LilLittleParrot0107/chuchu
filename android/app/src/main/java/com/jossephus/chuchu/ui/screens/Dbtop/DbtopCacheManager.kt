package com.jossephus.chuchu.ui.screens.Dbtop

import android.content.Context
import android.content.SharedPreferences
import com.jossephus.chuchu.data.model.dbtop.DbtopJson
import com.jossephus.chuchu.data.model.dbtop.DbtopState

/**
 * Trình quản lý Cache Offline cho dbtop, đảm bảo tốc độ nạp màn hình 0ms.
 */
class DbtopCacheManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveSnapshot(rawJson: String, timestamp: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_SNAPSHOT_JSON, rawJson)
            .putLong(KEY_SNAPSHOT_TIME, timestamp)
            .apply()
    }

    fun loadSnapshot(): Pair<DbtopState?, Long> {
        val json = prefs.getString(KEY_SNAPSHOT_JSON, null) ?: return null to 0L
        val timestamp = prefs.getLong(KEY_SNAPSHOT_TIME, 0L)
        val state = runCatching { DbtopJson.decodeFromString<DbtopState>(json) }.getOrNull()
        return state to timestamp
    }

    fun clearCache() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREF_NAME = "chuchu_dbtop_offline_cache"
        private const val KEY_SNAPSHOT_JSON = "cached_state_json"
        private const val KEY_SNAPSHOT_TIME = "cached_state_timestamp"
    }
}
