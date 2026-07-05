package com.retrovault.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.core.model.GameSystem
import com.retrovault.emulator.CoreAssets
import com.retrovault.settings.GameDb
import com.retrovault.settings.Origin
import com.retrovault.settings.PspSettings
import com.retrovault.settings.SettingsResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * P12 acceptance: the baked GameDB snapshot loads (890 serials from compat.ini v1.20.4), a
 * known-fussy serial auto-gets its flags, PPSSPP's own compat.ini ships into the core system
 * dir (the core applies its flags natively), and a GameDB settings override resolves through
 * the resolver with the GAMEDB origin badge.
 */
@RunWith(AndroidJUnit4::class)
class GameDbTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bakedSnapshotLoadsWithKnownFussySerials() {
        GameDb.invalidate()
        File(ctx.filesDir, "gamedb-cache.json").delete()
        val db = GameDb.load(ctx)
        assertTrue("expected ~890 serials, got ${db.size}", db.size >= 800)

        // Darkstalkers (EU): the classic fussy title — needs the software renderer.
        val flags = GameDb.flagsFor(ctx, "ULES-00016")
        assertTrue("missing DarkStalkersPresentHack: $flags", "DarkStalkersPresentHack" in flags)
        assertTrue("missing ForceSoftwareRenderer: $flags", "ForceSoftwareRenderer" in flags)

        // God of War (US): multi-flag entry.
        val gow = GameDb.flagsFor(ctx, "UCUS-98653")
        assertTrue("GoW flags wrong: $gow", "GoWFramerateHack60" in gow)

        // Save-state warning flag is queryable (gates auto-save in the player).
        assertTrue(GameDb.hasFlag(ctx, "UCJS-10113", "SaveStatesNotRecommended"))

        // Homebrew fake IDs and unknown serials resolve to nothing, quietly.
        assertNull(GameDb.entryFor(ctx, "HB12-345678"))
        assertNull(GameDb.entryFor(ctx, null))
        assertEquals(emptyList<String>(), GameDb.flagsFor(ctx, "XXXX-99999"))
    }

    @Test
    fun ppssppCompatIniShipsIntoSystemDir() {
        val systemDir = File(ctx.filesDir, "system")
        CoreAssets.ensureExtracted(ctx, systemDir)
        val ini = File(systemDir, "PPSSPP/compat.ini")
        assertTrue("compat.ini not extracted", ini.isFile && ini.length() > 10_000)
        // Sanity: it is the real PPSSPP file (a known section header exists).
        assertTrue(ini.readText().contains("[VertexDepthRounding]"))
    }

    @Test
    fun gameDbSettingsResolveWithGamedbOrigin() {
        // Curated settings for a serial (the gamedb_entries.settings column) must resolve
        // through the GAMEDB layer — beaten only by device-class and user overrides.
        File(ctx.filesDir, "settings").deleteRecursively()
        val resolver = SettingsResolver(ctx) { serial ->
            if (serial == "ULES-00016") mapOf(PspSettings.FRAMESKIP.key to "1") else emptyMap()
        }
        resolver.resolve(PspSettings.FRAMESKIP, "ULES-00016").let {
            assertEquals("1", it.value)
            assertEquals(Origin.GAMEDB, it.origin)
        }
        // User override still wins.
        resolver.setUserValue(PspSettings.FRAMESKIP, "3", gameKey = "ULES-00016")
        assertEquals(Origin.USER_GAME, resolver.resolve(PspSettings.FRAMESKIP, "ULES-00016").origin)
        File(ctx.filesDir, "settings").deleteRecursively()
    }
}
