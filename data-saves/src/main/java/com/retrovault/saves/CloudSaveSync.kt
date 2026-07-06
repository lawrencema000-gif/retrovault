package com.retrovault.saves

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** A save present on this device (file may be null if only the cloud has it). */
data class LocalSave(val kind: String, val slot: Int, val file: File?, val hash: String?)

/** A save that diverged on both sides — surfaced to the user, never auto-resolved. */
data class SaveConflict(val gameKey: String, val kind: String, val slot: Int, val remote: RemoteSave)

data class SyncResult(
    val pushed: Int,
    val pulled: Int,
    val inSync: Int,
    val conflicts: List<SaveConflict>,
)

/**
 * Orchestrates one game's save sync using [ConflictResolver] (three-way, never-clobber). The
 * "base" — the hash at the last successful sync — is tracked per device in a small local file,
 * which is what lets two devices editing the same save produce a conflict instead of silent loss.
 *
 * [baseDir] is per-account so switching accounts doesn't cross bases.
 */
class CloudSaveSync(
    private val api: CloudSaveApi,
    private val baseDir: File,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun baseFile(gameKey: String) =
        File(baseDir, "${gameKey.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json").apply { parentFile?.mkdirs() }

    private fun loadBase(gameKey: String): MutableMap<String, String> = runCatching {
        val f = baseFile(gameKey)
        if (f.isFile) json.decodeFromString<Map<String, String>>(f.readText()).toMutableMap()
        else mutableMapOf()
    }.getOrDefault(mutableMapOf())

    private fun saveBase(gameKey: String, base: Map<String, String>) {
        runCatching { baseFile(gameKey).writeText(json.encodeToString(base)) }
    }

    private fun key(kind: String, slot: Int) = "$kind:$slot"

    /**
     * Sync [locals] for [gameKey] against the cloud. [pullDest] supplies where a pulled cloud
     * save should be written. Conflicts are returned, NOT applied.
     */
    suspend fun sync(
        gameKey: String,
        locals: List<LocalSave>,
        pullDest: (kind: String, slot: Int) -> File,
    ): SyncResult {
        if (api.userId() == null) return SyncResult(0, 0, 0, emptyList())
        val remote = api.manifest(gameKey).associateBy { key(it.kind, it.slot) }
        val base = loadBase(gameKey)
        val localByKey = locals.associateBy { key(it.kind, it.slot) }
        val allKeys = (remote.keys + localByKey.keys).toSet()

        var pushed = 0; var pulled = 0; var inSync = 0
        val conflicts = mutableListOf<SaveConflict>()

        for (k in allKeys) {
            val local = localByKey[k]
            val rem = remote[k]
            when (ConflictResolver.resolve(local?.hash, rem?.sha256, base[k])) {
                SyncAction.NOTHING -> Unit
                SyncAction.IN_SYNC -> { local?.hash?.let { base[k] = it }; inSync++ }
                SyncAction.PUSH_LOCAL -> {
                    val l = local ?: continue
                    val f = l.file ?: continue
                    val up = api.upload(gameKey, l.kind, l.slot, f, l.hash ?: continue)
                    if (up != null) { base[k] = up.sha256; pushed++ }
                }
                SyncAction.PULL_REMOTE -> {
                    val r = rem ?: continue
                    if (api.download(r, pullDest(r.kind, r.slot))) { base[k] = r.sha256; pulled++ }
                }
                SyncAction.CONFLICT -> rem?.let {
                    conflicts += SaveConflict(gameKey, it.kind, it.slot, it)
                }
            }
        }
        saveBase(gameKey, base)
        return SyncResult(pushed, pulled, inSync, conflicts)
    }

    /**
     * Apply the user's choice for a conflicted save. KEEP_BOTH preserves the cloud copy in a
     * sidecar ([cloudSidecar]) so nothing is lost, then pushes the local copy.
     */
    suspend fun resolveConflict(
        gameKey: String,
        conflict: SaveConflict,
        choice: ConflictChoice,
        localFile: File?,
        localHash: String?,
        pullDest: File,
        cloudSidecar: File,
    ): Boolean {
        val k = key(conflict.kind, conflict.slot)
        val base = loadBase(gameKey)
        val ok = when (choice) {
            ConflictChoice.KEEP_DEVICE -> {
                val f = localFile ?: return false
                val up = api.upload(gameKey, conflict.kind, conflict.slot, f, localHash ?: return false)
                if (up != null) base[k] = up.sha256
                up != null
            }
            ConflictChoice.KEEP_CLOUD -> {
                val done = api.download(conflict.remote, pullDest)
                if (done) base[k] = conflict.remote.sha256
                done
            }
            ConflictChoice.KEEP_BOTH -> {
                api.download(conflict.remote, cloudSidecar) // preserve cloud copy locally
                val f = localFile ?: return false
                val up = api.upload(gameKey, conflict.kind, conflict.slot, f, localHash ?: return false)
                if (up != null) base[k] = up.sha256
                up != null
            }
        }
        if (ok) saveBase(gameKey, base)
        return ok
    }
}
