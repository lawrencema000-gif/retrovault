package com.retrovault.app

import android.app.Application
import com.retrovault.core.ui.AppPrefs

/** Application entry point. Hosts app-wide singletons (DI, cores, etc.) as they are added. */
class RetroVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Load UI prefs early so theme (OLED) + onboarding gating are correct on first frame,
        // in both the main and :emu processes.
        AppPrefs.init(this)
    }
}
