package com.retrovault.emulator

import android.content.Context
import com.retrovault.core.model.GameSystem
import java.io.File

enum class CoreStatus {
    /** No session started. */
    IDLE,

    /** Run loop active (core loaded; renders when a Surface is attached). */
    RUNNING,

    /** Native host not available, or the required core .so isn't installed. */
    UNAVAILABLE,

    /** Core or game failed to load. */
    ERROR,
}

/**
 * Owns one emulation run. [start] stages the session and spins the blocking native run loop on a
 * dedicated thread; the Surface is attached independently via [LibretroBridge.nativeSetVideoSurface].
 */
class EmulatorSession {

    val input = InputState()

    @Volatile
    var status: CoreStatus = CoreStatus.IDLE
        private set

    private var loopThread: Thread? = null

    /**
     * @param coreFileName the core .so name inside the app's native lib dir
     *                     (e.g. "ppsspp_libretro_android.so" or a test core)
     * @param gamePath     local path to the ROM/ISO, or null for no-content cores
     */
    fun start(context: Context, coreFileName: String, gamePath: String?) {
        if (status == CoreStatus.RUNNING) return
        if (!LibretroBridge.available) {
            status = CoreStatus.UNAVAILABLE
            return
        }

        val corePath = File(context.applicationInfo.nativeLibraryDir, coreFileName)
        if (!corePath.exists()) {
            status = CoreStatus.UNAVAILABLE
            return
        }

        val systemDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "system")
            .apply { mkdirs() }
        val saveDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "saves-core")
            .apply { mkdirs() }

        LibretroBridge.nativeStartSession(
            corePath.absolutePath, gamePath, systemDir.absolutePath, saveDir.absolutePath
        )
        status = CoreStatus.RUNNING
        loopThread = Thread({
            val ok = LibretroBridge.nativeRunLoop()
            status = if (ok) CoreStatus.IDLE else CoreStatus.ERROR
        }, "PulsarEmuLoop").also { it.start() }
    }

    fun start(context: Context, system: GameSystem, gamePath: String?) =
        start(context, "${CoreCatalog.coreLib(system)}.so", gamePath)

    /** Push the input snapshot to the core (call after mutating [input]). */
    fun syncInput(eventTimeNs: Long = 0L) {
        LibretroBridge.nativeSetInput(0, input.buttons, input.analogLX, input.analogLY, eventTimeNs)
    }

    fun stop() {
        if (status == CoreStatus.RUNNING || LibretroBridge.nativeIsRunning()) {
            LibretroBridge.nativeRequestStop()
            loopThread?.join(3000)
        }
        loopThread = null
        input.clear()
        if (status == CoreStatus.RUNNING) status = CoreStatus.IDLE
    }

    fun coreLib(system: GameSystem): String = CoreCatalog.coreLib(system)
}
