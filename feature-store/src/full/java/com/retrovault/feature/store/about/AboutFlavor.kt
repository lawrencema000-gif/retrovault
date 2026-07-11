package com.retrovault.feature.store.about

/**
 * full (Play) channel: NO update-check code — Google Play handles updates, and Play's Device &
 * Network Abuse policy treats sideload-update prompts as violations (RetroArch precedent). This
 * twin keeps the symbol resolvable so main-source Settings code compiles for every variant.
 */
fun updateCheckAvailable(): Boolean = false

/** full builds carry the opt-in Sentry reporter, so the consent toggle is shown. */
fun crashReportToggleAvailable(): Boolean = true

/** Never called in the full flavor ([updateCheckAvailable] is false); present for compilation. */
fun checkForUpdate(currentVersion: String, onResult: (UpdateResult) -> Unit) {
    onResult(UpdateResult.NoReleases)
}
