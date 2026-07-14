package com.retrovault.cheats

/**
 * PS1 GameShark / Action Replay code normalization (P24, import-only — Pulsar never ships codes).
 *
 * SwanStation's `retro_cheat_set` parses raw UNENCRYPTED hex pairs (verified against
 * `CheatList::ParseLibretroCheat` at the pinned commit): an 8-hex-digit code word and a
 * 4-hex-digit value. Within a pair the separator must be a space or '+', and multi-line codes
 * are joined with '+' — NEWLINES ARE REJECTED by the core, so pasted multi-line codes must be
 * normalized to RetroArch's '+' convention, e.g.
 *
 *     "80092E60 0063\n80092E62 0000"  ->  "80092E60 0063+80092E62 0000"
 *
 * Encrypted codes (GameShark v3+ etc.) are NOT supported by the core (it has no decryption);
 * they still LOOK like hex pairs, so we can't reliably detect them — the honest contract is
 * "paste unencrypted codes", stated in the UI.
 */
object Ps1CheatCodes {

    private val CANONICAL = Regex("^[0-9A-Fa-f]{8} [0-9A-Fa-f]{4}$")

    /**
     * Normalize a pasted code block into the single '+'-joined string SwanStation accepts,
     * or null when any non-comment line isn't an 8+4 hex pair (not a GameShark-style code).
     */
    fun normalize(pasted: String): String? {
        val pairs = ArrayList<String>()
        for (raw in pasted.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue
            // Collapse any accepted inner separator to a single space.
            val compact = line.replace(Regex("[ \\t+:\\-]+"), " ").trim()
            val bits = compact.split(' ')
            val joined = when {
                bits.size == 2 -> "${bits[0]} ${bits[1]}"
                bits.size == 1 && bits[0].length == 12 -> "${bits[0].take(8)} ${bits[0].drop(8)}"
                else -> return null
            }
            if (!CANONICAL.matches(joined)) return null
            pairs.add(joined.uppercase())
        }
        if (pairs.isEmpty()) return null
        return pairs.joinToString("+")
    }
}
