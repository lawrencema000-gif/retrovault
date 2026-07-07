package com.retrovault.emulator

/**
 * JNI boundary to the native RetroAchievements bridge (`rc_bridge.cpp`, rcheevos v11.6.0 rc_client),
 * compiled into the same `libpulsar_retro.so` that [LibretroBridge] loads.
 *
 * Threading contract: every `rc_client_*` call happens on the emulator run-loop thread. The methods
 * here only ENQUEUE commands / HTTP results or read mutex-guarded snapshots, so they are safe to
 * call from any thread. The HTTP transport is supplied by [RaHttpPump] (OkHttp), keeping rcheevos
 * free of its own networking. The live path (login + game load + real unlocks) needs an RA account
 * and RA-side approval of Pulsar as a hardcore-compliant emulator; it is staged.
 */
object RaBridge {

    /** rcheevos version clause for the stable User-Agent, e.g. "rcheevos/11.6". */
    external fun nativeRaVersionString(): String

    // ---- session lifecycle ----
    /** Create the rc_client (on the run-loop thread) with [hardcore] as the initial mode. */
    external fun nativeRaBeginSession(hardcore: Boolean)
    /** Unload the game; full teardown happens in the run loop before the core is unloaded. */
    external fun nativeRaEndSession()

    // ---- login / token ----
    external fun nativeRaLoginWithPassword(user: String, pass: String)
    external fun nativeRaLoginWithToken(user: String, token: String)
    external fun nativeRaLogout()

    // ---- game load / media ----
    /** Identify + load a game by file path. PSP is [RC_CONSOLE_PSP]; hashing runs natively. */
    external fun nativeRaStartGameByPath(consoleId: Int, filePath: String)
    external fun nativeRaStartGameByHash(hash: String)
    external fun nativeRaChangeMedia(filePath: String)

    // ---- hardcore ----
    external fun nativeRaSetHardcore(on: Boolean)
    external fun nativeRaGetHardcore(): Boolean
    /** One-way mid-session downgrade to softcore (RA permits it; the reverse needs a full reset). */
    external fun nativeRaDropToSoftcore()

    // ---- HTTP dispatcher (driven by RaHttpPump's thread) ----
    /** Block up to [timeoutMs] for the next request; returns {id,url,postData,contentType,method} or null. */
    external fun nativeRaWaitHttpRequest(timeoutMs: Int): Array<String>?
    /** Hand an HTTP result back; [httpStatus] < 0 uses rcheevos client-error sentinels. */
    external fun nativeRaCompleteHttpRequest(id: Long, httpStatus: Int, body: ByteArray?)

    // ---- event channel + UI snapshots ----
    /** Pop one event JSON (unlock/progress/login/load/mastery/…) or null when the queue is empty. */
    external fun nativeRaPollEvent(): String?
    external fun nativeRaAchievementListJson(): String
    external fun nativeRaGameSummaryJson(): String

    // ---- memory (reads the core's stable RAM pointer; no rc_client needed) ----
    /** rc_libretro_memory_init for PSP; returns (hasValidRegion << 32) | totalSize. */
    external fun nativeRaMemInit(): Long
    /** Read one byte at an RA flat address, or -1 if unmapped. */
    external fun nativeRaMemPeek(flatAddr: Int): Int

    // ---- offline self-tests (androidTest) ----
    external fun nativeRaSelfTestCreate(): Boolean
    external fun nativeRaSelfTestHttp(): Boolean
    external fun nativeRaTestContainer(): Boolean
    external fun nativeRaInflightCount(): Int

    /** RetroAchievements console id for the PSP (rc_consoles.h). */
    const val RC_CONSOLE_PSP = 41
}
