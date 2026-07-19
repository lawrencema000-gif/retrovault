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
import androidx.compose.foundation.layout.fillMaxSize
import com.retrovault.core.model.GameSystem
import com.retrovault.core.ui.theme.PulsarTheme
import com.retrovault.data.CompatReporter
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
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
class EmulatorActivity : ComponentActivity() {

    private val session = EmulatorSession()
    private val inputHub = InputHub()
    private lateinit var remapStore: RemapStore
    private lateinit var gamepad: GamepadMapper
    private var hotplug: HotplugMonitor? = null
    private var saveStates: SaveStateManager? = null
    private var saveStatesRecommended = true
    private var cheatManager: com.retrovault.cheats.CheatManager? = null

    // Player UI state driven from outside Compose (hotplug, virtkeys).
    private var gamepadConnected by mutableStateOf(false)
    private var pausedByHotplug by mutableStateOf(false)
    private var menuRequests by mutableIntStateOf(0)
    private var showCompatPrompt by mutableStateOf(false)
    private var showCheats by mutableStateOf(false)
    private var playerToast by mutableStateOf<String?>(null)
    private var cheatTick by mutableIntStateOf(0)

    // Compat reporting context (P13).
    private var gameSerial: String? = null
    private var playKey: String? = null
    private var sessionStartMs = 0L
    private val appVersion: String by lazy {
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull() ?: "dev"
    }

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

        // A different-game relaunch (see onNewIntent) parks its intent here — adopt it before
        // any extra is read.
        pendingRelaunch?.let { setIntent(it); pendingRelaunch = null }

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

        // Identify the disc serial (cheap header read) — it keys the GameDB layer and the
        // core's own compat.ini flags.
        val serial: String? = gamePath?.let { path ->
            runCatching {
                com.retrovault.library.GameIdentifier
                    .identify(applicationContext, java.io.File(path), system)
                    ?.takeIf { !it.fakeId }?.serial
            }.getOrNull()
        }

        // Resolve settings (4-layer: defaults → gamedb → device-class → user) and push the
        // core-variable-backed ones BEFORE the core loads (some are read only at load).
        val gameKeyForSettings = intent.getStringExtra(EXTRA_GAME_ID)
        val settings = com.retrovault.settings.SettingsResolver(applicationContext) {
            com.retrovault.settings.GameDb.settingsFor(applicationContext, serial)
        }
        settings.applyToCore(gameKeyForSettings)
        // Host present-pass settings (rotation / scale / post-shader stack) — applied per frame.
        settings.applyDisplay(gameKeyForSettings)

        // GameDB flag: some titles corrupt when save-stated — respect PPSSPP's judgment.
        saveStatesRecommended =
            !com.retrovault.settings.GameDb.hasFlag(applicationContext, serial, "SaveStatesNotRecommended")
        gameSerial = serial
        sessionStartMs = System.currentTimeMillis()

        // PS1 (P23): the SwanStation core looks for BIOS files at the TOP LEVEL of the libretro
        // system dir (non-recursive). Stage any imported dumps from bios/PS1/ into it — the core
        // falls back to its built-in OpenBIOS when none exist, so this is best-effort.
        if (system == GameSystem.PS1) {
            runCatching {
                val sysDir = java.io.File(filesDir, "system").apply { mkdirs() }
                com.retrovault.download.RomStorage.biosDir(applicationContext, GameSystem.PS1)
                    .listFiles()?.filter { it.isFile && it.length() > 0 }?.forEach { bios ->
                        val dest = java.io.File(sysDir, bios.name)
                        if (!dest.exists() || dest.length() != bios.length()) {
                            bios.copyTo(dest, overwrite = true)
                        }
                    }
            }
        }

        if (coreOverride != null) {
            session.start(this, coreOverride, gamePath)
        } else {
            session.start(this, system, gamePath)
        }

        // PS1 (P24): pick the emulated pad. SwanStation has NO core option for this — it is
        // selected via retro_set_controller_port_device (digital=1, DualShock=261), and the core
        // defaults to the digital pad. ForceAnalog makes DualShock sticks live immediately
        // (no in-game analog-toggle combo needed).
        if (system == GameSystem.PS1 && LibretroBridge.available) {
            val padChoice = settings
                .resolve(com.retrovault.settings.Ps1Settings.PS1_CONTROLLER, gameKeyForSettings).value
            val dualshock = padChoice == "dualshock"
            LibretroBridge.nativeSetControllerDevice(
                if (dualshock) com.retrovault.settings.Ps1Settings.DEVICE_DUALSHOCK
                else com.retrovault.settings.Ps1Settings.DEVICE_DIGITAL
            )
            LibretroBridge.nativeSetCoreVariable(
                "swanstation_Controller1_ForceAnalog", if (dualshock) "true" else "false"
            )
        }

