package com.retrovault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.retrovault.app.navigation.RetroVaultRoot
import com.retrovault.core.ui.AppPrefs
import com.retrovault.core.ui.theme.PulsarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.init(this)

        // P27: launcher-shortcut trampoline — "Continue <game>" resumes the last-played game.
        // (Shortcuts must target an exported activity; the emulator activity is internal.)
        if (intent?.getBooleanExtra("continueLastPlayed", false) == true) {
            val last = com.retrovault.library.RecentPlays(this).last()
            if (last != null) {
                val system = runCatching { com.retrovault.core.model.GameSystem.valueOf(last.system) }
                    .getOrDefault(com.retrovault.core.model.GameSystem.PSP)
                startActivity(
                    com.retrovault.feature.player.EmulatorActivity.intent(
                        this, last.gameKey, last.title, system, last.path, resume = true,
                    )
                )
                finish()
                return
            }
        }

        enableEdgeToEdge()
        setContent {
            // Reads AppPrefs.oledBlack (Compose state) so the theme flips live when toggled.
            PulsarTheme(oledBlack = AppPrefs.oledBlack) {
                RetroVaultRoot()
            }
        }
    }
}
