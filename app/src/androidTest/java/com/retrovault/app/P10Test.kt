package com.retrovault.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.core.model.GameSystem
import com.retrovault.download.GameInstaller
import com.retrovault.emulator.CoreAssets
import com.retrovault.emulator.LibretroBridge
import com.retrovault.saves.SaveStateManager
import kotlinx.coroutines.runBlocking
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
 * P10 acceptance: fast-forward genuinely speeds up the core (audio-frames-produced observable),
 * rewind snapshots ring + restore + drain, undo-save/undo-load round-trips, and the hardcore
 * interlock refuses FF and rewind. Auto-resume "Continue" UI flow is validated on device (P5);
 * its load-auto-slot mechanics are the same [SaveStateManager.load] proven here and in P8.
 */
@RunWith(AndroidJUnit4::class)
class P10Test {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = instrumentation.targetContext

    private fun startSession(corePath: String, gamePath: String?): Pair<CountDownLatch, AtomicBoolean> {
        val systemDir = File(ctx.filesDir, "system").apply { mkdirs() }
        val saveDir = File(ctx.filesDir, "saves-core").apply { mkdirs() }
        CoreAssets.ensureExtracted(ctx, systemDir)
        // Mirror production: resolver-driven core variables (IR JIT + native res on x86 AVD).
        com.retrovault.settings.SettingsResolver(ctx).applyToCore(null)
        LibretroBridge.nativeStartSession(corePath, gamePath, systemDir.absolutePath, saveDir.absolutePath)
        val done = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        Thread({ ok.set(LibretroBridge.nativeRunLoop()); done.countDown() }, "P10Loop").start()
        return done to ok
    }

    /** Teardown that cannot poison the next test: stop AND wait for the loop to exit. */
    private fun stopSessionHard(done: CountDownLatch?) {
        LibretroBridge.nativeRequestStop()
        done?.await(15, TimeUnit.SECONDS)
    }

