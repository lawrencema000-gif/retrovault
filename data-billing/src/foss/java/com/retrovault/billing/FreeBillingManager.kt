package com.retrovault.billing

import android.app.Activity
import android.content.Context

/**
 * foss build: Pulsar Gold is never available (no proprietary billing SDK). The free tier is fully
 * functional; Gold-gated conveniences simply stay locked. [isGold] reads the local entitlement,
 * which has no way to become true in this flavor.
 */
class FreeBillingManager(context: Context) : BillingManager {
    private val entitlements = Entitlements(context)
    override val isGold: Boolean get() = entitlements.isGold
    override val purchaseSupported: Boolean = false
    override fun purchaseGold(activity: Activity) { /* no purchase path in foss */ }
    override fun restore() { /* nothing to restore */ }
}
