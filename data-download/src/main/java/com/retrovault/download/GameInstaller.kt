package com.retrovault.download

import android.content.Context
import com.retrovault.core.model.GameSystem
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Install pipeline for downloaded store games (PPSSPP GameManager model): verified archives
 * are extracted into an app-private per-game directory, then the playable file is located.
 */
object GameInstaller {

    /** Playable extensions per system, in preference order. */
    private val playableExtensions = mapOf(
        GameSystem.PSP to listOf("pbp", "iso", "cso", "chd", "elf", "prx"),
        GameSystem.PS1 to listOf("chd", "cue", "pbp", "iso", "bin"),
        GameSystem.PS2 to listOf("chd", "iso", "elf"),
    )

    fun installDir(context: Context, system: GameSystem, slug: String): File =
        File(RomStorage.systemDir(context, system), slug)

    /**
     * Extract a downloaded .zip into the game's install dir (zip-slip safe), delete the
     * archive, and return the playable file. Non-zip payloads are moved in as-is.
     */
    fun install(context: Context, system: GameSystem, slug: String, artifact: File): File? {
        val dir = installDir(context, system, slug).apply { mkdirs() }

        if (artifact.extension.equals("zip", ignoreCase = true)) {
            ZipInputStream(artifact.inputStream().buffered()).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    val dest = File(dir, entry.name)
                    // zip-slip guard
                    if (!dest.canonicalPath.startsWith(dir.canonicalPath + File.separator) &&
                        dest.canonicalPath != dir.canonicalPath
                    ) {
                        zin.closeEntry()
                        entry = zin.nextEntry
                        continue
                    }
                    if (entry.isDirectory) {
                        dest.mkdirs()
                    } else {
                        dest.parentFile?.mkdirs()
                        dest.outputStream().use { out -> zin.copyTo(out) }
                    }
                    zin.closeEntry()
                    entry = zin.nextEntry
                }
            }
            artifact.delete()
        } else {
            val moved = File(dir, artifact.name)
            if (artifact.absolutePath != moved.absolutePath) {
                artifact.copyTo(moved, overwrite = true)
                artifact.delete()
            }
        }
        return installedPlayable(context, system, slug)
    }

    /** The playable file for an installed game, or null when not installed. */
    fun installedPlayable(context: Context, system: GameSystem, slug: String): File? {
        val dir = installDir(context, system, slug)
        if (!dir.isDirectory) return null
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) return null

        // EBOOT.PBP is the canonical PSP homebrew entry point.
        files.firstOrNull { it.name.equals("EBOOT.PBP", ignoreCase = true) }?.let { return it }

        val prefs = playableExtensions[system].orEmpty()
        for (ext in prefs) {
            files.firstOrNull { it.extension.equals(ext, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    fun isInstalled(context: Context, system: GameSystem, slug: String): Boolean =
        installedPlayable(context, system, slug) != null
}
