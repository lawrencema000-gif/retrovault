package com.retrovault.billing

import android.app.Activity

/**
 * Purchase surface for Pulsar Gold. The concrete impl is chosen by build flavor:
 *   - `full`: [com.retrovault.billing.createBillingManager] returns a real Play Billing v7 client
 *     that launches the purchase, SERVER-VERIFIES the token, then mirrors the entitlement locally.
 *   - `foss`: returns a no-op that reports Gold unavailable (no proprietary deps, free tier only).
 *
 * Call [createBillingManager] (declared per-flavor) — main code never references a proprietary type.
 */
interface BillingManager {
    /** Whether Pulsar Gold is currently entitled (a local mirror of the verified Play purchase). */
    val isGold: Boolean

    /** False in `foss` (no purchase path) — store chrome hides the upgrade row when false. */
    val purchaseSupported: Boolean

    /** Launch the Play purchase flow. Needs an [Activity]; a no-op where purchase isn't supported. */
    fun purchaseGold(activity: Activity)

    /** Re-hydrate the entitlement from owned purchases (survives reinstall). No-op in `foss`. */
    fun restore()
}
