package com.retrovault.saves

import com.retrovault.data.AuthManager
import com.retrovault.data.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/** One save's cloud state (the manifest row). */
data class RemoteSave(
    val kind: String,
    val slot: Int,
    val sha256: String,
    val sizeBytes: Long,
    val version: Int,
    val storageKey: String,
)

/** Abstraction so the sync engine can be unit-tested without the network. */
interface CloudSaveApi {
    suspend fun userId(): String?
    suspend fun manifest(gameKey: String): List<RemoteSave>
    suspend fun upload(gameKey: String, kind: String, slot: Int, file: File, sha256: String): RemoteSave?
    suspend fun download(remote: RemoteSave, dest: File): Boolean
}

/** Live Supabase implementation: `saves` Storage bucket + `cloud_saves` manifest table (RLS). */
class CloudSaveClient(private val auth: AuthManager, private val deviceId: String) : CloudSaveApi {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient()

    override suspend fun userId(): String? = auth.ensureSignedIn()

    override suspend fun manifest(gameKey: String): List<RemoteSave> = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: return@withContext emptyList()
        val url = "${SupabaseConfig.REST_URL}/cloud_saves" +
            "?game_key=eq.$gameKey&select=kind,slot,sha256,size_bytes,version,storage_key"
        val req = Request.Builder().url(url)
            .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $token")
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<RemoteSave>()
                json.parseToJsonElement(resp.body!!.string()).jsonArray.map { el ->
                    val o = el.jsonObject
                    RemoteSave(
                        kind = o["kind"]!!.jsonPrimitive.content,
                        slot = o["slot"]!!.jsonPrimitive.int,
                        sha256 = o["sha256"]!!.jsonPrimitive.content,
                        sizeBytes = o["size_bytes"]!!.jsonPrimitive.long,
                        version = o["version"]!!.jsonPrimitive.int,
                        storageKey = o["storage_key"]!!.jsonPrimitive.content,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun upload(
        gameKey: String, kind: String, slot: Int, file: File, sha256: String,
    ): RemoteSave? = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: return@withContext null
        val uid = auth.currentUserId ?: return@withContext null

        val current = manifest(gameKey).firstOrNull { it.kind == kind && it.slot == slot }
        val version = (current?.version ?: 0) + 1
        val storageKey = "$uid/$gameKey/$kind-$slot-v$version.bin"

        // 1. Upload the blob to the private saves bucket (owner-folder RLS).
        val putReq = Request.Builder()
            .url("${SupabaseConfig.URL}/storage/v1/object/saves/$storageKey")
            .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $token")
            .header("x-upsert", "true")
            .post(file.readBytes().toRequestBody(OCTET))
            .build()
        val uploaded = runCatching { http.newCall(putReq).execute().use { it.isSuccessful } }
            .getOrDefault(false)
        if (!uploaded) return@withContext null

        // 2. Upsert the manifest row (the trigger records a history version).
        val row = buildJsonObject {
            put("user_id", uid); put("game_key", gameKey); put("kind", kind); put("slot", slot)
            put("storage_key", storageKey); put("sha256", sha256); put("size_bytes", file.length())
            put("version", version); put("device_id", deviceId)
            put("updated_at", "now()")
        }
        val body = buildJsonArray { add(row) }.toString()
        val postReq = Request.Builder()
            .url("${SupabaseConfig.REST_URL}/cloud_saves?on_conflict=user_id,game_key,kind,slot")
            .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        val ok = runCatching { http.newCall(postReq).execute().use { it.isSuccessful } }
            .getOrDefault(false)
        if (!ok) return@withContext null
        RemoteSave(kind, slot, sha256, file.length(), version, storageKey)
    }

    override suspend fun download(remote: RemoteSave, dest: File): Boolean = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: return@withContext false
        val req = Request.Builder()
            .url("${SupabaseConfig.URL}/storage/v1/object/saves/${remote.storageKey}")
            .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $token")
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                dest.parentFile?.mkdirs()
                resp.body!!.byteStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
                true
            }
        }.getOrDefault(false)
    }

    companion object {
        private val OCTET = "application/octet-stream".toMediaType()
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