    private fun waitFrames(target: Long, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline &&
            LibretroBridge.nativeFramesPresented() < target
        ) Thread.sleep(100)
        assertTrue(
            "timed out waiting for $target frames",
            LibretroBridge.nativeFramesPresented() >= target
        )
    }

    @Test
    fun fastForwardMultipliesCoreOutputAndHardcoreBlocksIt() {
        assertTrue(LibretroBridge.available)
        val core = File(ctx.applicationInfo.nativeLibraryDir, "retro_test_libretro_android.so")
        assertTrue("test core not bundled", core.exists())

        val intent = Intent(ctx, RenderTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as RenderTestActivity
        var doneRef: CountDownLatch? = null
        try {
            assertTrue(activity.surfaceReady.await(8, TimeUnit.SECONDS))
            val (done, ok) = startSession(core.absolutePath, null)
            doneRef = done
            waitFrames(30, 10_000)

            // Baseline production rate at 1×.
            val b0 = LibretroBridge.nativeAudioFramesProduced()
            Thread.sleep(2000)
            val baseline = LibretroBridge.nativeAudioFramesProduced() - b0
            assertTrue("no audio production at 1x", baseline > 10_000)

            // 3×: production must at least double (headroom for scheduling noise).
            LibretroBridge.nativeSetSpeed(300)
            assertEquals(300, LibretroBridge.nativeGetSpeed())
            val f0 = LibretroBridge.nativeAudioFramesProduced()
            Thread.sleep(2000)
            val ff = LibretroBridge.nativeAudioFramesProduced() - f0
            assertTrue("FF did not speed up the core: $ff vs baseline $baseline", ff > baseline * 2)

            // Back to 1×.
            LibretroBridge.nativeSetSpeed(100)

            // Hardcore: FF refused.
            LibretroBridge.nativeSetHardcore(true)
            LibretroBridge.nativeSetSpeed(300)
            assertEquals("hardcore must pin speed at 100", 100, LibretroBridge.nativeGetSpeed())
            assertTrue(LibretroBridge.nativeIsHardcore())
            LibretroBridge.nativeSetHardcore(false)

            LibretroBridge.nativeRequestStop()
            assertTrue(done.await(8, TimeUnit.SECONDS))
            assertTrue(ok.get())
        } finally {
            // Always stop AND await the loop — a failed assertion must not poison the next test.
            stopSessionHard(doneRef)
            activity.finish()
            instrumentation.waitForIdleSync()
        }
    }

    @Test
    fun rewindAndUndoOnRealGame() {
        assertTrue(LibretroBridge.available)
        val playable = GameInstaller.installedPlayable(ctx, GameSystem.PSP, "battlegrounds-3")
        assertNotNull("battlegrounds-3 not installed (FirstLightTest provisions it)", playable)
        val core = File(ctx.applicationInfo.nativeLibraryDir, "ppsspp_libretro_android.so")

        val intent = Intent(ctx, RenderTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as RenderTestActivity
        var doneRef: CountDownLatch? = null
        try {
            assertTrue(activity.surfaceReady.await(8, TimeUnit.SECONDS))
            val (done, ok) = startSession(core.absolutePath, playable!!.absolutePath)
            doneRef = done
            waitFrames(90, 60_000)

            // ---- rewind: enable, accumulate snapshots, restore, drain ----
            LibretroBridge.nativeSetRewind(256L * 1024 * 1024, 45)
            val snapDeadline = System.currentTimeMillis() + 45_000
            while (System.currentTimeMillis() < snapDeadline && LibretroBridge.nativeRewindCount() < 2) {
                Thread.sleep(250)
            }
            val count = LibretroBridge.nativeRewindCount()
            assertTrue("rewind ring never filled (count=$count)", count >= 2)

            assertTrue("rewind step failed", LibretroBridge.nativeRewindStep())
            assertEquals(count - 1, LibretroBridge.nativeRewindCount())
            // Emulation must keep presenting after a restore.
            waitFrames(LibretroBridge.nativeFramesPresented() + 20, 20_000)

            // Hardcore blocks rewind even with snapshots available.
            LibretroBridge.nativeSetHardcore(true)
            assertFalse("hardcore must refuse rewind", LibretroBridge.nativeRewindStep())
            LibretroBridge.nativeSetHardcore(false)

            // Second, paced step (real usage is one step per tap — PPSSPP's threaded GL
            // renderer needs frames between restores; rapid-fire draining wedges it).
            if (LibretroBridge.nativeRewindCount() > 0) {
                Thread.sleep(500)
                assertTrue(LibretroBridge.nativeRewindStep())
                waitFrames(LibretroBridge.nativeFramesPresented() + 20, 20_000)
            }

            // Disabling frees the ring and refuses further steps.
            LibretroBridge.nativeSetRewind(0, 0)
            val offDeadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < offDeadline && LibretroBridge.nativeRewindCount() != 0) {
                Thread.sleep(100)
            }
            assertEquals(0, LibretroBridge.nativeRewindCount())
            assertFalse("disabled rewind must refuse steps", LibretroBridge.nativeRewindStep())

            // ---- undo-save / undo-load ----
            // Hermetic: wipe ALL prior artifacts (incl. .undo backups a failed run left).
            File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "saves/p10-undo").deleteRecursively()
            val mgr = SaveStateManager(ctx, "p10-undo")
            assertTrue(runBlocking { mgr.save(2) })
            assertFalse("first save has nothing to undo", mgr.canUndoSave(2))
            val firstBytes = mgr.stateFile(2).length()

            waitFrames(LibretroBridge.nativeFramesPresented() + 20, 15_000)
            assertTrue(runBlocking { mgr.save(2) })
            assertTrue("second save must be undoable", mgr.canUndoSave(2))
            assertTrue(runBlocking { mgr.undoSave(2) })
            assertEquals("undo must restore the first state file", firstBytes, mgr.stateFile(2).length())

            assertFalse(mgr.canUndoLoad())
            assertTrue(runBlocking { mgr.load(2) })
            assertTrue("load must snapshot the pre-load state", mgr.canUndoLoad())
            assertTrue(runBlocking { mgr.undoLoad() })

            LibretroBridge.nativeSetRewind(0, 0)
            LibretroBridge.nativeRequestStop()
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertTrue(ok.get())
            mgr.delete(2)
        } finally {
            // Always stop AND await the loop — a failed assertion must not poison the next test.
            stopSessionHard(doneRef)
            activity.finish()
            instrumentation.waitForIdleSync()
        }
    }
}
