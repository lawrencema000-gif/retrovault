package com.retrovault.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String) {
    data object Boot : Destination("boot")
    data object Onboarding : Destination("onboarding")
    data object Library : Destination("library")
    data object Saves : Destination("saves")
    data object Controls : Destination("controls")
    data object Settings : Destination("settings?gameKey={gameKey}") {
        /** Per-game settings entry (from the game-detail Settings tile). */
        fun create(gameKey: String) = "settings?gameKey=$gameKey"
    }
    data object Detail : Destination("detail/{gameId}") {
        fun create(gameId: String) = "detail/$gameId"
    }
}

data class TopLevel(val destination: Destination, val label: String, val icon: ImageVector)

val topLevelDestinations = listOf(
    TopLevel(Destination.Library, "Library", Icons.Filled.GridView),
    TopLevel(Destination.Saves, "Saves", Icons.Filled.Save),
    TopLevel(Destination.Controls, "Controls", Icons.Filled.VideogameAsset),
    TopLevel(Destination.Settings, "Settings", Icons.Filled.Tune),
)
