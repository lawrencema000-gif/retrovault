package com.retrovault.data

/**
 * Public Supabase connection info for the RetroVault backend.
 *
 * The publishable key is *designed* to ship in client apps — Row Level Security enforces what it
 * can read/write (only the published catalog + the signed-in user's own rows). The service_role
 * key and DB password are secrets and must never appear here or in the app.
 */
object SupabaseConfig {
    const val URL = "https://mxasjicdkryaqugrccdo.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_MWdq6gY3S90_AhUDuX5rdw_VQ-yDCw8"
    const val REST_URL = "$URL/rest/v1"
}
