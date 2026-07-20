package com.retrovault.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.emulator.CoreAssets
import com.retrovault.emulator.LibretroBridge
import com.retrovault.settings.AdhocMac
import com.retrovault.settings.PspSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adhoc multiplayer v1 acceptance (NETPLAY.md GO verdict): the MULTIPLAYER settings map to the
 * exact core-option keys verified against PPSSPP v1.20.4 source, the device MAC persists (an
 * all-zero MAC makes the core re-randomize every session — breaks friend pairing), the nickname
 * reaches the native GET_USERNAME store, and a real PSP game still boots and renders with the
 * whole networking stack enabled (WLAN on + built-in adhoc server + localhost address) — the
 * closest an emulator-only test can get to two-device play.
 */
@RunWith(AndroidJUnit4::class)
class NetplayTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = instrumentation.targetContext

    // ---- 1. keys pinned to the verified v1.20.4 option strings ----
    @Test
    fun settingKeysMatchVerifiedCoreOptions() {
        assertEquals("ppsspp_enable_wlan", PspSettings.ENABLE_WLAN.coreVariable)
        assertFalse("WLAN must default OFF (core + Play policy)", PspSettings.ENABLE_WLAN.defaultValue)
        assertEquals("ppsspp_change_pro_ad_hoc_server_address", PspSettings.ADHOC_SERVER.coreVariable)
        assertEquals("socom.cc", PspSettings.ADHOC_SERVER.defaultOption)
        assertEquals("ppsspp_enable_builtin_pro_ad_hoc_server", PspSettings.HOST_ON_LAN.coreVariable)
        assertFalse(PspSettings.HOST_ON_LAN.defaultValue)
        assertEquals("ppsspp_enable_upnp", PspSettings.UPNP.coreVariable)
        assertFalse(PspSettings.UPNP.defaultValue)
        // The defunct preset the core still ships must NOT be in our curated list.
        assertTrue(PspSettings.ADHOC_SERVER.options.none { it.first.contains("myneighborsushicat") })
        // MAC option keys must be byte-exact regardless of device locale: String.format("%02d")
        // localizes digits (Arabic-Indic etc.) — the padStart construction must not.
        val default = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("ar", "EG"))
            assertEquals(
                "ppsspp_change_mac_address01",
                "ppsspp_change_mac_address" + 1.toString().padStart(2, '0'),
            )
        } finally {
            java.util.Locale.setDefault(default)
        }
    }

    // ---- 2. persistent, well-formed MAC ----
    @Test
    fun macIsPersistentAndWellFormed() {
        val a = AdhocMac.digits(ctx)
        val b = AdhocMac.digits(ctx)
        assertEquals("MAC must be stable across reads (persisted)", a, b)
        assertTrue("MAC must be 12 lowercase hex, locally-administered unicast", AdhocMac.isValid(a))
        assertEquals(17, AdhocMac.formatted(ctx).length) // aa:bb:cc:dd:ee:ff
        assertFalse("all-zero MAC would make the core re-randomize", a == "000000000000")
    }

    // ---- 3. nickname round-trips through the native GET_USERNAME store ----
    @Test
    fun nicknameReachesNativeStore() {
        assertTrue(LibretroBridge.available)
        LibretroBridge.nativeSetUsername("PulsarTester")
        assertEquals("PulsarTester", LibretroBridge.nativeGetUsername())
        LibretroBridge.nativeSetUsername("")
        assertEquals("", LibretroBridge.nativeGetUsername())
    }

    // ---- 4. a real game boots and renders with the full networking stack ON ----
    @Test
    fun bootsAndRendersWithNetworkingEnabled() {
        assertTrue(LibretroBridge.available)
        val playable = TestGames.ensureBattlegrounds3(ctx)
        assertTrue("battlegrounds-3 unavailable", playable != null)
        val core = File(ctx.applicationInfo.nativeLibraryDir, "ppsspp_libretro_android.so")

        val intent = Intent(ctx, RenderTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as RenderTestActivity
        var doneRef: CountDownLatch? = null
        try {
            assertTrue(activity.surfaceReady.await(8, TimeUnit.SECONDS))
            val systemDir = File(ctx.filesDir, "system").apply { mkdirs() }
            val saveDir = File(ctx.filesDir, "saves-core").apply { mkdirs() }
            CoreAssets.ensureExtracted(ctx, systemDir)
            com.retrovault.settings.SettingsResolver(ctx).applyToCore(null)

            // The production push (EmulatorActivity does exactly this): identity + networking.
            LibretroBridge.nativeSetUsername("PulsarTester")
            AdhocMac.digits(ctx).forEachIndexed { i, d ->
                LibretroBridge.nativeSetCoreVariable(
                    "ppsspp_change_mac_address" + (i + 1).toString().padStart(2, '0'), d.toString()
                )
            }
            LibretroBridge.nativeSetCoreVariable("ppsspp_enable_wlan", "enabled")
            LibretroBridge.nativeSetCoreVariable("ppsspp_enable_builtin_pro_ad_hoc_server", "enabled")
            LibretroBridge.nativeSetCoreVariable("ppsspp_change_pro_ad_hoc_server_address", "localhost")

            LibretroBridge.nativeStartSession(
                core.absolutePath, playable!!.absolutePath, systemDir.absolutePath, saveDir.absolutePath
            )
            val done = CountDownLatch(1)
            val ok = AtomicBoolean(false)
            Thread({ ok.set(LibretroBridge.nativeRunLoop()); done.countDown() }, "NetplayLoop").start()
            doneRef = done

            // Networking-on must not break boot: the game reaches steady rendering.
            val deadline = System.currentTimeMillis() + 60_000
            while (System.currentTimeMillis() < deadline && LibretroBridge.nativeFramesPresented() < 120) {
                Thread.sleep(100)
            }
            assertTrue(
                "game failed to render 120 frames with networking enabled",
                LibretroBridge.nativeFramesPresented() >= 120
            )

            LibretroBridge.nativeRequestStop()
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertTrue("run loop reported failure", ok.get())
        } finally {
            LibretroBridge.nativeRequestStop()
            doneRef?.await(15, TimeUnit.SECONDS)
            // Never leak WLAN-on into later tests (shared process-global core vars).
            LibretroBridge.nativeSetCoreVariable("ppsspp_enable_wlan", "disabled")
            LibretroBridge.nativeSetCoreVariable("ppsspp_enable_builtin_pro_ad_hoc_server", "disabled")
            LibretroBridge.nativeSetUsername("")
            activity.finish()
            instrumentation.waitForIdleSync()
        }
    }
}
