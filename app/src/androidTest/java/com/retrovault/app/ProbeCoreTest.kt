package com.retrovault.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.emulator.LibretroBridge
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * P1 acceptance: the native host loads and can dlopen the CI-built PPSSPP libretro core,
 * reading retro_get_system_info without initializing it.
 */
@RunWith(AndroidJUnit4::class)
class ProbeCoreTest {

    @Test
    fun nativeHostLoads() {
        assertTrue("libpulsar_retro.so failed to load", LibretroBridge.available)
    }

    @Test
    fun probesPpssppCore() {
        assertTrue(LibretroBridge.available)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val libDir = ctx.applicationInfo.nativeLibraryDir
        val corePath = File(libDir, "ppsspp_libretro_android.so").absolutePath

        val info = LibretroBridge.nativeProbeCore(corePath)
        assertNotNull("nativeProbeCore returned null for $corePath", info)

        // "name\nversion\nextensions\napiVersion" (extensions are pipe-delimited)
        val parts = info!!.split("\n")
        assertTrue("unexpected probe format: $info", parts.size == 4)
        assertTrue("expected PPSSPP, got: $info", parts[0].contains("PPSSPP", ignoreCase = true))
        assertTrue("expected iso support, got: $info", parts[2].contains("iso"))
        assertTrue("expected libretro API v1, got: $info", parts[3] == "1")
    }

    /** P23: the CI-built SwanStation core dlopens and reports the expected PS1 content types. */
    @Test
    fun probesSwanstationCore() {
        assertTrue(LibretroBridge.available)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val libDir = ctx.applicationInfo.nativeLibraryDir
        val corePath = File(libDir, "swanstation_libretro_android.so").absolutePath
        assertTrue("swanstation core not bundled — run scripts/fetch-cores.ps1", File(corePath).exists())

        val info = LibretroBridge.nativeProbeCore(corePath)
        assertNotNull("nativeProbeCore returned null for $corePath", info)

        val parts = info!!.split("\n")
        assertTrue("unexpected probe format: $info", parts.size == 4)
        assertTrue("expected SwanStation, got: $info", parts[0].contains("SwanStation", ignoreCase = true))
        // The research-verified extension list: exe|psexe|cue|bin|img|iso|chd|pbp|ecm|mds|psf|m3u
        assertTrue("expected cue support, got: $info", parts[2].contains("cue"))
        assertTrue("expected chd support, got: $info", parts[2].contains("chd"))
        assertTrue("expected m3u multi-disc support, got: $info", parts[2].contains("m3u"))
        assertTrue("expected libretro API v1, got: $info", parts[3] == "1")
    }
}
