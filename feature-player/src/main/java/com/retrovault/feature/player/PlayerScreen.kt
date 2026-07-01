package com.retrovault.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovault.core.model.GameSystem
import com.retrovault.core.ui.coverBrush
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarBlueSoft
import com.retrovault.core.ui.theme.PulsarPink
import com.retrovault.core.ui.theme.PulsarPrimary
import com.retrovault.core.ui.theme.PulsarRed
import com.retrovault.core.ui.theme.PulsarStroke
import com.retrovault.core.ui.theme.PulsarTeal
import com.retrovault.core.ui.theme.PulsarText
import com.retrovault.core.ui.theme.PulsarTextDim
import com.retrovault.core.ui.theme.PulsarYellow
import com.retrovault.emulator.CoreStatus
import com.retrovault.emulator.EmulatorSession
import com.retrovault.emulator.InputState
import com.retrovault.emulator.RetroPad

private val Glass = Color(0x9910161F)
private val HudGlass = Color(0x99080C14)
private val ControlIcon = Color(0xFF8EA3C8)

@Composable
fun PlayerScreen(title: String, system: GameSystem, onQuit: () -> Unit) {
    val session = remember { EmulatorSession().apply { start(system, gamePath = null) } }
    var showMenu by remember { mutableStateOf(false) }
    var fastForward by remember { mutableStateOf(false) }
    val running = session.status == CoreStatus.RUNNING

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // gameplay viewport (a Surface-backed renderer replaces this at core integration)
        Box(Modifier.fillMaxSize().background(coverBrush(title)))
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(0.4f to Color.Transparent, 1f to Color(0x8C000000)))
        )

        // center status
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.SportsEsports, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(46.dp))
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
                if (running) "[ ${system.shortCode} GAMEPLAY ]" else "CORE COMING SOON · ${system.shortCode}",
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
        }

        // top HUD
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
                Chip(if (running) "60 FPS" else "-- FPS", Color.White.copy(alpha = 0.75f), HudGlass)
            }
        }

        // shoulder buttons
        HoldPill("L", session.input, RetroPad.L, Modifier.align(Alignment.CenterStart).padding(top = 40.dp))
        HoldPill("R", session.input, RetroPad.R, Modifier.align(Alignment.CenterEnd).padding(top = 40.dp))

        // bottom controls
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Dpad(session.input)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HoldPill("SELECT", session.input, RetroPad.SELECT)
                HoldPill("START", session.input, RetroPad.START)
            }
            FaceButtons(session.input)
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

/* ---- input helper ---- */

private fun Modifier.holdable(onChange: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        onChange(true)
        var pressed = true
        while (pressed) {
            val event = awaitPointerEvent()
            pressed = event.changes.any { it.pressed }
        }
        onChange(false)
    }
}

/* ---- components ---- */

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
private fun Dpad(input: InputState) {
    Box(Modifier.size(118.dp)) {
        DpadKey(Icons.Filled.KeyboardArrowUp, input, RetroPad.UP, Modifier.align(Alignment.TopCenter))
        DpadKey(Icons.Filled.KeyboardArrowDown, input, RetroPad.DOWN, Modifier.align(Alignment.BottomCenter))
        DpadKey(Icons.AutoMirrored.Filled.KeyboardArrowLeft, input, RetroPad.LEFT, Modifier.align(Alignment.CenterStart))
        DpadKey(Icons.AutoMirrored.Filled.KeyboardArrowRight, input, RetroPad.RIGHT, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun DpadKey(icon: ImageVector, input: InputState, mask: Int, modifier: Modifier) {
    Box(
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Glass)
            .border(1.dp, PulsarStroke, RoundedCornerShape(12.dp))
            .holdable { input.set(mask, it) },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = ControlIcon, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun FaceButtons(input: InputState) {
    Box(Modifier.size(132.dp)) {
        FaceKey("△", PulsarTeal, input, RetroPad.X, Modifier.align(Alignment.TopCenter))
        FaceKey("✕", PulsarBlueSoft, input, RetroPad.B, Modifier.align(Alignment.BottomCenter))
        FaceKey("□", PulsarPink, input, RetroPad.Y, Modifier.align(Alignment.CenterStart))
        FaceKey("○", PulsarRed, input, RetroPad.A, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun FaceKey(symbol: String, color: Color, input: InputState, mask: Int, modifier: Modifier) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Glass)
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
            .holdable { input.set(mask, it) },
        contentAlignment = Alignment.Center
    ) { Text(symbol, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
private fun HoldPill(label: String, input: InputState, mask: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Glass)
            .border(1.dp, PulsarStroke, RoundedCornerShape(20.dp))
            .holdable { input.set(mask, it) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, color = ControlIcon, fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
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
