package com.retrovault.feature.store.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * foss build shows no ads — this is an empty composable. Its signature is byte-identical to the
 * full-flavor [AdBanner] so main chrome code compiles against either variant.
 */
@Composable
fun AdBanner(isGold: Boolean, modifier: Modifier = Modifier) {
    // intentionally empty — the foss flavor ships zero ads and zero proprietary deps
}
