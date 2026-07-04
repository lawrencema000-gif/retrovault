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

    val combinedButtons: Int get() = touchButtons or padButtons

    // Whichever source is deflected further wins (touch stick vs physical stick).
    val analogX: Int
        get() = if (mag(touchAnalogX, touchAnalogY) >= mag(padAnalogX, padAnalogY)) touchAnalogX else padAnalogX
    val analogY: Int
        get() = if (mag(touchAnalogX, touchAnalogY) >= mag(padAnalogX, padAnalogY)) touchAnalogY else padAnalogY

    private fun mag(x: Int, y: Int): Long = x.toLong() * x + y.toLong() * y

    fun clear() {
        touchButtons = 0
        padButtons = 0
        padAnalogX = 0
        padAnalogY = 0
        touchAnalogX = 0
        touchAnalogY = 0
        push(0L)
    }

    private fun push(eventTimeNs: Long) {
        if (LibretroBridge.available) {
            LibretroBridge.nativeSetInput(0, combinedButtons, padAnalogX, padAnalogY, eventTimeNs)
        }
    }
}
