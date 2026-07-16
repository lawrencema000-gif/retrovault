package com.retrovault.library

import android.content.Context
import org.json.JSONObject

/**
 * Last-played game + per-game playtime (P27). Backs the "Continue playing" widget, dynamic
 * shortcuts, and the playtime chip. SharedPreferences-backed (cross-process safe enough for a
 * write-on-session / read-on-widget-update pattern).
 */
class RecentPlays(context: Context) {

    data class Entry(
        val gameKey: String,
        val title: String,
        val system: String,
        val path: String,
        val lastPlayedMs: Long,
        val totalPlayMs: Long,
    )

    private val prefs = context.applicationContext
        .getSharedPreferences("pulsar-recent-plays", Context.MODE_PRIVATE)

    /** Record that a session started for this game (also makes it the widget's target). */
    fun recordSession(gameKey: String, title: String, system: String, path: String) {
        prefs.edit()
            .putString(KEY_LAST, JSONObject()
                .put("gameKey", gameKey).put("title", title)
                .put("system", system).put("path", path)
                .put("at", System.currentTimeMillis())
                .toString())
            .apply()
    }

    /** Accumulate play duration for a finished session. */
    fun addPlaytime(gameKey: String, durationMs: Long) {
        if (durationMs <= 0) return
        val k = "time-$gameKey"
        prefs.edit().putLong(k, prefs.getLong(k, 0L) + durationMs).apply()
    }

    fun playtimeMs(gameKey: String): Long = prefs.getLong("time-$gameKey", 0L)

    /** The most recently played game, or null before the first session. */
    fun last(): Entry? {
        val raw = prefs.getString(KEY_LAST, null) ?: return null
        return runCatching {
            val j = JSONObject(raw)
            Entry(
                gameKey = j.getString("gameKey"),
                title = j.getString("title"),
                system = j.getString("system"),
                path = j.getString("path"),
                lastPlayedMs = j.getLong("at"),
                totalPlayMs = playtimeMs(j.getString("gameKey")),
            )
        }.getOrNull()
    }

    companion object {
        private const val KEY_LAST = "last"

        /** "3h 24m" / "12m" / "<1m" for the playtime chip. */
        fun format(ms: Long): String {
            val minutes = ms / 60_000
            return when {
                minutes < 1 -> "<1m"
                minutes < 60 -> "${minutes}m"
                else -> "${minutes / 60}h ${minutes % 60}m"
            }
        }
    }
}
