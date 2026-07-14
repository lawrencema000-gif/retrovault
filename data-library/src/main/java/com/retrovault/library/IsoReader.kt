package com.retrovault.library

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal ISO9660 reader for locating files inside a disc image (e.g. PSP `PSP_GAME/PARAM.SFO`,
 * PS1 `SYSTEM.CNF`). Physical layout (plain 2048 vs raw 2352 CD sectors) is abstracted behind
 * [DiscSectorSource]; the [RandomAccessFile] constructor keeps the original plain-2048 behavior
 * for PSP callers.
 *
 * NOTE: CSO/CHD compressed images are decompressed on the native side (libchdr, added with
 * the core pass-through); this reader handles uncompressed images.
 */
class IsoReader private constructor(
    private val raf: RandomAccessFile?,
    private val source: DiscSectorSource?,
) {

    constructor(raf: RandomAccessFile) : this(raf, null)
    constructor(source: DiscSectorSource) : this(null, source)

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
        source?.let { src ->
            val sectors = (size + SECTOR - 1) / SECTOR
            val data = src.read(lba, sectors) ?: return null
            if (data.size < size) return null
            return if (data.size == size) data else data.copyOf(size)
        }
        val r = raf ?: return null
        val start = lba * SECTOR
        if (start + size > r.length()) return null
        r.seek(start)
        val buf = ByteArray(size)
        r.readFully(buf)
        return buf
    }
}
