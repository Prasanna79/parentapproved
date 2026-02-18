package com.kidswatch.tv.auth

import android.content.SharedPreferences
import org.json.JSONObject

class SharedPrefsSessionPersistence(
    private val prefs: SharedPreferences
) : SessionPersistence {

    companion object {
        private const val KEY_SESSIONS = "sessions_json"
    }

    override fun save(sessions: Map<String, Long>) {
        val json = JSONObject()
        for ((token, timestamp) in sessions) {
            json.put(token, timestamp)
        }
        prefs.edit().putString(KEY_SESSIONS, json.toString()).apply()
    }

    override fun load(): Map<String, Long> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            val map = HashMap<String, Long>()
            for (key in json.keys()) {
                map[key] = json.getLong(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
