package com.retrovault.app.data.repository

import com.retrovault.app.data.model.Game
import com.retrovault.app.data.model.GameSystem

/**
 * Temporary in-memory catalog of placeholder legal titles. This will be replaced
 * by a Supabase-backed repository once the backend is provisioned. Every entry
 * represents a homebrew / public-domain / freely-redistributable game — never a
 * copyrighted commercial ROM.
 */
object CatalogRepository {

    private val games: List<Game> = listOf(
        Game(
            id = "psp-homebrew-1",
            title = "Sample PSP Homebrew",
            system = GameSystem.PSP,
            developer = "Homebrew Community",
            description = "Placeholder entry for a freely redistributable PSP homebrew title. " +
                "Real catalog data will be served from the backend once provisioned.",
            license = "GPLv2",
            boxArtUrl = null,
            downloadUrl = null,
            sizeBytes = 12L * 1024 * 1024
        ),
        Game(
            id = "psp-homebrew-2",
            title = "Open Card Battler",
            system = GameSystem.PSP,
            developer = "Open Source",
            description = "Placeholder open-source card game for PSP. Redistribution permitted under its license.",
            license = "GPLv3",
            boxArtUrl = null,
            downloadUrl = null,
            sizeBytes = 34L * 1024 * 1024
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
            id = "ps1-homebrew-2",
            title = "Net Yaroze Sampler",
            system = GameSystem.PS1,
            developer = "Yaroze Authors",
            description = "Placeholder collection of freely shared Net Yaroze homebrew games.",
            license = "Freeware (redistribution permitted)",
            boxArtUrl = null,
            downloadUrl = null,
            sizeBytes = 5L * 1024 * 1024
        ),
        Game(
            id = "ps2-homebrew-1",
            title = "PS2 Homebrew Demo",
            system = GameSystem.PS2,
            developer = "PS2DEV Community",
            description = "Placeholder PS2 homebrew tech demo. Freely redistributable under its stated license.",
            license = "MIT",
            boxArtUrl = null,
            downloadUrl = null,
            sizeBytes = 48L * 1024 * 1024
        ),
        Game(
            id = "ps2-homebrew-2",
            title = "Open Puzzle Deluxe",
            system = GameSystem.PS2,
            developer = "Indie (CC-BY)",
            description = "Placeholder Creative Commons puzzle game the developer permits redistributing.",
            license = "CC BY 4.0",
            boxArtUrl = null,
            downloadUrl = null,
            sizeBytes = 96L * 1024 * 1024
        )
    )

    fun all(): List<Game> = games

    fun bySystem(system: GameSystem?): List<Game> =
        if (system == null) games else games.filter { it.system == system }

    fun byId(id: String): Game? = games.firstOrNull { it.id == id }
}
