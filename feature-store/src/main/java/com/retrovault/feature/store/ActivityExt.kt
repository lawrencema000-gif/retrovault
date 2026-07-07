package com.retrovault.feature.store

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Unwrap a Compose LocalContext (usually a ContextWrapper) to the hosting Activity, or null. */
fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
