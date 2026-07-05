package com.retrovault.saves

import android.content.Context
import com.retrovault.emulator.LibretroBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Operational save-state layer for one game, on top of [SaveStore] paths: serialize/restore
 * the running session into numbered slots (slot 0 = rolling auto-save) with PNG thumbnails.
 *
 * Serialization itself executes on the emulator run-loop thread (the only thread where
 * retro_serialize is safe — see [LibretroBridge.nativeSaveState]); this class owns slot
 * paths, thumbnail conversion from the native raw-frame dump, and slot listing.
 */
class SaveStateManager(
    context: Context,
    private val gameKey: String,
) {
    private val store = SaveStore(context)

    fun stateFile(slot: Int): File = store.statePath(gameKey, slot)
    fun thumbFile(slot: Int): File = store.screenshotPath(gameKey, slot)

    fun isPopulated(slot: Int): Boolean = stateFile(slot).let { it.isFile && it.length() > 0 }

    /**
     * Serialize the running session into [slot] and write a PNG thumbnail of the last frame.
     * An existing state in the slot is kept as the undo backup first ([undoSave] restores it).
     * Callable from any thread; the native call blocks until the run loop executes the op.
     */
    suspend fun save(slot: Int): Boolean = withContext(Dispatchers.IO) {
        val state = stateFile(slot)
        if (state.isFile && state.length() > 0) {
            state.copyTo(undoSaveFile(slot), overwrite = true)
            thumbFile(slot).takeIf { it.isFile }?.copyTo(undoSaveThumb(slot), overwrite = true)
        }
        val raw = File(state.parentFile, "slot$slot.rawfb")
        val ok = LibretroBridge.nativeSaveState(state.absolutePath, raw.absolutePath)
        if (ok && raw.isFile) {
            RawFrames.toPng(raw, thumbFile(slot), maxWidth = THUMB_WIDTH)
        }
        raw.delete()
        ok
    }

    /**
     * Restore the session from [slot]. The pre-load state is snapshotted first so a mis-tap
     * can be reverted with [undoLoad].
     */
    suspend fun load(slot: Int): Boolean = withContext(Dispatchers.IO) {
        if (!isPopulated(slot)) return@withContext false
        LibretroBridge.nativeSaveState(undoLoadFile().absolutePath, null)
        LibretroBridge.nativeLoadState(stateFile(slot).absolutePath)
    }

    /** Revert the last [save] on [slot] to the state it overwrote. */
    suspend fun undoSave(slot: Int): Boolean = withContext(Dispatchers.IO) {
        val backup = undoSaveFile(slot)
        if (!backup.isFile || backup.length() == 0L) return@withContext false
        backup.copyTo(stateFile(slot), overwrite = true)
        undoSaveThumb(slot).takeIf { it.isFile }?.copyTo(thumbFile(slot), overwrite = true)
        true
    }

    fun canUndoSave(slot: Int): Boolean = undoSaveFile(slot).let { it.isFile && it.length() > 0 }

    /** Return the session to where it was just before the last [load]. */
    suspend fun undoLoad(): Boolean = withContext(Dispatchers.IO) {
        val f = undoLoadFile()
        f.isFile && f.length() > 0 && LibretroBridge.nativeLoadState(f.absolutePath)
    }

    fun canUndoLoad(): Boolean = undoLoadFile().let { it.isFile && it.length() > 0 }

    private fun undoSaveFile(slot: Int) = File(dirOf(slot), "slot$slot.state.undo")
    private fun undoSaveThumb(slot: Int) = File(dirOf(slot), "slot$slot.png.undo")
    private fun undoLoadFile() = File(dirOf(0), "before-load.state")
    private fun dirOf(slot: Int): File = stateFile(slot).parentFile!!

    fun delete(slot: Int) {
        stateFile(slot).delete()
        thumbFile(slot).delete()
    }

    /** Populated slots only, auto-save (slot 0) first. */
    fun slots(count: Int = MAX_SLOTS + 1): List<SaveSlot> =
        store.listSlots(gameKey, count).filter { !it.isEmpty }

    companion object {
        const val AUTO_SLOT = 0
        const val MAX_SLOTS = 4
        private const val THUMB_WIDTH = 320
    }
}
