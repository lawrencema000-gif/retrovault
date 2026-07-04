package com.retrovault.input

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Button haptics (Dolphin PR #13220 model): a crisp CLICK primitive on press and a lighter
 * TICK on release, with an intensity scale. All vibrator calls are posted to a dedicated
 * HandlerThread — binder calls must never run on the touch-dispatch path.
 */
class Haptics(context: Context) {

    var enabled: Boolean = true
    var releaseEnabled: Boolean = true

    /** 0.0–1.0 intensity scale. */
    var intensity: Float = 0.7f

    private val vibrator: Vibrator? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        }
        else -> @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    }

    private val supportsPrimitives: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator?.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
            ) == true

    private val thread = HandlerThread("PulsarHaptics").apply { start() }
    private val handler = Handler(thread.looper)

    fun press() {
        if (!enabled) return
        post(pressEffect())
    }

    fun release() {
        if (!enabled || !releaseEnabled) return
        post(releaseEffect())
    }

    private fun pressEffect(): VibrationEffect =
        if (supportsPrimitives) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity.coerceIn(0.05f, 1f))
                .compose()
        } else {
            oneShot(durationMs = 12, scale = 1f)
        }

    private fun releaseEffect(): VibrationEffect =
        if (supportsPrimitives) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, (intensity * 0.6f).coerceIn(0.05f, 1f))
                .compose()
        } else {
            oneShot(durationMs = 6, scale = 0.5f)
        }

    private fun oneShot(durationMs: Long, scale: Float): VibrationEffect {
        val amp = if (vibrator?.hasAmplitudeControl() == true) {
            (255 * intensity * scale).toInt().coerceIn(1, 255)
        } else {
            VibrationEffect.DEFAULT_AMPLITUDE
        }
        return VibrationEffect.createOneShot(durationMs, amp)
    }

    private fun post(effect: VibrationEffect) {
        val v = vibrator ?: return
        handler.post {
            runCatching { v.vibrate(effect) }
        }
    }

    fun shutdown() {
        thread.quitSafely()
    }
}
