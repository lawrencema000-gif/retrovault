package com.retrovault.saves

import com.retrovault.data.SupabaseConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/**
 * Syncs save-state blobs to the private `saves` bucket at `{uid}/{gameId}/slot{n}.state` via the
 * Supabase Storage REST API. Storage RLS scopes writes to the user's own `{uid}/` folder, so a
 * signed-in access token is required. Conflict handling (last-write-wins + updated_at) and
 * core-version gating are added in the functional pass.
 */
class SaveSyncClient(private val accessToken: String) {

    private val client = OkHttpClient()

    private fun objectUrl(path: String) = "${SupabaseConfig.URL}/storage/v1/object/saves/$path"

    fun upload(uid: String, gameId: String, slot: Int, file: File) {
        val path = "$uid/$gameId/slot$slot.state"
        val req = Request.Builder()
            .url(objectUrl(path))
            .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $accessToken")
            .header("x-upsert", "true")
            .post(file.asRequestBody())
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("save upload failed: HTTP ${resp.code}")
        }
    }

    fun download(uid: String, gameId: String, slot: Int, dest: File) {
        val path = "$uid/$gameId/slot$slot.state"
        val req = Request.Builder()
            .url(objectUrl(path))
            .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("save download failed: HTTP ${resp.code}")
            resp.body?.byteStream()?.use { input -> dest.outputStream().use { input.copyTo(it) } }
        }
    }
}
