package com.retrovault.library

/**
 * PS1 serial extraction from SYSTEM.CNF (the boot config at the ISO9660 root of every retail
 * disc). The BOOT line names the game executable, whose filename IS the serial:
 *
 *   BOOT = cdrom:\SLUS_123.45;1        ->  SLUS-12345
 *   BOOT=cdrom:\TEKKEN\SLPS_012.34;1   ->  SLPS-01234   (subdirectory form)
 *   BOOT = cdrom:PSX.EXE;1             ->  null         (unlicensed/homebrew — no serial)
 */
object Ps1Serial {

    private val REGION_PREFIXES = setOf(
        "SLUS", "SCUS", "LSP",           // NTSC-U
        "SLES", "SCES", "SCED", "SLED",  // PAL
        "SLPS", "SLPM", "SCPS", "SIPS",  // NTSC-J
        "PBPX", "PCPX", "PAPX", "ESPM",  // specials/demos
    )

    /** Parse SYSTEM.CNF text into a canonical serial ("SLUS-12345"), or null when unserialed. */
    fun fromSystemCnf(text: String): String? {
        // First BOOT-ish key wins (BOOT for PS1; be tolerant of stray whitespace/case).
        val bootLine = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.uppercase().startsWith("BOOT") && it.contains('=') }
            ?: return null
        val value = bootLine.substringAfter('=').trim()
        return fromBootPath(value)
    }

    /** "cdrom:\DIR\SLUS_123.45;1" -> "SLUS-12345". */
    fun fromBootPath(path: String): String? {
        val name = path
            .substringAfterLast('\\')
            .substringAfterLast('/')
            .substringAfterLast(':')   // "cdrom:SLUS_123.45" (no backslash) also occurs
            .substringBefore(';')
            .trim()
        if (name.isEmpty()) return null
        // SLUS_123.45 / SLUS-123.45 / SLUS123.45 -> prefix + 5 digits.
        val compact = name.uppercase().replace("_", "").replace("-", "").replace(".", "")
        val m = Regex("^([A-Z]{4})(\\d{5})").find(compact) ?: return null
        val (prefix, digits) = m.destructured
        if (prefix !in REGION_PREFIXES) return null
        return "$prefix-$digits"
    }
}
