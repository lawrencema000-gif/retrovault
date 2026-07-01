package com.retrovault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.retrovault.app.navigation.RetroVaultRoot
import com.retrovault.core.ui.theme.RetroVaultTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RetroVaultTheme {
                RetroVaultRoot()
            }
        }
    }
}