        // Slot manager keyed by game id (falls back to file name for imported games).
        val gameKey = intent.getStringExtra(EXTRA_GAME_ID)
            ?: gamePath?.substringAfterLast('/')?.substringBeforeLast('.')
        if (gameKey != null && gamePath != null) {
            saveStates = SaveStateManager(applicationContext, gameKey)
        }
        playKey = gameKey

        // P27: this becomes the "Continue playing" target — record it, refresh the home-screen
        // widget (broadcast by component name; the widget lives in the app module), and push a
        // launcher shortcut that resumes via the exported MainActivity trampoline.
        if (gameKey != null && gamePath != null) {
            runCatching {
                com.retrovault.library.RecentPlays(applicationContext)
                    .recordSession(gameKey, title, system.name, gamePath)
                val component = android.content.ComponentName(
                    packageName, "com.retrovault.app.widget.ContinueWidgetReceiver"
                )
                val mgr = android.appwidget.AppWidgetManager.getInstance(this)
                val ids = mgr.getAppWidgetIds(component)
                if (ids.isNotEmpty()) {
                    sendBroadcast(Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                        setComponent(component)
                        putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    })
                }
                val shortcutIntent = Intent(Intent.ACTION_VIEW)
                    .setClassName(packageName, "com.retrovault.app.MainActivity")
                    .putExtra("continueLastPlayed", true)
                val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(this, "continue")
                    .setShortLabel("Continue $title".take(24))
                    .setLongLabel("Continue playing $title")
                    .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(
                        this, android.R.drawable.ic_media_play))
                    .setIntent(shortcutIntent)
                    .build()
                androidx.core.content.pm.ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
            }
        }

        // Rewind ring: 256MB budget, snapshot every 2s (PSP states ≈ 42MB → ~6 snapshots =
        // ~12s of rewind; tiny cores get minutes). Gated by the settings framework.
        if (settings.resolve(com.retrovault.settings.PspSettings.REWIND_ENABLED, gameKeyForSettings).asBoolean) {
            session.enableRewind(256L * 1024 * 1024, intervalFrames = 120)
        }

        // Cheats: apply this game's enabled cheats (user-imported cheat.db, by serial).
        cheatManager = com.retrovault.cheats.CheatManager(applicationContext)
        applyCheats()

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
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize()
                ) {
                    PlayerScreen(
                        title = title,
                        system = system,
                        session = session,
                        inputHub = inputHub,
                        onQuit = { requestQuit() },
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
                                // No share-sheet hijack mid-game: confirm quietly; the shot is in
                                // the gallery (Photos > Pulsar) where sharing lives anyway.
                                val shot = com.retrovault.saves.Screenshots.capture(
                                    applicationContext, "${gameKey ?: "game"}-${System.currentTimeMillis()}"
                                )
                                playerToast = when {
                                    shot == null -> "Screenshot failed"
                                    shot.galleryUri != null -> "Saved to Photos › Pulsar"
                                    else -> "Screenshot saved"
                                }
                            }
                        },
                        onCheats = { showCheats = true },
                        toast = playerToast,
                        onToastDone = { playerToast = null },
                    )

                    val mgr = cheatManager
                    if (showCheats && mgr != null) {
                        // cheatTick re-reads entries after a toggle.
                        val entries = androidx.compose.runtime.remember(cheatTick, gameSerial) {
                            mgr.entriesFor(gameSerial)
                        }
                        CheatsSheet(
                            dbImported = mgr.isDbImported,
                            hasSerial = gameSerial != null,
                            entries = entries,
                            onToggle = { name, on ->
                                mgr.setEnabled(gameSerial, name, on)
                                applyCheats()
                                cheatTick++
                            },
                            onDismiss = { showCheats = false },
                            // PS1 (P24): paste-a-code import. SwanStation parses raw unencrypted
                            // hex pairs; multi-line codes are '+'-joined (newlines are rejected).
                            onAddCode = if (system == GameSystem.PS1) { name, pasted ->
                                val normalized = com.retrovault.cheats.Ps1CheatCodes.normalize(pasted)
                                when {
                                    name.isBlank() -> "Give the cheat a name."
                                    normalized == null ->
                                        "That doesn't look like an unencrypted GameShark code " +
                                            "(lines of 8+4 hex digits)."
                                    !mgr.addManualCode(gameSerial, name, normalized) ->
                                        "A cheat with that name already exists."
                                    else -> {
                                        applyCheats()
                                        cheatTick++
                                        null
                                    }
                                }
                            } else null,
                        )
                    }

                    // Post-session compat prompt (≥10 min, once per serial+version).
                    if (showCompatPrompt) {
                        CompatRatingSheet(
                            gameTitle = title,
                            onSubmit = { rating, subScores ->
                                submitCompatReport(rating, subScores)
                                showCompatPrompt = false
                                finish()
                            },
                            onSkip = {
                                showCompatPrompt = false
                                finish()
                            },
                        )
                    }
                }
            }
        }
    }

    /** Quit path: pause, maybe ask "how did it run?", then finish (teardown auto-saves). */
    private fun requestQuit() {
        val serial = gameSerial
        val played = System.currentTimeMillis() - sessionStartMs
        if (serial != null && CompatReporter.shouldPrompt(applicationContext, played, serial, appVersion)) {
            CompatReporter.markPrompted(applicationContext, serial, appVersion)
            session.paused = true
            showCompatPrompt = true
        } else {
            finish()
        }
    }

    private fun submitCompatReport(rating: Int, subScores: Map<String, Int>) {
        val serial = gameSerial ?: return
        val ctx = applicationContext
        val device = buildMap {
            put("manufacturer", android.os.Build.MANUFACTURER ?: "")
            put("model", android.os.Build.MODEL ?: "")
            put("sdk", android.os.Build.VERSION.SDK_INT.toString())
            put("abi", android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "")
            put("gpuFamily", com.retrovault.settings.DeviceClass.family().name)
            if (android.os.Build.VERSION.SDK_INT >= 31) put("soc", android.os.Build.SOC_MODEL ?: "")
            put("backend", "gles3")
        }
        val settingsDiff = buildMap {
            com.retrovault.settings.SettingsStore(ctx).read(null).forEach { (k, v) -> put(k, v) }
            intent.getStringExtra(EXTRA_GAME_ID)?.let { gk ->
                com.retrovault.settings.SettingsStore(ctx).read(gk).forEach { (k, v) -> put("game:$k", v) }
            }
        }
        // Fire-and-forget on the process lifecycle: must survive activity finish().
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            CompatReporter.submit(
                ctx,
                serial = serial,
                rating = rating,
                subScores = subScores,
                device = device,
                settingsDiff = settingsDiff,
                appVersion = appVersion,
                coreVersion = "ppsspp_libretro v1.20.4",
            )
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
            // Silent success/failure taught users not to trust these buttons — always confirm.
            val ok = if (save) mgr.save(QUICK_SLOT) else mgr.load(QUICK_SLOT)
            playerToast = when {
                save && ok -> "Saved to Slot $QUICK_SLOT"
                save -> "Save failed"
                ok -> "Loaded Slot $QUICK_SLOT"
                else -> "No save in Slot $QUICK_SLOT yet"
            }
        }
    }

    /** Push the currently-enabled cheats to the core; any active cheat clears hardcore mode. */
    private fun applyCheats() {
        val mgr = cheatManager ?: return
        val codes = mgr.enabledCodes(gameSerial)
        if (codes.isNotEmpty()) session.hardcoreActive = false
        session.applyCheats(codes)
    }

    // Cross-process live-apply: the Settings tab runs in the MAIN process and writes the settings
    // JSON; this (:emu) process read it once at onCreate. Re-resolving on every return makes
    // "background the game → tweak video settings → come back" take effect immediately — core
    // variables flow through GET_VARIABLE_UPDATE, the display config is applied per frame.
    private var settingsAppliedAtCreate = false

    override fun onResume() {
        super.onResume()
        if (!settingsAppliedAtCreate) { settingsAppliedAtCreate = true; return } // onCreate just did it
        if (session.status != CoreStatus.RUNNING) return
        runCatching {
            val settings = com.retrovault.settings.SettingsResolver(applicationContext) {
                com.retrovault.settings.GameDb.settingsFor(applicationContext, gameSerial)
            }
            settings.applyToCore(playKey)
            settings.applyDisplay(playKey)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: a widget/shortcut/store launch while a session runs lands here instead of
        // onCreate. Same game → just come to the front. Different game → restart through the
        // normal lifecycle: onDestroy auto-saves + records playtime for the OLD game, then
        // onCreate boots the new one. recreate() (not finish()+startActivity()) because it
        // destroys the old instance BEFORE creating the new one — overlapping native sessions
        // poison the run loop. The relaunch reuses the activity record's ORIGINAL intent
        // (setIntent only changes the client-side field), so the new intent rides a
        // process-local stash that onCreate picks up first.
        val newKey = intent.getStringExtra(EXTRA_GAME_ID) ?: return
        if (newKey == playKey) return
        pendingRelaunch = intent
        recreate()
    }

    override fun onDestroy() {
        hotplug?.stop()
        // Auto-save before teardown: the run loop is still alive (backgrounded branch
        // executes state ops even with the Surface detached). Blocking is intentional —
        // the state must hit disk before the process can be killed.
        val mgr = saveStates
        if (mgr != null && saveStatesRecommended && session.status == CoreStatus.RUNNING) {
            session.paused = false // a frozen loop still services ops, but never save mid-pause transition
            runBlocking { runCatching { mgr.save(SaveStateManager.AUTO_SLOT) } }
        }
        session.stop()
        // P27: accumulate playtime for the playtime chip + widget subtitle.
        playKey?.let { key ->
            runCatching {
                com.retrovault.library.RecentPlays(applicationContext)
                    .addPlaytime(key, System.currentTimeMillis() - sessionStartMs)
            }
        }
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

        /** Intent for a different-game relaunch via recreate() — see onNewIntent. */
        private var pendingRelaunch: Intent? = null

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
