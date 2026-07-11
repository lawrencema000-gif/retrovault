package com.retrovault.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.feature.store.UpdateCheck
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P22 acceptance (code half): the APK itself is GPL-compliant — the license + notice texts ship
 * INSIDE it (GPLv3 §4/§6: recipients get a copy of the license with the program; a URL is not a
 * copy) — and the update-check version comparison is correct, including the malformed-tag and
 * no-releases-yet realities of the direct-APK channel.
 */
@RunWith(AndroidJUnit4::class)
class LegalTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun licenseTextsShipInsideTheApk() {
        // NOTICE.md + LICENSE are copied from the repo root at build time (drift-proof); the
        // third-party texts are static assets. All must be present and real.
        val expectations = mapOf(
            "legal/NOTICE.md" to listOf("rcheevos", "PPSSPP", "Chakra Petch", "FidelityFX"),
            "legal/gpl-3.0.txt" to listOf("GNU GENERAL PUBLIC LICENSE", "Version 3"),
            "legal/gpl-2.0.txt" to listOf("GNU GENERAL PUBLIC LICENSE", "Version 2"),
            "legal/apache-2.0.txt" to listOf("Apache License", "Version 2.0"),
            "legal/mit.txt" to listOf("MIT License"),
            "legal/ofl-1.1.txt" to listOf("SIL OPEN FONT LICENSE"),
            "legal/bsd-3-clause.txt" to listOf("Redistribution"),
            "legal/zlib.txt" to listOf("zlib"),
        )
        for ((path, markers) in expectations) {
            val text = ctx.assets.open(path).bufferedReader().readText()
            assertTrue("$path is suspiciously small (${text.length} chars)", text.length > 300)
            for (marker in markers) {
                assertTrue("$path lacks expected marker \"$marker\"", text.contains(marker, ignoreCase = true))
            }
        }
    }

    @Test
    fun updateVersionCompareTruthTable() {
        // Newer.
        assertTrue(UpdateCheck.isNewer("v1.0.0", "0.1.0"))
        assertTrue(UpdateCheck.isNewer("1.0.0", "0.1.0"))
        assertTrue(UpdateCheck.isNewer("v0.1.1", "0.1.0"))
        assertTrue(UpdateCheck.isNewer("v0.2", "0.1.9"))
        assertTrue(UpdateCheck.isNewer("v2", "1.9.9"))
        assertTrue(UpdateCheck.isNewer("v1.0.0-rc1", "0.9.0")) // pre-release suffix ignored
        // Not newer.
        assertFalse(UpdateCheck.isNewer("v0.1.0", "0.1.0"))
        assertFalse(UpdateCheck.isNewer("v0.1", "0.1.0"))     // 0.1 == 0.1.0
        assertFalse(UpdateCheck.isNewer("v0.0.9", "0.1.0"))
        // Malformed input must never nag the user to update.
        assertFalse(UpdateCheck.isNewer("", "0.1.0"))
        assertFalse(UpdateCheck.isNewer("banana", "0.1.0"))
        assertFalse(UpdateCheck.isNewer("v1.0.0", "garbage"))
    }
}
