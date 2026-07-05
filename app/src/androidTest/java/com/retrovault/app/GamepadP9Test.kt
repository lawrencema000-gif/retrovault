package com.retrovault.app

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.emulator.LibretroBridge
import com.retrovault.emulator.RetroPad
import com.retrovault.input.BindWizard
import com.retrovault.input.ControllerDb
import com.retrovault.input.DefaultMapping
import com.retrovault.input.GamepadMapper
import com.retrovault.input.InputHub
import com.retrovault.input.InputSource
import com.retrovault.input.InputTarget
import com.retrovault.input.MappingProfile
import com.retrovault.input.RemapStore
import com.retrovault.input.VirtKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * P9 acceptance (emulator portion): gamecontrollerdb parse/match/translate, per-device remap
 * persistence (the "remapped clone persists" criterion), press-to-bind wizard, analog tuning
 * math, virtkey routing, and native pause (the auto-pause primitive for hotplug).
 * Physical Xbox/DS instant-recognition is validated in the P5 device session.
 */
@RunWith(AndroidJUnit4::class)
class GamepadP9Test {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    // DS4-shaped Android row (vid 054c pid 09cc embedded little-endian in the GUID).
    private val ds4Line = "050000004c050000cc09000000000000,PS4 Controller," +
        "a:b0,b:b1,x:b2,y:b3,back:b4,start:b6,leftshoulder:b9,rightshoulder:b10," +
        "dpup:h0.1,dpright:h0.2,dpdown:h0.4,dpleft:h0.8,leftx:a0,lefty:a1~,platform:Android"

    @Test
    fun dbLineParsesAndTranslates() {
        val entry = ControllerDb.parseLine(ds4Line)
        assertNotNull(entry)
        assertEquals(0x054c, entry!!.vendorId)
        assertEquals(0x09cc, entry.productId)
        assertEquals("PS4 Controller", entry.name)

        val profile = ControllerDb.toProfile(entry, device = null) // axes fall back to X,Y,Z,RZ
        val byPair = profile.bindings.associateBy { it.source to it.target }

        fun has(source: InputSource, target: InputTarget) =
            assertTrue("missing $source -> $target", (source to target) in byPair)

        // SDL b0 (a) = Cross = RetroPad.B; b3 (y) = Triangle = RetroPad.X
        has(InputSource.Key(KeyEvent.KEYCODE_BUTTON_A), InputTarget.Button(RetroPad.B))
        has(InputSource.Key(KeyEvent.KEYCODE_BUTTON_Y), InputTarget.Button(RetroPad.X))
        has(InputSource.Key(KeyEvent.KEYCODE_BUTTON_SELECT), InputTarget.Button(RetroPad.SELECT))
        // hats
        has(InputSource.Axis(MotionEvent.AXIS_HAT_Y, -1), InputTarget.Button(RetroPad.UP))
        has(InputSource.Axis(MotionEvent.AXIS_HAT_X, +1), InputTarget.Button(RetroPad.RIGHT))
        // leftx:a0 → identity halves on AXIS_X
        has(InputSource.Axis(MotionEvent.AXIS_X, -1), InputTarget.Analog(0, 0, -1))
        has(InputSource.Axis(MotionEvent.AXIS_X, +1), InputTarget.Analog(0, 0, +1))
        // lefty:a1~ → inverted halves on AXIS_Y
        has(InputSource.Axis(MotionEvent.AXIS_Y, -1), InputTarget.Analog(0, 1, +1))
        has(InputSource.Axis(MotionEvent.AXIS_Y, +1), InputTarget.Analog(0, 1, -1))
    }

    @Test
    fun bundledDbLoadsAndroidRows() {
        val entries = ControllerDb.load(ctx)
        assertTrue("expected 100+ Android rows, got ${entries.size}", entries.size > 100)
        // Every row parsed a plausible vid/pid or at least a 32-char guid
        assertTrue(entries.all { it.guid.length == 32 })
    }

