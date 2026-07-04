package com.retrovault.saves

import android.content.Context
import android.graphics.Bitmap
import com.retrovault.emulator.LibretroBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
     * Callable from any thread; the native call blocks until the run loop executes the op.
     */
    suspend fun save(slot: Int): Boolean = withContext(Dispatchers.IO) {
        val raw = File(stateFile(slot).parentFile, "slot$slot.rawfb")
        val ok = LibretroBridge.nativeSaveState(stateFile(slot).absolutePath, raw.absolutePath)
        if (ok && raw.isFile) {
            rawFrameToPng(raw, thumbFile(slot))
        }
        raw.delete()
        ok
    }

    suspend fun load(slot: Int): Boolean = withContext(Dispatchers.IO) {
        isPopulated(slot) && LibretroBridge.nativeLoadState(stateFile(slot).absolutePath)
    }

    fun delete(slot: Int) {
        stateFile(slot).delete()
        thumbFile(slot).delete()
    }

    /** Populated slots only, auto-save (slot 0) first. */
    fun slots(count: Int = MAX_SLOTS + 1): List<SaveSlot> =
        store.listSlots(gameKey, count).filter { !it.isEmpty }

    /** Decode the native raw dump (int32 w, int32 h LE, RGBA top-down) into a scaled PNG. */
    private fun rawFrameToPng(raw: File, out: File): Boolean = runCatching {
        val bytes = raw.readBytes()
        if (bytes.size < 8) return@runCatching false
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val w = bb.getInt(0)
        val h = bb.getInt(4)
        if (w <= 0 || h <= 0 || bytes.size < 8 + w * h * 4) return@runCatching false

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bb.position(8)
        bmp.copyPixelsFromBuffer(bb) // ARGB_8888 buffer layout == RGBA byte order
        val scale = THUMB_WIDTH.toFloat() / w
        val thumb = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, THUMB_WIDTH, (h * scale).toInt().coerceAtLeast(1), true)
        } else bmp
        FileOutputStream(out).use { thumb.compress(Bitmap.CompressFormat.PNG, 90, it) }
        if (thumb !== bmp) thumb.recycle()
        bmp.recycle()
        true
    }.getOrDefault(false)

    companion object {
        const val AUTO_SLOT = 0
        const val MAX_SLOTS = 4
        private const val THUMB_WIDTH = 320
    }
}
