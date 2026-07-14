package com.retrovault.emulator

import com.retrovault.core.model.GameSystem

/**
 * Maps a system to the libretro core it runs on. The matching `<lib>_android.so` is shipped
 * per-ABI (or downloaded via Play Asset Delivery) in the integration pass.
 *
 * PSP → PPSSPP (no BIOS); PS1 → SwanStation; PS2 → ARMSX2 (both need a user-supplied BIOS).
 */
object CoreCatalog {
    fun coreLib(system: GameSystem): String = when (system) {
        GameSystem.PSP -> "ppsspp_libretro_android"
        GameSystem.PS1 -> "swanstation_libretro_android"
        GameSystem.PS2 -> "armsx2_libretro_android"
    }

    fun requiresBios(system: GameSystem): Boolean = when (system) {
        GameSystem.PSP -> false
        // SwanStation embeds an OpenBIOS fallback (MIT, PCSX-Redux) — PS1 boots without a user
        // BIOS; a real dump is recommended for compatibility but never REQUIRED to play (P23).
        GameSystem.PS1 -> false
        GameSystem.PS2 -> true
    }
}
