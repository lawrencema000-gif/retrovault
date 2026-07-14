package com.retrovault.input

import com.retrovault.emulator.LibretroBridge

/**
 * Merges the two input sources — touch overlay and external gamepad — into one combined
 * state and pushes it to the native atomic snapshot (read by `retro_input_state` on the
 * emu thread with no locks and no main-thread hop).
 *
 * Both producers deliver on the main thread (View touch dispatch + Activity key dispatch),
 * so plain fields are safe; the JNI push is a single atomic store on the native side.
 */
class InputHub {

    private var touchButtons: Int = 0
    private var padButtons: Int = 0
    private var padAnalogX: Int = 0
    private var padAnalogY: Int = 0
    private var touchAnalogX: Int = 0
    private var touchAnalogY: Int = 0
    private var padAnalogRX: Int = 0
    private var padAnalogRY: Int = 0
    private var touchAnalogRX: Int = 0
    private var touchAnalogRY: Int = 0

    fun onTouchState(buttons: Int, eventTimeNs: Long) {
        touchButtons = buttons
        push(eventTimeNs)
    }

    fun onTouchAnalog(x: Int, y: Int, eventTimeNs: Long) {
        touchAnalogX = x
        touchAnalogY = y
        push(eventTimeNs)
    }

    fun onPadButtons(buttons: Int, eventTimeNs: Long) {
        padButtons = buttons
        push(eventTimeNs)
    }

    fun onPadAnalog(x: Int, y: Int, eventTimeNs: Long) {
        padAnalogX = x
        padAnalogY = y
        push(eventTimeNs)
    }

    /** Right stick (PS1 DualShock; P24). */
    fun onPadAnalogRight(x: Int, y: Int, eventTimeNs: Long) {
        padAnalogRX = x
        padAnalogRY = y
        push(eventTimeNs)
    }

    fun onTouchAnalogRight(x: Int, y: Int, eventTimeNs: Long) {
        touchAnalogRX = x
        touchAnalogRY = y
        push(eventTimeNs)
    }

    val combinedButtons: Int get() = touchButtons or padButtons

    // Whichever source is deflected further wins (touch stick vs physical stick).
    val analogX: Int
        get() = if (mag(touchAnalogX, touchAnalogY) >= mag(padAnalogX, padAnalogY)) touchAnalogX else padAnalogX
    val analogY: Int
        get() = if (mag(touchAnalogX, touchAnalogY) >= mag(padAnalogX, padAnalogY)) touchAnalogY else padAnalogY

    val analogRX: Int
        get() = if (mag(touchAnalogRX, touchAnalogRY) >= mag(padAnalogRX, padAnalogRY)) touchAnalogRX else padAnalogRX
    val analogRY: Int
        get() = if (mag(touchAnalogRX, touchAnalogRY) >= mag(padAnalogRX, padAnalogRY)) touchAnalogRY else padAnalogRY

    private fun mag(x: Int, y: Int): Long = x.toLong() * x + y.toLong() * y

    fun clear() {
        touchButtons = 0
        padButtons = 0
        padAnalogX = 0
        padAnalogY = 0
        touchAnalogX = 0
        touchAnalogY = 0
        padAnalogRX = 0
        padAnalogRY = 0
        touchAnalogRX = 0
        touchAnalogRY = 0
        push(0L)
    }

    private fun push(eventTimeNs: Long) {
        if (LibretroBridge.available) {
            // MERGED analog (P24 fix): this previously pushed the PAD values only, silently
            // dropping the floating touch stick before it ever reached the core.
            LibretroBridge.nativeSetInput(
                0, combinedButtons, analogX, analogY, analogRX, analogRY, eventTimeNs,
            )
        }
    }
}
