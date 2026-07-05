package com.retrovault.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice

/**
 * Watches controller connect/disconnect. Player behavior per the plan: a pad connecting hides
 * the touch overlay; the active pad disconnecting auto-pauses (never lose a run to a dead
 * battery).
 */
class HotplugMonitor(
    context: Context,
    private val onGamepadConnected: (InputDevice) -> Unit,
    private val onGamepadDisconnected: (deviceId: Int) -> Unit,
) : InputManager.InputDeviceListener {

    private val inputManager =
        context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val gamepadIds = HashSet<Int>()

    fun start() {
        inputManager.registerInputDeviceListener(this, Handler(Looper.getMainLooper()))
        // Seed with already-connected pads (and fire connect for them so the caller can react).
        InputDevice.getDeviceIds().forEach { onInputDeviceAdded(it) }
    }

    fun stop() {
        inputManager.unregisterInputDeviceListener(this)
        gamepadIds.clear()
    }

    fun anyGamepadConnected(): Boolean = gamepadIds.isNotEmpty()

    override fun onInputDeviceAdded(deviceId: Int) {
        val device = InputDevice.getDevice(deviceId) ?: return
        if (!isGamepad(device)) return
        if (gamepadIds.add(deviceId)) onGamepadConnected(device)
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        if (gamepadIds.remove(deviceId)) onGamepadDisconnected(deviceId)
    }

    override fun onInputDeviceChanged(deviceId: Int) = Unit

    companion object {
        fun isGamepad(device: InputDevice): Boolean {
            val src = device.sources
            return src and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                src and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        }
    }
}
