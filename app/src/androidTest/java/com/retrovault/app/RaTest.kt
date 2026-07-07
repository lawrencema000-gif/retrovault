package com.retrovault.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.emulator.CoreAssets
import com.retrovault.emulator.LibretroBridge
import com.retrovault.emulator.RaBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * P20 acceptance (emulator-testable half): rcheevos rc_client builds + links; the memory-read
 * callback maps PSP RAM (or gracefully reports none); the HTTP marshalling delivers responses on
 * the run-loop thread; achievement progress rides inside save states; and — the crown jewel —
 * hardcore mode verifiably BLOCKS the banned features (state-load, slow-mo, cheats) while ALLOWING
 * fast-forward and save-state creation (RA's actual rules).
 *
 * The live half (real login + real unlock in softcore + hardcore) needs an RA account AND RA-side
 * approval of Pulsar as a compliant emulator; it is staged.
 */
@RunWith(AndroidJUnit4::class)
class RaTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = instrumentation.targetContext

    // ---- offline unit tests: no core, no server ----
    @Test
    fun rcheevosLinksSelfTestsAndProgressContainer() {
        assertTrue("native host missing", LibretroBridge.available)

        // rcheevos compiled + linked; version derived from rc_version_string() ("11.6", not "11.6.0").
        assertEquals("11.6", RaBridge.nativeRaVersionString().substringAfter('/'))

        // rc_client create/destroy with the real memory + server-call shims (hardcore default on).
        assertTrue("rc_client create/destroy failed", RaBridge.nativeRaSelfTestCreate())

        // HTTP marshalling: a completion posted from another thread must invoke rc_client's callback
        // on the DRAINING (run-loop) thread, exactly once — never on the completing thread.
        assertTrue("http callback did not marshal to the run-loop thread", RaBridge.nativeRaSelfTestHttp())

        // Save-state RA-progress container: [core][rc][footer] splits back exactly, and a footer-less
        // legacy state still loads as pure-core.
        assertTrue("progress container round-trip failed", RaBridge.nativeRaTestContainer())
    }

    // ---- hardcore interlock + memory on the real PPSSPP session ----
    @Test
    fun hardcoreBlocksBannedFeaturesAndMemoryMapsOrReportsNone() {
        assertTrue(LibretroBridge.available)
        val playable = TestGames.ensureBattlegrounds3(ctx)
        assertTrue("battlegrounds-3 unavailable", playable != null)
        val core = File(ctx.applicationInfo.nativeLibraryDir, "ppsspp_libretro_android.so")

        val intent = Intent(ctx, RenderTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as RenderTestActivity
        var doneRef: CountDownLatch? = null
        val stateFile = File(ctx.filesDir, "ra-test.state")
        try {
            assertTrue(activity.surfaceReady.await(8, TimeUnit.SECONDS))
            val systemDir = File(ctx.filesDir, "system").apply { mkdirs() }
            val saveDir = File(ctx.filesDir, "saves-core").apply { mkdirs() }
            CoreAssets.ensureExtracted(ctx, systemDir)
            com.retrovault.settings.SettingsResolver(ctx).applyToCore(null)
            LibretroBridge.nativeStartSession(core.absolutePath, playable!!.absolutePath, systemDir.absolutePath, saveDir.absolutePath)
            val done = CountDownLatch(1); val ok = AtomicBoolean(false)
            Thread({ ok.set(LibretroBridge.nativeRunLoop()); done.countDown() }, "RaLoop").start()
            doneRef = done
            waitFrames(120, 60_000)

            // --- (A) RA memory mapping: valid 32 MB PSP RAM, else a graceful "no memory" report ---
            val packed = RaBridge.nativeRaMemInit()
            val valid = (packed ushr 32).toInt()
            val total = packed and 0xFFFFFFFFL
            if (valid == 1) {
                assertEquals("PSP main RAM should be 32 MB", 0x02000000L, total)
                // Reads land in mapped RAM: a spread of addresses across the 32 MB returns real
                // bytes (0..255), not the -1 "unmapped" sentinel.
                val stride = 0x20000
                val a0 = (0 until 0x02000000 step stride).map { RaBridge.nativeRaMemPeek(it) }
                assertTrue("no RA memory reads succeeded (all unmapped)", a0.any { it in 0..255 })
                // Liveness is best-effort — a menu's RAM may be quiescent in the sampled cells, so
                // this is a logged metric, not a hard assertion.
                waitFrames(LibretroBridge.nativeFramesPresented() + 60, 10_000)
                val a1 = (0 until 0x02000000 step stride).map { RaBridge.nativeRaMemPeek(it) }
                android.util.Log.i("RaTest", "RA memory live-change observed: ${a0 != a1}")
            } // else: this PPSSPP build exposes no SYSTEM_RAM here; RA would report disabled (allowed).

            // --- (B) a state saved in softcore ---
            assertTrue("softcore save failed", LibretroBridge.nativeSaveState(stateFile.absolutePath, null))
            waitFrames(LibretroBridge.nativeFramesPresented() + 20, 10_000)

            // --- (C) enable hardcore -> banned features refused, allowed features permitted ---
            LibretroBridge.nativeSetHardcore(true)
            assertTrue(LibretroBridge.nativeIsHardcore())

            // state LOAD is blocked...
            assertFalse("hardcore must block state-load", LibretroBridge.nativeLoadState(stateFile.absolutePath))
            // ...but creating a state is allowed...
            assertTrue("hardcore must allow creating a state", LibretroBridge.nativeSaveState(stateFile.absolutePath, null))
            // ...slow-mo is refused, fast-forward is allowed...
            LibretroBridge.nativeSetSpeed(50)
            assertEquals("hardcore must refuse slow-mo", 100, LibretroBridge.nativeGetSpeed())
            LibretroBridge.nativeSetSpeed(300)
            assertEquals("hardcore must allow fast-forward", 300, LibretroBridge.nativeGetSpeed())
            LibretroBridge.nativeSetSpeed(100)

            // --- (D) cheats are cleared when hardcore turns on (mutually exclusive) ---
            LibretroBridge.nativeSetHardcore(false)
            LibretroBridge.nativeApplyCheats(arrayOf("_L 0x2089F4C4 0x0098967F"))
            LibretroBridge.nativeSetHardcore(true)
            assertEquals("hardcore must clear the active cheat set", 0, LibretroBridge.nativeActiveCheatCount())

            // --- (E) back to softcore: the same state now loads ---
            LibretroBridge.nativeSetHardcore(false)
            waitFrames(LibretroBridge.nativeFramesPresented() + 20, 10_000)
            assertTrue("softcore must allow state-load", LibretroBridge.nativeLoadState(stateFile.absolutePath))
            waitFrames(LibretroBridge.nativeFramesPresented() + 20, 20_000)

            // --- (F) an RA session begins + tears down cleanly (no login: plumbing/teardown only) ---
            RaBridge.nativeRaBeginSession(false)
            waitFrames(LibretroBridge.nativeFramesPresented() + 30, 10_000)  // let the run loop create the client
            RaBridge.nativeRaEndSession()
            waitFrames(LibretroBridge.nativeFramesPresented() + 30, 10_000)
            assertEquals("no HTTP left in flight after teardown", 0, RaBridge.nativeRaInflightCount())

            LibretroBridge.nativeRequestStop()
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertTrue("game failed to load", ok.get())
        } finally {
            LibretroBridge.nativeRequestStop()
            doneRef?.await(15, TimeUnit.SECONDS)
            stateFile.delete()
            activity.finish()
            instrumentation.waitForIdleSync()
        }
    }

    private fun waitFrames(target: Long, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && LibretroBridge.nativeFramesPresented() < target) {
            Thread.sleep(100)
        }
        assertTrue("timed out waiting for $target frames", LibretroBridge.nativeFramesPresented() >= target)
    }
}
