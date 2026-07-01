package com.retrovault.feature.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.retrovault.core.model.GameSystem
import com.retrovault.core.ui.theme.PulsarTheme

/** Full-screen, landscape gameplay host. Runs in the :emu process (see manifest). */
class EmulatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Game"
        val system = runCatching { GameSystem.valueOf(intent.getStringExtra(EXTRA_SYSTEM).orEmpty()) }
            .getOrDefault(GameSystem.PSP)

        setContent {
            PulsarTheme {
                PlayerScreen(title = title, system = system, onQuit = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_GAME_ID = "gameId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SYSTEM = "system"

        fun intent(context: Context, gameId: String, title: String, system: GameSystem): Intent =
            Intent(context, EmulatorActivity::class.java).apply {
                putExtra(EXTRA_GAME_ID, gameId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SYSTEM, system.name)
            }
    }
}
