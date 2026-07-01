package com.retrovault.saves

import android.content.Context
import java.io.File

/**
 * Local-first save-state + memory-card storage. Save-states live under app storage per game/slot;
 * memory cards (durable, portable) under a per-system file. Cloud sync is layered on top by
 * [SaveSyncClient] in the functional pass.
 */
class SaveStore(private val context: Context) {

    private fun root(): File = context.getExternalFilesDir(null) ?: context.filesDir

    private fun gameSavesDir(gameId: String): File =
        File(root(), "saves/$gameId").apply { mkdirs() }

    fun statePath(gameId: String, slot: Int): File =
        File(gameSavesDir(gameId), "slot$slot.state")

    fun screenshotPath(gameId: String, slot: Int): File =
        File(gameSavesDir(gameId), "slot$slot.png")

    fun memoryCardPath(systemName: String): File =
        File(root(), "memcards/${systemName.lowercase()}.mcd").apply { parentFile?.mkdirs() }

    /** List slots 0..count-1, marking empty ones. Slot 0 is the auto-save. */
    fun listSlots(gameId: String, count: Int = 6): List<SaveSlot> = (0 until count).map { i ->
        val f = statePath(gameId, i)
        val exists = f.exists()
        SaveSlot(
            slot = i,
            label = if (i == 0) "Auto Save" else "Slot $i",
            sizeBytes = if (exists) f.length() else 0L,
            updatedAtEpochMs = if (exists) f.lastModified() else 0L,
            coreVersion = null,
            isAuto = i == 0,
            isEmpty = !exists,
            localPath = if (exists) f.absolutePath else null,
            screenshotPath = screenshotPath(gameId, i).takeIf { it.exists() }?.absolutePath,
        )
    }
}
