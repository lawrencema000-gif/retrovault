package com.retrovault.download

import android.content.Context
import com.retrovault.core.model.GameSystem
import java.io.File

/**
 * App-scoped storage (no runtime permission, removed on uninstall) for downloaded games,
 * user-imported ROMs, and user-supplied BIOS. Nothing here is ever uploaded.
 */
object RomStorage {

    private fun root(context: Context): File =
        context.getExternalFilesDir(null) ?: context.filesDir

    fun systemDir(context: Context, system: GameSystem): File =
        File(root(context), "roms/${system.name.lowercase()}").apply { mkdirs() }

    fun biosDir(context: Context, system: GameSystem): File =
        File(root(context), "bios/${system.name.lowercase()}").apply { mkdirs() }

    fun importsDir(context: Context): File =
        File(root(context), "imports").apply { mkdirs() }

    fun gameFile(context: Context, system: GameSystem, fileName: String): File =
        File(systemDir(context, system), fileName)
}
