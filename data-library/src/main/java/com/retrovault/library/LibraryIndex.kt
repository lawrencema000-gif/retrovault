package com.retrovault.library

import android.content.Context
import com.retrovault.core.model.GameSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** One installed/imported game in the on-device library. */
@Serializable
data class LibraryEntry(
    val serial: String,           // canonical key
    val title: String,
    val system: GameSystem,
    val sourceUri: String,        // content:// or file:// of the game file
    val displayName: String,      // file/document name
    val sizeBytes: Long,
    val iconPath: String?,
    val discVersion: String?,
    val bootCrc: String?,
    val fakeId: Boolean,
    val addedAtEpochMs: Long,
    val lastModified: Long,       // for incremental rescan (skip unchanged)
)

@Serializable
private data class IndexFile(val schemaVersion: Int = 1, val entries: List<LibraryEntry> = emptyList())

/**
 * JSON-cached library index. Keyed by canonical serial; persisted so rescans are instant for
 * unchanged files (matched by sourceUri + lastModified). A repository interface fronts the
 * storage so it can move to Room later without touching callers.
 */
class LibraryIndex(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val file = File(context.filesDir, "library-index.json")
    private val byKey = LinkedHashMap<String, LibraryEntry>()

    init {
        load()
    }

    @Synchronized
    fun all(): List<LibraryEntry> = byKey.values.sortedBy { it.title.lowercase() }

    @Synchronized
    fun get(serial: String): LibraryEntry? = byKey[serial]

    /** Whether an unchanged file is already indexed (skip re-identify on rescan). */
    @Synchronized
    fun isCurrent(sourceUri: String, lastModified: Long): Boolean =
        byKey.values.any { it.sourceUri == sourceUri && it.lastModified == lastModified }

    @Synchronized
    fun upsert(entry: LibraryEntry) {
        byKey[entry.serial] = entry
        save()
    }

    @Synchronized
    fun remove(serial: String) {
        if (byKey.remove(serial) != null) save()
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            json.decodeFromString<IndexFile>(file.readText()).entries.forEach { byKey[it.serial] = it }
        }
    }

    private fun save() {
        runCatching {
            file.writeText(json.encodeToString(IndexFile(entries = byKey.values.toList())))
        }
    }
}
