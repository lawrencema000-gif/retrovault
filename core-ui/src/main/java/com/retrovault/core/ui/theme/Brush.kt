package com.retrovault.core.ui.theme

import androidx.compose.ui.graphics.Brush

/** Primary CTA gradient (blue -> teal). */
val PulsarAccentBrush = Brush.linearGradient(listOf(PulsarPrimary, PulsarTeal))

/** Blue depth gradient (avatar tiles, selected chips). */
val PulsarBlueBrush = Brush.linearGradient(listOf(PulsarPrimary, PulsarPrimaryDeep))

/** App background wash (top glow -> deep black). */
val PulsarBackgroundBrush = Brush.verticalGradient(listOf(PulsarBgTop, PulsarBg, PulsarBgDeep))
