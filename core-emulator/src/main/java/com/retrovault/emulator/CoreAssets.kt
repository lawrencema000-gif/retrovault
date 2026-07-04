package com.retrovault.emulator

import android.content.Context
import java.io.File

/**
 * Extracts bundled core support files (everything under APK asset dir `coresystem`) into the
 * libretro system directory before a session starts. Currently: PPSSPP's
 * `PPSSPP/ppge_atlas.zim` — the HLE OSD/dialog font atlas. Without it, sceUtility dialogs
 * (savedata prompts many homebrew open at boot) render invisibly and the game looks like a
 * white-screen hang.
 */
object CoreAssets {

    private const val ROOT = "coresystem"

    fun ensureExtracted(context: Context, systemDir: File) {
        runCatching { extractDir(context, ROOT, systemDir) }
    }

    private fun extractDir(context: Context, assetPath: String, target: File) {
        val names = context.assets.list(assetPath) ?: return
        for (name in names) {
            val childAsset = "$assetPath/$name"
            val grandchildren = context.assets.list(childAsset)
            if (grandchildren.isNullOrEmpty()) {
                val out = File(target, childAsset.removePrefix("$ROOT/"))
                if (!out.isFile || out.length() == 0L) {
                    out.parentFile?.mkdirs()
                    context.assets.open(childAsset).use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                }
            } else {
                extractDir(context, childAsset, target)
            }
        }
    }
}
