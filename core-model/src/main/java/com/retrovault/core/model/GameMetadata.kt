package com.retrovault.core.model

/**
 * Metadata identified from a game file — the universal key ([serial]) plus display fields
 * extracted from the disc/EBOOT itself (no external scraping needed for PSP).
 */
data class GameMetadata(
    val serial: String,        // canonical LETTERS-DIGITS, or a stable fake ID for homebrew
    val title: String,
    val discVersion: String?,
    val iconPath: String?,     // absolute path to extracted ICON0.png in app storage, or null
    val bootCrc: String?,      // CRC32 of the boot module (bad-dump / revision detection)
    val fakeId: Boolean,       // true when no DISC_ID was present (homebrew)
    val system: GameSystem,
)

/** Canonical serial handling shared between library identification and the compat GameDB. */
object Serial {

    /** "UCUS98615" → "UCUS-98615"; already-dashed or unusual ids are just upper-cased. */
    fun canonical(discId: String): String {
        val s = discId.trim().uppercase().replace('_', '-')
        if (s.isEmpty()) return s
        if (s.contains('-')) return s
        val m = Regex("^([A-Z]{2,4})([0-9]{3,6})$").find(s) ?: return s
        return "${m.groupValues[1]}-${m.groupValues[2]}"
    }

    /** Stable pseudo-ID for homebrew with no DISC_ID, derived from a seed (file name/path). */
    fun fakeId(seed: String): String {
        var h = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
        for (c in seed) {
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        val v = h and 0xFFFFFFFFFFFFFFL
        val hex = v.toString(16).uppercase().padStart(8, '0')
        return "HB${hex.take(2)}-${hex.takeLast(6)}"
    }
}
