package com.retrovault.app

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.core.model.GameSystem
import com.retrovault.data.SupabaseCatalogRepository
import com.retrovault.download.GameInstaller
import com.retrovault.download.RomStorage
import com.retrovault.download.SignedUrlClient
import com.retrovault.emulator.LibretroBridge
import com.retrovault.saves.SaveStateManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
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
 * P8 acceptance: save states on the REAL game. Boot Battlegrounds 3 in PPSSPP, serialize into
 * a slot (executed on the run-loop thread, mid-session), verify the state file + PNG thumbnail,
 * keep running, then unserialize back — and confirm the auto-save slot machinery.
 */
@RunWith(AndroidJUnit4::class)
class SaveStateTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = instrumentation.targetContext

    /** The real installed EBOOT — provisions via the live pipeline if this device lacks it. */
    private fun ensureGameInstalled(): File {
        GameInstaller.installedPlayable(ctx, GameSystem.PSP, "battlegrounds-3")?.let { return it }
        val game = runBlocking { SupabaseCatalogRepository.fetchGames() }
            .firstOrNull { it.slug == "battlegrounds-3" }
        assertNotNull("battlegrounds-3 missing from live catalog", game)
        val signed = SignedUrlClient.request(game!!.id)
        val artifact = RomStorage.gameFile(ctx, GameSystem.PSP, signed.fileName ?: "bg3.zip")
        OkHttpClient().newCall(Request.Builder().url(signed.url).build()).execute().use { resp ->
            assertTrue("download failed: HTTP ${resp.code}", resp.isSuccessful)
            resp.body!!.byteStream().use { input ->
                artifact.outputStream().use { input.copyTo(it) }
            }
        }
        val playable = GameInstaller.install(ctx, GameSystem.PSP, game.slug, artifact)
        assertNotNull("install produced no playable", playable)
        return playable!!
    }

    @Test
    fun saveAndLoadStateMidSessionWithThumbnail() {
        assertTrue("native host missing", LibretroBridge.available)
        val playable = ensureGameInstalled()

        val core = File(ctx.applicationInfo.nativeLibraryDir, "ppsspp_libretro_android.so")
        assertTrue("ppsspp core not bundled", core.exists())
        val systemDir = File(ctx.filesDir, "system").apply { mkdirs() }
        val saveDir = File(ctx.filesDir, "saves-core").apply { mkdirs() }
        com.retrovault.emulator.CoreAssets.ensureExtracted(ctx, systemDir)

        val mgr = SaveStateManager(ctx, "battlegrounds-3")
        mgr.delete(1)
        mgr.delete(SaveStateManager.AUTO_SLOT)

        // No session running: ops must refuse cleanly instead of hanging.
        assertFalse("save with no session must fail fast",
            LibretroBridge.nativeSaveState(mgr.stateFile(1).absolutePath, null))

        val intent = Intent(ctx, RenderTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as RenderTestActivity
        try {
            assertTrue("surface never came up", activity.surfaceReady.await(8, TimeUnit.SECONDS))

            LibretroBridge.nativeStartSession(
                core.absolutePath, playable.absolutePath,
                systemDir.absolutePath, saveDir.absolutePath
            )
            val loopOk = AtomicBoolean(false)
            val loopDone = CountDownLatch(1)
            Thread({
                loopOk.set(LibretroBridge.nativeRunLoop())
                loopDone.countDown()
            }, "SaveStateLoop").start()

            // Let the game genuinely boot before serializing.
            waitForFrames(120, 60_000)

            // ---- SAVE into slot 1 (mid-session, from the instrumentation thread) ----
            val saved = runBlocking { mgr.save(1) }
            assertTrue("save state failed", saved)
            assertTrue("state file empty", mgr.stateFile(1).length() > 0)
            assertTrue("slot not reported populated", mgr.isPopulated(1))

            // Thumbnail must be a real decodable PNG of the actual frame.
            val thumb = mgr.thumbFile(1)
            assertTrue("thumbnail missing", thumb.isFile && thumb.length() > 0)
            val bmp = BitmapFactory.decodeFile(thumb.absolutePath)
            assertNotNull("thumbnail not decodable", bmp)
            assertTrue("thumbnail too small: ${bmp.width}x${bmp.height}", bmp.width >= 160 && bmp.height >= 90)
            // Guard against the black-render regression: a solid-color frame PNG-compresses to
            // a few hundred bytes; a genuinely rendered game frame is many KB. Also verify the
            // pixels aren't uniform (the game is actually drawing).
            assertTrue("thumbnail suspiciously small (black frame?): ${thumb.length()} bytes",
                thumb.length() > 2000)
            assertTrue("thumbnail pixels are uniform — game rendered a blank frame",
                hasVariedPixels(bmp))
            bmp.recycle()

            // Emulation must have kept running across the save.
            val framesAfterSave = LibretroBridge.nativeFramesPresented()
            waitForFrames(framesAfterSave + 30, 20_000)

            // ---- LOAD it back mid-session ----
            val loaded = runBlocking { mgr.load(1) }
            assertTrue("load state failed", loaded)

            // Still alive and presenting after the restore.
            val framesAfterLoad = LibretroBridge.nativeFramesPresented()
            waitForFrames(framesAfterLoad + 30, 20_000)

            // ---- AUTO-SAVE slot uses the same machinery ----
            assertTrue("auto-save failed", runBlocking { mgr.save(SaveStateManager.AUTO_SLOT) })
            val slots = mgr.slots()
            assertTrue("expected auto + slot1 populated, got $slots", slots.size >= 2)
            assertTrue("auto slot must list first", slots.first().slot == SaveStateManager.AUTO_SLOT)

            LibretroBridge.nativeRequestStop()
            assertTrue("run loop did not exit", loopDone.await(10, TimeUnit.SECONDS))
            assertTrue("core failed", loopOk.get())
        } finally {
            activity.finish()
            instrumentation.waitForIdleSync()
        }
    }

    private fun waitForFrames(target: Long, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (LibretroBridge.nativeFramesPresented() >= target) return
            Thread.sleep(250)
        }
        assertTrue(
            "timed out waiting for $target frames (at ${LibretroBridge.nativeFramesPresented()})",
            LibretroBridge.nativeFramesPresented() >= target
        )
    }

    private fun waitForFrames(target: Int, timeoutMs: Long) = waitForFrames(target.toLong(), timeoutMs)

    /** True if the bitmap has meaningfully varied pixels (not a solid/near-solid color). */
    private fun hasVariedPixels(bmp: android.graphics.Bitmap): Boolean {
        val stepX = (bmp.width / 16).coerceAtLeast(1)
        val stepY = (bmp.height / 16).coerceAtLeast(1)
        var first: Int? = null
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val p = bmp.getPixel(x, y)
                if (first == null) first = p
                else if (p != first) return true
                x += stepX
            }
            y += stepY
        }
        return false
    }
}
