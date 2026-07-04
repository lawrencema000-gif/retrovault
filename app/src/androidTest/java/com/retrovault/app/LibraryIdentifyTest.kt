package com.retrovault.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.core.model.GameSystem
import com.retrovault.core.model.Serial
import com.retrovault.library.GameIdentifier
import com.retrovault.library.LibraryIndex
import com.retrovault.library.ParamSfo
import com.retrovault.download.GameInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * P6 acceptance: PSP metadata identification (PARAM.SFO → serial/title, ICON0 extraction,
 * fake IDs for homebrew) and the cached library index. Validated deterministically with a
 * synthetic PBP, plus opportunistically against the real installed Battlegrounds 3 EBOOT.
 */
@RunWith(AndroidJUnit4::class)
class LibraryIdentifyTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun serialCanonicalizationAndFakeId() {
        assertEquals("UCUS-98615", Serial.canonical("UCUS98615"))
        assertEquals("ULUS-10041", Serial.canonical("ULUS10041"))
        assertEquals("NPUH-10075", Serial.canonical("npuh10075"))
        // fake IDs are stable + well-formed + collision-resistant across names
        val a = Serial.fakeId("battlegrounds3/EBOOT.PBP")
        assertEquals(a, Serial.fakeId("battlegrounds3/EBOOT.PBP"))
        assertTrue(a.startsWith("HB"))
        assertFalse(a == Serial.fakeId("otherGame/EBOOT.PBP"))
    }

    @Test
    fun paramSfoRoundTrip() {
        val sfo = buildParamSfo(
            linkedMapOf(
                "CATEGORY" to "MG",
                "DISC_ID" to "UCUS98615",
                "DISC_VERSION" to "1.00",
                "TITLE" to "Test Game Title",
            )
        )
        val parsed = ParamSfo.parse(sfo)
        assertEquals("UCUS98615", parsed["DISC_ID"])
        assertEquals("Test Game Title", parsed["TITLE"])
        assertEquals("1.00", parsed["DISC_VERSION"])
    }

    @Test
    fun identifiesSyntheticPbpWithSerialTitleAndIcon() {
        val sfo = buildParamSfo(
            linkedMapOf("DISC_ID" to "ULUS10041", "DISC_VERSION" to "1.00", "TITLE" to "Neon Drift")
        )
        val icon = minimalPng()
        val pbp = buildPbp(sfo, icon)
        val file = File(ctx.cacheDir, "synthetic_eboot.pbp").apply { writeBytes(pbp) }

        val meta = GameIdentifier.identify(ctx, file, GameSystem.PSP)
        assertNotNull("identify returned null", meta)
        assertEquals("ULUS-10041", meta!!.serial)
        assertEquals("Neon Drift", meta.title)
        assertEquals("1.00", meta.discVersion)
        assertFalse("should be a real disc id", meta.fakeId)
        assertNotNull("boot crc missing", meta.bootCrc)
        assertNotNull("icon not extracted", meta.iconPath)
        assertTrue("extracted icon is not a valid file", File(meta.iconPath!!).length() > 8)
    }

    @Test
    fun homebrewWithoutDiscIdGetsStableFakeId() {
        val sfo = buildParamSfo(linkedMapOf("TITLE" to "Some Homebrew"))
        val file = File(ctx.cacheDir, "hb_eboot.pbp").apply { writeBytes(buildPbp(sfo, null)) }
        val meta = GameIdentifier.identify(ctx, file, GameSystem.PSP)!!
        assertTrue("expected fake id", meta.fakeId)
        assertTrue(meta.serial.startsWith("HB"))
        assertEquals("Some Homebrew", meta.title)
    }

    @Test
    fun libraryIndexCachesAndRescanSkips() {
        val index = LibraryIndex(ctx)
        val before = index.all().size
        val entry = sampleEntry("TEST-00001", 1000L)
        index.upsert(entry)
        assertEquals(entry, index.get("TEST-00001"))
        assertTrue("unchanged file should be current", index.isCurrent(entry.sourceUri, 1000L))
        assertFalse("modified file should NOT be current", index.isCurrent(entry.sourceUri, 2000L))

        // Persisted across a fresh instance.
        val reopened = LibraryIndex(ctx)
        assertNotNull(reopened.get("TEST-00001"))
        reopened.remove("TEST-00001")
        assertEquals(before, LibraryIndex(ctx).all().size)
    }

    @Test
    fun identifiesRealBattlegrounds3IfInstalled() {
        // Opportunistic: if FirstLightTest installed the real EBOOT, identify it too.
        val playable = GameInstaller.installedPlayable(ctx, GameSystem.PSP, "battlegrounds-3")
            ?: return
        val meta = GameIdentifier.identify(ctx, playable, GameSystem.PSP)
        assertNotNull("real EBOOT failed to identify", meta)
        assertTrue("title empty", meta!!.title.isNotBlank())
        // Serial is either a real disc id or a stable fake id — both valid for homebrew.
        assertTrue(meta.serial.isNotBlank())
    }

    // ---- synthetic builders (format per ParamSFO / PBP spec) ----

    private fun sampleEntry(serial: String, lm: Long) = com.retrovault.library.LibraryEntry(
        serial = serial, title = "Sample", system = GameSystem.PSP,
        sourceUri = "file:///x/$serial.pbp", displayName = "$serial.pbp",
        sizeBytes = 1234, iconPath = null, discVersion = "1.00", bootCrc = "AABBCCDD",
        fakeId = false, addedAtEpochMs = 1L, lastModified = lm,
    )

    private fun buildParamSfo(kv: LinkedHashMap<String, Any>): ByteArray {
        val n = kv.size
        // key table
        val keyBytes = ByteArrayOutputStream()
        val keyOffsets = IntArray(n)
        kv.keys.forEachIndexed { i, k ->
            keyOffsets[i] = keyBytes.size()
            keyBytes.write(k.toByteArray(Charsets.UTF_8)); keyBytes.write(0)
        }
        var keyTable = keyBytes.toByteArray()
        // pad key table to 4 bytes
        while (keyTable.size % 4 != 0) keyTable += 0

        // data table
        val dataBytes = ByteArrayOutputStream()
        val dataOffsets = IntArray(n)
        val dataFmts = IntArray(n)
        val dataLens = IntArray(n)
        val dataMax = IntArray(n)
        kv.values.forEachIndexed { i, v ->
            dataOffsets[i] = dataBytes.size()
            when (v) {
                is String -> {
                    val b = v.toByteArray(Charsets.UTF_8)
                    dataBytes.write(b); dataBytes.write(0)
                    var pad = (b.size + 1)
                    dataFmts[i] = 0x0204; dataLens[i] = b.size + 1
                    val max = ((pad + 3) / 4) * 4
                    while (dataBytes.size() - dataOffsets[i] < max) dataBytes.write(0)
                    dataMax[i] = max
                }
                is Int -> {
                    val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v)
                    dataBytes.write(bb.array())
                    dataFmts[i] = 0x0404; dataLens[i] = 4; dataMax[i] = 4
                }
            }
        }
        val dataTable = dataBytes.toByteArray()

        val indexSize = 16 * n
        val headerSize = 20
        val keyTableStart = headerSize + indexSize
        val dataTableStart = keyTableStart + keyTable.size

        val out = ByteBuffer.allocate(dataTableStart + dataTable.size).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46535000)          // magic
        out.putInt(0x00000101)          // version 1.1
        out.putInt(keyTableStart)
        out.putInt(dataTableStart)
        out.putInt(n)
        for (i in 0 until n) {
            out.putShort(keyOffsets[i].toShort())
            out.putShort(dataFmts[i].toShort())
            out.putInt(dataLens[i])
            out.putInt(dataMax[i])
            out.putInt(dataOffsets[i])
        }
        out.put(keyTable)
        out.put(dataTable)
        return out.array()
    }

    private fun buildPbp(sfo: ByteArray, icon: ByteArray?): ByteArray {
        val headerLen = 40
        val sfoStart = headerLen
        val iconStart = sfoStart + sfo.size
        val iconLen = icon?.size ?: 0
        val end = iconStart + iconLen
        val bb = ByteBuffer.allocate(end).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(byteArrayOf(0, 'P'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte()))
        bb.putInt(0x00010000) // version
        bb.putInt(sfoStart)                       // [0] PARAM.SFO
        bb.putInt(iconStart)                      // [1] ICON0.PNG
        val rest = if (iconLen > 0) end else iconStart
        repeat(6) { bb.putInt(rest) }             // [2..7] all point at end (empty)
        bb.put(sfo)
        if (icon != null) bb.put(icon)
        return bb.array()
    }

    /** Smallest valid 1x1 PNG. */
    private fun minimalPng(): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F.toByte(), 0x15, 0xC4.toByte(),
        0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
        0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
        0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
        0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
        0x42, 0x60, 0x82.toByte(),
    )
}
