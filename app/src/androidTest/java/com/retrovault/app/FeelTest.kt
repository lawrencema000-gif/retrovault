package com.retrovault.app

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.emulator.LibretroBridge
import com.retrovault.emulator.RetroPad
import com.retrovault.input.InputHub
import com.retrovault.input.TouchOverlayView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * P7 acceptance (feel layer): 8-way d-pad diagonals + hysteresis (no flicker on a rolling
 * thumb), floating auto-centering analog stick with deadzone rescale, and the gliding
 * (keep-first-pressed) option — all verified against the native snapshot.
 */
@RunWith(AndroidJUnit4::class)
class FeelTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun makeOverlay(hub: InputHub, config: TouchOverlayView.() -> Unit = {}): TouchOverlayView {
        lateinit var view: TouchOverlayView
        instrumentation.runOnMainSync {
            view = TouchOverlayView(instrumentation.targetContext, hub, haptics = null)
            view.config()
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1600, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 1600, 720)
        }
        return view
    }

    private fun props(id: Int) = MotionEvent.PointerProperties().apply {
        this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER
    }

    private fun coords(x: Float, y: Float) = MotionEvent.PointerCoords().apply {
        this.x = x; this.y = y; pressure = 1f; size = 1f
    }

    private fun dispatch(view: TouchOverlayView, action: Int, points: List<Pair<Float, Float>>) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(
            now, now, action, points.size,
            Array(points.size) { props(it) },
            Array(points.size) { coords(points[it].first, points[it].second) },
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        instrumentation.runOnMainSync { view.dispatchTouchEvent(event) }
        event.recycle()
    }

    private fun cancelAll(view: TouchOverlayView, at: Pair<Float, Float>) {
        dispatch(view, MotionEvent.ACTION_CANCEL, listOf(at))
    }

    @Test
    fun dpadDiagonalRegisters() {
        val hub = InputHub()
        val view = makeOverlay(hub)
        val (cx, cy) = view.dpadCenter()
        val upRight = view.controlCenter(RetroPad.UP or RetroPad.RIGHT)!!

        dispatch(view, MotionEvent.ACTION_DOWN, listOf(upRight))
        assertEquals(RetroPad.UP or RetroPad.RIGHT, LibretroBridge.nativeDebugButtons())

        // 4-way mode collapses diagonals to the nearest cardinal.
        cancelAll(view, upRight)
        instrumentation.runOnMainSync { view.disableDiagonals = true }
        dispatch(view, MotionEvent.ACTION_DOWN, listOf(upRight))
        val v = LibretroBridge.nativeDebugButtons()
        assertTrue("4-way should be a single cardinal, got $v", v == RetroPad.UP || v == RetroPad.RIGHT)
        cancelAll(view, upRight)
        assertEquals(0, LibretroBridge.nativeDebugButtons())
        // silence unused warnings
        assertTrue(cx > 0 && cy > 0)
    }

    @Test
    fun dpadHysteresisPreventsFlicker() {
        val hub = InputHub()
        val view = makeOverlay(hub)
        val up = view.controlCenter(RetroPad.UP)!!
        val (cx, cy) = view.dpadCenter()
        val r = up.second.let { cy - it } // radius used by controlCenter

        dispatch(view, MotionEvent.ACTION_DOWN, listOf(up))
        assertEquals(RetroPad.UP, LibretroBridge.nativeDebugButtons())

        // Rotate 24° off vertical: just past the 22.5° sector edge but inside the
        // hysteresis band (edge + 6°) — direction must NOT change.
        val a1 = Math.toRadians((90 - 24).toDouble())
        val p1 = (cx + (Math.cos(a1) * r).toFloat()) to (cy - (Math.sin(a1) * r).toFloat())
        dispatch(view, MotionEvent.ACTION_MOVE, listOf(p1))
        assertEquals("hysteresis should hold UP", RetroPad.UP, LibretroBridge.nativeDebugButtons())

        // Rotate 45° (diagonal center) — clearly outside the band: now UP|RIGHT.
        val a2 = Math.toRadians(45.0)
        val p2 = (cx + (Math.cos(a2) * r).toFloat()) to (cy - (Math.sin(a2) * r).toFloat())
        dispatch(view, MotionEvent.ACTION_MOVE, listOf(p2))
        assertEquals(RetroPad.UP or RetroPad.RIGHT, LibretroBridge.nativeDebugButtons())

        cancelAll(view, p2)
    }

    @Test
    fun floatingStickAnchorsAndRescalesDeadzone() {
        val hub = InputHub()
        val view = makeOverlay(hub)
        val (sx, sy) = view.stickCenter()
        // Anchor NOT at the zone center — floating base recenters at touch-down.
        val ox = sx - 20f
        val oy = sy + 15f
        dispatch(view, MotionEvent.ACTION_DOWN, listOf(ox to oy))
        assertEquals("no deflection at anchor", 0 to 0, view.currentAnalog())

        // Full deflection right from the ANCHOR (not the zone center).
        dispatch(view, MotionEvent.ACTION_MOVE, listOf((ox + 500f) to oy))
        val (ax, ay) = view.currentAnalog()
        assertTrue("expected strong +X, got $ax", ax > 30000)
        assertTrue("expected ~0 Y, got $ay", abs(ay) < 2500)
        assertTrue("hub should carry touch analog", hub.analogX > 30000)

        // Release: auto-centers to zero.
        dispatch(view, MotionEvent.ACTION_UP, listOf((ox + 500f) to oy))
        assertEquals(0 to 0, view.currentAnalog())
        assertEquals(0, hub.analogX)
    }

    @Test
    fun glidingKeepsFirstPressedButton() {
        val hub = InputHub()
        val view = makeOverlay(hub) { gliding = true }
        val cross = view.controlCenter(RetroPad.B)!!
        val circle = view.controlCenter(RetroPad.A)!!

        dispatch(view, MotionEvent.ACTION_DOWN, listOf(cross))
        assertEquals(RetroPad.B, LibretroBridge.nativeDebugButtons())

        // Slide onto Circle: gliding keeps Cross held (PPSSPP bTouchGliding behavior).
        dispatch(view, MotionEvent.ACTION_MOVE, listOf(circle))
        assertEquals("gliding should keep the first button", RetroPad.B, LibretroBridge.nativeDebugButtons())

        dispatch(view, MotionEvent.ACTION_UP, listOf(circle))
        assertEquals(0, LibretroBridge.nativeDebugButtons())
    }
}
