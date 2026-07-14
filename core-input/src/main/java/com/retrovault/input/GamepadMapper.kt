package com.retrovault.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Profile-driven gamepad → RetroPad mapping. The active [MappingProfile] resolves per device
 * (user remap > gamecontrollerdb > Android default — see [RemapStore.resolve]) and is applied
 * per event; profiles are cached by device id and invalidated on hotplug.
 *
 * Analog tuning per profile: radial or axial deadzone, inverse deadzone (output floor for
 * worn sticks), and a response curve exponent. Emulator shortcuts ([VirtKey]) bound to pad
 * controls fire through [onVirtKey] instead of the game input path.
 */
class GamepadMapper(
    private val hub: InputHub,
    /** Resolves the profile for a device; defaults to the Android-standard profile only. */
    private val profileResolver: (InputDevice?) -> MappingProfile = { DefaultMapping.profile },
    /** Emulator shortcut sink (menu, save/load state, fast-forward, screenshot). */
    var onVirtKey: ((VirtKey, Boolean) -> Unit)? = null,
) {
    private var buttons: Int = 0
    private val profileCache = HashMap<Int, ProfileIndex>()

    // per-source pressed state for axis-driven buttons/virtkeys (edge detection)
    private val axisPressed = HashMap<Long, Boolean>()

    private class ProfileIndex(val profile: MappingProfile) {
        val byKey = HashMap<Int, MutableList<InputTarget>>()
        val axisBindings = ArrayList<Pair<InputSource.Axis, InputTarget>>()

        init {
            for (b in profile.bindings) {
                when (val s = b.source) {
                    is InputSource.Key -> byKey.getOrPut(s.keyCode) { ArrayList() }.add(b.target)
                    is InputSource.Axis -> axisBindings.add(s to b.target)
                }
            }
        }
    }

    /** Drop cached profiles (device connected/disconnected or remap saved). */
    fun invalidateProfiles() {
        profileCache.clear()
    }

    private fun indexFor(device: InputDevice?): ProfileIndex {
        val id = device?.id ?: -1
        return profileCache.getOrPut(id) { ProfileIndex(profileResolver(device)) }
    }

    fun isGamepadEvent(event: KeyEvent): Boolean {
        val src = event.source
        return src and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            src and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            KeyEvent.isGamepadButton(event.keyCode)
    }

    /** @return true if consumed. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val idx = indexFor(event.device)
        val targets = idx.byKey[event.keyCode] ?: return false
        if (event.repeatCount > 0) return true
        val down = event.action == KeyEvent.ACTION_DOWN
        var next = buttons
        for (t in targets) {
            when (t) {
                is InputTarget.Button -> next = if (down) next or t.mask else next and t.mask.inv()
                is InputTarget.Emu -> t.virtKey?.let { onVirtKey?.invoke(it, down) }
                is InputTarget.Analog -> Unit // keys don't drive analog targets
            }
        }
        if (next != buttons) {
            buttons = next
            hub.onPadButtons(buttons, event.eventTime * 1_000_000L)
        }
        return true
    }

    /** @return true if consumed. Applies every axis binding of the device's profile. */
    fun onMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK ||
            event.action != MotionEvent.ACTION_MOVE
        ) return false

        val idx = indexFor(event.device)
        val p = idx.profile
        var next = buttons
        var lx = 0f
        var ly = 0f
        var rx = 0f
        var ry = 0f

        for ((source, target) in idx.axisBindings) {
            val raw = event.getAxisValue(source.axis)
            when (target) {
                is InputTarget.Analog -> {
                    // stick 0 = left, 1 = right (PS1 DualShock right stick, P24)
                    if (target.stick == 1) {
                        if (target.axis == 0) rx = accumulate(rx, raw, source, target)
                        else ry = accumulate(ry, raw, source, target)
                    } else {
                        if (target.axis == 0) lx = accumulate(lx, raw, source, target)
                        else ly = accumulate(ly, raw, source, target)
                    }
                }

                is InputTarget.Button -> {
                    val pressed = axisActive(raw, source.direction)
                    val key = sourceKey(source)
                    if (axisPressed[key] != pressed) {
                        axisPressed[key] = pressed
                        next = if (pressed) next or target.mask else next and target.mask.inv()
                    }
                }

                is InputTarget.Emu -> {
                    val pressed = axisActive(raw, source.direction)
                    val key = sourceKey(source)
                    if (axisPressed[key] != pressed) {
                        axisPressed[key] = pressed
                        target.virtKey?.let { onVirtKey?.invoke(it, pressed) }
                    }
                }
            }
        }

        val (tx, ty) = tuneAnalog(lx, ly, p)
        hub.onPadAnalog(
            (tx * 32767f).toInt().coerceIn(-32767, 32767),
            (ty * 32767f).toInt().coerceIn(-32767, 32767),
            event.eventTime * 1_000_000L,
        )
        val (trx, try_) = tuneAnalog(rx, ry, p)
        hub.onPadAnalogRight(
            (trx * 32767f).toInt().coerceIn(-32767, 32767),
            (try_ * 32767f).toInt().coerceIn(-32767, 32767),
            event.eventTime * 1_000_000L,
        )

        if (next != buttons) {
            buttons = next
            hub.onPadButtons(buttons, event.eventTime * 1_000_000L)
        }
        return true
    }

    /**
     * Fold one axis binding into the running stick-axis value. Semantics: `half` is how far
     * the source axis is deflected in the binding's direction (0..1); the contribution drives
     * the target axis in `target.direction`. Summing handles split-axis (+aN/−aN) and
     * full-axis (both directions emitted by the profile) alike.
     */
    private fun accumulate(current: Float, raw: Float, source: InputSource.Axis, target: InputTarget.Analog): Float {
        val half = if (source.direction > 0) raw.coerceIn(0f, 1f) else (-raw).coerceIn(0f, 1f)
        return (current + half * target.direction).coerceIn(-1f, 1f)
    }

    private fun axisActive(v: Float, direction: Int): Boolean =
        if (direction > 0) v > AXIS_BUTTON_THRESHOLD else v < -AXIS_BUTTON_THRESHOLD

    private fun sourceKey(s: InputSource.Axis): Long = (s.axis.toLong() shl 8) or (s.direction + 1).toLong()

    /** Deadzone (radial or axial) + inverse deadzone + response curve. Public for tests/UI plot. */
    fun tuneAnalog(x: Float, y: Float, p: MappingProfile): Pair<Float, Float> {
        if (p.axial) {
            return tuneAxis(x, p) to tuneAxis(y, p)
        }
        val mag = hypot(x, y)
        if (mag < p.deadzone) return 0f to 0f
        var out = ((mag - p.deadzone) / (1f - p.deadzone)).coerceIn(0f, 1f)
        out = out.pow(p.curve)
        out = p.inverseDeadzone + out * (1f - p.inverseDeadzone)
        val scale = out / mag
        return (x * scale).coerceIn(-1f, 1f) to (y * scale).coerceIn(-1f, 1f)
    }

    private fun tuneAxis(v: Float, p: MappingProfile): Float {
        val a = abs(v)
        if (a < p.deadzone) return 0f
        var out = ((a - p.deadzone) / (1f - p.deadzone)).coerceIn(0f, 1f)
        out = out.pow(p.curve)
        out = p.inverseDeadzone + out * (1f - p.inverseDeadzone)
        return if (v < 0) -out else out
    }

    companion object {
        private const val AXIS_BUTTON_THRESHOLD = 0.5f
    }
}
