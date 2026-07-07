package com.retrovault.emulator

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the RetroAchievements login token (which is password-equivalent) in Android
 * Keystore-backed [EncryptedSharedPreferences] — never plaintext. On next launch the stored token
 * drives a silent `nativeRaLoginWithToken`; an expired/invalid token clears the store and falls
 * back to the password screen.
 */
class RaCredentialStore(context: Context) {

    data class Credentials(val user: String, val token: String)

    private val prefs by lazy {
        val key = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "pulsar-ra-creds",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): Credentials? {
        val u = prefs.getString(KEY_USER, null) ?: return null
        val t = prefs.getString(KEY_TOKEN, null) ?: return null
        return Credentials(u, t)
    }

    fun save(user: String, token: String) {
        prefs.edit().putString(KEY_USER, user).putString(KEY_TOKEN, token).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_USER = "user"
        const val KEY_TOKEN = "token"
    }
}
