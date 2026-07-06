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
        enableEdgeToEdge()
        setContent {
            // Reads AppPrefs.oledBlack (Compose state) so the theme flips live when toggled.
            PulsarTheme(oledBlack = AppPrefs.oledBlack) {
                RetroVaultRoot()
            }
        }
    }
}
