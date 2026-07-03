package com.retrovault.app

import androidx.test.core.app.ActivityScenario
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

/**
 * P2 acceptance: libretro test cores render through the full native video path —
 * EGL/GLES3 context, run loop, present pacing — with frames advancing at a sane rate.
 *
 * `retro_test` exercises the software-framebuffer blit path; `testgl` exercises
 * SET_HW_RENDER (FBO + context_reset + hw present).
 */
@RunWith(AndroidJUnit4::class)
class RenderTest {

    private fun runCore(coreFile: String, minFrames: Long = 120): Pair<Long, Long> {
        assertTrue("native host missing", LibretroBridge.available)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val core = File(ctx.applicationInfo.nativeLibraryDir, coreFile)
        assertTrue("test core not bundled: ${core.absolutePath}", core.exists())

        val systemDir = File(ctx.filesDir, "system").apply { mkdirs() }
        val saveDir = File(ctx.filesDir, "saves-core").apply { mkdirs() }

        val scenario = ActivityScenario.launch(RenderTestActivity::class.java)
        try {
            var surfaceLatch: CountDownLatch? = null
            scenario.onActivity { surfaceLatch = it.surfaceReady }
            assertTrue("surface never came up", surfaceLatch!!.await(8, TimeUnit.SECONDS))

            LibretroBridge.nativeStartSession(
                core.absolutePath, null, systemDir.absolutePath, saveDir.absolutePath
            )
            val loopOk = AtomicBoolean(false)
            val loopDone = CountDownLatch(1)
            Thread({
                loopOk.set(LibretroBridge.nativeRunLoop())
                loopDone.countDown()
            }, "RenderTestLoop").start()

            // Poll until enough frames have been presented (or time out).
            val deadline = System.currentTimeMillis() + 15_000
            var frames = 0L
            while (System.currentTimeMillis() < deadline) {
                frames = LibretroBridge.nativeFramesPresented()
                if (frames >= minFrames) break
                Thread.sleep(200)
            }
            val avgIntervalUs = LibretroBridge.nativeAvgFrameIntervalUs()

            LibretroBridge.nativeRequestStop()
            assertTrue("run loop did not exit", loopDone.await(8, TimeUnit.SECONDS))
            assertTrue("core/game failed to load ($coreFile)", loopOk.get())
            assertTrue("only $frames frames presented ($coreFile)", frames >= minFrames)
            return frames to avgIntervalUs
        } finally {
            scenario.close()
        }
    }

    @Test
    fun softwareCoreRendersPaced() {
        val (frames, avgUs) = runCore("retro_test_libretro_android.so")
        // ~60fps ≈ 16.7ms/frame; generous bounds for the software-rendered emulator image.
        assertTrue("suspicious frame interval: $avgUs us after $frames frames", avgUs in 8_000..45_000)
    }

    @Test
    fun glHwRenderCoreRenders() {
        runCore("testgl_libretro_android.so")
    }
}
