package com.retrovault.settings

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class BlacklistFile(val schemaVersion: Int = 1, val failed: Set<String> = emptySet())

/**
 * Graphics-backend selection with a failed-backend blacklist: if a backend fails to
 * initialize on this device, it is recorded and never auto-picked again — the session
 * falls back down the chain instead of crash-looping.
 *
 * Backends: "gles3" (implemented) and "vulkan" (context negotiation lands with the PS2
 * pass; selectable but currently resolves to the fallback).
 */
class BackendPolicy(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "backend-blacklist.json")
    private var failed: MutableSet<String> =
        runCatching { json.decodeFromString<BlacklistFile>(file.readText()).failed.toMutableSet() }
            .getOrDefault(mutableSetOf())

    val implemented: Set<String> = setOf("gles3")

    /** The chain tried for a preference, best first. */
    private fun chainFor(preferred: String): List<String> = when (preferred) {
        "vulkan" -> listOf("vulkan", "gles3")
        else -> listOf("gles3")
    }

    /**
     * Pick the backend the session should use: the first entry of the preference chain that
     * is implemented and not blacklisted. GLES3 is the terminal fallback even if blacklisted
     * (something must render — a broken GLES3 device can't run an emulator at all).
     */
    fun choose(preferred: String): String =
        chainFor(preferred).firstOrNull { it in implemented && it !in failed } ?: "gles3"

    /** Record a backend that failed to initialize; the next [choose] skips it. */
    fun recordFailure(backend: String) {
        if (failed.add(backend)) save()
    }

    fun isBlacklisted(backend: String): Boolean = backend in failed

    fun clear() {
        failed.clear()
        save()
    }

    private fun save() {
        runCatching { file.writeText(json.encodeToString(BlacklistFile(failed = failed))) }
    }
}
