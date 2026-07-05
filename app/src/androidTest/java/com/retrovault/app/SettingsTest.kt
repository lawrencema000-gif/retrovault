package com.retrovault.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.core.model.GameSystem
import com.retrovault.download.GameInstaller
import com.retrovault.emulator.CoreAssets
import com.retrovault.emulator.LibretroBridge
import com.retrovault.settings.BackendPolicy
import com.retrovault.settings.DeviceClass
import com.retrovault.settings.Origin
import com.retrovault.settings.PspSettings
import com.retrovault.settings.SettingsResolver
import com.retrovault.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * P11 acceptance: 4-layer resolution with origin badges (per-game wins over global wins over
 * device-class wins over default), user-diff persistence, device-class detection, backend
 * fallback + blacklist, and — end to end — resolver-pushed core variables driving a REAL
 * PPSSPP boot (the hardcoded override table is gone; if this plumbing broke, the game would
 * render black or hang).
 */
@RunWith(AndroidJUnit4::class)
class SettingsTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = instrumentation.targetContext

    private fun wipe() {
        File(ctx.filesDir, "settings").deleteRecursively()
        File(ctx.filesDir, "backend-blacklist.json").delete()
    }

    @Test
    fun fourLayerResolutionAndOrigins() {
        wipe()
        val resolver = SettingsResolver(
            ctx,
            gameDbProvider = { gameKey ->
                if (gameKey == "fussy-game") mapOf(PspSettings.FRAMESKIP.key to "2") else emptyMap()
            },
        )

        // DEFAULT layer.
        resolver.resolve(PspSettings.FRAMESKIP).let {
            assertEquals("0", it.value)
            assertEquals(Origin.DEFAULT, it.origin)
        }

        // GAMEDB layer beats default (for the flagged game only).
        resolver.resolve(PspSettings.FRAMESKIP, "fussy-game").let {
            assertEquals("2", it.value)
            assertEquals(Origin.GAMEDB, it.origin)
        }
        assertEquals(Origin.DEFAULT, resolver.resolve(PspSettings.FRAMESKIP, "other-game").origin)

        // DEVICE layer beats gamedb: this test runs on an x86 emulator, so cpu_core
        // resolves from the device class.
        assertTrue("test expects an x86 AVD", DeviceClass.isX86)
        resolver.resolve(PspSettings.CPU_CORE).let {
            assertEquals("IR JIT", it.value)
            assertEquals(Origin.DEVICE, it.origin)
        }

        // USER_GLOBAL beats device.
        resolver.setUserValue(PspSettings.CPU_CORE, "Interpreter")
        resolver.resolve(PspSettings.CPU_CORE, "fussy-game").let {
            assertEquals("Interpreter", it.value)
            assertEquals(Origin.USER_GLOBAL, it.origin)
        }

        // USER_GAME beats everything — and only for that game.
        resolver.setUserValue(PspSettings.CPU_CORE, "JIT", gameKey = "fussy-game")
        resolver.resolve(PspSettings.CPU_CORE, "fussy-game").let {
            assertEquals("JIT", it.value)
            assertEquals(Origin.USER_GAME, it.origin)
        }
        assertEquals(Origin.USER_GLOBAL, resolver.resolve(PspSettings.CPU_CORE, "other-game").origin)

        // Clearing falls back down the chain.
        resolver.clearUserValue(PspSettings.CPU_CORE, gameKey = "fussy-game")
        assertEquals(Origin.USER_GLOBAL, resolver.resolve(PspSettings.CPU_CORE, "fussy-game").origin)
        resolver.clearUserValue(PspSettings.CPU_CORE)
        assertEquals(Origin.DEVICE, resolver.resolve(PspSettings.CPU_CORE, "fussy-game").origin)
        wipe()
    }

    @Test
    fun userDiffPersistsAcrossInstances() {
        wipe()
        SettingsStore(ctx).set(null, PspSettings.INTERNAL_RESOLUTION.key, "1920x1088")
        SettingsStore(ctx).set("game-x", PspSettings.FRAMESKIP.key, "1")

        // Fresh instances read the same diffs.
        assertEquals("1920x1088", SettingsStore(ctx).read(null)[PspSettings.INTERNAL_RESOLUTION.key])
        assertEquals("1", SettingsStore(ctx).read("game-x")[PspSettings.FRAMESKIP.key])

        // Only explicit diffs are stored.
        assertEquals(1, SettingsStore(ctx).read(null).size)
        wipe()
    }

    @Test
    fun backendPolicyFallsBackAndBlacklists() {
        wipe()
        val policy = BackendPolicy(ctx)

        // Vulkan preferred but unimplemented → falls back to gles3 cleanly.
        assertEquals("gles3", policy.choose("vulkan"))
        assertEquals("gles3", policy.choose("gles3"))

        // A backend that failed once is blacklisted persistently.
        policy.recordFailure("vulkan")
        assertTrue(BackendPolicy(ctx).isBlacklisted("vulkan"))
        assertEquals("gles3", BackendPolicy(ctx).choose("vulkan"))

        // gles3 is the terminal fallback even if it were blacklisted.
        policy.recordFailure("gles3")
        assertEquals("gles3", BackendPolicy(ctx).choose("vulkan"))
        wipe()
    }

    @Test
    fun resolverDrivenCoreVariablesBootTheRealGame() {
        wipe()
        assertTrue(LibretroBridge.available)
        val playable = GameInstaller.installedPlayable(ctx, GameSystem.PSP, "battlegrounds-3")
        assertNotNull("battlegrounds-3 not installed (FirstLightTest provisions it)", playable)

        // Resolve + push: on this x86 AVD the DEVICE layer must supply IR JIT + native res —
        // exactly what the deleted hardcoded override table used to do.
        val resolver = SettingsResolver(ctx)
        resolver.applyToCore(gameKey = null)
        assertEquals("IR JIT", LibretroBridge.nativeGetCoreVariable("ppsspp_cpu_core"))
        assertEquals("480x272", LibretroBridge.nativeGetCoreVariable("ppsspp_internal_resolution"))

        val core = File(ctx.applicationInfo.nativeLibraryDir, "ppsspp_libretro_android.so")
        val systemDir = File(ctx.filesDir, "system").apply { mkdirs() }
        val saveDir = File(ctx.filesDir, "saves-core").apply { mkdirs() }
        CoreAssets.ensureExtracted(ctx, systemDir)

        val intent = Intent(ctx, RenderTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as RenderTestActivity
        var doneRef: CountDownLatch? = null
        try {
            assertTrue(activity.surfaceReady.await(8, TimeUnit.SECONDS))
            LibretroBridge.nativeStartSession(
                core.absolutePath, playable!!.absolutePath,
                systemDir.absolutePath, saveDir.absolutePath
            )
            val done = CountDownLatch(1)
            doneRef = done
            val ok = AtomicBoolean(false)
            Thread({ ok.set(LibretroBridge.nativeRunLoop()); done.countDown() }, "SettingsBoot").start()

            val deadline = System.currentTimeMillis() + 60_000
            while (System.currentTimeMillis() < deadline && LibretroBridge.nativeFramesPresented() < 90) {
                Thread.sleep(250)
            }
            assertTrue("game did not boot with resolver-driven variables",
                LibretroBridge.nativeFramesPresented() >= 90)

            // Live update: change a variable mid-session; the dirty flag must clear once the
            // core re-queries (PPSSPP polls GET_VARIABLE_UPDATE every frame).
            LibretroBridge.nativeSetCoreVariable("ppsspp_frameskip", "1")
            assertTrue(LibretroBridge.nativeVariablesDirty())
            val dirtyDeadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < dirtyDeadline && LibretroBridge.nativeVariablesDirty()) {
                Thread.sleep(100)
            }
            assertFalse("core never re-queried variables", LibretroBridge.nativeVariablesDirty())

            LibretroBridge.nativeRequestStop()
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertTrue(ok.get())
        } finally {
            LibretroBridge.nativeRequestStop()
            doneRef?.await(15, TimeUnit.SECONDS)
            activity.finish()
            instrumentation.waitForIdleSync()
        }
    }
}
