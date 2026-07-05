package com.retrovault.emulator

import android.app.Activity
import android.view.Surface

/**
 * JNI boundary to the native libretro host (`src/main/cpp`).
 *
 * Threading contract: [nativeRunLoop] blocks and must run on a dedicated thread — it performs
 * EGL init + core dlopen + `retro_load_game` + the frame loop on that thread so that GL
 * environment callbacks (SET_HW_RENDER) fire with the context current. Everything else may be
 * called from any thread.
 */
object LibretroBridge {

    val available: Boolean = runCatching { System.loadLibrary("pulsar_retro") }.isSuccess

    /**
     * dlopen a core and return "name\nversion\nextensions\napiVersion" from
     * `retro_get_system_info`/`retro_api_version` without initializing it. Null on failure.
     * (Newline-separated: the extensions field is itself pipe-delimited, e.g. "elf|iso|cso".)
     */
    external fun nativeProbeCore(corePath: String): String?

    /** Initialize AGDK Swappy frame pacing for this activity (safe no-op if unsupported). */
    external fun nativeInitSwappy(activity: Activity)

    /** Stage a session: core path, optional game path (null = no-content), core dirs. */
    external fun nativeStartSession(
        corePath: String,
        gamePath: String?,
        systemDir: String,
        saveDir: String,
    )

    /** Provide/revoke the render Surface. Callable from the main thread at any time. */
    external fun nativeSetVideoSurface(surface: Surface?)

    /**
     * Run the emulator session to completion (blocks until [nativeRequestStop]).
     * Returns false if the core/game failed to load. Call on a dedicated thread.
     */
    external fun nativeRunLoop(): Boolean

    /** Ask the run loop to exit; it unloads the core and tears down EGL on its way out. */
    external fun nativeRequestStop()

    external fun nativeIsRunning(): Boolean

    /**
     * Freeze/unfreeze emulation without tearing anything down (menu open, gamepad unplugged).
     * While paused the core stops running frames, audio stops, and save/load-state ops are
     * still serviced.
     */
    external fun nativeSetPaused(paused: Boolean)
    external fun nativeIsPaused(): Boolean

    /**
     * Push the current input state for a port (RetroPad bitmask + left-analog axes).
     * [eventTimeNs] = the originating Android input event time (uptime-based, ns) for
     * input→frame latency instrumentation; pass 0 when not applicable.
     */
    external fun nativeSetInput(port: Int, buttons: Int, analogLX: Int, analogLY: Int, eventTimeNs: Long)

    /** Current button bitmask as seen by the core-side snapshot (tests/debug overlay). */
    external fun nativeDebugButtons(): Int

    /** EMA of input-event → core-sample latency, microseconds. */
    external fun nativeInputLatencyUsEma(): Long
    external fun nativeInputEventsSampled(): Long

    // ---- video stats (P2 acceptance + future debug overlay) ----
    external fun nativeFramesPresented(): Long
    external fun nativeAvgFrameIntervalUs(): Long
    external fun nativeSwappyActive(): Boolean

    // ---- audio stats / config (P3) ----
    external fun nativeAudioFramesOut(): Long
    /** Audio frames the core has produced — scales with emulation speed (FF observable). */
    external fun nativeAudioFramesProduced(): Long
    external fun nativeAudioUnderruns(): Long
    external fun nativeAudioFillPct(): Int
    /** Dynamic-rate-control deviation ×1e6 (±5000 = the ±0.5% cap). */
    external fun nativeAudioRateDeltaPpm(): Int
    external fun nativeAudioDeviceRate(): Int
    /** Larger audio buffering for Bluetooth routes (applies on next stream start). */
    external fun nativeSetBtFriendlyAudio(bt: Boolean)

    // ---- save states (P8) ----

    /**
     * Serialize the full core state to [statePath] (written atomically: tmp + rename), and —
     * if [rawFramePath] is non-null — dump the last presented frame as raw RGBA (int32 w,
     * int32 h, top-down rows) for thumbnail generation. Callable from any thread: the op is
     * executed by the run-loop thread between frames; this call blocks until it completes.
     * Returns false if no session is running or the core failed to serialize.
     */
    external fun nativeSaveState(statePath: String, rawFramePath: String?): Boolean

    /** Counterpart to [nativeSaveState]: retro_unserialize [statePath] on the run-loop thread. */
    external fun nativeLoadState(statePath: String): Boolean

    /** Dump the last presented frame (raw RGBA, same format as save-state frames). */
    external fun nativeScreenshot(rawFramePath: String): Boolean

    // ---- speed / rewind / hardcore (P10) ----

    /** Percent of realtime: 50 (slow-mo), 100, 200–500. No-op while hardcore is active. */
    external fun nativeSetSpeed(pct: Int)
    external fun nativeGetSpeed(): Int

    /**
     * Enable rewind with a RAM budget: slots = budgetBytes / serialize_size (min 1, cap 120),
     * one snapshot every [intervalFrames]. Pass budget 0 to disable and free the ring.
     */
    external fun nativeSetRewind(budgetBytes: Long, intervalFrames: Int)
    external fun nativeRewindCount(): Int

    /** Restore the newest rewind snapshot (blocking op). False when the ring is empty. */
    external fun nativeRewindStep(): Boolean

    /** RetroAchievements-ready interlock: while on, FF/slow-mo/rewind are refused. */
    external fun nativeSetHardcore(on: Boolean)
    external fun nativeIsHardcore(): Boolean
}
