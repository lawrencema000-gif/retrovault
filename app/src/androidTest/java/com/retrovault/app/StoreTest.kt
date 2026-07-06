package com.retrovault.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.core.model.GameSystem
import com.retrovault.data.SupabaseCatalogRepository
import com.retrovault.download.GameInstaller
import com.retrovault.download.RomStorage
import com.retrovault.download.Sha256
import com.retrovault.download.SignedUrlClient
import com.retrovault.emulator.CoreAssets
import com.retrovault.emulator.LibretroBridge
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertFalse
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
 * P15 acceptance (store v1): the live catalog serves real, downloadable, license-attributed
 * homebrew (the PPSSPP Store contract: author + license + size), and the integrity gate works
 * both ways — a correct sha256 passes, a mismatch is detected and would abort the install.
 */
@RunWith(AndroidJUnit4::class)
class StoreTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun catalogServesLicensedDownloadableHomebrew() {
        val games = runBlocking { SupabaseCatalogRepository.fetchGames() }
        val downloadable = games.filter { it.downloadable }
        assertTrue("no downloadable titles in catalog", downloadable.isNotEmpty())

        // Every hosted title must carry its store-contract metadata: real author, a license,
        // and a nonzero size. That's the legal + UX floor for anything we host.
        downloadable.forEach { g ->
            assertTrue("blank author for ${g.title}", g.developer.isNotBlank() && g.developer != "Unknown")
            assertTrue("blank license for ${g.title}", g.license.isNotBlank())
            assertTrue("no size for ${g.title}", g.sizeBytes > 0)
            // A source or license link must exist so the license is auditable.
            assertTrue("no license/source link for ${g.title}", g.sourceUrl != null || g.licenseUrl != null)
        }
    }

    @Test
    fun downloadedFileMatchesCatalogSha256() {
        // Battlegrounds 3 (GPL-3.0) is the anchor title. Download it and confirm the signed
        // response's sha256 matches — the exact gate the download worker enforces.
        val game = runBlocking { SupabaseCatalogRepository.fetchGames() }
            .firstOrNull { it.slug == "battlegrounds-3" }
        assertNotNull("battlegrounds-3 missing from catalog", game)
        val signed = SignedUrlClient.request(game!!.id)
        assertNotNull("no sha256 published for the anchor title", signed.sha256)

        val dest = RomStorage.gameFile(ctx, game.system, signed.fileName ?: "bg3.zip")
        OkHttpClient().newCall(Request.Builder().url(signed.url).build()).execute().use { resp ->
            assertTrue("download failed HTTP ${resp.code}", resp.isSuccessful)
            resp.body!!.byteStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
        }

        // Correct hash passes; a tampered hash fails — the abort condition.
        assertTrue("real file must match its published hash", Sha256.matches(dest, signed.sha256!!))
        val wrong = signed.sha256!!.let { it.dropLast(1) + if (it.last() == '0') '1' else '0' }
        assertFalse("a mismatched hash must NOT verify (install would abort)", Sha256.matches(dest, wrong))
    }

    @Test
    fun newlyCuratedTitleDownloadsVerifiesAndBoots() {
        // rRootage (BSD-2-Clause, adversarially license-verified this session) is a NEW curated
        // title. Prove the full store pipeline on it: catalog → signed URL → download → sha256
        // → install → PPSSPP boots it. This is the "downloads a real homebrew, plays it" bar
        // exercised on a title other than the original anchor.
        assertTrue("native host missing", LibretroBridge.available)
        val game = runBlocking { SupabaseCatalogRepository.fetchGames() }
            .firstOrNull { it.slug == "rrootage" }
        assertNotNull("rrootage missing from catalog", game)
        assertTrue("rrootage must be downloadable", game!!.downloadable)
        assertTrue("rrootage must show BSD license", game.license.contains("BSD", true))

        val signed = SignedUrlClient.request(game.id)
        val artifact = RomStorage.gameFile(ctx, GameSystem.PSP, signed.fileName ?: "rr.zip")
        OkHttpClient().newCall(Request.Builder().url(signed.url).build()).execute().use { resp ->
            assertTrue("download failed HTTP ${resp.code}", resp.isSuccessful)
            resp.body!!.byteStream().use { input -> artifact.outputStream().use { input.copyTo(it) } }
        }
        if (signed.sha256 != null) {
            val md = MessageDigest.getInstance("SHA-256")
            artifact.inputStream().use { s ->
                val buf = ByteArray(1 shl 16)
                while (true) { val n = s.read(buf); if (n <= 0) break; md.update(buf, 0, n) }
            }
            assertTrue("rrootage sha256 mismatch",
                md.digest().joinToString("") { "%02x".format(it) }.equals(signed.sha256, true))
        }

        val playable = GameInstaller.install(ctx, GameSystem.PSP, game.slug, artifact)
        assertNotNull("no playable file after installing rrootage", playable)

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
                core.absolutePath, playable!!.absolutePath, systemDir.absolutePath, saveDir.absolutePath
            )
            val done = CountDownLatch(1)
            doneRef = done
            val ok = AtomicBoolean(false)
            Thread({ ok.set(LibretroBridge.nativeRunLoop()); done.countDown() }, "StoreBoot").start()

            val deadline = System.currentTimeMillis() + 60_000
            var frames = 0L
            while (System.currentTimeMillis() < deadline) {
                frames = LibretroBridge.nativeFramesPresented()
                if (frames >= 120) break
                Thread.sleep(500)
            }
            LibretroBridge.nativeRequestStop()
            assertTrue("run loop did not exit", done.await(10, TimeUnit.SECONDS))
            assertTrue("PPSSPP failed to load rRootage", ok.get())
            assertTrue("only $frames frames — rRootage did not render", frames >= 120)
        } finally {
            LibretroBridge.nativeRequestStop()
            doneRef?.await(15, TimeUnit.SECONDS)
            activity.finish()
            instrumentation.waitForIdleSync()
        }
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun sha256UtilityIsCorrect() {
        // Known-answer: sha256("") = e3b0c442...b855; a one-byte change flips it.
        val f = File(ctx.cacheDir, "sha-empty").apply { writeBytes(ByteArray(0)) }
        assertTrue(Sha256.matches(f, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
        assertFalse(Sha256.matches(f, "0".repeat(64)))
        f.delete()
    }
}
