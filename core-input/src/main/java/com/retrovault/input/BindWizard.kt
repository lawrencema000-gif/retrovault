package com.retrovault.input

import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Press-to-bind engine: walk a list of targets, capture the next physical control the user
 * activates for each. Pure state machine — the Compose screen just renders [currentTarget]
 * and feeds it raw events. Multi-bind: run the wizard again and merge, or bind the same
 * source to several targets across steps.
 */
class BindWizard(
    private val targets: List<Step>,
    /** Axis must cross this to register as a bind press (generous: triggers rest at 0). */
    private val axisThreshold: Float = 0.6f,
) {
    data class Step(val label: String, val target: InputTarget)

    private var index = 0
    private val captured = ArrayList<MappingProfile.Binding>()

    /** Axis values seen at wizard start — treated as resting positions, not presses. */
    private val restingAxis = HashMap<Int, Float>()

    val isDone: Boolean get() = index >= targets.size
    val currentStep: Step? get() = targets.getOrNull(index)
    val bindings: List<MappingProfile.Binding> get() = captured.toList()

    /** Feed a key event; returns true if it bound the current step. */
    fun onKey(event: KeyEvent): Boolean {
        if (isDone || event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        capture(InputSource.Key(event.keyCode))
        return true
    }

    /** Feed a joystick motion event; returns true if an axis deflection bound the step. */
    fun onMotion(event: MotionEvent): Boolean {
        if (isDone) return false
        val device = event.device ?: return false
        for (range in device.motionRanges) {
            val axis = range.axis
            val v = event.getAxisValue(axis)
            val rest = restingAxis.getOrPut(axis) { v }
            if (abs(v - rest) >= axisThreshold) {
                val dir = if (v > rest) +1 else -1
                capture(InputSource.Axis(axis, dir))
                return true
            }
        }
        return false
    }

    /** Skip the current step (leave the target unbound / keep existing). */
    fun skip() {
        if (!isDone) index++
    }

    private fun capture(source: InputSource) {
        val step = targets[index]
        captured += MappingProfile.Binding(source, step.target)
        index++
    }

    /** The finished bindings merged over [base] (rebound targets replace old sources). */
    fun mergedProfile(base: MappingProfile, name: String = base.name): MappingProfile {
        val reboundTargets = captured.map { it.target }.toSet()
        val kept = base.bindings.filter { it.target !in reboundTargets }
        return base.copy(name = name, bindings = kept + captured)
    }
}
