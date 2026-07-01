package com.retrovault.app.util

import java.util.Locale

/** Human-readable byte size, e.g. 12 MB / 1.4 GB. */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val pattern = if (value >= 100 || unit == 0) "%.0f %s" else "%.1f %s"
    return String.format(Locale.US, pattern, value, units[unit])
}
