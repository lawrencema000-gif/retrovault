package com.retrovault.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object Library : Destination("library")
    data object Settings : Destination("settings")
    data object Detail : Destination("detail/{gameId}") {
        fun create(gameId: String) = "detail/$gameId"
    }
}

data class TopLevel(val destination: Destination, val label: String, val icon: ImageVector)

val topLevelDestinations = listOf(
    TopLevel(Destination.Home, "Store", Icons.Filled.Home),
    TopLevel(Destination.Library, "Library", Icons.Filled.VideogameAsset),
    TopLevel(Destination.Settings, "Settings", Icons.Filled.Settings)
)
