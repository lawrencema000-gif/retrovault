package com.retrovault.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.retrovault.core.ui.AppPrefs

private fun pulsarColors(background: Color) = darkColorScheme(
    primary = PulsarPrimary,
    onPrimary = PulsarOnAccent,
    secondary = PulsarTeal,
    onSecondary = PulsarOnAccent,
    background = background,
    onBackground = PulsarText,
    surface = background,
    onSurface = PulsarText,
    surfaceVariant = PulsarSurfaceVariant,
    onSurfaceVariant = PulsarTextDim,
    outline = PulsarOutline,
    error = PulsarRed,
)

private val PulsarColorScheme = pulsarColors(PulsarBg)
private val PulsarOledScheme = pulsarColors(Color.Black)

/**
 * Pulsar dark theme — dark-navy base, blue→teal accents, Chakra Petch + Manrope.
 * [oledBlack] swaps the base to true black for OLED power savings (defaults to the pref).
 */
@Composable
fun PulsarTheme(oledBlack: Boolean = AppPrefs.oledBlack, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (oledBlack) PulsarOledScheme else PulsarColorScheme,
        typography = PulsarTypography,
        content = content
    )
}
