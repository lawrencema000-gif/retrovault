package com.retrovault.core.model

/**
 * A catalog entry. In the shipping app these are served from Supabase.
 *
 * Only games we are legally allowed to distribute ever carry a [downloadUrl].
 * Commercial titles are never hosted — users import their own dumps instead.
 */
data class Game(
    val id: String,
    val slug: String,
    val title: String,
    val system: GameSystem,
    val developer: String,
    val description: String,
    val license: String,
    /** Link to the license text / source repo — shown prominently for transparency. */
    val licenseUrl: String? = null,
    /** Canonical source (author repo / homepage) proving redistribution rights. */
    val sourceUrl: String? = null,
    val boxArtUrl: String?,
    val downloadUrl: String?,
    val sizeBytes: Long,
    /** True when the catalog hosts a legally-redistributable file for this title. */
    val downloadable: Boolean = false,
    val homebrew: Boolean = true
)
