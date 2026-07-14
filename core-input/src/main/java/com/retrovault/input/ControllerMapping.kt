package com.retrovault.input

import android.view.KeyEvent
import android.view.MotionEvent
import com.retrovault.emulator.RetroPad
import kotlinx.serialization.Serializable

/**
 * Where a physical control's input comes from: an Android key code, a motion axis direction,
 * or a hat (d-pad axis) direction.
 */
@Serializable
sealed class InputSource {
    @Serializable
    data class Key(val keyCode: Int) : InputSource()

    /** Axis crossing ±threshold in [direction] (+1 or −1); e.g. right trigger, stick-as-dpad. */
    @Serializable
    data class Axis(val axis: Int, val direction: Int) : InputSource()
}

/** Emulator shortcut actions bindable to pad controls (routed outside the game input path). */
enum class VirtKey { MENU, SAVE_STATE, LOAD_STATE, FAST_FORWARD, SCREENSHOT }

/** What a physical control drives: a RetroPad button, an analog half-axis, or a virtkey. */
@Serializable
sealed class InputTarget {
    @Serializable data class Button(val mask: Int) : InputTarget()

    /** stick: 0 = left stick (PSP's only stick). axis: 0 = X, 1 = Y. direction: +1/−1. */
    @Serializable data class Analog(val stick: Int, val axis: Int, val direction: Int) : InputTarget()

    @Serializable data class Emu(val virtKeyName: String) : InputTarget() {
        val virtKey: VirtKey? get() = runCatching { VirtKey.valueOf(virtKeyName) }.getOrNull()
    }
}

/**
 * A complete controller profile: every binding from physical source to target. Multi-bind is
 * allowed in both directions (two sources → same target; one source → several targets).
 */
@Serializable
data class MappingProfile(
    val name: String,
    val bindings: List<Binding> = emptyList(),
    // analog tuning
    val deadzone: Float = 0.12f,          // radial inner deadzone
    val inverseDeadzone: Float = 0f,      // output floor once past the deadzone (worn sticks)
    val axial: Boolean = false,           // per-axis deadzone instead of radial
    val curve: Float = 1.0f,              // response exponent: 1 = linear, >1 = finer center control
) {
    @Serializable
    data class Binding(val source: InputSource, val target: InputTarget)
}

/** Android-default profile: what a Google-spec pad delivers with no db entry or user remap. */
object DefaultMapping {
    val profile = MappingProfile(
        name = "Android standard",
        bindings = listOf(
            bind(KeyEvent.KEYCODE_BUTTON_A, RetroPad.B),        // Cross
            bind(KeyEvent.KEYCODE_BUTTON_B, RetroPad.A),        // Circle
            bind(KeyEvent.KEYCODE_BUTTON_X, RetroPad.Y),        // Square
            bind(KeyEvent.KEYCODE_BUTTON_Y, RetroPad.X),        // Triangle
            bind(KeyEvent.KEYCODE_BUTTON_L1, RetroPad.L),
            bind(KeyEvent.KEYCODE_BUTTON_R1, RetroPad.R),
            bind(KeyEvent.KEYCODE_BUTTON_START, RetroPad.START),
            bind(KeyEvent.KEYCODE_BUTTON_SELECT, RetroPad.SELECT),
            bind(KeyEvent.KEYCODE_DPAD_UP, RetroPad.UP),
            bind(KeyEvent.KEYCODE_DPAD_DOWN, RetroPad.DOWN),
            bind(KeyEvent.KEYCODE_DPAD_LEFT, RetroPad.LEFT),
            bind(KeyEvent.KEYCODE_DPAD_RIGHT, RetroPad.RIGHT),
            // hat d-pad
            MappingProfile.Binding(InputSource.Axis(MotionEvent.AXIS_HAT_X, -1), InputTarget.Button(RetroPad.LEFT)),
            MappingProfile.Binding(InputSource.Axis(MotionEvent.AXIS_HAT_X, +1), InputTarget.Button(RetroPad.RIGHT)),
            MappingProfile.Binding(InputSource.Axis(MotionEvent.AXIS_HAT_Y, -1), InputTarget.Button(RetroPad.UP)),
            MappingProfile.Binding(InputSource.Axis(MotionEvent.AXIS_HAT_Y, +1), InputTarget.Button(RetroPad.DOWN)),
            // left stick → PSP analog
            MappingProfile.Binding(InputSource.Axis(MotionEvent.AXIS_X, -1), InputTarget.Analog(0, 0, -1)),
            MappingProfile.Binding(InputSource.Axis(MotionEvent.AXIS_X, +1), InputTarget.Analog(0, 0, +1)),
            MappingProfile.Binding(InputSource.Axis(MotionEvent.AXIS_Y, -1), InputTarget.Analog(0, 1, -1)),
            MappingProfile.Binding(InputSource.Axis(MotionEvent.AXIS_Y, +1), InputTarget.Analog(0, 1, +1)),
            // Right stick (Android standard: AXIS_Z / AXIS_RZ) -> RetroPad right analog (P24).
            MappingProfile.Binding(InputSource.Axis(MotionEvent.AXIS_Z, -1), InputTarget.Analog(1, 0, -1)),
            MappingProfile.Binding(InputSource.Axis(MotionEvent.AXIS_Z, +1), InputTarget.Analog(1, 0, +1)),
            MappingProfile.Binding(InputSource.Axis(MotionEvent.AXIS_RZ, -1), InputTarget.Analog(1, 1, -1)),
            MappingProfile.Binding(InputSource.Axis(MotionEvent.AXIS_RZ, +1), InputTarget.Analog(1, 1, +1)),
        ),
    )

    private fun bind(keyCode: Int, mask: Int) =
        MappingProfile.Binding(InputSource.Key(keyCode), InputTarget.Button(mask))
}
