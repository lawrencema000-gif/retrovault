package com.retrovault.core.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Process-wide UI preferences backed by SharedPreferences, exposed as Compose state so toggling
 * one recomposes readers (theme, settings). Call [init] once from the Application/Activity.
 *
 * The backing state is private and read through public getters — reading a getter that touches
 * a MutableState still tracks recomposition, and it avoids a generated-setter JVM clash.
 */
object AppPrefs {

    private const val FILE = "pulsar-ui-prefs"
    private const val KEY_ONBOARDED = "onboarding_seen"
    private const val KEY_OLED = "oled_black"
    private const val KEY_LANG = "language_tag"
    private const val KEY_CRASH_OPT_IN = "crash_reports_opt_in"

    private var prefs: android.content.SharedPreferences? = null

    private var onboardingSeenState by mutableStateOf(false)
    private var oledBlackState by mutableStateOf(false)
    private var languageTagState by mutableStateOf("")
    private var crashReportsOptInState by mutableStateOf(false)

    val onboardingSeen: Boolean get() = onboardingSeenState
    val oledBlack: Boolean get() = oledBlackState

    /** BCP-47 language tag, or "" for the system default. */
    val languageTag: String get() = languageTagState

    /** Opt-in crash reporting (default OFF; only the `full` flavor has a reporter at all). */
    val crashReportsOptIn: Boolean get() = crashReportsOptInState

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        onboardingSeenState = p.getBoolean(KEY_ONBOARDED, false)
        oledBlackState = p.getBoolean(KEY_OLED, false)
        languageTagState = p.getString(KEY_LANG, "") ?: ""
        crashReportsOptInState = p.getBoolean(KEY_CRASH_OPT_IN, false)
    }

    fun setOnboardingSeen(value: Boolean) {
        onboardingSeenState = value
        prefs?.edit()?.putBoolean(KEY_ONBOARDED, value)?.apply()
    }

    fun setOledBlack(value: Boolean) {
        oledBlackState = value
        prefs?.edit()?.putBoolean(KEY_OLED, value)?.apply()
    }

    fun setLanguageTag(tag: String) {
        languageTagState = tag
        prefs?.edit()?.putString(KEY_LANG, tag)?.apply()
    }

    fun setCrashReportsOptIn(value: Boolean) {
        crashReportsOptInState = value
        prefs?.edit()?.putBoolean(KEY_CRASH_OPT_IN, value)?.apply()
    }
}
