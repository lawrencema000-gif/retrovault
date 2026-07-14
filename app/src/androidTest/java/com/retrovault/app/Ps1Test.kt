package com.retrovault.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.core.model.GameSystem
import com.retrovault.library.CueSheet
import com.retrovault.library.GameIdentifier
import com.retrovault.library.Ps1Serial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * P23 (PS1 bring-up, emulator-testable half): SYSTEM.CNF serial extraction, .cue resolution, and
 * raw-2352-sector ISO9660 reading — validated against a synthetic PS1 disc built the way real
 * dumps are laid out (MODE2/XA raw sectors: 12-byte sync + header + 8-byte subheader + 2048 data).
 * Booting a real PS1 game needs the user's BIOS + the SwanStation core (P5 device session).
 */
@RunWith(AndroidJUnit4::class)
class Ps1Test {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    // ---- pure serial-normalization rules ----
    @Test
    fun systemCnfSerialTruthTable() {
        assertEquals("SLUS-12345", Ps1Serial.fromSystemCnf("BOOT = cdrom:\\SLUS_123.45;1\r\nTCB = 4"))
        assertEquals("SLES-01234", Ps1Serial.fromSystemCnf("boot=cdrom:\\SLES_012.34;1"))
        assertEquals("SLPS-01234", Ps1Serial.fromSystemCnf("BOOT = cdrom:\\TEKKEN\\SLPS_012.34;1")) // subdir form
        assertEquals("SCUS-94900", Ps1Serial.fromSystemCnf("BOOT = cdrom:SCUS_949.00;1"))          // no backslash
        assertNull(Ps1Serial.fromSystemCnf("BOOT = cdrom:\\PSX.EXE;1"))     // unserialed homebrew
        assertNull(Ps1Serial.fromSystemCnf("TCB = 4\r\nEVENT = 10"))        // no BOOT line
        assertNull(Ps1Serial.fromSystemCnf("BOOT = cdrom:\\ZZZZ_123.45;1")) // unknown region prefix
    }

    // ---- synthetic disc: raw MODE2/2352 .bin + .cue ----
    @Test
    fun syntheticRawBinIdentifiesViaCueAndDirectly() {
        val dir = File(ctx.cacheDir, "ps1-test").apply { deleteRecursively(); mkdirs() }
        val bin = File(dir, "Test Game (USA).bin")
        bin.writeBytes(buildRawPs1Disc("BOOT = cdrom:\\SLUS_123.45;1\r\nTCB = 4\r\n"))
        val cue = File(dir, "Test Game (USA).cue")
        cue.writeText(
            "FILE \"Test Game (USA).bin\" BINARY\n" +
                "  TRACK 01 MODE2/2352\n" +
                "    INDEX 01 00:00:00\n"
        )

        // Cue resolves to its data track.
        val track = CueSheet.firstDataTrack(cue)
        assertNotNull("cue did not resolve its bin", track)
        assertEquals(bin.name, track!!.binFile.name)
        assertEquals("MODE2/2352", track.mode)

        // Identification via the .cue and via the .bin both yield the real serial.
        for (input in listOf(cue, bin)) {
            val meta = GameIdentifier.identify(ctx, input, GameSystem.PS1)
            assertNotNull("identify(${input.extension}) returned null", meta)
            assertEquals("SLUS-12345", meta!!.serial)
            assertFalse("serial should not be a fake id", meta.fakeId)
            assertEquals(GameSystem.PS1, meta.system)
        }
        dir.deleteRecursively()
    }

    @Test
    fun homebrewDiscWithoutSerialGetsStableFakeId() {
        val dir = File(ctx.cacheDir, "ps1-test-hb").apply { deleteRecursively(); mkdirs() }
        val bin = File(dir, "homebrew.bin")
        bin.writeBytes(buildRawPs1Disc("BOOT = cdrom:\\PSX.EXE;1\r\n"))
        val meta = GameIdentifier.identify(ctx, bin, GameSystem.PS1)
        assertNotNull(meta)
        assertTrue("no-serial disc must get a fake id", meta!!.fakeId)
        val again = GameIdentifier.identify(ctx, bin, GameSystem.PS1)
        assertEquals("fake id must be stable", meta.serial, again!!.serial)
        dir.deleteRecursively()
    }

