package com.retrovault.data

import com.retrovault.core.model.Game
import com.retrovault.core.model.GameSystem

/**
 * Bundled offline fallback catalog. Used when the Supabase fetch fails so the store still renders.
 * Every entry represents a homebrew / public-domain / freely-redistributable game — never a
 * copyrighted commercial ROM.
 */
object CatalogRepository {

    private val games: List<Game> = listOf(
        Game(
            id = "psp-homebrew-1",
            title = "Sample PSP Homebrew",
            system = GameSystem.PSP,
            developer = "Homebrew Community",
            description = "Placeholder entry for a freely redistributable PSP homebrew title.",
            license = "GPL",
            boxArtUrl = null,
            downloadUrl = null,
            sizeBytes = 12L * 1024 * 1024
        ),
        Game(
            id = "ps1-homebrew-1",
            title = "Public Domain Racer",
            system = GameSystem.PS1,
            developer = "PSXDEV Community",
            description = "Placeholder PS1 homebrew racing demo released into the public domain.",
            license = "Public Domain",
            boxArtUrl = null,
            downloadUrl = null,
            sizeBytes = 8L * 1024 * 1024
        ),
        Game(
            id = "ps2-homebrew-1",
            title = "PS2 Homebrew Demo",
            system = GameSystem.PS2,
            developer = "PS2DEV Community",
            description = "Placeholder PS2 homebrew tech demo. Freely redistributable under its license.",
            license = "MIT",
            boxArtUrl = null,
            downloadUrl = null,
            sizeBytes = 48L * 1024 * 1024
        )
    )

    fun all(): List<Game> = games

    fun bySystem(system: GameSystem?): List<Game> =
        if (system == null) games else games.filter { it.system == system }

    fun byId(id: String): Game? = games.firstOrNull { it.id == id }
}
