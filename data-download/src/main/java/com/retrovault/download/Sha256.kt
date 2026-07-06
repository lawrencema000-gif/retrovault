package com.retrovault.download

import java.io.File
import java.security.MessageDigest

/** SHA-256 integrity verification for downloaded game archives. */
object Sha256 {

    fun of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { s ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = s.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** True when [file]'s hash matches [expected] (case-insensitive hex). */
    fun matches(file: File, expected: String): Boolean =
        of(file).equals(expected, ignoreCase = true)
}
