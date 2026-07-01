package com.retrovault.download

import com.retrovault.data.SupabaseConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Requests a short-TTL signed download URL from the `get-download-url` Edge Function, which checks
 * the game is published/eligible before signing (from Cloudflare R2 or Supabase Storage).
 * The function is deployed in the Stage 5 functional pass, once catalog files are hosted.
 */
object SignedUrlClient {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun request(gameId: String, accessToken: String? = null): SignedDownload {
        val endpoint = "${SupabaseConfig.URL}/functions/v1/get-download-url"
        val body = """{"game_id":"$gameId"}""".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            .header("Authorization", "Bearer ${accessToken ?: SupabaseConfig.PUBLISHABLE_KEY}")
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("signed url failed: HTTP ${resp.code}")
            return json.decodeFromString(resp.body?.string().orEmpty())
        }
    }
}
