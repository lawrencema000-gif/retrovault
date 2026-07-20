package com.retrovault.settings

import android.content.Context
import kotlin.random.Random

/**
 * The device's persistent PSP adhoc MAC address, as the 12 hex digits PPSSPP's
 * `ppsspp_change_mac_address01..12` core options expect (one digit per option).
 *
 * Persistence matters: with all-zero digits the core generates a random MAC each session and
 * hands it back via RETRO_ENVIRONMENT_SET_VARIABLE — which this host doesn't implement — so
 * without this store every session gets a fresh identity and game-side friend pairing (which
 * keys on MAC) breaks. Generated once, locally-administered unicast (bit 1 of the first octet
 * set, bit 0 clear — same shape as PPSSPP's own CreateRandMAC), then reused forever.
 */
object AdhocMac {

    private const val PREFS = "pulsar-adhoc"
    private const val KEY_MAC = "mac-digits"

    /** 12 lowercase hex digits, generated on first call and persisted. */
    fun digits(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_MAC, null)?.takeIf { isValid(it) }?.let { return it }
        val mac = generate()
        prefs.edit().putString(KEY_MAC, mac).apply()
        return mac
    }

    /** Display form aa:bb:cc:dd:ee:ff for the settings UI. */
    fun formatted(context: Context): String =
        digits(context).chunked(2).joinToString(":")

    fun isValid(s: String): Boolean =
        s.length == 12 && s.all { it in "0123456789abcdef" } &&
            // locally administered (bit 1 set), unicast (bit 0 clear) first octet
            (s[1] in "26ae")

    private fun generate(): String {
        val d = CharArray(12) { "0123456789abcdef"[Random.nextInt(16)] }
        d[1] = "26ae"[Random.nextInt(4)]
        return String(d)
    }
}
