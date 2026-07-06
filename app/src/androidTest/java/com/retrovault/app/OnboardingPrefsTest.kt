package com.retrovault.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.core.ui.AppPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P17: the onboarding-once gate and the appearance prefs (OLED theme, per-app language) persist
 * across process restarts (simulated by re-[AppPrefs.init]) — a new user sees onboarding once,
 * and their theme/language choices survive relaunches.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingPrefsTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun clearPrefs() {
        ctx.getSharedPreferences("pulsar-ui-prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun onboardingIsShownOnceThenRemembered() {
        clearPrefs()
        AppPrefs.init(ctx)
        assertFalse("fresh install must show onboarding", AppPrefs.onboardingSeen)

        AppPrefs.setOnboardingSeen(true)
        assertTrue(AppPrefs.onboardingSeen)

        // Simulate a fresh process: re-init reads persisted prefs.
        AppPrefs.init(ctx)
        assertTrue("onboarding must NOT reappear after being seen", AppPrefs.onboardingSeen)
    }

    @Test
    fun appearancePrefsPersist() {
        clearPrefs()
        AppPrefs.init(ctx)
        assertFalse(AppPrefs.oledBlack)
        assertEquals("", AppPrefs.languageTag)

        AppPrefs.setOledBlack(true)
        AppPrefs.setLanguageTag("ja")

        AppPrefs.init(ctx) // fresh process
        assertTrue("OLED choice must persist", AppPrefs.oledBlack)
        assertEquals("language choice must persist", "ja", AppPrefs.languageTag)

        // Reset so a subsequent run/app starts clean.
        clearPrefs()
        AppPrefs.init(ctx)
    }
}
