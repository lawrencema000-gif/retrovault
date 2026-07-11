package com.retrovault.feature.store

/**
 * Version comparison for the manual update check (foss/direct-APK channel only — the Play build
 * ships no update-check code at all, per Play's Device & Network Abuse policy; F-Droid tolerates a
 * strictly user-initiated check). Pure logic, exercised by the instrumented suite.
 */
object UpdateCheck {

    const val RELEASES_API =
        "https://api.github.com/repos/lawrencema000-gif/retrovault/releases/latest"
    const val RELEASES_PAGE = "https://github.com/lawrencema000-gif/retrovault/releases"

    /**
     * True when [latestTag] (a release tag like "v1.2.0", "1.2", "v1.0.0-rc1") denotes a version
     * strictly newer than [current] (a versionName like "0.1.0"). Unparseable input is never
     * "newer" — a malformed tag must not nag users to update.
     */
    fun isNewer(latestTag: String, current: String): Boolean {
        val latest = parse(latestTag) ?: return false
        val cur = parse(current) ?: return false
        for (i in 0 until maxOf(latest.size, cur.size)) {
            val l = latest.getOrElse(i) { 0 }
            val c = cur.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    /** "v1.2.3-rc1" -> [1, 2, 3]; null when there is no leading numeric version at all. */
    private fun parse(raw: String): List<Int>? {
        val core = raw.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+').trim()
        if (core.isEmpty()) return null
        val parts = core.split('.')
        val nums = ArrayList<Int>(parts.size)
        for (p in parts) {
            val n = p.toIntOrNull() ?: return if (nums.isEmpty()) null else nums
            nums.add(n)
        }
        return nums
    }
}
