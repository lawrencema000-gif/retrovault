package com.retrovault.billing

import android.content.Context

/**
 * Purchase surface for Pulsar Gold. The functional pass wraps Google Play Billing (v7+), using the
 * US external-billing option (Epic v. Google, sunsets Nov 2027) where it lowers the take rate.
 * For now [LocalBillingManager] flips the local entitlement so Gold-gated UI is exercisable.
 */
interface BillingManager {
    val isGold: Boolean
    fun purchaseGold()
    fun restore()
}

class LocalBillingManager(context: Context) : BillingManager {
    private val entitlements = Entitlements(context)

    override val isGold: Boolean get() = entitlements.isGold

    // Dev stub — the real flow launches the Play purchase and verifies the token server-side.
    override fun purchaseGold() {
        entitlements.isGold = true
    }

    override fun restore() {
        // Functional pass: query Play for owned products and re-sync the entitlement.
    }
}
