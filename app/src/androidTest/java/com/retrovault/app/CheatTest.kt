package com.retrovault.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.cheats.CheatDb
import com.retrovault.cheats.CheatManager
import com.retrovault.core.model.GameSystem
import com.retrovault.download.GameInstaller
import com.retrovault.emulator.CoreAssets
import com.retrovault.emulator.LibretroBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * P14 acceptance: CWCheat cheat.db parsing, per-game enable state round-trip, the legal
 * boundary (no cheat.db bundled in the APK), and live application of cheats to a running
 * PPSSPP session (retro_cheat_reset/set plumbing works — "toggling applies live").
 */
@RunWith(AndroidJUnit4::class)
class CheatTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = instrumentation.targetContext

    private val sampleDb = """
        _S ULUS-10041
        _G Battlegrounds Sample
        _C0 Infinite Health
        _L 0x2089F4C4 0x0098967F
        _L 0x1089F4C8 0x000003E7
        _C1 Max Ammo (default on)
        _L 0x2089A000 0x000F423F

        _S ULUS-99999
        _G Other Game
        _C0 Widescreen
        _L 0x20000000 0x00000001
    """.trimIndent()

    @Test
    fun parsesCwCheatFormat() {
        val db = CheatDb.parse(sampleDb)
        assertEquals(2, db.size)

        val bg = db["ULUS-10041"]!!
        assertEquals(2, bg.size)
        assertEquals("Infinite Health", bg[0].name)
        assertFalse(bg[0].defaultOn)
        assertTrue("code lines joined", bg[0].code.contains("0x2089F4C4 0x0098967F"))
        assertTrue("multi-line code", bg[0].code.contains("\n"))
        assertEquals("Max Ammo (default on)", bg[1].name)
        assertTrue("_C1 = default on", bg[1].defaultOn)

        // Dashless serial lookup normalizes.
        assertEquals(2, CheatDb.cheatsFor(sampleDb, "ULUS10041").size)
    }

    @Test
    fun noCheatDbBundledInApk() {
        // Legal boundary: cheat data is user-imported, never shipped. Assert the app's asset
        // tree contains no cheat.db anywhere.
        fun walk(path: String): Boolean {
            val kids = ctx.assets.list(path) ?: return false
            for (k in kids) {
                val child = if (path.isEmpty()) k else "$path/$k"
                if (k.equals("cheat.db", true) || k.endsWith(".cheat", true)) return true
                if (walk(child)) return true
            }
            return false
        }
        assertFalse("a cheat database must never ship in the APK", walk(""))
    }

    @Test
    fun managerRoundTripsEnabledState() {
        File(ctx.filesDir, "cheats").deleteRecursively()
        val dir = File(ctx.filesDir, "cheats").apply { mkdirs() }
        File(dir, "cheat.db").writeText(sampleDb)
        val mgr = CheatManager(ctx)
        assertTrue(mgr.isDbImported)

        val serial = "ULUS-10041"
        // First run honors default-on flags.
        val entries = mgr.entriesFor(serial)
        assertEquals(2, entries.size)
        assertFalse(entries.first { it.cheat.name == "Infinite Health" }.enabled)
        assertTrue(entries.first { it.cheat.name.startsWith("Max Ammo") }.enabled)
        assertEquals(listOf("0x2089A000 0x000F423F"), mgr.enabledCodes(serial))

        // Toggle Infinite Health on → persists + appears in enabled codes.
        mgr.setEnabled(serial, "Infinite Health", true)
        assertTrue(mgr.anyEnabled(serial))
        assertEquals(2, mgr.enabledCodes(serial).size)

        // Fresh manager instance reads the same state.
        val reopened = CheatManager(ctx)
        assertTrue(reopened.entriesFor(serial).first { it.cheat.name == "Infinite Health" }.enabled)

        // Turn one off.
        reopened.setEnabled(serial, "Infinite Health", false)
        assertEquals(1, CheatManager(ctx).enabledCodes(serial).size)

        // Unknown serial / homebrew → nothing.
        assertTrue(mgr.availableFor("HB12-345678").isEmpty())
        assertTrue(mgr.availableFor(null).isEmpty())
        File(ctx.filesDir, "cheats").deleteRecursively()
    }

    @Test
    fun cheatsApplyLiveToRunningCore() {
        assertTrue(LibretroBridge.available)
        val playable = GameInstaller.installedPlayable(ctx, GameSystem.PSP, "battlegrounds-3")
        assertNotNull("battlegrounds-3 not installed (FirstLightTest provisions it)", playable)

        val core = File(ctx.applicationInfo.nativeLibraryDir, "ppsspp_libretro_android.so")
        val systemDir = File(ctx.filesDir, "system").apply { mkdirs() }
        val saveDir = File(ctx.filesDir, "saves-core").apply { mkdirs() }
        CoreAssets.ensureExtracted(ctx, systemDir)
        com.retrovault.settings.SettingsResolver(ctx).applyToCore(null)

        val intent = Intent(ctx, RenderTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as RenderTestActivity
        var doneRef: CountDownLatch? = null
        try {
            assertTrue(activity.surfaceReady.await(8, TimeUnit.SECONDS))
            LibretroBridge.nativeStartSession(
                core.absolutePath, playable!!.absolutePath,
                systemDir.absolutePath, saveDir.absolutePath
            )
            val done = CountDownLatch(1)
            doneRef = done
            val ok = AtomicBoolean(false)
            Thread({ ok.set(LibretroBridge.nativeRunLoop()); done.countDown() }, "CheatLoop").start()

            // Boot far enough that the CWCheat engine is live.
            val deadline = System.currentTimeMillis() + 60_000
            while (System.currentTimeMillis() < deadline && LibretroBridge.nativeFramesPresented() < 120) {
                Thread.sleep(250)
            }
            assertTrue("game did not boot", LibretroBridge.nativeFramesPresented() >= 120)
            assertTrue("PPSSPP must expose the cheat interface", LibretroBridge.nativeCoreSupportsCheats())

            // Apply a cheat live, then clear it — the run loop must survive both (no crash /
            // no wedge) and keep presenting frames.
            LibretroBridge.nativeApplyCheats(arrayOf("_C0 Test\n0x2089F4C4 0x0098967F"))
            val f1 = LibretroBridge.nativeFramesPresented()
            var d = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < d && LibretroBridge.nativeFramesPresented() < f1 + 30) Thread.sleep(150)
            assertTrue("core wedged after enabling a cheat", LibretroBridge.nativeFramesPresented() >= f1 + 30)

            LibretroBridge.nativeApplyCheats(emptyArray())
            val f2 = LibretroBridge.nativeFramesPresented()
            d = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < d && LibretroBridge.nativeFramesPresented() < f2 + 30) Thread.sleep(150)
            assertTrue("core wedged after clearing cheats", LibretroBridge.nativeFramesPresented() >= f2 + 30)

            LibretroBridge.nativeRequestStop()
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertTrue(ok.get())
        } finally {
            LibretroBridge.nativeRequestStop()
            doneRef?.await(15, TimeUnit.SECONDS)
            activity.finish()
            instrumentation.waitForIdleSync()
        }
    }
}
