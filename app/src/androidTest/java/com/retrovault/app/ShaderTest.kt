package com.retrovault.app

import android.content.Intent
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.emulator.LibretroBridge
import com.retrovault.saves.Screenshots
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * P19 acceptance: the host present pass ships stackable post-shaders (CRT + FSR) with adjustable
 * uniforms, an internal rotation for vertical-shmup setups, and screenshots that land in the
 * gallery. Runs against the `testgl` hw-render core so there is a real GLES3 context + frame.
 *
 * Validates, on the emulator:
 *  1. every built-in present program (base + all 3 post shaders) compiles + links (self-test);
 *  2. a CRT+FSR stack with a 90° rotation + integer scale keeps presenting (no crash/deadlock in
 *     the resolve → ping-pong → rotated final blit path) with the uniforms live;
 *  3. an in-game screenshot is written AND published to MediaStore (queryable gallery entry).
 */
@RunWith(AndroidJUnit4::class)
class ShaderTest {

    @Test
    fun shadersCompileStackRotatesAndScreenshotReachesGallery() {
        assertTrue("native host missing", LibretroBridge.available)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ctx = instrumentation.targetContext
        val core = File(ctx.applicationInfo.nativeLibraryDir, "testgl_libretro_android.so")
        assertTrue("hw-render test core not bundled: ${core.absolutePath}", core.exists())

        val systemDir = File(ctx.filesDir, "system").apply { mkdirs() }
        val saveDir = File(ctx.filesDir, "saves-core").apply { mkdirs() }

        val intent = Intent(ctx, RenderTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as RenderTestActivity
        val loopDone = CountDownLatch(1)
        val loopOk = AtomicBoolean(false)
        try {
            assertTrue("surface never came up", activity.surfaceReady.await(8, TimeUnit.SECONDS))

            LibretroBridge.nativeStartSession(
                core.absolutePath, null, systemDir.absolutePath, saveDir.absolutePath
            )
            Thread({
                loopOk.set(LibretroBridge.nativeRunLoop())
                loopDone.countDown()
            }, "ShaderTestLoop").start()

            // Wait until the core is rendering real hw frames before touching the present pass.
            assertTrue("core never presented frames", awaitFrames(120, 15_000))

            // (1) Every present program must compile + link: bit0 base, bits1..3 post shaders.
            val mask = LibretroBridge.nativeShaderSelfTest()
            assertEquals("all present programs must compile+link (bitmask)", 0b1111, mask)

            // (2) CRT + FSR stack (pass1 = FSR sharpen id 2, pass2 = CRT scanlines id 1), rotated
            // 90° with integer scaling and live uniforms. The present path must keep advancing.
            LibretroBridge.nativeSetDisplayConfig(
                /* rotationDeg = */ 90, /* scaleMode = integer */ 2,
                /* pass1 = */ 2, /* pass2 = */ 1,
                /* scanlineIntensity = */ 0.35f, /* maskIntensity = */ 0.1f, /* sharpenAmount = */ 0.6f,
            )
            val before = LibretroBridge.nativeFramesPresented()
            assertTrue(
                "stacked CRT+FSR present stalled under rotation",
                awaitFrames(before + 90, 12_000),
            )

            // Adjust the uniforms live (sharpen off, heavy scanlines) — still presenting.
            LibretroBridge.nativeSetDisplayConfig(0, 0, 1, 0, 0.9f, 0.15f, 0.0f)
            val before2 = LibretroBridge.nativeFramesPresented()
            assertTrue("uniform re-apply stalled present", awaitFrames(before2 + 30, 8_000))

            // (3) Screenshot → private PNG + MediaStore gallery entry.
            val shot = runBlocking { Screenshots.capture(ctx, "pulsar-shadertest") }
            assertNotNull("screenshot capture returned null", shot)
            assertTrue("screenshot file empty", shot!!.file.length() > 0)
            val uri = shot.galleryUri
            assertNotNull("screenshot was not published to the gallery", uri)

            ctx.contentResolver.query(
                uri!!, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null
            ).use { c ->
                assertTrue("gallery entry not found in MediaStore", c != null && c.moveToFirst())
            }
            // Clean up the gallery entry we inserted so repeated runs don't accumulate.
            runCatching { ctx.contentResolver.delete(uri, null, null) }
        } finally {
            LibretroBridge.nativeRequestStop()
            loopDone.await(8, TimeUnit.SECONDS)
            activity.finish()
            instrumentation.waitForIdleSync()
        }
        assertTrue("core/game failed to load (testgl)", loopOk.get())
    }

    private fun awaitFrames(target: Long, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (LibretroBridge.nativeFramesPresented() >= target) return true
            Thread.sleep(150)
        }
        return false
    }
}
