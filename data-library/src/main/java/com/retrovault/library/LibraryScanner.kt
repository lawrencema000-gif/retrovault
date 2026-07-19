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
    private companion object {
        /** Bounded identify probe: enough for PBP headers + ISO9660 PVD/dirs/SYSTEM.CNF. */
        const val SCAN_PROBE_BYTES = 16L * 1024 * 1024
    }

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

    private data class ProbeResult(val meta: GameMetadata?, val hitLimit: Boolean)

    /**
     * Copy up to [limit] bytes of [uri] into [tmp] (truncating any previous content), then
     * identify. [ProbeResult.hitLimit] is ground truth from the copy loop itself — whether the
     * stream still had data at the bound — because SAF providers may report COLUMN_SIZE as
     * null/0 and the fallback decision must not trust it.
     */
    private fun copyAndIdentify(uri: Uri, tmp: File, hint: GameSystem, limit: Long): ProbeResult {
        var copied = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(256 * 1024)
                var remaining = limit
                while (remaining > 0) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    if (n < 0) break
                    out.write(buf, 0, n)
                    copied += n
                    remaining -= n
                }
            }
        } ?: return ProbeResult(null, hitLimit = false)
        return ProbeResult(identifyRefined(tmp, hint), hitLimit = copied >= limit)
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

                // Identify from a BOUNDED temp copy first — headers and the early filesystem
                // structures (PBP sections, ISO9660 PVD/root dir, SYSTEM.CNF) live in the first
                // MBs for every supported format, and copying whole multi-GB discs froze scans
                // of big folders. If the bounded probe can't produce a real ID and the file is
                // larger than the probe, fall back to the old full copy so correctness never
                // regresses (a metadata file that happens to sit late in the image still works).
                val tmp = File.createTempFile("scan", ".$ext", context.cacheDir)
                try {
                    val probe = copyAndIdentify(childUri, tmp, system, SCAN_PROBE_BYTES)
                    var meta = probe.meta
                    // Full-copy fallback ONLY when a bigger copy could change the answer:
                    // chd/cso are content-blind in GameIdentifier (name + first-1MB CRC → always
                    // fakeId), so re-copying gigabytes for them is a guaranteed no-op; and a
                    // failed second pass keeps the probe's valid result instead of dropping the
                    // game (the SAF stream can vanish between opens).
                    val contentBlind = ext == "chd" || ext == "cso"
                    if (!contentBlind && (meta == null || meta.fakeId) && probe.hitLimit) {
                        meta = copyAndIdentify(childUri, tmp, system, Long.MAX_VALUE).meta ?: meta
                    }
                    if (meta == null) continue
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
