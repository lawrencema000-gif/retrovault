package com.retrovault.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/** The persisted auth session. Anonymous-first: [isAnonymous] until the user links an email. */
@Serializable
data class PulsarAuthSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val isAnonymous: Boolean,
    val expiresAtEpochSec: Long,
    val email: String? = null,
)

/**
 * Supabase Auth over the GoTrue REST API (no supabase-kt dependency). Anonymous-first: the app
 * signs in anonymously on first use so cloud saves work with zero friction; the user can later
 * link an email to make the account permanent and cross-device.
 */
class AuthManager(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(appContext.filesDir, "pulsar-auth.json")
    private val http = OkHttpClient()
    private val mutex = Mutex()

    @Volatile
    private var session: PulsarAuthSession? = runCatching {
        if (file.isFile) json.decodeFromString<PulsarAuthSession>(file.readText()) else null
    }.getOrNull()

    val currentUserId: String? get() = session?.userId
    val isSignedIn: Boolean get() = session != null
    val isAnonymous: Boolean get() = session?.isAnonymous ?: true

    /** Ensure a session exists (anonymous if none), returning the user id. */
    suspend fun ensureSignedIn(): String? = mutex.withLock {
        session?.let { return it.userId }
        signInAnonymouslyLocked()?.userId
    }

    /** A currently-valid access token, refreshing if it has expired. Null if sign-in fails. */
    suspend fun validAccessToken(): String? = mutex.withLock {
        val s = session ?: signInAnonymouslyLocked() ?: return null
        if (s.expiresAtEpochSec - 60 > nowSec()) return s.accessToken
        refreshLocked(s)?.accessToken ?: signInAnonymouslyLocked()?.accessToken
    }

    fun signOut() {
        session = null
        file.delete()
    }

    // ---- GoTrue calls (mutex held) ----

    private suspend fun signInAnonymouslyLocked(): PulsarAuthSession? = withContext(Dispatchers.IO) {
        val req = authRequest("signup", "{}")
        runCatching { execToSession(req) }.getOrNull()?.also { persist(it) }
    }

    private suspend fun refreshLocked(s: PulsarAuthSession): PulsarAuthSession? = withContext(Dispatchers.IO) {
        val body = """{"refresh_token":"${s.refreshToken}"}"""
        val req = authRequest("token?grant_type=refresh_token", body)
        runCatching { execToSession(req) }.getOrNull()?.also { persist(it) }
    }

    /** Link an email + password to the current (anonymous) user — makes the account permanent. */
    suspend fun linkEmail(email: String, password: String): Boolean = mutex.withLock {
        val token = session?.accessToken ?: return false
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(EmailUpdate(email, password))
            val req = Request.Builder()
                .url("${SupabaseConfig.URL}/auth/v1/user")
                .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .header("Authorization", "Bearer $token")
                .put(body.toRequestBody(JSON_MEDIA))
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        }
    }

    @Serializable
    private data class EmailUpdate(val email: String, val password: String)

    private fun authRequest(path: String, body: String): Request =
        Request.Builder()
            .url("${SupabaseConfig.URL}/auth/v1/$path")
            .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

    private fun execToSession(req: Request): PulsarAuthSession {
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            require(resp.isSuccessful) { "auth ${resp.code}: $text" }
            val obj = json.parseToJsonElement(text).jsonObject
            val access = obj["access_token"]!!.jsonPrimitive.content
            val refresh = obj["refresh_token"]!!.jsonPrimitive.content
            val expiresIn = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600
            val user = obj["user"]!!.jsonObject
            return PulsarAuthSession(
                userId = user["id"]!!.jsonPrimitive.content,
                accessToken = access,
                refreshToken = refresh,
                isAnonymous = user["is_anonymous"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                expiresAtEpochSec = nowSec() + expiresIn,
                email = user["email"]?.jsonPrimitive?.content?.ifBlank { null },
            )
        }
    }

    private fun persist(s: PulsarAuthSession) {
        session = s
        runCatching { file.writeText(json.encodeToString(s)) }
    }

    private fun nowSec(): Long = System.currentTimeMillis() / 1000

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
