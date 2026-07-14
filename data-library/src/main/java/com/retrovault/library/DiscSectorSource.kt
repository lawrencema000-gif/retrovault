package com.retrovault.library

import java.io.File
import java.io.RandomAccessFile

/**
 * Maps ISO9660 logical 2048-byte sectors onto a disc image file, hiding the physical sector
 * layout. PSP `.iso` files are plain 2048-byte sectors; PS1 `.bin` dumps are RAW 2352-byte
 * CD sectors, where each logical sector's 2048 data bytes sit behind a physical header:
 *
 *   MODE1/2352:       12-byte sync + 3-byte address + 1-byte mode            -> data at +16
 *   MODE2/2352 FORM1: 12-byte sync + 3-byte address + 1-byte mode + 8-byte XA subheader -> +24
 *
 * PS1 game data tracks are almost always MODE2 FORM1 (CD-XA). The layout is sniffed from the
 * first sector's sync pattern (00 FF FF FF FF FF FF FF FF FF FF 00) + mode byte.
 */
class DiscSectorSource private constructor(
    private val raf: RandomAccessFile,
    private val physicalSectorSize: Int,
    private val dataOffset: Int,
) : AutoCloseable {

    /** Read [count] logical 2048-byte sectors starting at [lba]; null past EOF. */
    fun read(lba: Long, count: Int): ByteArray? {
        if (lba < 0 || count <= 0) return null
        val out = ByteArray(count * LOGICAL)
        for (i in 0 until count) {
            val physStart = (lba + i) * physicalSectorSize + dataOffset
            if (physStart + LOGICAL > raf.length()) {
                // Final partial sector: tolerate a short tail (some dumps truncate padding).
                if (i == 0) return null
                return out.copyOf(i * LOGICAL)
            }
            raf.seek(physStart)
            raf.readFully(out, i * LOGICAL, LOGICAL)
        }
        return out
    }

    override fun close() {
        runCatching { raf.close() }
    }

    companion object {
        const val LOGICAL = 2048
        private val SYNC = byteArrayOf(
            0x00, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x00
        )

        /** Open [file], sniffing plain-2048 vs raw-2352 (MODE1 or MODE2/XA) layout. */
        fun open(file: File): DiscSectorSource? {
            val raf = runCatching { RandomAccessFile(file, "r") }.getOrNull() ?: return null
            if (raf.length() < 2352) {
                raf.close()
                return null
            }
            val head = ByteArray(16)
            raf.seek(0)
            raf.readFully(head)
            val isRaw = SYNC.indices.all { head[it] == SYNC[it] }
            if (!isRaw) {
                // Plain 2048-byte logical sectors (e.g. a PSP .iso or a cooked PS1 iso).
                return DiscSectorSource(raf, physicalSectorSize = LOGICAL, dataOffset = 0)
            }
            return when (head[15].toInt() and 0xFF) {
                1 -> DiscSectorSource(raf, physicalSectorSize = 2352, dataOffset = 16)   // MODE1
                2 -> DiscSectorSource(raf, physicalSectorSize = 2352, dataOffset = 24)   // MODE2 XA FORM1
                else -> { raf.close(); null }
            }
        }
    }
}
