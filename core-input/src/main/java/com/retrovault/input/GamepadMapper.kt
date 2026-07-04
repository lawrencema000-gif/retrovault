package com.retrovault.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.retrovault.emulator.RetroPad
import kotlin.math.abs

/**
 * Android-standard gamepad → RetroPad mapping (Xbox/DualSense/Switch Pro all normalize to
 * these keycodes/axes). Per-device remap profiles + gamecontrollerdb arrive in P9.
 *
 * PSP/RetroPad convention: RetroPad.B = Cross (bottom), A = Circle (right),
 * Y = Square (left), X = Triangle (top).
 */
class GamepadMapper(
    private val hub: InputHub,
    private val analogDeadzone: Float = 0.15f,
) {
    private var buttons: Int = 0

    fun isGamepadEvent(event: KeyEvent): Boolean {
        val src = event.source
        return src and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            src and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            KeyEvent.isGamepadButton(event.keyCode)
    }

    /** @return true if consumed. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val mask = keyToMask(event.keyCode) ?: return false
        val down = event.action == KeyEvent.ACTION_DOWN
        val next = if (down) buttons or mask else buttons and mask.inv()
        if (next != buttons) {
            buttons = next
            hub.onPadButtons(buttons, event.eventTime * 1_000_000L)
        }
        return true
    }

    /** @return true if consumed. Joystick move events: left stick + d-pad hat. */
    fun onMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK ||
            event.action != MotionEvent.ACTION_MOVE
        ) return false

        // Left analog stick with radial deadzone, rescaled to full range.
        val rawX = event.getAxisValue(MotionEvent.AXIS_X)
        val rawY = event.getAxisValue(MotionEvent.AXIS_Y)
        val x = applyDeadzone(rawX)
        val y = applyDeadzone(rawY)
        hub.onPadAnalog(
            (x * 32767f).toInt().coerceIn(-32767, 32767),
            (y * 32767f).toInt().coerceIn(-32767, 32767),
            event.eventTime * 1_000_000L
        )

        // D-pad reported as HAT axes on most pads.
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        var next = buttons and
            (RetroPad.LEFT or RetroPad.RIGHT or RetroPad.UP or RetroPad.DOWN).inv()
        if (hatX < -0.5f) next = next or RetroPad.LEFT
        if (hatX > 0.5f) next = next or RetroPad.RIGHT
        if (hatY < -0.5f) next = next or RetroPad.UP
        if (hatY > 0.5f) next = next or RetroPad.DOWN
        if (next != buttons) {
            buttons = next
            hub.onPadButtons(buttons, event.eventTime * 1_000_000L)
        }
        return true
    }

    private fun applyDeadzone(v: Float): Float {
        if (abs(v) < analogDeadzone) return 0f
        val sign = if (v < 0) -1f else 1f
        return sign * ((abs(v) - analogDeadzone) / (1f - analogDeadzone)).coerceIn(0f, 1f)
    }

    private fun keyToMask(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> RetroPad.B      // Cross
        KeyEvent.KEYCODE_BUTTON_B -> RetroPad.A      // Circle
        KeyEvent.KEYCODE_BUTTON_X -> RetroPad.Y      // Square
        KeyEvent.KEYCODE_BUTTON_Y -> RetroPad.X      // Triangle
        KeyEvent.KEYCODE_BUTTON_L1 -> RetroPad.L
        KeyEvent.KEYCODE_BUTTON_R1 -> RetroPad.R
        KeyEvent.KEYCODE_BUTTON_START -> RetroPad.START
        KeyEvent.KEYCODE_BUTTON_SELECT -> RetroPad.SELECT
        KeyEvent.KEYCODE_DPAD_UP -> RetroPad.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> RetroPad.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> RetroPad.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> RetroPad.RIGHT
        else -> null
    }
}
