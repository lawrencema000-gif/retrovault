package com.retrovault.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.retrovault.core.model.GameSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** UI-facing download status (WorkManager stays encapsulated in this module). */
enum class DownloadStatus { NONE, DOWNLOADING, FAILED, SUCCEEDED }

/** Status + byte progress (null until the worker knows the content length, and when not RUNNING). */
data class DownloadState(val status: DownloadStatus, val progressPct: Int? = null)

/** Enqueues resumable game downloads via WorkManager. */
object DownloadManager {

    fun enqueue(
        context: Context,
        gameId: String,
        slug: String,
        system: GameSystem,
        wifiOnly: Boolean = true,
        replace: Boolean = false,
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

        WorkManager.getInstance(context).enqueueUniqueWork(
            "download-$gameId",
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Observe the download for [gameId] as a simple status the store UI can render — including
     * FAILED, which the old poll-the-filesystem approach could never surface. State lives in
     * WorkManager, so it survives leaving and re-entering the screen.
     */
    fun status(context: Context, gameId: String): Flow<DownloadState> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("download-$gameId")
            .map { infos ->
                val info = infos.lastOrNull()
                val status = when (info?.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED ->
                        DownloadStatus.DOWNLOADING
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> DownloadStatus.FAILED
                    WorkInfo.State.SUCCEEDED -> DownloadStatus.SUCCEEDED
                    null -> DownloadStatus.NONE
                }
                val pct = info?.takeIf { it.state == WorkInfo.State.RUNNING }
                    ?.progress?.getInt(GameDownloadWorker.PROGRESS_PCT, -1)
                    ?.takeIf { it >= 0 }
                DownloadState(status, pct)
            }

    /** Re-enqueue after a failure (REPLACE so the failed chain doesn't block the retry). */
    fun retry(context: Context, gameId: String, slug: String, system: GameSystem, wifiOnly: Boolean) {
        enqueue(context, gameId, slug, system, wifiOnly, replace = true)
    }
}
