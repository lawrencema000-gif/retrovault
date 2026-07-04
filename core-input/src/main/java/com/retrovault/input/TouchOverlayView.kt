package com.retrovault.input

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.retrovault.emulator.RetroPad
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The touch input hot path + feel layer: a plain [View] layered over the emulator SurfaceView.
 *
 * Feel features (P7):
 * - 8-way d-pad as ONE control: 9 zones classified by angle from the pad center with a
 *   hysteresis band, so a rolling thumb never flickers between directions; 4-way option.
 * - Floating auto-centering analog stick: the base anchors wherever the thumb lands in the
 *   stick zone; deflection = clamp((touch-origin)/radius) with inner deadzone + saturation;
 *   the pointer owns the stick until lift, even wandering outside the zone.
 * - Slide-off/slide-between by default; PPSSPP-style "gliding" (keep first pressed) and
 *   "sticky d-pad" options.
 * - Hitboxes decoupled from visuals: region modifiers (1.0–2.0) inflate d-pad/action hit
 *   areas, and held controls grow their hitbox 1.5× (RetroArch range_mod) so a wandering
 *   thumb doesn't drop a hold.
 * - Haptics: crisp press + lighter release, off the touch thread ([Haptics]).
 * - Pressed-state brighten + global opacity.
 *
 * Never Compose, never allocating on the move path, never blocking: the resulting state goes
 * straight to [InputHub] → the native atomic snapshot.
 */
