package com.retrovault.billing

/**
 * Product identifiers. Real values are set in the Play Console during the functional pass.
 * Monetization is Gold-only: the app stays open-source (GPLv3), revenue comes from the optional
 * Gold unlock — GPL source is never gated behind payment, and there are NO ads (linking AdMob
 * into the same APK as the GPL PPSSPP core would be a GPL-compliance risk; dropped 2026-07-17).
 */
object MonetizationConfig {
    const val GOLD_PRODUCT_ID = "pulsar_gold_unlock"           // one-time IAP
    const val CLOUD_STORAGE_SUB_ID = "pulsar_cloud_monthly"     // optional subscription

    // Server-side purchase verification (full flavor): a Supabase edge function calls the Play
    // Developer API with a service account. verify_jwt=false, so no auth header is needed.
    const val VERIFY_PURCHASE_URL =
        "https://mxasjicdkryaqugrccdo.supabase.co/functions/v1/verify-purchase"
}
