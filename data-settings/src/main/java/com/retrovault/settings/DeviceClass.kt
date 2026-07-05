package com.retrovault.settings

import android.os.Build

/** GPU family — drives device-class defaults (and later, the compat DB's device profiles). */
enum class GpuFamily { ADRENO, MALI, XCLIPSE, POWERVR, EMULATOR, UNKNOWN }

/**
 * Device-class detection + the device layer of the settings resolver. The GL renderer string
 * is captured once a GL context exists; until then classification falls back to [Build] data.
 */
object DeviceClass {

    @Volatile
    var glRenderer: String? = null

    fun family(renderer: String? = glRenderer): GpuFamily {
        val r = (renderer ?: "").lowercase()
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        return when {
            "swiftshader" in r || "emulator" in r || "android emulator" in r -> GpuFamily.EMULATOR
            "adreno" in r -> GpuFamily.ADRENO
            "mali" in r -> GpuFamily.MALI
            "xclipse" in r -> GpuFamily.XCLIPSE
            "powervr" in r -> GpuFamily.POWERVR
            r.isEmpty() && abi.startsWith("x86") -> GpuFamily.EMULATOR
            else -> GpuFamily.UNKNOWN
        }
    }

    val isX86: Boolean get() = (Build.SUPPORTED_ABIS.firstOrNull() ?: "").startsWith("x86")

    /**
     * Device-layer setting values. Small and curated: things that are wrong for a class of
     * hardware regardless of game or user taste.
     */
    fun layerValues(): Map<String, String> {
        val values = HashMap<String, String>()
        if (isX86) {
            // PPSSPP's MIPS JIT emits self-modifying code that breaks under nested
            // emulation (x86 AVD); IR JIT is correct there. Real arm64 devices keep JIT.
            values[PspSettings.CPU_CORE.key] = "IR JIT"
        }
        when (family()) {
            GpuFamily.EMULATOR -> {
                // SwiftShader is a software rasterizer — render at native res.
                values[PspSettings.INTERNAL_RESOLUTION.key] = "480x272"
            }
            GpuFamily.POWERVR -> {
                // Historically weak with heavy texture upscale.
                values[PspSettings.TEXTURE_SCALING.key] = "1"
            }
            else -> Unit
        }
        return values
    }
}
