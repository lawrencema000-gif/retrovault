package com.retrovault.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RetroVaultColorScheme = darkColorScheme(
    primary = RvPrimary,
    onPrimary = RvOnPrimary,
    secondary = RvSecondary,
    background = RvBackground,
    onBackground = RvOnBackground,
    surface = RvSurface,
    onSurface = RvOnBackground,
    surfaceVariant = RvSurfaceVariant,
    onSurfaceVariant = RvOnSurfaceVariant,
    outline = RvOutline
)

/** Dark-first theme for the storefront. */
@Composable
fun RetroVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RetroVaultColorScheme,
        typography = RetroVaultTypography,
        content = content
    )
}
