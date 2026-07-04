package com.retrovault.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.core.model.GameSystem
import com.retrovault.data.SupabaseCatalogRepository
import com.retrovault.download.GameInstaller
import com.retrovault.download.RomStorage
import com.retrovault.download.SignedUrlClient
import com.retrovault.emulator.LibretroBridge
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FIRST LIGHT (emulator edition): the real, license-verified GPL homebrew
 * ("Battlegrounds 3") flows through the ENTIRE production pipeline —
 * live catalog → signed-URL edge function → download → sha256 → unzip install →
 * PPSSPP libretro core boots it and presents frames.
 */
@RunWith(AndroidJUnit4::class)
class FirstLightTest {

    @Test
    fun realHomebrewDownloadsInstallsAndBoots() {
        assertTrue("native host missing", LibretroBridge.available)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ctx = instrumentation.targetContext

        // 1. Live catalog: find the real title.
        val games = runBlocking { SupabaseCatalogRepository.fetchGames() }
        val game = games.firstOrNull { it.slug == "battlegrounds-3" }
        assertNotNull("battlegrounds-3 missing from live catalog", game)
        assertTrue("expected downloadable", game!!.downloadable)
        assertEquals(GameSystem.PSP, game.system)

        // 2. Signed URL from the edge function; download; verify sha256.
        val signed = SignedUrlClient.request(game.id)
        val artifact = RomStorage.gameFile(ctx, GameSystem.PSP, signed.fileName ?: "bg3.zip")
        OkHttpClient().newCall(Request.Builder().url(signed.url).build()).execute().use { resp ->
            assertTrue("download failed: HTTP ${resp.code}", resp.isSuccessful)
            resp.body!!.byteStream().use { input ->
                artifact.outputStream().use { input.copyTo(it) }
            }
        }
        if (signed.sha256 != null) {
            val md = MessageDigest.getInstance("SHA-256")
            artifact.inputStream().use { s ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = s.read(buf); if (n <= 0) break; md.update(buf, 0, n)
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            assertEquals("sha256 mismatch", signed.sha256!!.lowercase(), hex)
        }

        // 3. Install (unzip + locate playable).
        val playable = GameInstaller.install(ctx, GameSystem.PSP, game.slug, artifact)
        assertNotNull("no playable file found after install", playable)

        // 4. Boot it in the PPSSPP core and require real frames.
        val core = File(ctx.applicationInfo.nativeLibraryDir, "ppsspp_libretro_android.so")
        assertTrue("ppsspp core not bundled", core.exists())
        val systemDir = File(ctx.filesDir, "system").apply { mkdirs() }
        val saveDir = File(ctx.filesDir, "saves-core").apply { mkdirs() }
        com.retrovault.emulator.CoreAssets.ensureExtracted(ctx, systemDir)

        val intent = Intent(ctx, RenderTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as RenderTestActivity
        try {
            assertTrue("surface never came up", activity.surfaceReady.await(8, TimeUnit.SECONDS))

            LibretroBridge.nativeStartSession(
                core.absolutePath, playable!!.absolutePath,
                systemDir.absolutePath, saveDir.absolutePath
            )
            val loopOk = AtomicBoolean(false)
            val loopDone = CountDownLatch(1)
            Thread({
                loopOk.set(LibretroBridge.nativeRunLoop())
                loopDone.countDown()
            }, "FirstLightLoop").start()

            // PSP boot + JIT warmup on the emulator can take a while — be patient.
            val deadline = System.currentTimeMillis() + 60_000
            var frames = 0L
            while (System.currentTimeMillis() < deadline) {
                frames = LibretroBridge.nativeFramesPresented()
                if (frames >= 120) break
                Thread.sleep(500)
            }

            LibretroBridge.nativeRequestStop()
            assertTrue("run loop did not exit", loopDone.await(10, TimeUnit.SECONDS))
            assertTrue("PPSSPP failed to load the game", loopOk.get())
            assertTrue("only $frames frames presented — game did not render", frames >= 120)
        } finally {
            activity.finish()
            instrumentation.waitForIdleSync()
        }
    }
}
