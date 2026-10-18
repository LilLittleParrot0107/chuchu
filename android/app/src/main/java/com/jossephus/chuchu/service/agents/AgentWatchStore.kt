package com.jossephus.chuchu.service.agents

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Nơi duy nhất đọc/ghi snapshot trực agent. Tách khỏi AgentWatch để phần
 * điều phối không tự sờ SharedPreferences và để commit() luôn đi cùng save.
 */
internal class AgentWatchStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rev(): String? = prefs.getString(KEY_REV, null)

    fun savedSnaps(): List<AgentSnap> {
        val raw = prefs.getString(KEY_SNAP, null) ?: return emptyList()
        return runCatching { parseSnaps(raw) }.getOrDefault(emptyList())
    }

    fun save(rev: String, snaps: List<AgentSnap>) {
        // commit() trước khi receiver finish: apply() bất đồng bộ có thể mất
        // snapshot nếu process bị kill ngay sau khi post notification.
        prefs.edit()
            .putString(KEY_REV, rev)
            .putString(KEY_SNAP, snapsJson(snaps))
            .commit()
    }

    fun clear() {
        prefs.edit().clear().commit()
    }

    private fun snapsJson(snaps: List<AgentSnap>): String {
        val arr = JSONArray()
        for (s in snaps) {
            arr.put(
                JSONObject()
                    .put("pane", s.pane)
                    .put("name", s.name)
                    .put("label", s.label)
                    .put("preview", s.preview),
            )
        }
        return arr.toString()
    }

    private fun parseSnaps(json: String): List<AgentSnap> {
        val arr = JSONArray(json)
        val out = ArrayList<AgentSnap>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pane = o.optString("pane")
            if (pane.isBlank()) continue
            out += AgentSnap(
                pane = pane,
                name = o.optString("name"),
                label = o.optString("label"),
                preview = o.optString("preview"),
            )
        }
        return out
    }

    private companion object {
        const val PREFS = "agent_watch"
        const val KEY_REV = "rev"
        const val KEY_SNAP = "snap"
    }
}
