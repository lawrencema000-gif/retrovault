package com.retrovault.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
data class AuthUser(val id: String, val email: String? = null)

@Serializable
data class AuthSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: AuthUser? = null,
)

@Serializable
private data class Credentials(val email: String, val password: String)

/**
 * Minimal GoTrue (Supabase Auth) email+password client. Kept lightweight (OkHttp +
 * kotlinx.serialization) rather than pulling the full supabase-kt/Ktor stack. Session persistence,
 * token refresh, and a sign-in UI are added in the functional pass.
 */
object AuthClient {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val JSON = "application/json".toMediaType()

    @Volatile
    var session: AuthSession? = null
        private set

    val userId: String? get() = session?.user?.id
    val isSignedIn: Boolean get() = session != null

    fun signIn(email: String, password: String): AuthSession =
        auth("/auth/v1/token?grant_type=password", email, password)

    fun signUp(email: String, password: String): AuthSession =
        auth("/auth/v1/signup", email, password)

    fun signOut() {
        session = null
    }

    private fun auth(path: String, email: String, password: String): AuthSession {
        val body = json.encodeToString(Credentials(email, password)).toRequestBody(JSON)
        val request = Request.Builder()
            .url("${SupabaseConfig.URL}$path")
            .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("auth failed: HTTP ${resp.code}")
            return json.decodeFromString<AuthSession>(text).also { session = it }
        }
    }
}
