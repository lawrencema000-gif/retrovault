package com.retrovault.feature.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.retrovault.core.model.GameSystem
import com.retrovault.core.ui.coverBrush
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarPrimary
import com.retrovault.core.ui.theme.PulsarStroke
import com.retrovault.core.ui.theme.PulsarTeal
import com.retrovault.core.ui.theme.PulsarText
import com.retrovault.core.ui.theme.PulsarTextDim
import com.retrovault.core.ui.theme.PulsarYellow
import com.retrovault.download.BiosStatus
import com.retrovault.emulator.CoreCatalog
import com.retrovault.emulator.CoreStatus
import com.retrovault.emulator.DeviceCapabilities
import com.retrovault.emulator.EmulatorSession
import com.retrovault.emulator.LibretroBridge
import com.retrovault.input.InputHub
import com.retrovault.input.TouchOverlayView

private val HudGlass = Color(0x99080C14)

/**
 * Gameplay chrome. The INPUT path is NOT Compose: [TouchOverlayView] (raw View) sits directly
 * over the SurfaceView and writes the native input snapshot. Compose renders only chrome —
 * HUD menu button, status chips, quick-menu sheet.
 */
@Composable
fun PlayerScreen(
    title: String,
    system: GameSystem,
    session: EmulatorSession,
    inputHub: InputHub,
    onQuit: () -> Unit,
    gamepadConnected: Boolean = false,
    pausedExternally: Boolean = false,
    onResumeExternal: () -> Unit = {},
    menuRequests: Int = 0,
    onSaveState: () -> Unit = {},
    onLoadState: () -> Unit = {},
    saveStates: com.retrovault.saves.SaveStateManager? = null,
    onScreenshot: () -> Unit = {},
    onCheats: () -> Unit = {},
    toast: String? = null,
    onToastDone: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }
    var showSlots by remember { mutableStateOf(false) }
    var fastForward by remember { mutableStateOf(false) }
    // Transient feedback for quick actions (save/load/rewind/screenshot were silent before).
    var localToast by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(toast) {
        if (toast != null) { localToast = toast; onToastDone() }
    }
    androidx.compose.runtime.LaunchedEffect(localToast) {
        if (localToast != null) { kotlinx.coroutines.delay(2200); localToast = null }
    }
    // Gamepad MENU virtkey opens the quick menu (counter bump per press).
    androidx.compose.runtime.LaunchedEffect(menuRequests) {
        if (menuRequests > 0) showMenu = true
    }
    val running = session.status == CoreStatus.RUNNING
    // Opening the menu pauses the game (universal expectation) — but never clobber a pause set
    // by someone else (controller-disconnect): only unpause what the menu itself paused.
    var pausedByMenu by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(showMenu, showSlots) {
        val menuOpen = showMenu || showSlots
        if (menuOpen && !session.paused) {
            session.paused = true
            pausedByMenu = true
        } else if (!menuOpen && pausedByMenu) {
            session.paused = false
            pausedByMenu = false
        }
    }
    // Loading overlay until the first real frame lands — a black screen for the boot seconds
    // otherwise reads as a crash.
    var firstFrameSeen by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(running) {
        while (running && !firstFrameSeen) {
            if (com.retrovault.emulator.LibretroBridge.nativeFramesPresented() > 0) firstFrameSeen = true
            else kotlinx.coroutines.delay(120)
        }
    }
    // Predictive back: the system back gesture opens the pause/quick menu; a second back
    // (with the menu open) dismisses it. Quitting is an explicit menu action.
    androidx.activity.compose.BackHandler(enabled = running && !showMenu && !showSlots) { showMenu = true }
    androidx.activity.compose.BackHandler(enabled = showMenu) { showMenu = false }
    val ctx = LocalContext.current
    // Plain language, in priority order — and never claim "no game loaded" when the truth is
    // that this system's emulator isn't shipped yet (UNAVAILABLE).
    val statusMsg = when {
        running -> null
        session.status == CoreStatus.UNAVAILABLE && system != GameSystem.PSP ->
            "${system.shortCode} support arrives in a future update"
        session.status == CoreStatus.UNAVAILABLE ->
            "The emulator couldn't start — try reinstalling Pulsar"
        CoreCatalog.requiresBios(system) && !BiosStatus.isInstalled(ctx, system) ->
            "Playing ${system.shortCode} games needs a BIOS from your own console — import it in Settings › BIOS"
        system == GameSystem.PS2 && !DeviceCapabilities.supportsPs2(ctx) ->
            "This device may be too slow for PS2"
        session.status == CoreStatus.ERROR -> "This game couldn't start — try re-downloading it"
        else -> "Nothing is playing"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (running) {
            // Emulator viewport + the raw-View touch overlay directly above it.
            AndroidView(
                factory = { context ->
                    SurfaceView(context).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                LibretroBridge.nativeSetVideoSurface(holder.surface)
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder, format: Int, width: Int, height: Int,
                            ) {
                                LibretroBridge.nativeSetVideoSurface(holder.surface)
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                LibretroBridge.nativeSetVideoSurface(null)
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            // Touch controls only while no physical pad is connected (plan: pad connect →
            // overlay hides; disconnect → it returns).
            if (!gamepadConnected) {
                AndroidView(
                    factory = { context ->
                        TouchOverlayView(context, inputHub).apply {
                            // Apply the user's .pulsarskin layout, if one is active (P27).
                            skin = com.retrovault.input.SkinStore(context).activeSkin()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            DisposableEffect(Unit) {
                onDispose {
                    LibretroBridge.nativeSetVideoSurface(null)
                    inputHub.clear()
                }
            }
        } else {
            // No running core: branded standby backdrop + status.
            Box(Modifier.fillMaxSize().background(coverBrush(title)))
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(0.4f to Color.Transparent, 1f to Color(0x8C000000)))
            )
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.SportsEsports, null,
                    tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(46.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    title,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 3.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    statusMsg ?: "",
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }

        // top HUD (chrome only)
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HudGlass)
                    .border(1.dp, PulsarStroke, RoundedCornerShape(12.dp))
                    .clickable { showMenu = true },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Menu, null, tint = Color.White, modifier = Modifier.size(22.dp)) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fastForward) Chip("${session.speedPct / 100}×", PulsarPrimary, Color(0x402A7FFF))
                Chip(if (running) "ON" else "--", Color.White.copy(alpha = 0.75f), HudGlass)
            }
        }

        if (showMenu) {
            QuickMenu(
                title = title,
                fastForward = fastForward,
                onToggleFf = {
                    fastForward = !fastForward
                    session.speedPct = if (fastForward) 200 else 100
                },
                onDismiss = { showMenu = false },
                // Session is NOT stopped here: the activity's teardown auto-saves first
                // (stopping now would skip the auto-save and lose "Continue").
                onQuit = { showMenu = false; onQuit() },
                onSaveState = { onSaveState(); showMenu = false },
                onLoadState = { onLoadState(); showMenu = false },
                onRewind = {
                    val ok = session.rewindStep()
                    localToast = if (ok) "Rewound" else "Nothing to rewind yet"
                    showMenu = false
                },
                onScreenshot = { onScreenshot(); showMenu = false },
                onSlots = if (saveStates != null) ({ showSlots = true; showMenu = false }) else null,
                onCheats = { onCheats(); showMenu = false },
            )
        }

        if (showSlots && saveStates != null) {
            SlotManagerSheet(
                manager = saveStates,
                onDismiss = { showSlots = false },
            )
        }

        // Boot overlay: the seconds between PLAY and the first presented frame are a black
        // screen otherwise, which reads as a crash to anyone new.
        if (running && !firstFrameSeen) {
            Column(
                Modifier.matchParentSize().background(Color(0xE0030508)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    title,
                    fontFamily = ChakraPetch, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, color = PulsarText
                )
                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.CircularProgressIndicator(color = PulsarYellow)
                Spacer(Modifier.height(10.dp))
                Text("Starting…", fontSize = 12.sp, color = PulsarTextDim)
            }
        }

        // Transient action feedback ("Saved to Slot 1", "Nothing to rewind yet", …).
        localToast?.let { msg ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE0121A2A))
                    .border(1.dp, PulsarStroke, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(msg, fontSize = 12.sp, color = PulsarText)
            }
        }

        // Auto-pause scrim (controller disconnected mid-game).
        if (pausedExternally) {
            Column(
                Modifier
                    .matchParentSize()
                    .background(Color(0xCC030508))
                    .clickable(onClick = onResumeExternal),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.SportsEsports, null,
                    tint = PulsarYellow, modifier = Modifier.size(42.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "PAUSED",
                    fontFamily = ChakraPetch, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, letterSpacing = 4.sp, color = PulsarText
                )
                Text(
                    "Controller disconnected · tap to resume",
                    fontSize = 12.sp, color = PulsarTextDim
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String, textColor: Color, bg: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, PulsarStroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = textColor, fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
private fun BoxScope.QuickMenu(
    title: String,
    fastForward: Boolean,
    onToggleFf: () -> Unit,
    onDismiss: () -> Unit,
    onQuit: () -> Unit,
    onSaveState: () -> Unit = {},
    onLoadState: () -> Unit = {},
    onRewind: () -> Unit = {},
    onScreenshot: () -> Unit = {},
    onSlots: (() -> Unit)? = null,
    onCheats: () -> Unit = {},
) {
    Box(
        Modifier
            .matchParentSize()
            .background(Color(0x99030508))
            .clickable(onClick = onDismiss)
    )
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color(0xFF0A0D14))
            .border(1.dp, PulsarStroke, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .padding(20.dp)
    ) {
        Text(title, fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PulsarText)
        Text("Quick menu", fontSize = 11.sp, color = PulsarTextDim)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuTile(Icons.Filled.Save, PulsarPrimary, "Save State", Modifier.weight(1f), onClick = onSaveState)
            MenuTile(Icons.Filled.History, PulsarTeal, "Load State", Modifier.weight(1f), onClick = onLoadState)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuTile(Icons.Filled.FastForward, PulsarYellow, if (fastForward) "Fast Fwd On" else "Fast Fwd Off", Modifier.weight(1f), onClick = onToggleFf)
            MenuTile(Icons.Filled.CameraAlt, Color.White, "Screenshot", Modifier.weight(1f), onClick = onScreenshot)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuTile(Icons.Filled.History, PulsarYellow, "Rewind 2s", Modifier.weight(1f), onClick = onRewind)
            if (onSlots != null) {
                MenuTile(Icons.Filled.Save, PulsarTeal, "Slots…", Modifier.weight(1f), onClick = onSlots)
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuTile(Icons.Filled.Bolt, PulsarPrimary, "Cheats", Modifier.weight(1f), onClick = onCheats)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0x1FFF5A5A))
                .border(1.dp, Color(0x4DFF5A5A), RoundedCornerShape(15.dp))
                .clickable(onClick = onQuit)
                .padding(15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color(0xFFFF8A8A), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Quit to Library", color = Color(0xFFFF8A8A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MenuTile(icon: ImageVector, tint: Color, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0x0AFFFFFF))
            .border(1.dp, PulsarStroke, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = PulsarText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
