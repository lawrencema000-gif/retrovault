package com.retrovault.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PulsarColorScheme = darkColorScheme(
    primary = PulsarPrimary,
    onPrimary = PulsarOnAccent,
    secondary = PulsarTeal,
    onSecondary = PulsarOnAccent,
    background = PulsarBg,
    onBackground = PulsarText,
    surface = PulsarBg,
    onSurface = PulsarText,
    surfaceVariant = PulsarSurfaceVariant,
    onSurfaceVariant = PulsarTextDim,
    outline = PulsarOutline,
    error = PulsarRed,
)

/** Pulsar dark theme — dark-navy base, blue→teal accents, Chakra Petch + Manrope. */
@Composable
fun PulsarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PulsarColorScheme,
        typography = PulsarTypography,
        content = content
    )
}
