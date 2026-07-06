package com.retrovault.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.data.CompatReporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * P13 acceptance: prompt policy (≥10 min, once per serial+version), a real report round-trip
 * through the live edge function (insert → duplicate rejected → public summary reflects it).
 * Test rows land on the reserved serial ZZZT-99999 with a random app version per run, so the
 * once-per constraint is exercised without cross-run interference.
 */
@RunWith(AndroidJUnit4::class)
class CompatReportTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun promptPolicyTenMinutesAndOncePerSerialVersion() {
        File(ctx.filesDir, "compat-prompted.json").delete()
        val v = "test-${UUID.randomUUID().toString().take(8)}"

        // Under 10 minutes → never.
        assertFalse(CompatReporter.shouldPrompt(ctx, playedMs = 9 * 60 * 1000L, serial = "ULUS-10041", appVersion = v))
        // No serial (homebrew) → never.
        assertFalse(CompatReporter.shouldPrompt(ctx, playedMs = 60 * 60 * 1000L, serial = null, appVersion = v))
        // Eligible once…
        assertTrue(CompatReporter.shouldPrompt(ctx, playedMs = 11 * 60 * 1000L, serial = "ULUS-10041", appVersion = v))
        CompatReporter.markPrompted(ctx, "ULUS-10041", v)
        // …then never again for that serial+version.
        assertFalse(CompatReporter.shouldPrompt(ctx, playedMs = 11 * 60 * 1000L, serial = "ULUS-10041", appVersion = v))
        // A different serial is still eligible.
        assertTrue(CompatReporter.shouldPrompt(ctx, playedMs = 11 * 60 * 1000L, serial = "UCUS-98615", appVersion = v))
        File(ctx.filesDir, "compat-prompted.json").delete()
    }

    @Test
    fun installIdIsStable() {
        val a = CompatReporter.installId(ctx)
        val b = CompatReporter.installId(ctx)
        assertEquals(a, b)
        assertEquals(36, a.length)
    }

    @Test
    fun reportRoundTripInsertDuplicateAndSummary() {
        val serial = "ZZZT-99999"
        val version = "test-${UUID.randomUUID().toString().take(8)}"

        // First submission stores.
        val first = runBlocking {
            CompatReporter.submit(
                ctx, serial = serial, rating = 4,
                subScores = mapOf("graphics" to 5, "speed" to 3),
                device = mapOf("model" to "emulator-x86_64", "gpuFamily" to "EMULATOR"),
                settingsDiff = mapOf("psp.video.frameskip" to "1"),
                appVersion = version, coreVersion = "test",
            )
        }
        assertTrue("first report should insert", first)

        // Same install+serial+version again → backend refuses the duplicate.
        val second = runBlocking {
            CompatReporter.submit(ctx, serial = serial, rating = 1, appVersion = version)
        }
        assertFalse("duplicate must be rejected", second)

        // Public summary exists and counts at least this run's report.
        val summary = runBlocking { CompatReporter.summaryFor(serial) }
        assertNotNull("summary missing", summary)
        assertTrue("count should be >= 1, got ${summary!!.reportCount}", summary.reportCount >= 1)
        assertNotNull("avg missing", summary.avgRating)
        assertTrue("avg out of range: ${summary.avgRating}", summary.avgRating!! in 1.0..5.0)
    }
}
