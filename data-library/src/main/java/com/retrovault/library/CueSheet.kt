package com.retrovault.library

import java.io.File

/**
 * Minimal .cue parser: enough to find the FIRST DATA TRACK's backing .bin for a PS1 disc.
 * Handles the common shapes:
 *
 *   FILE "Game (USA).bin" BINARY
 *     TRACK 01 MODE2/2352
 *       INDEX 01 00:00:00
 *   FILE "Game (USA) (Track 2).bin" BINARY
 *     TRACK 02 AUDIO
 *       ...
 *
 * Audio tracks are ignored — the game's filesystem (SYSTEM.CNF) lives on the data track.
 */
object CueSheet {

    data class DataTrack(val binFile: File, val mode: String)

    /** Resolve the first data track of [cueFile], or null if none parses/exists. */
    fun firstDataTrack(cueFile: File): DataTrack? {
        val lines = runCatching { cueFile.readLines() }.getOrNull() ?: return null
        var currentFile: String? = null
        for (raw in lines) {
            val line = raw.trim()
            when {
                line.startsWith("FILE", ignoreCase = true) -> {
                    // FILE "name.bin" BINARY  — the name may be quoted or bare.
                    currentFile = Regex("\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                        ?: line.split(Regex("\\s+")).getOrNull(1)
                }
                line.startsWith("TRACK", ignoreCase = true) -> {
                    val parts = line.split(Regex("\\s+"))
                    val mode = parts.getOrNull(2) ?: continue
                    if (!mode.equals("AUDIO", ignoreCase = true)) {
                        val name = currentFile ?: continue
                        // Cue references are relative to the cue's directory.
                        val bin = File(cueFile.parentFile, name)
                            .takeIf { it.isFile }
                            ?: File(cueFile.parentFile, File(name).name).takeIf { it.isFile }
                            ?: continue
                        return DataTrack(bin, mode.uppercase())
                    }
                }
            }
        }
        return null
    }
}
