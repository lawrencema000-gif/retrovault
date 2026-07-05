package com.retrovault.input

import android.content.Context
import android.view.InputDevice
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class RemapFile(val schemaVersion: Int = 1, val profiles: Map<String, MappingProfile> = emptyMap())

/**
 * Per-device user remaps, keyed by [InputDevice.getDescriptor] (stable across reconnects of
 * the same physical controller, unlike the transient device id). A stored profile is the
 * user's word: it wins over the controller DB and the Android default.
 */
class RemapStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "controller-remaps.json")
    private val profiles = LinkedHashMap<String, MappingProfile>()

    init {
        runCatching {
            if (file.exists()) {
                json.decodeFromString<RemapFile>(file.readText()).profiles.forEach { (k, v) ->
                    profiles[k] = v
                }
            }
        }
    }

    @Synchronized
    fun get(descriptor: String): MappingProfile? = profiles[descriptor]

    @Synchronized
    fun put(descriptor: String, profile: MappingProfile) {
        profiles[descriptor] = profile
        save()
    }

    @Synchronized
    fun remove(descriptor: String) {
        if (profiles.remove(descriptor) != null) save()
    }

    /**
     * Resolve the active profile for a device: user remap > controller DB > Android default.
     */
    fun resolve(context: Context, device: InputDevice?): MappingProfile {
        if (device != null) {
            get(device.descriptor)?.let { return it }
            ControllerDb.match(context, device)?.let { return ControllerDb.toProfile(it, device) }
        }
        return DefaultMapping.profile
    }

    private fun save() {
        runCatching { file.writeText(json.encodeToString(RemapFile(profiles = profiles))) }
    }
}