    @Test
    fun remappedProfilePersistsAcrossInstances() {
        File(ctx.filesDir, "controller-remaps.json").delete()
        val descriptor = "test-clone-descriptor"
        val custom = MappingProfile(
            name = "Weird clone (fixed)",
            bindings = listOf(
                MappingProfile.Binding(
                    InputSource.Key(KeyEvent.KEYCODE_BUTTON_C), InputTarget.Button(RetroPad.B)
                ),
                MappingProfile.Binding(
                    InputSource.Key(KeyEvent.KEYCODE_BUTTON_Z), InputTarget.Emu(VirtKey.MENU.name)
                ),
            ),
            deadzone = 0.2f,
            curve = 1.5f,
        )
        RemapStore(ctx).put(descriptor, custom)

        // Fresh instance = fresh process-start simulation: profile must round-trip fully.
        val reloaded = RemapStore(ctx).get(descriptor)
        assertEquals(custom, reloaded)

        RemapStore(ctx).remove(descriptor)
        assertNull(RemapStore(ctx).get(descriptor))
    }

    @Test
    fun bindWizardCapturesAndMerges() {
        val wizard = BindWizard(
            listOf(
                BindWizard.Step("Cross", InputTarget.Button(RetroPad.B)),
                BindWizard.Step("Menu", InputTarget.Emu(VirtKey.MENU.name)),
                BindWizard.Step("L", InputTarget.Button(RetroPad.L)),
            )
        )
        fun key(code: Int) = KeyEvent(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
            KeyEvent.ACTION_DOWN, code, 0, 0, 1, 0, 0, InputDevice.SOURCE_GAMEPAD,
        )

        assertEquals("Cross", wizard.currentStep!!.label)
        assertTrue(wizard.onKey(key(KeyEvent.KEYCODE_BUTTON_C)))   // odd clone: C = Cross
        assertEquals("Menu", wizard.currentStep!!.label)
        assertTrue(wizard.onKey(key(KeyEvent.KEYCODE_BUTTON_MODE)))
        wizard.skip()                                               // leave L as-is
        assertTrue(wizard.isDone)

        val merged = wizard.mergedProfile(DefaultMapping.profile, name = "clone")
        // Rebound: Cross now comes from BUTTON_C (old BUTTON_A binding replaced).
        assertTrue(merged.bindings.any {
            it.source == InputSource.Key(KeyEvent.KEYCODE_BUTTON_C) &&
                it.target == InputTarget.Button(RetroPad.B)
        })
        assertTrue(merged.bindings.none {
            it.source == InputSource.Key(KeyEvent.KEYCODE_BUTTON_A) &&
                it.target == InputTarget.Button(RetroPad.B)
        })
        // Untouched targets keep their defaults (L skipped).
        assertTrue(merged.bindings.any {
            it.source == InputSource.Key(KeyEvent.KEYCODE_BUTTON_L1) &&
                it.target == InputTarget.Button(RetroPad.L)
        })
    }

