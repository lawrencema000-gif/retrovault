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

    private var prefs: android.content.SharedPreferences? = null

    private var onboardingSeenState by mutableStateOf(false)
    private var oledBlackState by mutableStateOf(false)
    private var languageTagState by mutableStateOf("")

    val onboardingSeen: Boolean get() = onboardingSeenState
    val oledBlack: Boolean get() = oledBlackState

    /** BCP-47 language tag, or "" for the system default. */
    val languageTag: String get() = languageTagState

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        onboardingSeenState = p.getBoolean(KEY_ONBOARDED, false)
        oledBlackState = p.getBoolean(KEY_OLED, false)
        languageTagState = p.getString(KEY_LANG, "") ?: ""
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
}
