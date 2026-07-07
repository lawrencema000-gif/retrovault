package com.retrovault.billing

/**
 * Product + ad identifiers. Real values are set in the Play Console + AdMob during the functional
 * pass. Monetization follows the PPSSPP model: the app stays open-source (GPLv3), revenue comes
 * from ads + an optional Gold unlock — GPL source is never gated behind payment.
 */
object MonetizationConfig {
    const val GOLD_PRODUCT_ID = "pulsar_gold_unlock"           // one-time IAP
    const val CLOUD_STORAGE_SUB_ID = "pulsar_cloud_monthly"     // optional subscription

    // Placeholders — replaced with real AdMob IDs before shipping the ad-supported free tier.
    const val ADMOB_APP_ID = "ca-app-pub-0000000000000000~0000000000"
    const val ADMOB_INTERSTITIAL_ID = "ca-app-pub-0000000000000000/0000000000"

    // Server-side purchase verification (full flavor): a Supabase edge function calls the Play
    // Developer API with a service account. verify_jwt=false, so no auth header is needed.
    const val VERIFY_PURCHASE_URL =
        "https://mxasjicdkryaqugrccdo.supabase.co/functions/v1/verify-purchase"
}
