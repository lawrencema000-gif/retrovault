package com.retrovault.app

import android.content.Context
import com.retrovault.core.ui.AppPrefs
import io.sentry.android.core.SentryAndroid

/**
 * full flavor: Sentry crash reporting — strictly opt-in (default OFF) and fully inert without a
 * DSN. SentryAndroid.init is never even called unless the user opted in AND a DSN was injected at
 * build time (BuildConfig.SENTRY_DSN via CI secret; empty in every local/dev build today).
 * No PII is sent; session tracking stays off. Toggling opt-in takes effect on the next app start.
 */
fun initCrashReportingIfEnabled(context: Context) {
    val dsn = BuildConfig.SENTRY_DSN
    if (dsn.isEmpty() || !AppPrefs.crashReportsOptIn) return
    SentryAndroid.init(context) { options ->
        options.dsn = dsn
        options.isSendDefaultPii = false
        options.isEnableAutoSessionTracking = false
    }
}

/** Whether this build can report crashes at all (drives the Settings toggle visibility). */
fun crashReportingAvailable(): Boolean = true
