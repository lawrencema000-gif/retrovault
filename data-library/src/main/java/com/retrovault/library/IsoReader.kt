package com.retrovault.library

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal ISO9660 reader for locating files inside a PSP disc image (e.g.
 * `PSP_GAME/PARAM.SFO`, `PSP_GAME/ICON0.PNG`). Plain 2048-byte-sector ISO only.
 *
 * NOTE: CSO/CHD compressed images are decompressed on the native side (libchdr, added with
 * the core pass-through); this reader handles decompressed/plain `.iso`.
 */
class IsoReader(private val raf: RandomAccessFile) {

    companion object {
        private const val SECTOR = 2048
        private const val PVD_LBA = 16
    }

    private data class Entry(val lba: Long, val size: Long, val isDir: Boolean)

    /** Read a file by absolute path segments, e.g. ["PSP_GAME", "PARAM.SFO"]. */
    fun readFile(vararg path: String): ByteArray? {
        val root = rootDirectory() ?: return null
        var current: Entry = root
        for ((i, seg) in path.withIndex()) {
            val entry = findInDir(current, seg, wantDir = i < path.lastIndex) ?: return null
            current = entry
        }
        if (current.isDir) return null
        return readExtent(current.lba, current.size.toInt())
    }

    private fun rootDirectory(): Entry? {
        val pvd = readExtent(PVD_LBA.toLong(), SECTOR) ?: return null
        if (pvd.size < 190 || pvd[0].toInt() != 1) return null // type 1 = Primary Volume Descriptor
        // root directory record is 34 bytes at offset 156
        return parseDirRecord(pvd, 156)
    }

    private fun findInDir(dir: Entry, name: String, wantDir: Boolean): Entry? {
        val data = readExtent(dir.lba, dir.size.toInt()) ?: return null
        var pos = 0
        val target = name.uppercase()
        while (pos < data.size) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) {
                // advance to next sector boundary
                val next = ((pos / SECTOR) + 1) * SECTOR
                if (next <= pos) break
                pos = next
                continue
            }
            val entry = parseDirRecord(data, pos) ?: break
            val idLen = data[pos + 32].toInt() and 0xFF
            val rawId = String(data, pos + 33, idLen, Charsets.US_ASCII)
            val id = rawId.substringBefore(';').uppercase()
            if (id == target && entry.isDir == wantDir) return entry
            pos += len
        }
        return null
    }

    private fun parseDirRecord(b: ByteArray, off: Int): Entry? {
        if (off + 34 > b.size) return null
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        val lba = bb.getInt(off + 2).toLong() and 0xFFFFFFFFL
        val size = bb.getInt(off + 10).toLong() and 0xFFFFFFFFL
        val flags = b[off + 25].toInt()
        return Entry(lba, size, isDir = flags and 0x02 != 0)
    }

    private fun readExtent(lba: Long, size: Int): ByteArray? {
        if (lba < 0 || size <= 0 || size > 64 * 1024 * 1024) return null
        val start = lba * SECTOR
        if (start + size > raf.length()) return null
        raf.seek(start)
        val buf = ByteArray(size)
        raf.readFully(buf)
        return buf
    }
}