@SuppressLint("ViewConstructor")
class TouchOverlayView(
    context: Context,
    private val hub: InputHub,
    private val haptics: Haptics? = Haptics(context),
) : View(context) {

    // ---- feel options (wired to Settings in P11) ----
    var overlayOpacity: Float = 0.75f
    var gliding: Boolean = false          // keep-first-pressed while sliding (PPSSPP option)
    var stickyDpad: Boolean = false       // a pointer that starts on the d-pad stays d-pad
    var disableDiagonals: Boolean = false // 4-way d-pad
    var dpadRegionModifier: Float = 1.2f  // hit-area inflation (1.0–2.0)
    var actionRegionModifier: Float = 1.2f
    var growWhenPressed: Float = 1.5f     // hitbox scale while held
    var floatingStick: Boolean = true
    var stickDeadzone: Float = 0.10f
    var dpadHysteresisDeg: Float = 6f

    // ---- controls ----
    private class Button(val mask: Int, val label: String, val round: Boolean, val cluster: Cluster) {
        val visual = RectF()
        fun hitRect(modifier: Float, grow: Boolean, growScale: Float, out: RectF): RectF {
            val scale = modifier * (if (grow) growScale else 1f)
            val dw = visual.width() * (scale - 1f) / 2f
            val dh = visual.height() * (scale - 1f) / 2f
            out.set(visual.left - dw, visual.top - dh, visual.right + dw, visual.bottom + dh)
            return out
        }
    }

    private enum class Cluster { DPAD_AREA, ACTION, SYSTEM }

    private val buttons = listOf(
        Button(RetroPad.X, "△", round = true, Cluster.ACTION),
        Button(RetroPad.B, "✕", round = true, Cluster.ACTION),
        Button(RetroPad.Y, "□", round = true, Cluster.ACTION),
        Button(RetroPad.A, "○", round = true, Cluster.ACTION),
        Button(RetroPad.L, "L", round = false, Cluster.SYSTEM),
        Button(RetroPad.R, "R", round = false, Cluster.SYSTEM),
        Button(RetroPad.SELECT, "SELECT", round = false, Cluster.SYSTEM),
        Button(RetroPad.START, "START", round = false, Cluster.SYSTEM),
    )

    // D-pad: one control. visual = square bounds; zones by angle from center.
    private val dpadVisual = RectF()
    private var dpadCenterX = 0f
    private var dpadCenterY = 0f
    private var dpadRadius = 0f
    private var dpadDeadRadius = 0f

    // Stick zone (resting base position) — floating base anchors on touch-down.
    private val stickZone = RectF()
    private var stickRadius = 0f

    // ---- per-pointer state ----
    private enum class Role { FREE, STICK, DPAD }

    private class PointerState {
        var role = Role.FREE
        var heldMask = 0          // buttons contributed by this pointer (gliding keeps this)
        var dpadMask = 0          // current d-pad direction bits from this pointer
        var stickOriginX = 0f
        var stickOriginY = 0f
        fun reset() {
            role = Role.FREE; heldMask = 0; dpadMask = 0
        }
    }

    private val pointerStates = Array(MAX_POINTERS) { PointerState() }

    private var pressedMask = 0
    private var analogX = 0
    private var analogY = 0
    private var stickThumbX = 0f
    private var stickThumbY = 0f
    private var stickActive = false

    private val tmpRect = RectF()

    // ---- paints ----
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutControls(w.toFloat(), h.toFloat())
    }

    /** PSP landscape default: d-pad upper-left, stick lower-left, face right, L/R corners. */
    private fun layoutControls(w: Float, h: Float) {
        val d = min(w, h)
        val margin = d * 0.045f
        val bottomSafe = d * 0.045f // keep off the gesture area

        // d-pad (one square) upper-left of the left cluster
        val dpadSize = d * 0.34f
        dpadVisual.set(
            margin, h - bottomSafe - dpadSize * 1.9f,
            margin + dpadSize, h - bottomSafe - dpadSize * 0.9f
        )
        dpadCenterX = dpadVisual.centerX()
        dpadCenterY = dpadVisual.centerY()
        dpadRadius = dpadSize / 2f
        dpadDeadRadius = dpadRadius * 0.28f

        // stick zone lower-left (PSP single analog below the d-pad)
        stickRadius = d * 0.115f
        val sx = margin + dpadSize * 0.5f
        val sy = h - bottomSafe - stickRadius
        stickZone.set(sx - stickRadius * 1.4f, sy - stickRadius * 1.4f, sx + stickRadius * 1.4f, sy + stickRadius * 1.4f)
        if (!stickActive) {
            stickThumbX = stickZone.centerX()
            stickThumbY = stickZone.centerY()
        }

        // face cluster, bottom-right
        val btn = d * 0.135f
        val fcx = w - margin - btn * 1.6f
        val fcy = h - bottomSafe - btn * 1.7f
        buttons[0].visual.set(fcx - btn / 2, fcy - btn * 1.5f, fcx + btn / 2, fcy - btn * 0.5f) // △
        buttons[1].visual.set(fcx - btn / 2, fcy + btn * 0.5f, fcx + btn / 2, fcy + btn * 1.5f) // ✕
        buttons[2].visual.set(fcx - btn * 1.5f, fcy - btn / 2, fcx - btn * 0.5f, fcy + btn / 2) // □
        buttons[3].visual.set(fcx + btn * 0.5f, fcy - btn / 2, fcx + btn * 1.5f, fcy + btn / 2) // ○

        // shoulders
        val shW = d * 0.24f
        val shH = d * 0.09f
        buttons[4].visual.set(0f, margin, shW, margin + shH)          // L
        buttons[5].visual.set(w - shW, margin, w, margin + shH)       // R

        // select/start bottom-center
        val pillW = d * 0.17f
        val pillH = d * 0.07f
        buttons[6].visual.set(w / 2 - pillW - 8f, h - bottomSafe - pillH, w / 2 - 8f, h - bottomSafe)
        buttons[7].visual.set(w / 2 + 8f, h - bottomSafe - pillH, w / 2 + pillW + 8f, h - bottomSafe)

        textPaint.textSize = d * 0.045f
    }

    // ---------------------------------------------------------------- input

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(event)
                onPointerDown(event, event.actionIndex)
            }
            MotionEvent.ACTION_POINTER_DOWN -> onPointerDown(event, event.actionIndex)
            MotionEvent.ACTION_POINTER_UP -> onPointerUp(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                for (s in pointerStates) s.reset()
                stickActive = false
            }
        }

        recomputeState(event)
        return true
    }

    private fun onPointerDown(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        if (id >= MAX_POINTERS) return
        val st = pointerStates[id]
        st.reset()
        val x = event.getX(index)
        val y = event.getY(index)

        // Stick claims a pointer only at touch-down inside its zone (floating anchor there).
        if (!stickActive && stickZone.contains(x, y)) {
            st.role = Role.STICK
            st.stickOriginX = if (floatingStick) x else stickZone.centerX()
            st.stickOriginY = if (floatingStick) y else stickZone.centerY()
            stickActive = true
            return
        }
        if (stickyDpad && dpadHit(x, y)) {
            st.role = Role.DPAD
        }
    }

    private fun onPointerUp(id: Int) {
        if (id >= MAX_POINTERS) return
        if (pointerStates[id].role == Role.STICK) stickActive = false
        pointerStates[id].reset()
    }

    /** Rebuild buttons/analog from ALL active pointers (the lifted one excluded by caller). */
    private fun recomputeState(event: MotionEvent) {
        var mask = 0
        var newAnalogX = 0
        var newAnalogY = 0
        var newThumbX = stickZone.centerX()
        var newThumbY = stickZone.centerY()
        var anyStick = false

        val terminal = event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        val liftedIndex =
            if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1

        if (!terminal) {
            for (i in 0 until event.pointerCount) {
                if (i == liftedIndex) continue
                val id = event.getPointerId(i)
                if (id >= MAX_POINTERS) continue
                val st = pointerStates[id]
                val x = event.getX(i)
                val y = event.getY(i)

                when (st.role) {
                    Role.STICK -> {
                        var dx = (x - st.stickOriginX) / stickRadius
                        var dy = (y - st.stickOriginY) / stickRadius
                        val mag = hypot(dx, dy)
                        if (mag > 1f) { dx /= mag; dy /= mag }
                        val m2 = hypot(dx, dy)
                        if (m2 < stickDeadzone) { dx = 0f; dy = 0f }
                        else {
                            val rescale = (m2 - stickDeadzone) / (1f - stickDeadzone) / m2
                            dx *= rescale; dy *= rescale
                        }
                        newAnalogX = (dx * 32767f).toInt().coerceIn(-32767, 32767)
                        newAnalogY = (dy * 32767f).toInt().coerceIn(-32767, 32767)
                        newThumbX = st.stickOriginX + dx * stickRadius
                        newThumbY = st.stickOriginY + dy * stickRadius
                        anyStick = true
                    }

                    Role.DPAD -> {
                        st.dpadMask = classifyDpad(x, y, st.dpadMask)
                        mask = mask or st.dpadMask
                    }

                    Role.FREE -> {
                        var contributed = 0
                        // d-pad zone (also reachable by slide unless gliding holds a button)
                        if ((!gliding || st.heldMask == 0) && dpadHit(x, y)) {
                            st.dpadMask = classifyDpad(x, y, st.dpadMask)
                            contributed = contributed or st.dpadMask
                        } else {
                            st.dpadMask = 0
                        }
                        // buttons
                        if (gliding && st.heldMask != 0) {
                            contributed = contributed or st.heldMask
                        } else {
                            var hit = 0
                            for (b in buttons) {
                                val mod = if (b.cluster == Cluster.ACTION) actionRegionModifier else 1.05f
                                val grow = pressedMask and b.mask != 0
                                if (b.hitRect(mod, grow, growWhenPressed, tmpRect).contains(x, y)) {
                                    hit = hit or b.mask
                                }
                            }
                            st.heldMask = hit
                            contributed = contributed or hit
                        }
                        mask = mask or contributed
                    }
                }
            }
        }

        if (!anyStick) {
            newAnalogX = 0; newAnalogY = 0
            newThumbX = stickZone.centerX(); newThumbY = stickZone.centerY()
        }

        val t = event.eventTime * 1_000_000L
        if (mask != pressedMask) {
            // edge-triggered haptics
            val pressedNow = mask and pressedMask.inv()
            val releasedNow = pressedMask and mask.inv()
            if (pressedNow != 0) haptics?.press()
            if (releasedNow != 0) haptics?.release()
            pressedMask = mask
            hub.onTouchState(mask, t)
            invalidate()
        }
        if (newAnalogX != analogX || newAnalogY != analogY) {
            analogX = newAnalogX
            analogY = newAnalogY
            hub.onTouchAnalog(analogX, analogY, t)
        }
        if (newThumbX != stickThumbX || newThumbY != stickThumbY) {
            stickThumbX = newThumbX
            stickThumbY = newThumbY
            invalidate()
        }
    }

    private fun dpadHit(x: Float, y: Float): Boolean {
        val r = dpadRadius * dpadRegionModifier *
            (if (pressedMask and DPAD_MASK != 0) growWhenPressed else 1f)
        return hypot(x - dpadCenterX, y - dpadCenterY) <= r
    }

    /**
     * Angle-based 8-way (or 4-way) classification with a hysteresis band: the current
     * direction is kept unless the angle leaves its sector by more than [dpadHysteresisDeg].
     */
    private fun classifyDpad(x: Float, y: Float, current: Int): Int {
        val dx = x - dpadCenterX
        val dy = y - dpadCenterY
        val dist = hypot(dx, dy)
        if (dist < dpadDeadRadius) return 0
        if (!dpadHit(x, y)) return if (stickyDpad) current else 0

        var deg = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())).toFloat() // 0=right, CCW
        if (deg < 0) deg += 360f

        val sectorHalf = if (disableDiagonals) 45f else 22.5f
        val candidates = if (disableDiagonals) FOUR_WAY else EIGHT_WAY

        // hysteresis: if the current direction's sector (widened by the band) still contains
        // the angle, keep it.
        if (current != 0) {
            val center = directionAngle(current)
            if (center != null && angularDistance(deg, center) <= sectorHalf + dpadHysteresisDeg) {
                return current
            }
        }
        // otherwise pick the sector whose center is closest
        var best = 0
        var bestDist = Float.MAX_VALUE
        for ((mask, center) in candidates) {
            val ad = angularDistance(deg, center)
            if (ad < bestDist) { bestDist = ad; best = mask }
        }
        return best
    }

    private fun directionAngle(mask: Int): Float? =
        (EIGHT_WAY.firstOrNull { it.first == mask } ?: FOUR_WAY.firstOrNull { it.first == mask })?.second

    private fun angularDistance(a: Float, b: Float): Float {
        var d = Math.abs(a - b) % 360f
        if (d > 180f) d = 360f - d
        return d
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val alpha = (overlayOpacity * 255).toInt().coerceIn(0, 255)
        fillPaint.color = Color.argb((alpha * 0.35f).toInt(), 16, 22, 34)
        strokePaint.color = Color.argb((alpha * 0.45f).toInt(), 255, 255, 255)
        pressedPaint.color = Color.argb((alpha * 0.65f).toInt(), 42, 127, 255)
        textPaint.color = Color.argb(alpha, 142, 163, 200)

        drawDpad(canvas)
        drawStick(canvas)
        for (b in buttons) {
            val pressed = pressedMask and b.mask != 0
            val paint = if (pressed) pressedPaint else fillPaint
            if (b.round) {
                val r = b.visual.width() / 2
                canvas.drawCircle(b.visual.centerX(), b.visual.centerY(), r, paint)
                canvas.drawCircle(b.visual.centerX(), b.visual.centerY(), r, strokePaint)
            } else {
                canvas.drawRoundRect(b.visual, 14f, 14f, paint)
                canvas.drawRoundRect(b.visual, 14f, 14f, strokePaint)
            }
            val ty = b.visual.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(b.label, b.visual.centerX(), ty, textPaint)
        }
    }

    private fun drawDpad(canvas: Canvas) {
        val cx = dpadCenterX
        val cy = dpadCenterY
        val arm = dpadRadius
        val thick = arm * 0.62f
        // cross
        tmpRect.set(cx - arm, cy - thick / 2, cx + arm, cy + thick / 2)
        canvas.drawRoundRect(tmpRect, 12f, 12f, fillPaint)
        canvas.drawRoundRect(tmpRect, 12f, 12f, strokePaint)
        tmpRect.set(cx - thick / 2, cy - arm, cx + thick / 2, cy + arm)
        canvas.drawRoundRect(tmpRect, 12f, 12f, fillPaint)
        canvas.drawRoundRect(tmpRect, 12f, 12f, strokePaint)

        // active-direction highlight
        val active = pressedMask and DPAD_MASK
        if (active != 0) {
            directionAngle(active)?.let { deg ->
                val rad = Math.toRadians(deg.toDouble())
                val hx = cx + (cos(rad) * arm * 0.55f).toFloat()
                val hy = cy - (sin(rad) * arm * 0.55f).toFloat()
                canvas.drawCircle(hx, hy, thick * 0.34f, pressedPaint)
            }
        }
    }

    private fun drawStick(canvas: Canvas) {
        val baseX: Float
        val baseY: Float
        if (stickActive) {
            val owner = pointerStates.firstOrNull { it.role == Role.STICK }
            baseX = owner?.stickOriginX ?: stickZone.centerX()
            baseY = owner?.stickOriginY ?: stickZone.centerY()
        } else {
            baseX = stickZone.centerX()
            baseY = stickZone.centerY()
        }
        canvas.drawCircle(baseX, baseY, stickRadius, fillPaint)
        canvas.drawCircle(baseX, baseY, stickRadius, strokePaint)
        val headPaint = if (stickActive) pressedPaint else fillPaint
        canvas.drawCircle(stickThumbX, stickThumbY, stickRadius * 0.48f, headPaint)
        canvas.drawCircle(stickThumbX, stickThumbY, stickRadius * 0.48f, strokePaint)
    }

    // ---------------------------------------------------------------- test hooks

    /** Center of the control bound to [mask] — d-pad directions map to their zone centers. */
    fun controlCenter(mask: Int): Pair<Float, Float>? {
        buttons.firstOrNull { it.mask == mask }?.let { return it.visual.centerX() to it.visual.centerY() }
        directionAngle(mask)?.let { deg ->
            val rad = Math.toRadians(deg.toDouble())
            val r = (dpadDeadRadius + dpadRadius) / 2f
            return (dpadCenterX + (cos(rad) * r).toFloat()) to (dpadCenterY - (sin(rad) * r).toFloat())
        }
        return null
    }

    fun stickCenter(): Pair<Float, Float> = stickZone.centerX() to stickZone.centerY()
    fun dpadCenter(): Pair<Float, Float> = dpadCenterX to dpadCenterY
    fun currentAnalog(): Pair<Int, Int> = analogX to analogY

    companion object {
        private const val MAX_POINTERS = 16
        private const val DPAD_MASK =
            RetroPad.UP or RetroPad.DOWN or RetroPad.LEFT or RetroPad.RIGHT

        // (mask, angle°) — 0° = right, CCW positive, screen-Y inverted in classify.
        private val EIGHT_WAY = listOf(
            RetroPad.RIGHT to 0f,
            (RetroPad.RIGHT or RetroPad.UP) to 45f,
            RetroPad.UP to 90f,
            (RetroPad.UP or RetroPad.LEFT) to 135f,
            RetroPad.LEFT to 180f,
            (RetroPad.LEFT or RetroPad.DOWN) to 225f,
            RetroPad.DOWN to 270f,
            (RetroPad.DOWN or RetroPad.RIGHT) to 315f,
        )
        private val FOUR_WAY = listOf(
            RetroPad.RIGHT to 0f,
            RetroPad.UP to 90f,
            RetroPad.LEFT to 180f,
            RetroPad.DOWN to 270f,
        )
    }
}
