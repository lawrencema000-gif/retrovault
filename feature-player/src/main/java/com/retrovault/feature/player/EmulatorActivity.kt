package com.retrovault.feature.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.retrovault.core.model.GameSystem
import com.retrovault.core.ui.theme.PulsarTheme
import com.retrovault.emulator.EmulatorSession
import com.retrovault.emulator.LibretroBridge

/** Full-screen, landscape gameplay host. Runs in the :emu process (see manifest). */
class EmulatorActivity : ComponentActivity() {

    private val session = EmulatorSession()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Game"
        val system = runCatching { GameSystem.valueOf(intent.getStringExtra(EXTRA_SYSTEM).orEmpty()) }
            .getOrDefault(GameSystem.PSP)
        val gamePath = intent.getStringExtra(EXTRA_GAME_PATH)
        val coreOverride = intent.getStringExtra(EXTRA_CORE_OVERRIDE)

        if (LibretroBridge.available) {
            LibretroBridge.nativeInitSwappy(this)
        }

        if (coreOverride != null) {
            session.start(this, coreOverride, gamePath)
        } else {
            session.start(this, system, gamePath)
        }

        setContent {
            PulsarTheme {
                PlayerScreen(title = title, system = system, session = session, onQuit = { finish() })
            }
        }
    }

    override fun onDestroy() {
        session.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_GAME_ID = "gameId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SYSTEM = "system"
        const val EXTRA_GAME_PATH = "gamePath"

        /** Test/debug hook: load this core .so (by file name) instead of the system's core. */
        const val EXTRA_CORE_OVERRIDE = "coreOverride"

        fun intent(
            context: Context,
            gameId: String,
            title: String,
            system: GameSystem,
            gamePath: String? = null,
        ): Intent =
            Intent(context, EmulatorActivity::class.java).apply {
                putExtra(EXTRA_GAME_ID, gameId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SYSTEM, system.name)
                putExtra(EXTRA_GAME_PATH, gamePath)
            }
    }
}
