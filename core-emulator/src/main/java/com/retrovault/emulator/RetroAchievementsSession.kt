package com.retrovault.emulator

import android.content.Context
import android.os.Build
import org.json.JSONObject

/**
 * High-level RetroAchievements orchestrator: owns the [RaHttpPump] + an event-poller thread and
 * drives the native rc_client through [RaBridge]. The rc_client itself runs on the emulator
 * run-loop thread; this class only enqueues commands and drains events.
 *
 * The live path (real login + game identification + unlocks) needs an RA account AND RA-side
 * approval of Pulsar as a hardcore-compliant emulator, so it is staged. The plumbing, the hardcore
 * interlock, and the save-state progress container are exercised on the emulator without it.
 */
class RetroAchievementsSession(
    context: Context,
    private val userAgent: String,
) {
    private val appContext = context.applicationContext
    private val creds = RaCredentialStore(appContext)
    private val pump = RaHttpPump(userAgent)

    @Volatile private var poller: Thread? = null
    @Volatile private var listener: ((RaEvent) -> Unit)? = null

    /** Register a UI callback for unlock toasts / summary refreshes (invoked on the poller thread). */
    fun setListener(l: ((RaEvent) -> Unit)?) { listener = l }

    /** Begin an RA session with the given [hardcore] preference; starts the HTTP pump + poller and
     *  silently logs in with a stored token if one exists. */
    fun begin(hardcore: Boolean) {
        pump.start()
        RaBridge.nativeRaBeginSession(hardcore)
        startPoller()
        creds.load()?.let { RaBridge.nativeRaLoginWithToken(it.user, it.token) }
    }

    fun end() {
        RaBridge.nativeRaEndSession()
        stopPoller()
        pump.stop()
    }

    fun loginWithPassword(user: String, pass: String) = RaBridge.nativeRaLoginWithPassword(user, pass)
    fun startPspGame(filePath: String) = RaBridge.nativeRaStartGameByPath(RaBridge.RC_CONSOLE_PSP, filePath)
    fun setHardcore(on: Boolean) = RaBridge.nativeRaSetHardcore(on)
    fun dropToSoftcore() = RaBridge.nativeRaDropToSoftcore()
    fun isHardcore(): Boolean = RaBridge.nativeRaGetHardcore()
    fun achievementListJson(): String = RaBridge.nativeRaAchievementListJson()
    fun gameSummaryJson(): String = RaBridge.nativeRaGameSummaryJson()

    private fun startPoller() {
        if (poller != null) return
        poller = Thread({
            while (!Thread.currentThread().isInterrupted) {
                var ev = RaBridge.nativeRaPollEvent()
                while (ev != null) {
                    handleEvent(ev)
                    ev = RaBridge.nativeRaPollEvent()
                }
                try { Thread.sleep(200) } catch (e: InterruptedException) { break }
            }
        }, "RaEventPoller").apply { isDaemon = true; start() }
    }

    private fun stopPoller() {
        poller?.interrupt()
        poller = null
    }

    private fun handleEvent(json: String) {
        val e = runCatching { JSONObject(json) }.getOrNull() ?: return
        when (e.optString("type")) {
            "login_ok" -> {
                val u = e.optString("user"); val t = e.optString("token")
                if (u.isNotEmpty() && t.isNotEmpty()) creds.save(u, t)
            }
            "login_failed" -> {
                val code = e.optInt("code")
                if (code == RC_INVALID_CREDENTIALS || code == RC_EXPIRED_TOKEN) creds.clear()
            }
        }
        listener?.invoke(RaEvent.from(e))
    }

    companion object {
        private const val RC_INVALID_CREDENTIALS = -34
        private const val RC_EXPIRED_TOKEN = -35

        /**
         * The stable, RA-whitelisted User-Agent — byte-identical across every request and launch
         * (RA keys hardcore-emulator approval off it): "Pulsar/<ver> (Android <sdk>; <abi>) rcheevos/<v>".
         */
        fun userAgent(versionName: String): String {
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
            return "Pulsar/$versionName (Android ${Build.VERSION.SDK_INT}; $abi) ${RaBridge.nativeRaVersionString()}"
        }
    }
}

/** A typed view of a native RA event JSON, for toasts + the session summary. */
data class RaEvent(
    val type: String,
    val title: String = "",
    val points: Int = 0,
    val badge: String = "",
    val hardcore: Boolean = false,
    val message: String = "",
) {
    companion object {
        fun from(j: JSONObject) = RaEvent(
            type = j.optString("type"),
            title = j.optString("title"),
            points = j.optInt("points"),
            badge = j.optString("badge"),
            hardcore = j.optInt("hardcore") != 0,
            message = j.optString("message"),
        )
    }
}