    @Test
    fun mapperAppliesCustomProfileAndVirtkeys() {
        val hub = InputHub()
        val virtPresses = ArrayList<VirtKey>()
        val profile = MappingProfile(
            name = "custom",
            bindings = listOf(
                MappingProfile.Binding(InputSource.Key(KeyEvent.KEYCODE_BUTTON_C), InputTarget.Button(RetroPad.B)),
                MappingProfile.Binding(InputSource.Key(KeyEvent.KEYCODE_BUTTON_Z), InputTarget.Emu(VirtKey.SAVE_STATE.name)),
            ),
        )
        val mapper = GamepadMapper(hub, profileResolver = { profile }, onVirtKey = { k, down ->
            if (down) virtPresses += k
        })

        fun key(action: Int, code: Int) = KeyEvent(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
            action, code, 0, 0, 1, 0, 0, InputDevice.SOURCE_GAMEPAD,
        )

        assertTrue(mapper.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_C)))
        assertEquals(RetroPad.B, LibretroBridge.nativeDebugButtons())
        assertTrue(mapper.onKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_C)))
        assertEquals(0, LibretroBridge.nativeDebugButtons())

        assertTrue(mapper.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Z)))
        assertEquals(listOf(VirtKey.SAVE_STATE), virtPresses)

        // Unbound key: not consumed (falls through to the system).
        assertTrue(!mapper.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)))
        hub.clear()
    }

    @Test
    fun analogTuningMath() {
        val mapper = GamepadMapper(InputHub())
        val linear = MappingProfile(name = "t", deadzone = 0.2f, curve = 1f)

        // Inside the deadzone → zero.
        assertEquals(0f to 0f, mapper.tuneAnalog(0.1f, 0.05f, linear))
        // Full deflection → full output.
        val (fx, _) = mapper.tuneAnalog(1f, 0f, linear)
        assertTrue("full deflection should be ~1, got $fx", abs(fx - 1f) < 0.01f)
        // Just past the deadzone → small output (rescaled, not a jump).
        val (jx, _) = mapper.tuneAnalog(0.25f, 0f, linear)
        assertTrue("expected small rescaled value, got $jx", jx > 0f && jx < 0.15f)

        // Curve >1 pulls mid-range down (finer center control).
        val curved = linear.copy(curve = 2f)
        val (mx, _) = mapper.tuneAnalog(0.6f, 0f, linear)
        val (cx, _) = mapper.tuneAnalog(0.6f, 0f, curved)
        assertTrue("curve should reduce mid-range ($cx !< $mx)", cx < mx)

        // Inverse deadzone raises the floor just past the deadzone.
        val inverse = linear.copy(inverseDeadzone = 0.3f)
        val (ix, _) = mapper.tuneAnalog(0.25f, 0f, inverse)
        assertTrue("inverse deadzone floor missing: $ix", ix >= 0.3f)
    }

    @Test
    fun nativePauseFreezesAndResumesFrames() {
        assertTrue("native host missing", LibretroBridge.available)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val core = File(ctx.applicationInfo.nativeLibraryDir, "retro_test_libretro_android.so")
        assertTrue("test core not bundled", core.exists())
        val systemDir = File(ctx.filesDir, "system").apply { mkdirs() }
        val saveDir = File(ctx.filesDir, "saves-core").apply { mkdirs() }

        val intent = Intent(ctx, RenderTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as RenderTestActivity
        try {
            assertTrue(activity.surfaceReady.await(8, TimeUnit.SECONDS))
            LibretroBridge.nativeStartSession(core.absolutePath, null, systemDir.absolutePath, saveDir.absolutePath)
            val done = CountDownLatch(1)
            val ok = AtomicBoolean(false)
            Thread({ ok.set(LibretroBridge.nativeRunLoop()); done.countDown() }, "PauseTest").start()

            // Frames advance while running.
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline && LibretroBridge.nativeFramesPresented() < 30) {
                Thread.sleep(100)
            }
            assertTrue("no frames before pause", LibretroBridge.nativeFramesPresented() >= 30)

            // Pause: the frame counter must freeze.
            LibretroBridge.nativeSetPaused(true)
            Thread.sleep(300) // let an in-flight frame drain
            val frozen1 = LibretroBridge.nativeFramesPresented()
            Thread.sleep(700)
            val frozen2 = LibretroBridge.nativeFramesPresented()
            assertEquals("frames advanced while paused", frozen1, frozen2)
            assertTrue(LibretroBridge.nativeIsPaused())

            // Resume: frames advance again.
            LibretroBridge.nativeSetPaused(false)
            val resumeDeadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < resumeDeadline &&
                LibretroBridge.nativeFramesPresented() < frozen2 + 15
            ) {
                Thread.sleep(100)
            }
            assertTrue("frames did not advance after resume",
                LibretroBridge.nativeFramesPresented() >= frozen2 + 15)

            LibretroBridge.nativeRequestStop()
            assertTrue(done.await(8, TimeUnit.SECONDS))
            assertTrue(ok.get())
        } finally {
            activity.finish()
            instrumentation.waitForIdleSync()
        }
    }
}
