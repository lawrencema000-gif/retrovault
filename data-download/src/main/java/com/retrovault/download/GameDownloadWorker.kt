package com.retrovault.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.retrovault.core.model.GameSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream

/**
 * Downloads a legally-distributable game into app-scoped storage: resolve a signed URL, stream it
 * with resumable HTTP Range requests, verify sha256, mark installed. Enqueued by [DownloadManager].
 * The pipeline is exercised end-to-end once catalog files are hosted on R2 (Stage 5 functional pass).
 */
class GameDownloadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val gameId = inputData.getString(KEY_GAME_ID) ?: return@withContext Result.failure()
        val slug = inputData.getString(KEY_SLUG) ?: gameId
        val system = inputData.getString(KEY_SYSTEM)
            ?.let { runCatching { GameSystem.valueOf(it) }.getOrNull() }
            ?: return@withContext Result.failure()

        // Cap retries: without a cap, a dead URL or persistent network error retries FOREVER and
        // the UI can never say "failed" — the user stares at DOWNLOADING… for eternity.
        if (runAttemptCount >= MAX_ATTEMPTS) return@withContext Result.failure()

        try {
            val signed = SignedUrlClient.request(gameId)
            val fileName = signed.fileName ?: "$gameId.bin"
            val dest = RomStorage.gameFile(applicationContext, system, fileName)

            val existing = if (dest.exists()) dest.length() else 0L
            val request = Request.Builder().url(signed.url).apply {
                if (existing > 0) header("Range", "bytes=$existing-")
            }.build()

            OkHttpClient().newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.retry()
                val stream = resp.body?.byteStream() ?: return@withContext Result.retry()
                val append = existing > 0 && resp.code == 206
                stream.use { input ->
                    FileOutputStream(dest, append).use { output -> input.copyTo(output) }
                }
            }

            // Integrity gate: a hash mismatch aborts the install and deletes the bad file.
            if (signed.sha256 != null && !Sha256.matches(dest, signed.sha256)) {
                dest.delete()
                return@withContext Result.failure()
            }

            // Install: extract the verified archive and locate the playable file.
            val playable = GameInstaller.install(applicationContext, system, slug, dest)
                ?: return@withContext Result.failure()
            Result.success(
                Data.Builder().putString(OUT_PLAYABLE_PATH, playable.absolutePath).build()
            )
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_GAME_ID = "gameId"
        const val KEY_SLUG = "slug"
        const val KEY_SYSTEM = "system"
        const val OUT_PLAYABLE_PATH = "playablePath"
        const val MAX_ATTEMPTS = 4
    }
}
