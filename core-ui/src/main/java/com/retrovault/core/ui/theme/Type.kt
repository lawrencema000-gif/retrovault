package com.retrovault.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.retrovault.core.ui.R

/** Display / headings font (techy). */
val ChakraPetch = FontFamily(
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

/** Body / UI font. */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private val d = Typography()

/** Display/headline/title use Chakra Petch; body/label use Manrope. */
val PulsarTypography = Typography(
    displayLarge = d.displayLarge.copy(fontFamily = ChakraPetch),
    displayMedium = d.displayMedium.copy(fontFamily = ChakraPetch),
    displaySmall = d.displaySmall.copy(fontFamily = ChakraPetch),
    headlineLarge = d.headlineLarge.copy(fontFamily = ChakraPetch),
    headlineMedium = d.headlineMedium.copy(fontFamily = ChakraPetch),
    headlineSmall = d.headlineSmall.copy(fontFamily = ChakraPetch),
    titleLarge = d.titleLarge.copy(fontFamily = ChakraPetch),
    titleMedium = d.titleMedium.copy(fontFamily = ChakraPetch),
    titleSmall = d.titleSmall.copy(fontFamily = ChakraPetch),
    bodyLarge = d.bodyLarge.copy(fontFamily = Manrope),
    bodyMedium = d.bodyMedium.copy(fontFamily = Manrope),
    bodySmall = d.bodySmall.copy(fontFamily = Manrope),
    labelLarge = d.labelLarge.copy(fontFamily = Manrope),
    labelMedium = d.labelMedium.copy(fontFamily = Manrope),
    labelSmall = d.labelSmall.copy(fontFamily = Manrope),
)
