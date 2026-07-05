package com.retrovault.emulator

/** RetroPad button bits — matches libretro `RETRO_DEVICE_ID_JOYPAD_*`. */
object RetroPad {
    const val B = 1 shl 0
    const val Y = 1 shl 1
    const val SELECT = 1 shl 2
    const val START = 1 shl 3
    const val UP = 1 shl 4
    const val DOWN = 1 shl 5
    const val LEFT = 1 shl 6
    const val RIGHT = 1 shl 7
    const val A = 1 shl 8
    const val X = 1 shl 9
    const val L = 1 shl 10
    const val R = 1 shl 11
    const val L2 = 1 shl 12
    const val R2 = 1 shl 13
    const val L3 = 1 shl 14
    const val R3 = 1 shl 15
}

/**
 * A single input-state buffer written by the touch overlay + physical gamepad and read once per
 * frame by the render loop (which forwards it to [LibretroBridge.nativeSetInput]).
 */
class InputState {
    @Volatile
    var buttons: Int = 0
        private set

    @Volatile
    var analogLX: Int = 0

    @Volatile
    var analogLY: Int = 0

    fun press(mask: Int) { buttons = buttons or mask }
    fun release(mask: Int) { buttons = buttons and mask.inv() }
    fun set(mask: Int, down: Boolean) { if (down) press(mask) else release(mask) }
    fun clear() { buttons = 0; analogLX = 0; analogLY = 0 }
}
