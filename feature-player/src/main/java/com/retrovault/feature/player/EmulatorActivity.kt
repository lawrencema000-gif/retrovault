package com.retrovault.feature.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.retrovault.core.model.GameSystem
import com.retrovault.core.ui.theme.PulsarTheme
import com.retrovault.emulator.CoreStatus
import com.retrovault.emulator.EmulatorSession
import com.retrovault.emulator.LibretroBridge
import com.retrovault.input.GamepadMapper
import com.retrovault.input.HotplugMonitor
import com.retrovault.input.InputHub
import com.retrovault.input.RemapStore
import com.retrovault.input.VirtKey
import com.retrovault.saves.SaveStateManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Full-screen, landscape gameplay host. Runs in the :emu process (see manifest). */
class EmulatorActivity : ComponentActivity() {

    private val session = EmulatorSession()
    private val inputHub = InputHub()
    private lateinit var remapStore: RemapStore
    private lateinit var gamepad: GamepadMapper
    private var hotplug: HotplugMonitor? = null
    private var saveStates: SaveStateManager? = null

    // Player UI state driven from outside Compose (hotplug, virtkeys).
    private var gamepadConnected by mutableStateOf(false)
    private var pausedByHotplug by mutableStateOf(false)
    private var menuRequests by mutableIntStateOf(0)

    // External gamepads dispatch through the Activity — captured here even with the
    // Compose chrome present, then written into the same native snapshot as touch.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (gamepad.isGamepadEvent(event) && gamepad.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (gamepad.onMotionEvent(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        remapStore = RemapStore(applicationContext)
        gamepad = GamepadMapper(
            hub = inputHub,
            profileResolver = { device -> remapStore.resolve(applicationContext, device) },
            onVirtKey = ::onVirtKey,
        )

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Game"
        val system = runCatching { GameSystem.valueOf(intent.getStringExtra(EXTRA_SYSTEM).orEmpty()) }
            .getOrDefault(GameSystem.PSP)
        val gamePath = intent.getStringExtra(EXTRA_GAME_PATH)
        val coreOverride = intent.getStringExtra(EXTRA_CORE_OVERRIDE)

        if (LibretroBridge.available) {
            LibretroBridge.nativeInitSwappy(this)
        }

        // Resolve settings (4-layer: defaults → gamedb → device-class → user) and push the
        // core-variable-backed ones BEFORE the core loads (some are read only at load).
        val gameKeyForSettings = intent.getStringExtra(EXTRA_GAME_ID)
        val settings = com.retrovault.settings.SettingsResolver(applicationContext)
        settings.applyToCore(gameKeyForSettings)

        if (coreOverride != null) {
            session.start(this, coreOverride, gamePath)
        } else {
            session.start(this, system, gamePath)
        }

        // Slot manager keyed by game id (falls back to file name for imported games).
        val gameKey = intent.getStringExtra(EXTRA_GAME_ID)
            ?: gamePath?.substringAfterLast('/')?.substringBeforeLast('.')
        if (gameKey != null && gamePath != null) {
            saveStates = SaveStateManager(applicationContext, gameKey)
        }

        // Rewind ring: 256MB budget, snapshot every 2s (PSP states ≈ 42MB → ~6 snapshots =
        // ~12s of rewind; tiny cores get minutes). Gated by the settings framework.
        if (settings.resolve(com.retrovault.settings.PspSettings.REWIND_ENABLED, gameKeyForSettings).asBoolean) {
            session.enableRewind(256L * 1024 * 1024, intervalFrames = 120)
        }

        // "Continue" from the library: restore the auto-save once the game has booted.
        if (intent.getBooleanExtra(EXTRA_RESUME, false) && saveStates != null) {
            lifecycleScope.launch {
                val deadline = System.currentTimeMillis() + 30_000
                while (System.currentTimeMillis() < deadline &&
                    LibretroBridge.nativeFramesPresented() < 60
                ) {
                    kotlinx.coroutines.delay(150)
                }
                saveStates?.load(SaveStateManager.AUTO_SLOT)
            }
        }

        // Hotplug: pad connects → hide touch overlay; the pad disconnecting → auto-pause so
        // a dead battery never loses a run.
        hotplug = HotplugMonitor(
            this,
            onGamepadConnected = {
                gamepadConnected = true
                gamepad.invalidateProfiles()
            },
            onGamepadDisconnected = {
                gamepad.invalidateProfiles()
                val stillConnected = hotplug?.anyGamepadConnected() == true
                if (!stillConnected && gamepadConnected) {
                    gamepadConnected = false
                    if (session.status == CoreStatus.RUNNING) {
                        session.paused = true
                        pausedByHotplug = true
                    }
                }
            },
        ).also { it.start() }

        setContent {
            PulsarTheme {
                PlayerScreen(
                    title = title,
                    system = system,
                    session = session,
                    inputHub = inputHub,
                    onQuit = { finish() },
                    gamepadConnected = gamepadConnected,
                    pausedExternally = pausedByHotplug,
                    onResumeExternal = {
                        session.paused = false
                        pausedByHotplug = false
                    },
                    menuRequests = menuRequests,
                    onSaveState = { quickSlotOp(save = true) },
                    onLoadState = { quickSlotOp(save = false) },
                    saveStates = saveStates,
                    onScreenshot = {
                        lifecycleScope.launch {
                            com.retrovault.saves.Screenshots.capture(
                                applicationContext, "${gameKey ?: "game"}-${System.currentTimeMillis()}"
                            )
                        }
                    },
                )
            }
        }
    }

    private fun onVirtKey(key: VirtKey, down: Boolean) {
        if (!down) return
        when (key) {
            VirtKey.MENU -> menuRequests++
            VirtKey.SAVE_STATE -> quickSlotOp(save = true)
            VirtKey.LOAD_STATE -> quickSlotOp(save = false)
            VirtKey.FAST_FORWARD -> Unit // P10
            VirtKey.SCREENSHOT -> Unit   // P10
        }
    }

    private fun quickSlotOp(save: Boolean) {
        val mgr = saveStates ?: return
        lifecycleScope.launch {
            if (save) mgr.save(QUICK_SLOT) else mgr.load(QUICK_SLOT)
        }
    }

    override fun onDestroy() {
        hotplug?.stop()
        // Auto-save before teardown: the run loop is still alive (backgrounded branch
        // executes state ops even with the Surface detached). Blocking is intentional —
        // the state must hit disk before the process can be killed.
        val mgr = saveStates
        if (mgr != null && session.status == CoreStatus.RUNNING) {
            session.paused = false // a frozen loop still services ops, but never save mid-pause transition
            runBlocking { runCatching { mgr.save(SaveStateManager.AUTO_SLOT) } }
        }
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

        /** Restore the auto-save slot once the game boots ("Continue" library action). */
        const val EXTRA_RESUME = "resumeAuto"

        private const val QUICK_SLOT = 1

        fun intent(
            context: Context,
            gameId: String,
            title: String,
            system: GameSystem,
            gamePath: String? = null,
            resume: Boolean = false,
        ): Intent =
            Intent(context, EmulatorActivity::class.java).apply {
                putExtra(EXTRA_GAME_ID, gameId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SYSTEM, system.name)
                putExtra(EXTRA_GAME_PATH, gamePath)
                putExtra(EXTRA_RESUME, resume)
            }
    }
}
