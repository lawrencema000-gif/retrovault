package com.retrovault.app

import android.content.Context
import com.retrovault.core.model.GameSystem
import com.retrovault.data.SupabaseCatalogRepository
import com.retrovault.download.GameInstaller
import com.retrovault.download.RomStorage
import com.retrovault.download.SignedUrlClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Shared provisioning for instrumentation tests that need a real installed PSP game.
 *
 * Tests run in alphabetical class order, so a test that depends on Battlegrounds 3 cannot rely on
 * FirstLightTest having installed it first (CheatTest sorts earlier, and app data starts clean on
 * a fresh device). Any such test provisions it itself through this helper, which returns an
 * already-installed copy when present and otherwise runs the same production pipeline FirstLightTest
 * exercises (live catalog → signed URL → download → install). Idempotent and order-independent.
 */
object TestGames {

    const val BATTLEGROUNDS_3 = "battlegrounds-3"

    /** Ensure BG3 is installed; returns the playable file, or null if the catalog/download failed. */
    fun ensureBattlegrounds3(ctx: Context): java.io.File? {
        GameInstaller.installedPlayable(ctx, GameSystem.PSP, BATTLEGROUNDS_3)?.let { return it }
        return runCatching {
            val game = runBlocking { SupabaseCatalogRepository.fetchGames() }
                .firstOrNull { it.slug == BATTLEGROUNDS_3 && it.downloadable } ?: return null
            val signed = SignedUrlClient.request(game.id)
            val artifact = RomStorage.gameFile(ctx, GameSystem.PSP, signed.fileName ?: "bg3.zip")
            OkHttpClient().newCall(Request.Builder().url(signed.url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body!!.byteStream().use { input -> artifact.outputStream().use { input.copyTo(it) } }
            }
            GameInstaller.install(ctx, GameSystem.PSP, game.slug, artifact)
        }.getOrNull()
    }
}
