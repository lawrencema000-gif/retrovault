package com.retrovault.cheats

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** A cheat plus its current enabled state for a game. */
data class CheatEntry(val cheat: Cheat, val enabled: Boolean)

/**
 * Cheats for the app: the user imports a CWCheat `cheat.db` (SAF file or URL — never bundled),
 * stored once under app files; per-game enabled state is a small JSON set keyed by serial.
 *
 * The manager produces the enabled code list for [EmulatorSession.applyCheats]; enabling any
 * cheat is the caller's cue to clear hardcore/achievements mode.
 */
class CheatManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File(context.filesDir, "cheats").apply { mkdirs() }
    private val dbFile = File(dir, "cheat.db")

    val isDbImported: Boolean get() = dbFile.isFile && dbFile.length() > 0

    // ---- import (never bundled; user-supplied) ----

    /** Import from a SAF document Uri (the user's local cheat.db). */
    suspend fun importFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dbFile.outputStream().use { input.copyTo(it) }
            }
            validateAndKeep()
        }.getOrDefault(false)
    }

    /** Import from an https URL (e.g. a community cheat.db mirror the user pastes). */
    suspend fun importFromUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                resp.body!!.byteStream().use { input ->
                    dbFile.outputStream().use { input.copyTo(it) }
                }
            }
            validateAndKeep()
        }.getOrDefault(false)
    }

    private fun validateAndKeep(): Boolean {
        // Reject a file that parses to nothing (wrong format / HTML error page).
        val ok = runCatching { CheatDb.parse(dbFile.readText()).isNotEmpty() }.getOrDefault(false)
        if (!ok) dbFile.delete()
        cache = null
        return ok
    }

    fun deleteDb() {
        dbFile.delete()
        cache = null
    }

    // ---- per-game cheats ----

    private var cache: Map<String, List<Cheat>>? = null

    private fun db(): Map<String, List<Cheat>> {
        cache?.let { return it }
        val parsed = if (isDbImported) {
            runCatching { CheatDb.parse(dbFile.readText()) }.getOrDefault(emptyMap())
        } else emptyMap()
        cache = parsed
        return parsed
    }

    fun availableFor(serial: String?): List<Cheat> {
        if (serial == null) return emptyList()
        return db()[CheatDb.canonicalSerial(serial)] ?: emptyList()
    }

    /** Cheats for a serial with their persisted enabled state (default-on honored first run). */
    fun entriesFor(serial: String?): List<CheatEntry> {
        val cheats = availableFor(serial)
        if (cheats.isEmpty()) return emptyList()
        val enabled = enabledNames(serial)
        val firstRun = enabled == null
        return cheats.map {
            CheatEntry(it, if (firstRun) it.defaultOn else it.name in enabled!!)
        }
    }

    fun setEnabled(serial: String?, cheatName: String, enabled: Boolean) {
        if (serial == null) return
        val current = (enabledNames(serial) ?: currentDefaults(serial)).toMutableSet()
        if (enabled) current.add(cheatName) else current.remove(cheatName)
        writeEnabled(serial, current)
    }

    /** The CWCheat code text for every enabled cheat — hand straight to the core. */
    fun enabledCodes(serial: String?): List<String> =
        entriesFor(serial).filter { it.enabled }.map { it.cheat.code }

    fun anyEnabled(serial: String?): Boolean = entriesFor(serial).any { it.enabled }

    // ---- persistence (per-serial enabled set) ----

    private fun enabledFile(serial: String) =
        File(dir, "enabled-${CheatDb.canonicalSerial(serial)}.json")

    private fun enabledNames(serial: String?): Set<String>? {
        if (serial == null) return null
        val f = enabledFile(serial)
        if (!f.isFile) return null
        return runCatching { json.decodeFromString<Set<String>>(f.readText()) }.getOrNull()
    }

    private fun currentDefaults(serial: String?): Set<String> =
        availableFor(serial).filter { it.defaultOn }.map { it.name }.toSet()

    private fun writeEnabled(serial: String, names: Set<String>) {
        runCatching { enabledFile(serial).writeText(json.encodeToString(names)) }
    }
}
