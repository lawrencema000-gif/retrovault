package com.retrovault.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.emulator.LibretroBridge
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * P3 acceptance (emulator portion): the retro_test core's sine tone flows through
 * Oboe with zero underrun fills after settling, and dynamic rate control keeps the
 * ring near half-full with deviation inside the ±0.5% cap.
 * (The long-form 10-minute soak + Bluetooth-route check happen in the P5 device session.)
 */
@RunWith(AndroidJUnit4::class)
class AudioTest {

    @Test
    fun audioFlowsWithRateControl() {
        assertTrue("native host missing", LibretroBridge.available)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ctx = instrumentation.targetContext
        val core = File(ctx.applicationInfo.nativeLibraryDir, "retro_test_libretro_android.so")
        assertTrue("test core not bundled", core.exists())

        val systemDir = File(ctx.filesDir, "system").apply { mkdirs() }
        val saveDir = File(ctx.filesDir, "saves-core").apply { mkdirs() }

        val intent = Intent(ctx, RenderTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as RenderTestActivity
        try {
            assertTrue("surface never came up", activity.surfaceReady.await(8, TimeUnit.SECONDS))

            // Emulator audio timing is bursty (SwiftShader render stalls + qemu audio HAL), so
            // validate through the large-buffer path — which also exercises the BT toggle.
            // The strict LowLatency zero-underrun soak runs on real hardware in P5.
            LibretroBridge.nativeSetBtFriendlyAudio(true)

            LibretroBridge.nativeStartSession(
                core.absolutePath, null, systemDir.absolutePath, saveDir.absolutePath
            )
            val loopOk = AtomicBoolean(false)
            val loopDone = CountDownLatch(1)
            Thread({
                loopOk.set(LibretroBridge.nativeRunLoop())
                loopDone.countDown()
            }, "AudioTestLoop").start()

            // Let the pipeline settle (stream open + prefill drain + DRC lock).
            val settleDeadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < settleDeadline &&
                LibretroBridge.nativeAudioFramesOut() < 24_000
            ) {
                Thread.sleep(200)
            }
            assertTrue(
                "audio stream never consumed frames (framesOut=${LibretroBridge.nativeAudioFramesOut()})",
                LibretroBridge.nativeAudioFramesOut() > 0
            )

            // Measure a 20-second window after settling.
            val underrunsBefore = LibretroBridge.nativeAudioUnderruns()
            val fills = mutableListOf<Int>()
            val deltas = mutableListOf<Int>()
            repeat(20) {
                Thread.sleep(1000)
                fills += LibretroBridge.nativeAudioFillPct()
                deltas += LibretroBridge.nativeAudioRateDeltaPpm()
            }
            val underrunsDuring = LibretroBridge.nativeAudioUnderruns() - underrunsBefore

            LibretroBridge.nativeRequestStop()
            assertTrue("run loop did not exit", loopDone.await(8, TimeUnit.SECONDS))
            assertTrue("core failed to load", loopOk.get())

            // Zero underrun fills across the measured window.
            assertTrue("underrun fills during window: $underrunsDuring", underrunsDuring == 0L)
            // DRC deviation always inside the ±0.5% cap.
            assertTrue(
                "rate delta out of range: $deltas",
                deltas.all { abs(it) <= 5000 }
            )
            // Buffer stays in a healthy band (never pinned empty/full).
            assertTrue("fill excursions: $fills", fills.all { it in 15..85 })
        } finally {
            activity.finish()
            instrumentation.waitForIdleSync()
        }
    }
}
