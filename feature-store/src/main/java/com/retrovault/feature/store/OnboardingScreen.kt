package com.retrovault.feature.store

import android.content.Intent
import android.view.InputDevice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarAccentBrush
import com.retrovault.core.ui.theme.PulsarOnAccent
import com.retrovault.core.ui.theme.PulsarPrimary
import com.retrovault.core.ui.theme.PulsarStroke
import com.retrovault.core.ui.theme.PulsarSurface1
import com.retrovault.core.ui.theme.PulsarTeal
import com.retrovault.core.ui.theme.PulsarText
import com.retrovault.core.ui.theme.PulsarTextBody
import com.retrovault.core.ui.theme.PulsarTextDim
import com.retrovault.core.ui.theme.PulsarTextFaint
import com.retrovault.core.ui.theme.PulsarYellow

/**
 * First-run onboarding: a 3-step, fully skippable flow that gets a new user to gameplay quickly
 * — (1) the legal explainer, (2) an optional game-folder pick (SAF), (3) a controller check.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var folderPicked by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            folderPicked = true
        }
    }

    val controllerConnected = remember {
        InputDevice.getDeviceIds().any { id ->
            InputDevice.getDevice(id)?.let {
                it.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                    it.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            } == true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .padding(top = 60.dp, bottom = 36.dp)
    ) {
        // progress dots
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .height(4.dp)
                        .then(if (i == step) Modifier.size(width = 22.dp, height = 4.dp) else Modifier.size(width = 10.dp, height = 4.dp))
                        .clip(CircleShape)
                        .background(if (i <= step) PulsarPrimary else PulsarStroke)
                )
            }
        }

        Spacer(Modifier.height(40.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (step) {
                0 -> Step(
                    Icons.Filled.Gavel, PulsarTeal, "Welcome to Pulsar",
                    "A legal PSP emulator and storefront. Pulsar hosts only games their authors " +
                        "allow us to share — homebrew, public-domain, and open-source titles. " +
                        "For everything else, you import your own game backups and console BIOS " +
                        "from your device. No copyrighted ROMs are ever bundled.",
                )
                1 -> Step(
                    Icons.Filled.FolderOpen, PulsarPrimary,
                    if (folderPicked) "Games folder added" else "Add your games (optional)",
                    "Point Pulsar at a folder of your own PSP backups and it will show them in " +
                        "your library. You can always do this later in Settings.",
                    action = "Choose folder" to { folderPicker.launch(null) },
                    done = folderPicked,
                )
                2 -> Step(
                    Icons.Filled.SportsEsports,
                    if (controllerConnected) PulsarTeal else PulsarYellow,
                    if (controllerConnected) "Controller detected" else "Controls ready",
                    if (controllerConnected)
                        "A game controller is connected — you're all set. On-screen touch " +
                            "controls are always available too."
                    else
                        "No controller detected — Pulsar's on-screen touch controls will be used. " +
                            "Connect a Bluetooth or USB gamepad any time and it's recognised " +
                            "automatically.",
                )
            }
        }

        // nav row
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Skip",
                color = PulsarTextDim, fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onFinish)
                    .semantics { contentDescription = "Skip onboarding" }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(PulsarAccentBrush)
                    .clickable { if (step < 2) step++ else onFinish() }
                    .semantics { contentDescription = if (step < 2) "Next step" else "Finish onboarding" }
                    .padding(horizontal = 30.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (step == 2) Icon(Icons.AutoMirrored.Filled.LibraryBooks, null, tint = PulsarOnAccent, modifier = Modifier.size(20.dp))
                    Text(
                        if (step < 2) "Next" else "Enter Pulsar",
                        color = PulsarOnAccent, fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Step(
    icon: ImageVector,
    tint: Color,
    title: String,
    body: String,
    action: Pair<String, () -> Unit>? = null,
    done: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(PulsarSurface1)
                .border(1.dp, PulsarStroke, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            title, fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 24.sp,
            color = PulsarText, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Text(
            body, fontSize = 14.sp, lineHeight = 22.sp, color = PulsarTextBody,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(PulsarSurface1)
                    .border(1.dp, PulsarStroke, RoundedCornerShape(14.dp))
                    .clickable(onClick = action.second)
                    .padding(horizontal = 22.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (done) Icons.Filled.CheckCircle else Icons.Filled.FolderOpen,
                    null, tint = if (done) PulsarTeal else PulsarText, modifier = Modifier.size(18.dp)
                )
                Text(action.first, color = PulsarText, fontSize = 13.sp, fontFamily = ChakraPetch)
            }
        }
    }
}
