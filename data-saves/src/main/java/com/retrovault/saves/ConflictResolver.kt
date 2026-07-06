package com.retrovault.saves

/** What a sync should do for one save after comparing local, cloud, and the last-synced base. */
enum class SyncAction {
    /** Local and cloud already match. */
    IN_SYNC,

    /** Only the local copy changed since last sync → upload it. */
    PUSH_LOCAL,

    /** Only the cloud copy changed since last sync → download it. */
    PULL_REMOTE,

    /** BOTH changed since last sync (or no common base) → ask the user; never clobber. */
    CONFLICT,

    /** Neither side has this save. */
    NOTHING,
}

/**
 * Three-way save-sync resolver (RetroArch model). The invariant is **never clobber**: when both
 * the device and the cloud have diverged from the last-synced state, the result is [CONFLICT] and
 * the caller must ask the user — data is never silently overwritten.
 *
 * Inputs are content hashes:
 * - [local]: hash of the on-device save, or null if none.
 * - [remote]: hash of the cloud save, or null if none.
 * - [base]: hash recorded at the last successful sync (the common ancestor), or null if this
 *   save has never synced on this device.
 */
object ConflictResolver {

    fun resolve(local: String?, remote: String?, base: String?): SyncAction {
        if (local == null && remote == null) return SyncAction.NOTHING
        if (local == remote) return SyncAction.IN_SYNC // both present and equal (both-null handled)

        if (local != null && remote == null) {
            // Cloud has nothing. If we previously synced (base != null) the cloud copy was
            // deleted elsewhere; still push ours rather than lose local progress.
            return SyncAction.PUSH_LOCAL
        }
        if (local == null && remote != null) {
            return SyncAction.PULL_REMOTE
        }

        // Both present and different — the three-way decision.
        val localChanged = local != base
        val remoteChanged = remote != base
        return when {
            localChanged && remoteChanged -> SyncAction.CONFLICT
            localChanged -> SyncAction.PUSH_LOCAL
            remoteChanged -> SyncAction.PULL_REMOTE
            // They differ yet both equal base — impossible; treat conservatively.
            else -> SyncAction.CONFLICT
        }
    }
}

/** User's choice on the conflict sheet. */
enum class ConflictChoice { KEEP_DEVICE, KEEP_CLOUD, KEEP_BOTH }
