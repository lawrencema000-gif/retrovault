package com.retrovault.billing

import android.content.Context

/** Local record of the user's Gold entitlement (mirrors the verified Play purchase). */
class Entitlements(context: Context) {

    private val prefs = context.getSharedPreferences("pulsar_entitlements", Context.MODE_PRIVATE)

    var isGold: Boolean
        get() = prefs.getBoolean(KEY_GOLD, false)
        set(value) {
            prefs.edit().putBoolean(KEY_GOLD, value).apply()
        }

    fun has(feature: GoldFeature): Boolean = isGold

    private companion object {
        const val KEY_GOLD = "gold"
    }
}
