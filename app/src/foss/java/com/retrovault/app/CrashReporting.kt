package com.retrovault.app

import android.content.Context

/** foss flavor: zero crash-reporting code — nothing phones home, nothing to opt into. */
fun initCrashReportingIfEnabled(context: Context) { /* intentionally empty */ }

/** Whether this build can report crashes at all (drives the Settings toggle visibility). */
fun crashReportingAvailable(): Boolean = false
