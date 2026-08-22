package com.jossephus.chuchu.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.jossephus.chuchu.data.model.dbtop.DbtopJson
import com.jossephus.chuchu.data.model.dbtop.DbtopState

/** Persists the latest dbtop snapshot so Dashboard can render before polling. */
class DbtopCacheManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveSnapshot(rawJson: String) {
        prefs.edit().putString(KEY_SNAPSHOT_JSON, rawJson).apply()
    }

    fun loadSnapshot(): DbtopState? = prefs.getString(KEY_SNAPSHOT_JSON, null)
        ?.let { json -> runCatching { DbtopJson.decodeFromString<DbtopState>(json) }.getOrNull() }

    private companion object {
        const val PREF_NAME = "chuchu_dbtop_offline_cache"
        const val KEY_SNAPSHOT_JSON = "cached_state_json"
    }
}
