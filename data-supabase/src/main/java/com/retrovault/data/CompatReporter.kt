package com.retrovault.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** One game's community compatibility summary (public read). */
data class CompatSummary(val serial: String, val reportCount: Int, val avgRating: Double?)

/**
 * In-app compatibility reporting (P13). Account-less: reports key on a persistent install id;
 * the backend enforces once-per install+serial+app-version and keeps a public summary.
 *
 * Prompt policy: after a session of ≥10 minutes, once per serial+app-version, and never
 * again for that pair even if the user dismisses.
 */
object CompatReporter {

    const val MIN_SESSION_MS = 10 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true }
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    // ---- install identity + prompt bookkeeping ----

    fun installId(context: Context): String {
        val f = File(context.filesDir, "install-id")
        runCatching {
            if (f.isFile) {
                val v = f.readText().trim()
                if (v.length == 36) return v
            }
        }
        val id = UUID.randomUUID().toString()
        runCatching { f.writeText(id) }
        return id
    }

    private fun promptedFile(context: Context) = File(context.filesDir, "compat-prompted.json")

    @Serializable
    private data class Prompted(val keys: Set<String> = emptySet())

    private fun promptKey(serial: String, appVersion: String) = "$serial@$appVersion"

    fun shouldPrompt(context: Context, playedMs: Long, serial: String?, appVersion: String): Boolean {
        if (serial == null || playedMs < MIN_SESSION_MS) return false
        val prompted = runCatching {
            json.decodeFromString<Prompted>(promptedFile(context).readText()).keys
        }.getOrDefault(emptySet())
        return promptKey(serial, appVersion) !in prompted
    }

    fun markPrompted(context: Context, serial: String, appVersion: String) {
        val f = promptedFile(context)
        val prompted = runCatching {
            json.decodeFromString<Prompted>(f.readText()).keys
        }.getOrDefault(emptySet())
        runCatching {
            f.writeText(json.encodeToString(Prompted(prompted + promptKey(serial, appVersion))))
        }
    }

    // ---- submission ----

    /**
     * Submit a report. Returns true when the backend stored it (false = duplicate for this
     * install+serial+version, or network/validation failure — never throws).
     */
    suspend fun submit(
        context: Context,
        serial: String,
        rating: Int,
        subScores: Map<String, Int> = emptyMap(),
        note: String? = null,
        device: Map<String, String> = emptyMap(),
        settingsDiff: Map<String, String> = emptyMap(),
        appVersion: String,
        coreVersion: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = JsonObject(
                mapOf(
                    "installId" to JsonPrimitive(installId(context)),
                    "serial" to JsonPrimitive(serial),
                    "rating" to JsonPrimitive(rating),
                    "subScores" to JsonObject(subScores.mapValues { JsonPrimitive(it.value) }),
                    "note" to (note?.let { JsonPrimitive(it) } ?: JsonPrimitive("")),
                    "device" to JsonObject(device.mapValues { JsonPrimitive(it.value) }),
                    "settingsDiff" to JsonObject(settingsDiff.mapValues { JsonPrimitive(it.value) }),
                    "appVersion" to JsonPrimitive(appVersion),
                    "coreVersion" to (coreVersion?.let { JsonPrimitive(it) } ?: JsonPrimitive("")),
                )
            )
            val req = Request.Builder()
                .url("${SupabaseConfig.URL}/functions/v1/submit-compat-report")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val parsed = json.parseToJsonElement(resp.body!!.string()).jsonObject
                parsed["inserted"]?.jsonPrimitive?.content == "true"
            }
        }.getOrDefault(false)
    }

    // ---- public summary (game pages) ----

    suspend fun summaryFor(serial: String): CompatSummary? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(
                    "${SupabaseConfig.REST_URL}/compat_summary" +
                        "?serial=eq.$serial&select=serial,report_count,avg_rating"
                )
                .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .header("Authorization", "Bearer ${SupabaseConfig.PUBLISHABLE_KEY}")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val arr = json.parseToJsonElement(resp.body!!.string()).jsonArray
                if (arr.isEmpty()) return@use null
                val row = arr[0].jsonObject
                CompatSummary(
                    serial = row["serial"]!!.jsonPrimitive.content,
                    reportCount = row["report_count"]!!.jsonPrimitive.content.toInt(),
                    avgRating = row["avg_rating"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                )
            }
        }.getOrNull()
    }
}
