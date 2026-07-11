package com.retrovault.feature.store.about

/**
 * Result of a manual update check against GitHub Releases. `NoReleases` is today's reality
 * (the repo has no releases yet) and must read as "up to date", never as an error.
 */
sealed class UpdateResult {
    data class UpdateAvailable(val tag: String) : UpdateResult()
    object UpToDate : UpdateResult()
    object NoReleases : UpdateResult()
    object Error : UpdateResult()
}
