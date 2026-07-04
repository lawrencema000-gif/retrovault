package com.retrovault.library

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads the PSP PBP container (EBOOT.PBP). Header = magic "\0PBP" + version + 8 section
 * offsets: [0] PARAM.SFO, [1] ICON0.PNG, [2] ICON1.PMF, [3] PIC0.PNG, [4] PIC1.PNG,
 * [5] SND0.AT3, [6] DATA.PSP, [7] DATA.PSAR. Each section spans [offset[i], offset[i+1]).
 */
class PbpReader private constructor(
    private val file: File,
    private val offsets: IntArray,
    private val fileLen: Long,
) {
    enum class Section(val index: Int) {
        PARAM_SFO(0), ICON0(1), ICON1(2), PIC0(3), PIC1(4), SND0(5), DATA_PSP(6), DATA_PSAR(7)
    }

    private fun sectionEnd(index: Int): Long {
        for (j in index + 1 until offsets.size) {
            if (offsets[j] > offsets[index]) return offsets[j].toLong()
        }
        return fileLen
    }

    fun read(section: Section): ByteArray? {
        val start = offsets[section.index].toLong()
        val end = sectionEnd(section.index)
        if (start <= 0 || end <= start || end > fileLen) return null
        val len = (end - start).toInt()
        if (len <= 0 || len > 64 * 1024 * 1024) return null
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val buf = ByteArray(len)
            raf.readFully(buf)
            return buf
        }
    }

    companion object {
        fun open(file: File): PbpReader? {
            if (!file.isFile || file.length() < 40) return null
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(40)
                raf.readFully(header)
                // magic bytes: 00 'P' 'B' 'P'
                if (header[0].toInt() != 0 || header[1].toInt().toChar() != 'P' ||
                    header[2].toInt().toChar() != 'B' || header[3].toInt().toChar() != 'P'
                ) return null
                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val offsets = IntArray(8) { bb.getInt(8 + it * 4) }
                return PbpReader(file, offsets, file.length())
            }
        }
    }
}
