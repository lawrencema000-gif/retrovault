package com.retrovault.data

import com.retrovault.core.model.Game
import com.retrovault.core.model.GameSystem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** PostgREST row shape for `public.games`. */
@Serializable
data class GameDto(
    val id: String,
    @SerialName("system_id") val systemId: String,
    val title: String,
    val slug: String,
    val developer: String? = null,
    val description: String? = null,
    val license: String,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("download_size_bytes") val downloadSizeBytes: Long = 0,
    @SerialName("box_art_url") val boxArtUrl: String? = null,
)

fun GameDto.toDomain(): Game = Game(
    id = id,
    title = title,
    system = systemId.toGameSystem(),
    developer = developer ?: "Unknown",
    description = description.orEmpty(),
    license = license.toLicenseLabel(),
    boxArtUrl = boxArtUrl,
    downloadUrl = downloadUrl,
    sizeBytes = downloadSizeBytes,
)

private fun String.toGameSystem(): GameSystem = when (this) {
    "ps1" -> GameSystem.PS1
    "ps2" -> GameSystem.PS2
    else -> GameSystem.PSP
}

private fun String.toLicenseLabel(): String = when (this) {
    "public_domain" -> "Public Domain"
    "freeware" -> "Freeware"
    "cc" -> "CC BY"
    "gpl" -> "GPL"
    "mit" -> "MIT"
    "bsd" -> "BSD"
    "homebrew" -> "Homebrew"
    "user_owned" -> "Your copy"
    "demo" -> "Demo"
    else -> replaceFirstChar { it.uppercase() }
}
