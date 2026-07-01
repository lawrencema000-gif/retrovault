package com.retrovault.emulator

import com.retrovault.core.model.GameSystem

enum class CoreStatus {
    /** No core loaded yet. */
    IDLE,

    /** Native host + core available; would be running the retro_run loop. */
    RUNNING,

    /** Native host not built yet, or the system's core .so isn't installed. */
    UNAVAILABLE,

    /** Load or runtime error. */
    ERROR,
}

/**
 * Owns one emulation run. In the integration pass [start] will: resolve + dlopen the core,
 * `nativeLoadGame`, and spin the `retro_run` loop on a dedicated render thread, forwarding
 * [input] each frame. For now it reports whether the native host/core is available so the player
 * UI can show the right state.
 */
class EmulatorSession {

    val input = InputState()

    var status: CoreStatus = CoreStatus.IDLE
        private set

    /**
     * @param system which console core to run
     * @param gamePath local path to the ROM/ISO (null = not downloaded / BYO-ROM not chosen yet)
     */
    fun start(system: GameSystem, gamePath: String?) {
        status = when {
            !LibretroBridge.available -> CoreStatus.UNAVAILABLE
            gamePath == null -> CoreStatus.UNAVAILABLE
            else -> {
                // Integration pass: nativeInit(corePath) → nativeLoadGame(gamePath) → render loop.
                CoreStatus.RUNNING
            }
        }
    }

    fun coreLib(system: GameSystem): String = CoreCatalog.coreLib(system)

    fun stop() {
        if (LibretroBridge.available && status == CoreStatus.RUNNING) {
            LibretroBridge.nativeUnload()
        }
        input.clear()
        status = CoreStatus.IDLE
    }
}
