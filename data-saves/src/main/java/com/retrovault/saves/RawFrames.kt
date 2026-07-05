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

/** Decodes the native raw frame dump (int32 w, int32 h LE, RGBA top-down) into PNGs. */
object RawFrames {

    /** Convert [raw] to a PNG at [out]; downscales to [maxWidth] if wider. */
    fun toPng(raw: File, out: File, maxWidth: Int = Int.MAX_VALUE): Boolean = runCatching {
        val bytes = raw.readBytes()
        if (bytes.size < 8) return@runCatching false
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val w = bb.getInt(0)
        val h = bb.getInt(4)
        if (w <= 0 || h <= 0 || bytes.size < 8 + w * h * 4) return@runCatching false

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bb.position(8)
        bmp.copyPixelsFromBuffer(bb) // ARGB_8888 buffer layout == RGBA byte order
        val scale = maxWidth.toFloat() / w
        val outBmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, maxWidth, (h * scale).toInt().coerceAtLeast(1), true)
        } else bmp
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { outBmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        if (outBmp !== bmp) outBmp.recycle()
        bmp.recycle()
        true
    }.getOrDefault(false)
}

/** In-game screenshots: full-resolution PNGs under `filesDir/screenshots/`. */
object Screenshots {

    suspend fun capture(context: Context, nameHint: String): File? = withContext(Dispatchers.IO) {
        val raw = File(context.cacheDir, "shot.rawfb")
        try {
            if (!LibretroBridge.nativeScreenshot(raw.absolutePath)) return@withContext null
            val dir = File(context.filesDir, "screenshots").apply { mkdirs() }
            val safe = nameHint.replace(Regex("[^A-Za-z0-9._-]"), "_")
            var out = File(dir, "$safe.png")
            var n = 1
            while (out.exists()) out = File(dir, "$safe-${n++}.png")
            if (RawFrames.toPng(raw, out)) out else null
        } finally {
            raw.delete()
        }
    }
}
