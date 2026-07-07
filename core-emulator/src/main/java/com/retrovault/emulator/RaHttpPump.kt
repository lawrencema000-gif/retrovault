package com.retrovault.emulator

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The ONLY networking surface for RetroAchievements. rcheevos does no HTTP itself — rc_client just
 * produces `url` + `post_data`; this daemon thread pulls each request off the native queue, runs it
 * through OkHttp with the stable [userAgent], and hands the result back via
 * [RaBridge.nativeRaCompleteHttpRequest] (exactly once per id, or the async op leaks). The native
 * side then invokes rc_client's callback on the run-loop thread — never here.
 */
class RaHttpPump(private val userAgent: String) {

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "RaHttpPump").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun loop() {
        while (running) {
            // {id, url, postData, contentType, method}; null on the 500 ms poll timeout.
            val req = runCatching { RaBridge.nativeRaWaitHttpRequest(500) }.getOrNull() ?: continue
            if (req.size < 5) continue
            val id = req[0].toLongOrNull() ?: continue
            val url = req[1]
            val postData = req[2]
            val contentType = req[3].ifEmpty { "application/x-www-form-urlencoded" }

            var status = -1        // RC_API_SERVER_RESPONSE_CLIENT_ERROR (hard failure)
            var body: ByteArray? = null
            try {
                val b = Request.Builder().url(url).header("User-Agent", userAgent)
                if (postData.isEmpty()) b.get()
                else b.post(postData.toRequestBody(contentType.toMediaTypeOrNull()))
                client.newCall(b.build()).execute().use { resp ->
                    status = resp.code
                    body = resp.body?.bytes()
                }
            } catch (e: IOException) {
                status = -2        // RC_API_SERVER_RESPONSE_RETRYABLE_CLIENT_ERROR (timeout/DNS)
            } catch (t: Throwable) {
                status = -1
            } finally {
                runCatching { RaBridge.nativeRaCompleteHttpRequest(id, status, body) }
            }
        }
    }
}
