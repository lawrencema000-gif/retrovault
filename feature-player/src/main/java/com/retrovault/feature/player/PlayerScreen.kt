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
) {
    var showMenu by remember { mutableStateOf(false) }
    var fastForward by remember { mutableStateOf(false) }
    val running = session.status == CoreStatus.RUNNING
    val ctx = LocalContext.current
    val statusMsg = when {
        running -> null
        CoreCatalog.requiresBios(system) && !BiosStatus.isInstalled(ctx, system) ->
            "IMPORT A ${system.shortCode} BIOS TO PLAY"
        system == GameSystem.PS2 && !DeviceCapabilities.supportsPs2(ctx) ->
            "DEVICE MAY NOT RUN PS2 WELL"
        session.status == CoreStatus.ERROR -> "CORE FAILED TO START"
        else -> "NO GAME LOADED · ${system.shortCode}"
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
            AndroidView(
                factory = { context -> TouchOverlayView(context, inputHub) },
                modifier = Modifier.fillMaxSize()
            )
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
                if (fastForward) Chip("2×", PulsarPrimary, Color(0x402A7FFF))
                Chip(if (running) "ON" else "--", Color.White.copy(alpha = 0.75f), HudGlass)
            }
        }

        if (showMenu) {
            QuickMenu(
                title = title,
                fastForward = fastForward,
                onToggleFf = { fastForward = !fastForward },
                onDismiss = { showMenu = false },
                onQuit = { session.stop(); onQuit() }
            )
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
            MenuTile(Icons.Filled.Save, PulsarPrimary, "Save State", Modifier.weight(1f)) {}
            MenuTile(Icons.Filled.History, PulsarTeal, "Load State", Modifier.weight(1f)) {}
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuTile(Icons.Filled.FastForward, PulsarYellow, if (fastForward) "Fast Fwd On" else "Fast Fwd Off", Modifier.weight(1f), onClick = onToggleFf)
            MenuTile(Icons.Filled.CameraAlt, Color.White, "Screenshot", Modifier.weight(1f)) {}
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
