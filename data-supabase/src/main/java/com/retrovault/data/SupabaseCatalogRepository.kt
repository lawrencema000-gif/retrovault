package com.retrovault.data

import com.retrovault.core.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Reads the public, published catalog from Supabase via PostgREST. No sign-in required — the
 * publishable key plus RLS expose only published rows. Caches the last fetch in memory so the
 * detail screen can resolve a game by id without a second request.
 */
object SupabaseCatalogRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: List<Game> = emptyList()

    suspend fun fetchGames(): List<Game> = withContext(Dispatchers.IO) {
        val url = "${SupabaseConfig.REST_URL}/games" +
            "?select=id,system_id,title,slug,developer,description,license,license_url,source_url," +
            "storage_provider,download_url,download_size_bytes,box_art_url" +
            "&is_published=eq.true&order=system_id.asc,title.asc"

        val request = Request.Builder()
            .url(url)
            .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            .header("Authorization", "Bearer ${SupabaseConfig.PUBLISHABLE_KEY}")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Catalog request failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty().ifBlank { "[]" }
            val games = json.decodeFromString<List<GameDto>>(body).map { it.toDomain() }
            cache = games
            games
        }
    }

    fun cachedById(id: String): Game? = cache.firstOrNull { it.id == id }
}
