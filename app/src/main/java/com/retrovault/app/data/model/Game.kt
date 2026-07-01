package com.retrovault.app.data.model

/**
 * A catalog entry. In the shipping app these are served from Supabase; the
 * entries here are placeholder homebrew / public-domain examples.
 *
 * Only games we are legally allowed to distribute ever carry a [downloadUrl].
 * Commercial titles are never hosted — users import their own dumps instead.
 */
data class Game(
    val id: String,
    val title: String,
    val system: GameSystem,
    val developer: String,
    val description: String,
    val license: String,
    val boxArtUrl: String?,
    val downloadUrl: String?,
    val sizeBytes: Long,
    val homebrew: Boolean = true
)
