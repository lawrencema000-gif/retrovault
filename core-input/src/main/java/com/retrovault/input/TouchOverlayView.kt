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
import kotlin.math.min

/**
 * The touch input hot path: a plain [View] layered directly over the emulator SurfaceView.
 *
 * - `requestUnbufferedDispatch` so MOVE events arrive as generated, not vsync-batched.
 * - Every event re-hit-tests ALL active pointers against all controls and rebuilds the
 *   button mask from scratch — multi-touch, slide-off and slide-between fall out naturally.
 * - The resulting mask goes straight to [InputHub] → native atomic snapshot. No Compose,
 *   no allocation, no locks on this path.
 *
 * P4 ships a fixed default layout with minimal Canvas visuals; P7 (feel layer) and
 * P8 (layout editor/schema) build on this surface.
 */
@SuppressLint("ViewConstructor")
class TouchOverlayView(
    context: Context,
    private val hub: InputHub,
) : View(context) {

    private data class Control(
        val mask: Int,
        val label: String,
        val rect: RectF = RectF(),
        val round: Boolean = true,
    )

    private val controls = listOf(
        // d-pad (4-way; 8-way zones arrive in P7)
        Control(RetroPad.UP, "▲", round = false),
        Control(RetroPad.DOWN, "▼", round = false),
        Control(RetroPad.LEFT, "◀", round = false),
        Control(RetroPad.RIGHT, "▶", round = false),
        // face buttons (PSP glyphs; RetroPad: B=Cross A=Circle Y=Square X=Triangle)
        Control(RetroPad.X, "△"),
        Control(RetroPad.B, "✕"),
        Control(RetroPad.Y, "□"),
        Control(RetroPad.A, "○"),
        // shoulders + system
        Control(RetroPad.L, "L", round = false),
        Control(RetroPad.R, "R", round = false),
        Control(RetroPad.SELECT, "SELECT", round = false),
        Control(RetroPad.START, "START", round = false),
    )

    private var pressedMask = 0

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(70, 16, 22, 34)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(90, 255, 255, 255)
    }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(120, 42, 127, 255)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 142, 163, 200)
        textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutControls(w.toFloat(), h.toFloat())
    }

    /** Default landscape layout; replaced by the .pulsarlayout schema in P8. */
    private fun layoutControls(w: Float, h: Float) {
        val d = min(w, h)
        val btn = d * 0.13f       // face button diameter
        val dkey = d * 0.115f     // dpad key size
        val margin = d * 0.05f

        // d-pad cluster, bottom-left
        val dcx = margin + dkey * 1.5f
        val dcy = h - margin - dkey * 1.5f
        controls[0].rect.set(dcx - dkey / 2, dcy - dkey * 1.5f, dcx + dkey / 2, dcy - dkey * 0.5f) // UP
        controls[1].rect.set(dcx - dkey / 2, dcy + dkey * 0.5f, dcx + dkey / 2, dcy + dkey * 1.5f) // DOWN
        controls[2].rect.set(dcx - dkey * 1.5f, dcy - dkey / 2, dcx - dkey * 0.5f, dcy + dkey / 2) // LEFT
        controls[3].rect.set(dcx + dkey * 0.5f, dcy - dkey / 2, dcx + dkey * 1.5f, dcy + dkey / 2) // RIGHT

        // face cluster, bottom-right
        val fcx = w - margin - btn * 1.5f
        val fcy = h - margin - btn * 1.5f
        controls[4].rect.set(fcx - btn / 2, fcy - btn * 1.5f, fcx + btn / 2, fcy - btn * 0.5f)     // Triangle
        controls[5].rect.set(fcx - btn / 2, fcy + btn * 0.5f, fcx + btn / 2, fcy + btn * 1.5f)     // Cross
        controls[6].rect.set(fcx - btn * 1.5f, fcy - btn / 2, fcx - btn * 0.5f, fcy + btn / 2)     // Square
        controls[7].rect.set(fcx + btn * 0.5f, fcy - btn / 2, fcx + btn * 1.5f, fcy + btn / 2)     // Circle

        // shoulders at the top corners
        val shW = d * 0.22f
        val shH = d * 0.085f
        controls[8].rect.set(0f, margin, shW, margin + shH)                     // L
        controls[9].rect.set(w - shW, margin, w, margin + shH)                  // R

        // select/start bottom-center
        val pillW = d * 0.17f
        val pillH = d * 0.07f
        controls[10].rect.set(w / 2 - pillW - 8f, h - margin - pillH, w / 2 - 8f, h - margin)
        controls[11].rect.set(w / 2 + 8f, h - margin - pillH, w / 2 + pillW + 8f, h - margin)

        textPaint.textSize = d * 0.045f
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // MOVE events arrive as generated instead of vsync-batched.
            requestUnbufferedDispatch(event)
        }

        var mask = 0
        if (event.actionMasked != MotionEvent.ACTION_UP &&
            event.actionMasked != MotionEvent.ACTION_CANCEL
        ) {
            val liftedIndex =
                if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1
            for (i in 0 until event.pointerCount) {
                if (i == liftedIndex) continue
                val x = event.getX(i)
                val y = event.getY(i)
                for (c in controls) {
                    if (c.rect.contains(x, y)) mask = mask or c.mask
                }
            }
        }

        if (mask != pressedMask) {
            pressedMask = mask
            hub.onTouchState(mask, event.eventTimeNanos())
            invalidate()
        }
        return true
    }

    private fun MotionEvent.eventTimeNanos(): Long = eventTime * 1_000_000L

    /** Center of the control bound to [mask] — used by tests and (later) the layout editor. */
    fun controlCenter(mask: Int): Pair<Float, Float>? =
        controls.firstOrNull { it.mask == mask }?.let { it.rect.centerX() to it.rect.centerY() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (c in controls) {
            val pressed = pressedMask and c.mask != 0
            val paint = if (pressed) pressedPaint else fillPaint
            if (c.round) {
                val r = c.rect.width() / 2
                canvas.drawCircle(c.rect.centerX(), c.rect.centerY(), r, paint)
                canvas.drawCircle(c.rect.centerX(), c.rect.centerY(), r, strokePaint)
            } else {
                canvas.drawRoundRect(c.rect, 14f, 14f, paint)
                canvas.drawRoundRect(c.rect, 14f, 14f, strokePaint)
            }
            val ty = c.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(c.label, c.rect.centerX(), ty, textPaint)
        }
    }
}
