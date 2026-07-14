package com.retrovault.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.retrovault.core.model.GameMetadata
import com.retrovault.core.model.GameSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Scans SAF tree folders (and installed-game dirs) for PSP/PS1/PS2 game files, identifies each,
 * and upserts into the [LibraryIndex]. Unchanged files (same uri + lastModified) are skipped so
 * rescans are fast. Uses DocumentsContract directly (not DocumentFile) for speed on large trees.
 */
class LibraryScanner(
    private val context: Context,
    private val index: LibraryIndex,
) {
    private val psp = setOf("pbp", "iso", "cso", "chd")
    private val ps1 = setOf("chd", "cue", "pbp", "bin", "iso")
    private val ps2 = setOf("iso", "chd")

    private fun systemFor(ext: String): GameSystem? = when {
        ext == "pbp" || ext == "cso" -> GameSystem.PSP
        ext == "cue" || ext == "bin" -> GameSystem.PS1   // PSP never ships as .bin/.cue
        ext in psp || ext in ps1 || ext in ps2 -> GameSystem.PSP // .iso refined by content below
        else -> null
    }

    /**
     * Identify with content-based system refinement (P23): a `.iso` is tried as PSP first, and
     * when that yields no real DISC_ID the PS1 filesystem (SYSTEM.CNF) is probed — so a PS1 iso
     * dropped in a PSP scan doesn't get misfiled as a fake-id PSP game (and vice versa).
     */
    private fun identifyRefined(file: File, hint: GameSystem): GameMetadata? {
        val ext = file.extension.lowercase()
        val system = systemFor(ext) ?: hint
        val first = GameIdentifier.identify(context, file, system)
        if (first != null && !first.fakeId) return first
        if (ext == "iso" || ext == "img") {
            val other = if (system == GameSystem.PSP) GameSystem.PS1 else GameSystem.PSP
            val second = GameIdentifier.identify(context, file, other)
            if (second != null && !second.fakeId) return second
        }
        return first
    }

    /** Scan a local directory of already-extracted games (e.g. the store install root). */
    suspend fun scanLocalDir(dir: File, system: GameSystem): Int = withContext(Dispatchers.IO) {
        if (!dir.isDirectory) return@withContext 0
        var added = 0
        dir.listFiles()?.forEach { child ->
            val uri = Uri.fromFile(child).toString()
            val lm = child.lastModified()
            if (index.isCurrent(uri, lm)) return@forEach
            val meta = identifyRefined(child, system) ?: return@forEach
            index.upsert(
                LibraryEntry(
                    serial = meta.serial,
                    title = meta.title,
                    system = meta.system,
                    sourceUri = uri,
                    displayName = child.name,
                    sizeBytes = if (child.isFile) child.length() else child.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                    iconPath = meta.iconPath,
                    discVersion = meta.discVersion,
                    bootCrc = meta.bootCrc,
                    fakeId = meta.fakeId,
                    addedAtEpochMs = System.currentTimeMillis(),
                    lastModified = lm,
                )
            )
            added++
        }
        added
    }

    /**
     * Scan a SAF tree Uri. Copies each candidate to a temp file for identification (PSP metadata
     * lives in file headers; SAF gives us a stream). Persisted permission must already be held.
     */
    suspend fun scanTree(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        var added = 0
        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null, null, null,
        ) ?: return@withContext 0

        cursor.use { c ->
            while (c.moveToNext()) {
                val childDocId = c.getString(0)
                val name = c.getString(1) ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                val system = systemFor(ext) ?: continue
                val size = c.getLong(2)
                val lm = c.getLong(3)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                if (index.isCurrent(childUri.toString(), lm)) continue

                // Identify from a temp copy (headers only; small for PBP, first sectors for ISO).
                val tmp = File.createTempFile("scan", ".$ext", context.cacheDir)
                try {
                    context.contentResolver.openInputStream(childUri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                    val meta = identifyRefined(tmp, system) ?: continue
                    index.upsert(
                        LibraryEntry(
                            serial = meta.serial,
                            title = meta.title,
                            system = meta.system,
                            sourceUri = childUri.toString(),
                            displayName = name,
                            sizeBytes = size,
                            iconPath = meta.iconPath,
                            discVersion = meta.discVersion,
                            bootCrc = meta.bootCrc,
                            fakeId = meta.fakeId,
                            addedAtEpochMs = System.currentTimeMillis(),
                            lastModified = lm,
                        )
                    )
                    added++
                } finally {
                    tmp.delete()
                }
            }
        }
        added
    }
}
