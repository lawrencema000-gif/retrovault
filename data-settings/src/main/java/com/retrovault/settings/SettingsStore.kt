package com.retrovault.settings

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class LayerFile(val schemaVersion: Int = 1, val values: Map<String, String> = emptyMap())

/**
 * User-layer persistence: one JSON diff for global overrides, one per game. Only values the
 * user explicitly set are stored — everything else resolves through the lower layers.
 */
class SettingsStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File(context.filesDir, "settings").apply { mkdirs() }

    private fun fileFor(gameKey: String?): File =
        if (gameKey == null) File(dir, "global.json")
        else File(dir, "game-${gameKey.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    fun read(gameKey: String?): Map<String, String> = runCatching {
        val f = fileFor(gameKey)
        if (!f.exists()) emptyMap()
        else json.decodeFromString<LayerFile>(f.readText()).values
    }.getOrDefault(emptyMap())

    fun write(gameKey: String?, values: Map<String, String>) {
        runCatching {
            val f = fileFor(gameKey)
            if (values.isEmpty()) f.delete()
            else f.writeText(json.encodeToString(LayerFile(values = values)))
        }
    }

    fun set(gameKey: String?, key: String, value: String) {
        write(gameKey, read(gameKey) + (key to value))
    }

    /** Remove a user override so the value falls back through the layers. */
    fun clear(gameKey: String?, key: String) {
        write(gameKey, read(gameKey) - key)
    }
}
