package com.retrovault.feature.store.ads

import android.app.Activity
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the UMP consent flow, then initializes MobileAds — once per process, in the required order
 * (consent BEFORE any ad request). Ads are only enabled once [ConsentInformation.canRequestAds]
 * is true; a consent-update error leaves ads off. Uses AdMob TEST ids until the tier ships.
 */
object AdsInit {
    @Volatile
    var ready: Boolean = false
        private set

    private val started = AtomicBoolean(false)

    fun ensure(activity: Activity, onReady: () -> Unit) {
        if (ready) { onReady(); return }
        if (!started.compareAndSet(false, true)) return // another caller is already initializing
        val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (info.canRequestAds()) {
                        MobileAds.initialize(activity) {
                            ready = true
                            onReady()
                        }
                    }
                }
            },
            { /* consent info update failed — leave ads disabled this session */ },
        )
    }
}
