package com.retrovault.app

import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.emulator.RetroPad
import com.retrovault.input.InputHub
import com.retrovault.input.PulsarSkin
import com.retrovault.input.SkinControl
import com.retrovault.input.SkinCustomButton
import com.retrovault.input.SkinStore
import com.retrovault.input.TouchOverlayView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * P27 acceptance: a `.pulsarskin` round-trips (export → import → identical layout), and a custom
 * COMBO button presses multiple RetroPad buttons at once (with toggle mode latching). Runs the
 * real TouchOverlayView laid out offscreen with synthesized MotionEvents — the same state machine
 * the game sees.
 */
@RunWith(AndroidJUnit4::class)
class SkinTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = instrumentation.targetContext

    private fun layoutView(skin: PulsarSkin? = null): Pair<TouchOverlayView, InputHub> {
        val hub = InputHub()
        lateinit var view: TouchOverlayView
        instrumentation.runOnMainSync {
            view = TouchOverlayView(ctx, hub, haptics = null)
            view.skin = skin
            view.measure(
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 1920, 1080)
        }
        return view to hub
    }

    private fun tap(view: TouchOverlayView, x: Float, y: Float, action: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(now, now, action, x, y, 0)
        instrumentation.runOnMainSync { view.dispatchTouchEvent(ev) }
        ev.recycle()
    }

    // ---- acceptance 1: the skin round-trips ----
    @Test
    fun skinRoundTripsThroughFileAndStore() {
        val original = PulsarSkin(
            name = "RoundTrip Test",
            opacity = 0.6f,
            controls = mapOf(
                "dpad" to SkinControl(cx = 0.15f, cy = 0.6f, scale = 1.2f),
                "face" to SkinControl(cx = 0.85f, cy = 0.7f, scale = 0.9f),
                "l" to SkinControl(cx = 0.1f, cy = 0.1f, scale = 1f, visible = false),
            ),
            customButtons = listOf(
                SkinCustomButton("◎", cx = 0.5f, cy = 0.85f, buttons = listOf("A", "B"), mode = "press"),
                SkinCustomButton("T", cx = 0.6f, cy = 0.85f, buttons = listOf("X"), mode = "turbo"),
            ),
        )
        // File round-trip (.pulsarskin zip).
        val f = File(ctx.cacheDir, "roundtrip.pulsarskin")
        assertTrue(PulsarSkin.writeFile(original, f))
        val readBack = PulsarSkin.readFile(f)
        assertEquals(original, readBack)
        f.delete()

        // Store install/activate round-trip.
        val store = SkinStore(ctx)
        val name = store.install(original)
        assertNotNull(name)
        store.activeSkinName = name!!
        assertEquals(original, store.activeSkin())
        store.delete(name)
        assertEquals("", store.activeSkinName)

        // Layout round-trip: apply → export from the live view → the override positions survive.
        val (view, _) = layoutView(original)
        val exported = view.currentSkin("exported")
        val dpad = exported.controls["dpad"]!!
        assertEquals(0.15f, dpad.cx, 0.02f)
        assertEquals(0.6f, dpad.cy, 0.02f)
        assertEquals(1.2f, dpad.scale, 0.05f)

        // Junk rejection.
        assertNull(PulsarSkin.parse("{\"format\":\"other\"}"))
        assertNull(PulsarSkin.parse("not json"))
        assertNull(
            PulsarSkin.parse(
                PulsarSkin.toJson(original.copy(controls = mapOf("bogus" to SkinControl(0.5f, 0.5f))))
            )
        )
    }

    // ---- acceptance 2: a combo button presses BOTH its buttons at once ----
    @Test
    fun comboButtonPressesBothButtonsAndToggleLatches() {
        val skin = PulsarSkin(
            name = "combo",
            customButtons = listOf(
                SkinCustomButton("◎", cx = 0.5f, cy = 0.5f, buttons = listOf("A", "B"), mode = "press"),
                SkinCustomButton("L", cx = 0.3f, cy = 0.5f, buttons = listOf("R"), mode = "toggle"),
            ),
        )
        val (view, hub) = layoutView(skin)

        // COMBO: touching the button must press A AND B together.
        val (cx, cy) = view.customButtonCenter("◎")!!
        tap(view, cx, cy, MotionEvent.ACTION_DOWN)
        val comboMask = RetroPad.A or RetroPad.B
        assertEquals("combo must press both buttons", comboMask, hub.combinedButtons and comboMask)
        tap(view, cx, cy, MotionEvent.ACTION_UP)
        assertEquals("combo must release both buttons", 0, hub.combinedButtons and comboMask)

        // TOGGLE: tap latches, second tap clears.
        val (tx, ty) = view.customButtonCenter("L")!!
        tap(view, tx, ty, MotionEvent.ACTION_DOWN)
        tap(view, tx, ty, MotionEvent.ACTION_UP)
        assertEquals("toggle must stay latched after release", RetroPad.R, hub.combinedButtons and RetroPad.R)
        tap(view, tx, ty, MotionEvent.ACTION_DOWN)
        tap(view, tx, ty, MotionEvent.ACTION_UP)
        assertEquals("second tap must unlatch", 0, hub.combinedButtons and RetroPad.R)
    }
}
