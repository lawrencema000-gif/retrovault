package com.retrovault.download

import java.io.File
import java.security.MessageDigest

/**
 * PS1 BIOS identification by hash (P23). The hashes of retail Sony BIOS dumps are canonical,
 * publicly documented values (DuckStation/mednafen docs) — collecting HASHES is how emulators
 * verify a user's own dump; Pulsar never bundles or downloads BIOS files themselves.
 *
 * A recognized BIOS is renamed to its canonical lowercase filename on import so the SwanStation
 * core finds it in the libretro system directory.
 */
object Ps1Bios {

    data class KnownBios(
        val filename: String,   // canonical name the core expects, e.g. "scph5501.bin"
        val region: String,     // "NTSC-U" / "NTSC-J" / "PAL"
        val version: String,    // BIOS version string
        val md5: String,        // lowercase hex
    )

    const val SIZE_BYTES = 512 * 1024L

    // Canonical retail dumps, MD5s cross-confirmed against SwanStation's own bios.cpp table +
    // the libretro System.dat / mednafen docs (research-verified 2026-07-14). MD5 is what the
    // core itself matches on. The v3.0 trio (scph5500/5501/5502) is the recommended set and the
    // core's per-region defaults; psxonpsp660/ps1_rom are region-free.
    val KNOWN: List<KnownBios> = listOf(
        KnownBios("scph1000.bin", "NTSC-J", "v1.0", "239665b1a3dade1b5a52c06338011044"),
        KnownBios("scph3000.bin", "NTSC-J", "v1.1", "849515939161e62f6b866f6853006780"),
        KnownBios("scph3500.bin", "NTSC-J", "v2.1", "cba733ceeff5aef5c32254f1d617fa62"),
        KnownBios("scph5000.bin", "NTSC-J", "v2.2", "57a06303dfa9cf9351222dfcbb4a29d9"),
        KnownBios("scph5000.bin", "NTSC-J", "v2.2 (DTL-H1100)", "ca5cfc321f916756e3f0effbfaeba13b"),
        KnownBios("scph1001.bin", "NTSC-U", "v2.0", "dc2b9bf8da62ec93e868cfd29f0d067d"),
        KnownBios("scph1001.bin", "NTSC-U", "v2.2", "924e392ed05558ffdb115408c263dccf"),
        KnownBios("scph1002.bin", "PAL", "v2.0", "54847e693405ffeb0359c6287434cbef"),
        KnownBios("scph1002.bin", "PAL", "v2.1", "417b34706319da7cf001e76e40136c23"),
        KnownBios("scph1002.bin", "PAL", "v2.2", "e2110b8a2b97a8e0b857a45d32f7e187"),
        KnownBios("scph5500.bin", "NTSC-J", "v3.0 (recommended)", "8dd7d5296a650fac7319bce665a6a53c"),
        KnownBios("scph5501.bin", "NTSC-U", "v3.0 (recommended)", "490f666e1afb15b7362b406ed1cea246"),
        KnownBios("scph5502.bin", "PAL", "v3.0 (recommended)", "32736f17079d0b2b7024407c39bd3050"),
        KnownBios("scph7000.bin", "NTSC-J", "v4.0", "8e4c14f567745eff2f0408c8129f72a6"),
        KnownBios("scph7000w.bin", "NTSC-J", "v4.1 (region-free)", "b84be139db3ee6cbd075630aa20a6553"),
        KnownBios("scph7001.bin", "NTSC-U", "v4.1", "1e68c231d0896b7eadcad1d7d8e76129"),
        KnownBios("scph7502.bin", "PAL", "v4.1", "b9d9a0286c33dc6b7237bb13cd46fdee"),
        KnownBios("scph100.bin", "NTSC-J", "v4.3 (PSone)", "8abc1b549a4a80954addc48ef02c4521"),
        KnownBios("scph101.bin", "NTSC-U", "v4.4 (PSone)", "9a09ab7e49b422c007e6d54d7c49b965"),
        KnownBios("scph101.bin", "NTSC-U", "v4.5 (PSone)", "6e3735ff4c7dc899ee98981385f6f3d0"),
        KnownBios("scph102.bin", "PAL", "v4.4 (PSone)", "b10f5e0e3d9eb60e5159690680b1e774"),
        KnownBios("scph102.bin", "PAL", "v4.5 (PSone)", "de93caec13d1a141a40a79f5c86168d6"),
        KnownBios("psxonpsp660.bin", "Region-free", "v4.5 (PSP)", "c53ca5908936d412331790f4426c6c33"),
        KnownBios("ps1_rom.bin", "Region-free", "v5.0 (PS3)", "81bbe60ba7a3d1cea1d48c14cbcc647b"),
    )

    /** Identify a candidate BIOS file by size + MD5; null when unrecognized. */
    fun identify(file: File): KnownBios? {
        if (!file.isFile || file.length() != SIZE_BYTES) return null
        val md5 = md5Of(file) ?: return null
        return KNOWN.firstOrNull { it.md5.equals(md5, ignoreCase = true) }
    }

    /** The best already-imported BIOS in [dir] (recognized ones first, else any 512 KB file). */
    fun detectInstalled(dir: File): KnownBios? {
        val files = dir.listFiles()?.filter { it.isFile } ?: return null
        for (f in files) identify(f)?.let { return it }
        return null
    }

    private fun md5Of(file: File): String? = runCatching {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { s ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = s.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
