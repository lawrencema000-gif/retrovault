package com.retrovault.emulator

/**
 * JNI boundary to the native libretro host (`src/main/cpp`, compiled with the NDK in the final
 * integration pass). The host `dlopen`s a per-system libretro core `.so`
 * (e.g. `ppsspp_libretro_android.so`) and drives its `retro_*` lifecycle.
 *
 * Until the native library `pulsar_retro` is built AND a core is installed, [available] is false
 * and the player renders a "core not installed" state instead of calling into native code.
 */
object LibretroBridge {

    val available: Boolean = runCatching { System.loadLibrary("pulsar_retro") }.isSuccess

    /**
     * dlopen a core and return "name\nversion\nextensions\napiVersion" from
     * `retro_get_system_info`/`retro_api_version` without initializing it. Null on failure.
     * (Newline-separated: the extensions field is itself pipe-delimited, e.g. "elf|iso|cso".)
     * The P1 smoke test that a core binary is loadable on this device/ABI.
     */
    external fun nativeProbeCore(corePath: String): String?

    /** Load a libretro core `.so` by absolute path. */
    external fun nativeInit(corePath: String): Boolean

    /** Load a ROM/ISO into the initialized core. */
    external fun nativeLoadGame(gamePath: String): Boolean

    /** Provide the render Surface (ANativeWindow) the core draws into. */
    external fun nativeSetSurface(surface: Any?)

    /** Advance one frame (`retro_run`). Called on the render thread. */
    external fun nativeRunFrame()

    /** Push the current input state for a port before the next frame. */
    external fun nativeSetInput(port: Int, buttons: Int, analogLX: Int, analogLY: Int)

    external fun nativeSerializeSize(): Int
    external fun nativeSerialize(): ByteArray?
    external fun nativeUnserialize(data: ByteArray): Boolean

    /** Tear down the game + core (`retro_unload_game` + `retro_deinit`). */
    external fun nativeUnload()
}