    @Test
    fun nonDiscFileIsRejected() {
        val junk = File(ctx.cacheDir, "junk.bin")
        junk.writeBytes(ByteArray(4096) { 0x41 })
        assertNull(GameIdentifier.identify(ctx, junk, GameSystem.PS1))
        junk.delete()
    }

    // ---- BIOS hash identification (P23) ----
    @Test
    fun biosTableIsWellFormedAndRejectsImposters() {
        val known = com.retrovault.download.Ps1Bios.KNOWN
        assertTrue("hash table must not be empty", known.isNotEmpty())
        for (b in known) {
            assertTrue("bad md5 for ${b.filename}: ${b.md5}", b.md5.matches(Regex("[0-9a-f]{32}")))
            assertTrue("bad filename ${b.filename}", b.filename.endsWith(".bin"))
            assertTrue("bad region ${b.region}", b.region in setOf("NTSC-U", "NTSC-J", "PAL", "Region-free"))
        }
        // The recommended per-region trio (the core's own defaults) must be present.
        for (name in listOf("scph5500.bin", "scph5501.bin", "scph5502.bin")) {
            assertTrue("missing recommended $name", known.any { it.filename == name })
        }

        // A right-sized file with an unknown hash must be REJECTED (never a false "Installed").
        val fake = File(ctx.cacheDir, "fakebios.bin")
        fake.writeBytes(ByteArray(512 * 1024) { (it % 251).toByte() })
        assertNull(com.retrovault.download.Ps1Bios.identify(fake))
        // Wrong size is rejected before hashing.
        val small = File(ctx.cacheDir, "small.bin")
        small.writeBytes(ByteArray(1024))
        assertNull(com.retrovault.download.Ps1Bios.identify(small))
        fake.delete(); small.delete()
    }

    // ---- P24: GameShark code normalization (SwanStation's verified retro_cheat_set format) ----
    @Test
    fun gamesharkNormalizationTruthTable() {
        val n = com.retrovault.cheats.Ps1CheatCodes::normalize
        // Multi-line codes join with '+' (the core REJECTS newlines).
        assertEquals("80092E60 0063+80092E62 0000", n("80092E60 0063\n80092E62 0000"))
        // Alternate separators + comments + blank lines normalize away.
        assertEquals("80092E60 0063", n("  80092E60:0063  "))
        assertEquals("80092E60 0063", n("# infinite lives\n80092e60-0063"))
        assertEquals("80092E60 0063", n("80092E600063"))            // fused 12-hex form
        // Rejections: not hex pairs.
        assertNull(n("hello world"))
        assertNull(n("80092E60"))                                    // word without value
        assertNull(n("80092E60 0063\nnot a code"))                   // one bad line poisons the block
        assertNull(n(""))
    }

    /** Guard the verified SwanStation option keys — a typo'd key silently no-ops in the core. */
    @Test
    fun ps1SettingsUseVerifiedCoreOptionKeys() {
        val expected = mapOf(
            com.retrovault.settings.Ps1Settings.RENDERER to "swanstation_GPU_Renderer",
            com.retrovault.settings.Ps1Settings.RESOLUTION_SCALE to "swanstation_GPU_ResolutionScale",
            com.retrovault.settings.Ps1Settings.PGXP to "swanstation_GPU_PGXPEnable",
            com.retrovault.settings.Ps1Settings.TRUE_COLOR to "swanstation_GPU_TrueColor",
            com.retrovault.settings.Ps1Settings.WIDESCREEN to "swanstation_GPU_WidescreenHack",
            com.retrovault.settings.Ps1Settings.ASPECT_RATIO to "swanstation_Display_AspectRatio",
        )
        for ((def, key) in expected) {
            assertEquals("core-option key drifted for ${def.key}", key, def.coreVariable)
        }
        // Controller type is NOT a core variable (device id via retro_set_controller_port_device).
        assertNull(com.retrovault.settings.Ps1Settings.PS1_CONTROLLER.coreVariable)
        assertEquals(1, com.retrovault.settings.Ps1Settings.DEVICE_DIGITAL)
        assertEquals(261, com.retrovault.settings.Ps1Settings.DEVICE_DUALSHOCK)
        // And the resolver actually surfaces the PS1 defs.
        val all = com.retrovault.settings.SettingsResolver(ctx).resolveAll(null)
        assertTrue("resolver must include PS1 settings",
            all.any { it.def.key == com.retrovault.settings.Ps1Settings.PGXP.key })
    }

