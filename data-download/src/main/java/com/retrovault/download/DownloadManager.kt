package com.retrovault.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.retrovault.core.model.GameSystem

/** Enqueues resumable game downloads via WorkManager. */
object DownloadManager {

    fun enqueue(
        context: Context,
        gameId: String,
        slug: String,
        system: GameSystem,
        wifiOnly: Boolean = true,
    ) {
        val data = Data.Builder()
            .putString(GameDownloadWorker.KEY_GAME_ID, gameId)
            .putString(GameDownloadWorker.KEY_SLUG, slug)
            .putString(GameDownloadWorker.KEY_SYSTEM, system.name)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<GameDownloadWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("download-$gameId", ExistingWorkPolicy.KEEP, request)
    }
}
