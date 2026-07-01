package com.retrovault.feature.store

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarAccentBrush
import com.retrovault.core.ui.theme.PulsarBlueBrush
import com.retrovault.core.ui.theme.PulsarPrimary
import com.retrovault.core.ui.theme.PulsarStroke
import com.retrovault.core.ui.theme.PulsarStrokeSoft
import com.retrovault.core.ui.theme.PulsarSurface1
import com.retrovault.core.ui.theme.PulsarSurface3
import com.retrovault.core.ui.theme.PulsarTeal
import com.retrovault.core.ui.theme.PulsarText
import com.retrovault.core.ui.theme.PulsarTextBody
import com.retrovault.core.ui.theme.PulsarTextDim
import com.retrovault.core.ui.theme.PulsarTextFaint
import com.retrovault.core.ui.theme.PulsarYellow

@Composable
fun SettingsScreen() {
    var res by remember { mutableIntStateOf(1) }
    var crt by remember { mutableStateOf(true) }
    var smoothing by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf(true) }
    var vol by remember { mutableFloatStateOf(0.8f) }
    var autosave by remember { mutableStateOf(true) }
    var rewind by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 56.dp, bottom = 110.dp)
            .padding(horizontal = 22.dp)
    ) {
        Text("Settings", fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = PulsarText)

        Section("VIDEO") {
            ResolutionRow(res) { res = it }
            Divider()
            ToggleRow(Icons.Filled.Grain, PulsarTeal, "CRT Filter", crt) { crt = !crt }
            Divider()
            ToggleRow(Icons.Filled.BlurOn, PulsarYellow, "Texture Smoothing", smoothing) { smoothing = !smoothing }
        }

        Section("AUDIO") {
            ToggleRow(Icons.Filled.VolumeUp, PulsarPrimary, "Enable Audio", audio) { audio = !audio }
            Divider()
            VolumeRow(vol) { vol = it }
        }

        Section("EMULATION") {
            ToggleRow(Icons.Filled.BookmarkAdded, PulsarTeal, "Auto Save States", autosave) { autosave = !autosave }
            Divider()
            ToggleRow(Icons.Filled.Replay, PulsarYellow, "Rewind Buffer", rewind) { rewind = !rewind }
            Divider()
            NavRow(Icons.Filled.VideogameAsset, PulsarYellow, "Controller Mapping")
        }

        Section("SYSTEM") {
            StatusRow(Icons.Filled.Memory, PulsarPrimary, "BIOS Status", "Not loaded", PulsarTextFaint, showCheck = false)
            Divider()
            StatusRow(Icons.Filled.Info, PulsarTextBody, "Version", "PULSAR 0.1.0", PulsarTextDim, showCheck = false)
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Pulsar hosts only legally distributable games and never bundles copyrighted ROMs or " +
                "console BIOS. Import your own BIOS and game backups from your device.",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = PulsarTextFaint
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(22.dp))
    Text(
        title,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 2.sp,
        color = PulsarTextDim
    )
    Spacer(Modifier.height(10.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PulsarSurface1)
            .border(1.dp, PulsarStrokeSoft, RoundedCornerShape(16.dp))
    ) { content() }
}

@Composable
private fun Divider() = HorizontalDivider(color = PulsarStrokeSoft, thickness = 1.dp)

@Composable
private fun RowIcon(icon: ImageVector, tint: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        Text(label, fontSize = 14.sp, color = PulsarTextBody)
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, tint: Color, label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RowIcon(icon, tint, label)
        PulsarSwitch(checked, onToggle)
    }
}

@Composable
private fun NavRow(icon: ImageVector, tint: Color, label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RowIcon(icon, tint, label)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = PulsarTextDim, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StatusRow(icon: ImageVector, tint: Color, label: String, value: String, valueColor: Color, showCheck: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RowIcon(icon, tint, label)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showCheck) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = PulsarTeal, modifier = Modifier.size(16.dp))
            Text(value, fontSize = 12.sp, color = valueColor, fontFamily = ChakraPetch)
        }
    }
}

@Composable
private fun ResolutionRow(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(16.dp)) {
        RowIcon(Icons.Filled.Hd, PulsarPrimary, "Resolution")
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x4D000000))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("1×", "2×", "3×").forEachIndexed { i, label ->
                val sel = selected == i
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .then(if (sel) Modifier.background(PulsarBlueBrush) else Modifier)
                        .clickable { onSelect(i) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (sel) Color.White else PulsarTextDim
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeRow(value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Master Volume", fontSize = 13.sp, color = PulsarTextBody)
            Text("${(value * 100).toInt()}%", fontSize = 13.sp, color = PulsarTeal, fontFamily = ChakraPetch)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = PulsarPrimary,
                inactiveTrackColor = PulsarSurface3
            )
        )
    }
}

@Composable
private fun PulsarSwitch(checked: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .width(46.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(if (checked) Modifier.background(PulsarAccentBrush) else Modifier.background(PulsarSurface3))
            .border(1.dp, PulsarStroke, RoundedCornerShape(20.dp))
            .clickable(onClick = onToggle)
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
