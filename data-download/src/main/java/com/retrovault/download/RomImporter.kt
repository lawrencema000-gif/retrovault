package com.retrovault.download

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.retrovault.core.model.GameSystem
import java.io.File

/**
 * Copies user-picked game/BIOS files (a `content://` Uri from the Storage Access Framework) into
 * app-scoped storage. This is the legal path for commercial titles and console BIOS: the user
 * supplies files from their own device; the app never downloads or hosts them.
 */
object RomImporter {

    /** Import a user's own game/ROM into the shared imports folder. */
    fun import(context: Context, uri: Uri): File? {
        val name = queryName(context, uri) ?: "imported-${System.currentTimeMillis()}"
        return copyTo(context, uri, File(RomStorage.importsDir(context), name))
    }

    /** Import a user's own console BIOS (PS1/PS2). Stored locally only, never uploaded. */
    fun importBios(context: Context, system: GameSystem, uri: Uri): File? {
        val name = queryName(context, uri) ?: "bios.bin"
        return copyTo(context, uri, File(RomStorage.biosDir(context, system), name))
    }

    private fun copyTo(context: Context, uri: Uri, dest: File): File? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest
    } catch (e: Exception) {
        null
    }

    private fun queryName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
}