    /** Manual (pasted) cheats persist per serial and merge into the cheat list. */
    @Test
    fun manualCheatRoundTrip() {
        val mgr = com.retrovault.cheats.CheatManager(ctx)
        val serial = "SLUS-99998"
        mgr.removeManualCode(serial, "Test Moon Jump")
        assertTrue(mgr.addManualCode(serial, "Test Moon Jump", "80092E60 0063+80092E62 0000"))
        assertFalse("duplicate name must be refused", mgr.addManualCode(serial, "Test Moon Jump", "80000000 0000"))
        val entry = mgr.entriesFor(serial).firstOrNull { it.cheat.name == "Test Moon Jump" }
        assertNotNull("manual cheat missing from entries", entry)
        mgr.setEnabled(serial, "Test Moon Jump", true)
        assertTrue(mgr.enabledCodes(serial).contains("80092E60 0063+80092E62 0000"))
        mgr.removeManualCode(serial, "Test Moon Jump")
        assertTrue(mgr.entriesFor(serial).none { it.cheat.name == "Test Moon Jump" })
    }

    // ------------------------------------------------------------------ synthetic disc builder

    /**
     * A minimal but structurally-true PS1 disc: 24 raw MODE2/2352 sectors — PVD at LBA 16, root
     * directory at LBA 20 containing SYSTEM.CNF, whose content is [cnf] at LBA 21.
     */
    private fun buildRawPs1Disc(cnf: String): ByteArray {
        val sectors = 24
        val out = ByteArray(sectors * 2352)

        fun writeRawSector(lba: Int, data: ByteArray) {
            val base = lba * 2352
            // sync: 00 FF x10 00
            out[base] = 0x00
            for (i in 1..10) out[base + i] = 0xFF.toByte()
            out[base + 11] = 0x00
            // address (BCD mm:ss:ff — dummy values are fine for a file image)
            out[base + 12] = 0x00; out[base + 13] = 0x02; out[base + 14] = (lba % 75).toByte()
            out[base + 15] = 0x02 // MODE2
            // 8-byte XA subheader (FORM1, zeros suffice)
            data.copyInto(out, base + 24, 0, minOf(data.size, 2048))
        }
        // Give every sector a valid raw header so the layout sniff sees sync+mode at sector 0.
        for (l in 0 until sectors) writeRawSector(l, ByteArray(0))

        val cnfBytes = cnf.toByteArray(Charsets.US_ASCII)

        // PVD (LBA 16): type 1 + "CD001" + root dir record @156 -> root dir at LBA 20, 2048 bytes.
        val pvd = ByteArray(2048)
        pvd[0] = 1
        "CD001".toByteArray(Charsets.US_ASCII).copyInto(pvd, 1)
        dirRecord(pvd, 156, lba = 20, size = 2048, isDir = true, id = byteArrayOf(0))
        writeRawSector(16, pvd)

        // Root directory (LBA 20): ".", "..", SYSTEM.CNF.
        val rootDir = ByteArray(2048)
        var off = 0
        off += dirRecord(rootDir, off, 20, 2048, isDir = true, id = byteArrayOf(0))        // .
        off += dirRecord(rootDir, off, 20, 2048, isDir = true, id = byteArrayOf(1))        // ..
        dirRecord(rootDir, off, 21, cnfBytes.size, isDir = false,
            id = "SYSTEM.CNF;1".toByteArray(Charsets.US_ASCII))
        writeRawSector(20, rootDir)

        // SYSTEM.CNF content (LBA 21).
        writeRawSector(21, cnfBytes.copyOf(2048))
        return out
    }

    /** Write one ISO9660 directory record; returns its (even-padded) length. */
    private fun dirRecord(buf: ByteArray, off: Int, lba: Int, size: Int, isDir: Boolean, id: ByteArray): Int {
        var len = 33 + id.size
        if (len % 2 == 1) len++
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        buf[off] = len.toByte()
        bb.putInt(off + 2, lba)               // extent LBA (LE half of the both-endian field)
        bb.putInt(off + 10, size)             // extent size (LE half)
        buf[off + 25] = if (isDir) 0x02 else 0x00
        buf[off + 32] = id.size.toByte()
        id.copyInto(buf, off + 33)
        return len
    }
}
