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

    /** Push the current input state for a port (RetroPad bitmask + left-analog axes). */
    external fun nativeSetInput(port: Int, buttons: Int, analogLX: Int, analogLY: Int)

    // ---- video stats (P2 acceptance + future debug overlay) ----
    external fun nativeFramesPresented(): Long
    external fun nativeAvgFrameIntervalUs(): Long
    external fun nativeSwappyActive(): Boolean

    // ---- save states (functional from P10) ----
    external fun nativeSerializeSize(): Int
    external fun nativeSerialize(): ByteArray?
    external fun nativeUnserialize(data: ByteArray): Boolean
}
