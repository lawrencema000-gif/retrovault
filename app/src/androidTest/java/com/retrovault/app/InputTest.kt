package com.retrovault.app

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.emulator.LibretroBridge
import com.retrovault.emulator.RetroPad
import com.retrovault.input.GamepadMapper
import com.retrovault.input.InputHub
import com.retrovault.input.TouchOverlayView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P4 acceptance: simultaneous d-pad + two face buttons + shoulder held with no drops;
 * slide-between releases/presses across buttons; gamepad events land in the same native
 * snapshot (verified via nativeDebugButtons — the value the core samples).
 */
@RunWith(AndroidJUnit4::class)
class InputTest {

    private fun makeOverlay(hub: InputHub): TouchOverlayView {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var view: TouchOverlayView
        instrumentation.runOnMainSync {
            view = TouchOverlayView(instrumentation.targetContext, hub)
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1600, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 1600, 720)
        }
        return view
    }

    private fun props(id: Int) = MotionEvent.PointerProperties().apply {
        this.id = id
        toolType = MotionEvent.TOOL_TYPE_FINGER
    }

    private fun coords(x: Float, y: Float) = MotionEvent.PointerCoords().apply {
        this.x = x
        this.y = y
        pressure = 1f
        size = 1f
    }

    private fun dispatch(view: TouchOverlayView, action: Int, points: List<Pair<Float, Float>>) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val now = android.os.SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(
            now, now, action,
            points.size,
            Array(points.size) { props(it) },
            Array(points.size) { coords(points[it].first, points[it].second) },
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        instrumentation.runOnMainSync { view.dispatchTouchEvent(event) }
        event.recycle()
    }

    @Test
    fun multitouchHoldsFourControlsAndSlidesBetween() {
        assertTrue("native host missing", LibretroBridge.available)
        val hub = InputHub()
        val view = makeOverlay(hub)

        val up = view.controlCenter(RetroPad.UP)!!
        val cross = view.controlCenter(RetroPad.B)!!
        val triangle = view.controlCenter(RetroPad.X)!!
        val shoulderR = view.controlCenter(RetroPad.R)!!
        val circle = view.controlCenter(RetroPad.A)!!

        // Press four controls simultaneously: d-pad UP + Cross + Triangle + R shoulder.
        dispatch(view, MotionEvent.ACTION_DOWN, listOf(up))
        dispatch(view, pointerDown(1), listOf(up, cross))
        dispatch(view, pointerDown(2), listOf(up, cross, triangle))
        dispatch(view, pointerDown(3), listOf(up, cross, triangle, shoulderR))

        val expected = RetroPad.UP or RetroPad.B or RetroPad.X or RetroPad.R
        assertEquals("4 simultaneous controls", expected, LibretroBridge.nativeDebugButtons())

        // Slide pointer 1 from Cross to Circle: B releases, A presses, others hold.
        dispatch(view, MotionEvent.ACTION_MOVE, listOf(up, circle, triangle, shoulderR))
        val afterSlide = RetroPad.UP or RetroPad.A or RetroPad.X or RetroPad.R
        assertEquals("slide-between", afterSlide, LibretroBridge.nativeDebugButtons())

        // Lift everything.
        dispatch(view, MotionEvent.ACTION_CANCEL, listOf(up, circle, triangle, shoulderR))
        assertEquals("all released", 0, LibretroBridge.nativeDebugButtons())
    }

    private fun pointerDown(index: Int): Int =
        MotionEvent.ACTION_POINTER_DOWN or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    @Test
    fun gamepadButtonsAndAnalogReachSnapshot() {
        assertTrue("native host missing", LibretroBridge.available)
        val hub = InputHub()
        val pad = GamepadMapper(hub)

        fun key(action: Int, code: Int) = KeyEvent(
            android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(),
            action, code, 0, 0, 1, 0, 0, InputDevice.SOURCE_GAMEPAD,
        )

        // Cross (BUTTON_A) + Triangle (BUTTON_Y) + R1 down.
        assertTrue(pad.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)))
        assertTrue(pad.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y)))
        assertTrue(pad.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_R1)))
        assertEquals(
            RetroPad.B or RetroPad.X or RetroPad.R,
            LibretroBridge.nativeDebugButtons(),
        )

        // Release Cross.
        assertTrue(pad.onKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A)))
        assertEquals(RetroPad.X or RetroPad.R, LibretroBridge.nativeDebugButtons())

        // Left stick full-right + hat-down via a joystick MOVE event.
        val pc = MotionEvent.PointerCoords().apply {
            setAxisValue(MotionEvent.AXIS_X, 1f)
            setAxisValue(MotionEvent.AXIS_Y, 0f)
            setAxisValue(MotionEvent.AXIS_HAT_X, 0f)
            setAxisValue(MotionEvent.AXIS_HAT_Y, 1f)
        }
        val now = android.os.SystemClock.uptimeMillis()
        val move = MotionEvent.obtain(
            now, now, MotionEvent.ACTION_MOVE, 1,
            arrayOf(props(0)), arrayOf(pc),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_JOYSTICK, 0,
        )
        assertTrue(pad.onMotionEvent(move))
        move.recycle()

        assertTrue("analog X should be ~full right, got ${hub.analogX}", hub.analogX > 30000)
        assertEquals("hat down adds DOWN", RetroPad.X or RetroPad.R or RetroPad.DOWN, LibretroBridge.nativeDebugButtons())

        // Cleanup.
        hub.clear()
        assertEquals(0, LibretroBridge.nativeDebugButtons())
    }
}
