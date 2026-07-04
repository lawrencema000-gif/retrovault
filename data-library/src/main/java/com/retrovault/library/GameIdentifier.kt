package com.retrovault.library

import android.content.Context
import com.retrovault.core.model.GameMetadata
import com.retrovault.core.model.GameSystem
import com.retrovault.core.model.Serial
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.CRC32

/**
 * Identifies a game file: canonical serial, title, disc version, and extracted ICON0 art —
 * from the disc/EBOOT itself, no external scraping. Mirrors PPSSPP's GameInfoCache approach.
 *
 * Supported now: PSP `.pbp` (EBOOT) and plain `.iso`; PSP folders containing EBOOT.PBP.
 * CSO/CHD are decompressed on the native side (added with the core pass-through).
 */
object GameIdentifier {

    fun identify(context: Context, file: File, system: GameSystem): GameMetadata? {
        if (system != GameSystem.PSP) {
            // PS1/PS2 identification (SYSTEM.CNF) arrives with those systems (P23/P25).
            return fallback(context, file, system, sfo = emptyMap(), icon = null)
        }

        val target = when {
            file.isDirectory -> File(file, "EBOOT.PBP").takeIf { it.exists() }
                ?: file.walkTopDown().firstOrNull { it.name.equals("EBOOT.PBP", true) }
            else -> file
        } ?: return null

        val ext = target.extension.lowercase()
        val (sfo, icon) = when (ext) {
            "pbp" -> {
                val pbp = PbpReader.open(target) ?: return null
                val s = pbp.read(PbpReader.Section.PARAM_SFO)?.let { ParamSfo.parse(it) } ?: emptyMap()
                s to pbp.read(PbpReader.Section.ICON0)
            }
            "iso" -> RandomAccessFile(target, "r").use { raf ->
                val iso = IsoReader(raf)
                val s = iso.readFile("PSP_GAME", "PARAM.SFO")?.let { ParamSfo.parse(it) } ?: emptyMap()
                s to iso.readFile("PSP_GAME", "ICON0.PNG")
            }
            else -> emptyMap<String, Any>() to null
        }

        return fallback(context, target, system, sfo, icon)
    }

    private fun fallback(
        context: Context,
        file: File,
        system: GameSystem,
        sfo: Map<String, Any>,
        icon: ByteArray?,
    ): GameMetadata {
        val discId = (sfo["DISC_ID"] as? String)?.takeIf { it.isNotBlank() }
        val fake = discId == null
        val serial = if (discId != null) Serial.canonical(discId) else Serial.fakeId(file.name)
        val title = (sfo["TITLE"] as? String)?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension
        val version = sfo["DISC_VERSION"] as? String

        val iconPath = icon?.let { writeIcon(context, serial, it) }
        val crc = bootCrc(file)

        return GameMetadata(
            serial = serial,
            title = title,
            discVersion = version,
            iconPath = iconPath,
            bootCrc = crc,
            fakeId = fake,
            system = system,
        )
    }

    private fun writeIcon(context: Context, serial: String, png: ByteArray): String? {
        // Only accept a real PNG (magic 89 50 4E 47).
        if (png.size < 8 || png[0].toInt() and 0xFF != 0x89 ||
            png[1].toInt().toChar() != 'P' || png[2].toInt().toChar() != 'N' ||
            png[3].toInt().toChar() != 'G'
        ) return null
        val dir = File(context.filesDir, "library-art").apply { mkdirs() }
        val out = File(dir, "${serial.replace('/', '_')}.png")
        return try {
            out.writeBytes(png)
            out.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** CRC32 of the first up-to-1MB of the boot file — cheap, stable, good enough for dedup. */
    private fun bootCrc(file: File): String? = try {
        val crc = CRC32()
        file.inputStream().use { s ->
            val buf = ByteArray(1 shl 16)
            var total = 0
            while (total < (1 shl 20)) {
                val n = s.read(buf)
                if (n <= 0) break
                crc.update(buf, 0, n)
                total += n
            }
        }
        crc.value.toString(16).uppercase().padStart(8, '0')
    } catch (e: Exception) {
        null
    }
}
