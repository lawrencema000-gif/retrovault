package com.retrovault.feature.store.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.retrovault.feature.store.findActivity

/**
 * AdMob banner for the library/store chrome (full flavor only — never in-game, no interstitials).
 * Renders nothing when the user has Gold (NO_ADS) or before UMP consent + MobileAds init complete.
 * Uses the AdMob TEST banner unit; swap for the real unit id before shipping the ad tier.
 */
@Composable
fun AdBanner(isGold: Boolean, modifier: Modifier = Modifier) {
    if (isGold) return
    val activity = LocalContext.current.findActivity() ?: return
    var ready by remember { mutableStateOf(AdsInit.ready) }
    LaunchedEffect(Unit) { AdsInit.ensure(activity) { ready = true } }
    if (!ready) return
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-3940256099942544/6300978111" // AdMob TEST banner unit
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
