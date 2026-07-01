package com.retrovault.saves

/**
 * A save-state slot for a game. Slot 0 is the rolling auto-save.
 *
 * Save-states are a full core snapshot and are FRAGILE across core versions — [coreVersion] tags
 * each so restore can warn on mismatch. In-game/memory-card saves are the durable, portable path.
 */
data class SaveSlot(
    val slot: Int,
    val label: String,
    val sizeBytes: Long,
    val updatedAtEpochMs: Long,
    val coreVersion: String?,
    val isAuto: Boolean,
    val isEmpty: Boolean,
    val localPath: String?,
    val screenshotPath: String?,
)
