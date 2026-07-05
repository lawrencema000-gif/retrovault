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
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Search
import com.retrovault.core.model.GameSystem
import com.retrovault.download.BiosStatus
import com.retrovault.download.RomImporter
import com.retrovault.billing.LocalBillingManager
import com.retrovault.settings.Category
import com.retrovault.settings.DeviceClass
import com.retrovault.settings.Origin
import com.retrovault.settings.ResolvedSetting
import com.retrovault.settings.SettingDef
import com.retrovault.settings.SettingsResolver
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

/**
 * Settings, driven by the 4-layer resolver. [gameKey] switches the screen into per-game
 * mode: edits write the game's diff layer and rows badge where each value came from.
 */
@Composable
fun SettingsScreen(gameKey: String? = null, gameTitle: String? = null) {
    val context = LocalContext.current
    val resolver = remember { SettingsResolver(context) }
    var tick by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val resolved = remember(tick, gameKey) { resolver.resolveAll(gameKey) }
    val visible = remember(resolved, query) {
        if (query.isBlank()) resolved
        else resolved.filter {
            it.def.title.contains(query, true) || it.def.description.contains(query, true)
        }
    }

    fun setValue(r: ResolvedSetting, value: String) {
        resolver.setUserValue(r.def, value, gameKey)
        // Live-apply to a running session (no-op when nothing is running).
        resolver.applyToCore(gameKey)
        tick++
    }

    fun reset(r: ResolvedSetting) {
        resolver.clearUserValue(r.def, gameKey)
        resolver.applyToCore(gameKey)
        tick++
    }

    var biosTick by remember { mutableIntStateOf(0) }
    val ps1Bios = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { RomImporter.importBios(context, GameSystem.PS1, it); biosTick++ }
    }
    val ps2Bios = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { RomImporter.importBios(context, GameSystem.PS2, it); biosTick++ }
    }
    val ps1BiosInstalled = remember(biosTick) { BiosStatus.isInstalled(context, GameSystem.PS1) }
    val ps2BiosInstalled = remember(biosTick) { BiosStatus.isInstalled(context, GameSystem.PS2) }

    val billing = remember { LocalBillingManager(context) }
    var goldTick by remember { mutableIntStateOf(0) }
    val isGold = remember(goldTick) { billing.isGold }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 56.dp, bottom = 110.dp)
            .padding(horizontal = 22.dp)
    ) {
        Text(
            if (gameKey == null) "Settings" else "Game settings",
            fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = PulsarText
        )
        if (gameTitle != null) {
            Text(gameTitle, fontSize = 12.sp, color = PulsarTextDim)
        }

        Spacer(Modifier.height(14.dp))
        SearchField(query) { query = it }

        if (gameKey == null) {
            Section("PULSAR GOLD") {
                GoldRow(isGold) { if (!isGold) { billing.purchaseGold(); goldTick++ } }
            }
        }

        for (category in listOf(
            Category.VIDEO, Category.AUDIO, Category.EMULATION, Category.CONTROLS, Category.SYSTEM
        )) {
            val rows = visible.filter { it.def.category == category }
            if (rows.isEmpty()) continue
            Section(category.name) {
                rows.forEachIndexed { i, r ->
                    if (i > 0) Divider()
                    when (val def = r.def) {
                        is SettingDef.Toggle -> SettingToggleRow(
                            r, onToggle = { setValue(r, (!r.asBoolean).toString()) },
                            onReset = { reset(r) },
                        )
                        is SettingDef.Choice -> SettingChoiceRow(
                            r, def,
                            onCycle = {
                                val idx = def.options.indexOfFirst { it.first == r.value }
                                val next = def.options[(idx + 1).mod(def.options.size)].first
                                setValue(r, next)
                            },
                            onReset = { reset(r) },
                        )
                    }
                }
            }
        }

        if (gameKey == null && query.isBlank()) {
            Section("BIOS") {
                BiosRow("PlayStation", ps1BiosInstalled) { ps1Bios.launch(arrayOf("*/*")) }
                Divider()
                BiosRow("PlayStation 2", ps2BiosInstalled) { ps2Bios.launch(arrayOf("*/*")) }
            }

            Section("SYSTEM INFO") {
                StatusRow(Icons.Filled.Info, PulsarTextBody, "Version", "PULSAR 0.1.0", PulsarTextDim, showCheck = false)
                Divider()
                StatusRow(
                    Icons.Filled.Memory, PulsarTextBody, "Device class",
                    DeviceClass.family().name, PulsarTextDim, showCheck = false
                )
            }
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
private fun SearchField(query: String, onChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = PulsarText, fontSize = 13.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(PulsarPrimary),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PulsarSurface1)
                    .border(1.dp, PulsarStrokeSoft, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Search, null, tint = PulsarTextDim, modifier = Modifier.size(17.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search settings…", fontSize = 13.sp, color = PulsarTextFaint)
                    inner()
                }
            }
        }
    )
}

@Composable
private fun OriginBadge(origin: Origin) {
    val (label, color) = when (origin) {
        Origin.DEFAULT -> return // defaults are unbadged noise
        Origin.GAMEDB -> "GAMEDB" to PulsarPrimary
        Origin.DEVICE -> "DEVICE" to PulsarYellow
        Origin.USER_GLOBAL -> "CUSTOM" to PulsarTeal
        Origin.USER_GAME -> "THIS GAME" to PulsarTeal
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 8.sp, letterSpacing = 1.sp, color = color, fontFamily = ChakraPetch, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingTitle(r: ResolvedSetting) {
    Column(Modifier.fillMaxWidth(0.62f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(r.def.title, fontSize = 14.sp, color = PulsarTextBody)
            OriginBadge(r.origin)
        }
        Text(r.def.description, fontSize = 10.sp, lineHeight = 14.sp, color = PulsarTextFaint)
    }
}

@Composable
private fun SettingToggleRow(r: ResolvedSetting, onToggle: () -> Unit, onReset: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SettingTitle(r)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResetDot(r, onReset)
            PulsarSwitch(r.asBoolean, onToggle)
        }
    }
}

@Composable
private fun SettingChoiceRow(
    r: ResolvedSetting,
    def: SettingDef.Choice,
    onCycle: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onCycle)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SettingTitle(r)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResetDot(r, onReset)
            Text(
                def.options.firstOrNull { it.first == r.value }?.second ?: r.value,
                fontSize = 12.sp, color = PulsarTeal, fontFamily = ChakraPetch,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = PulsarTextDim, modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Small reset affordance shown only when the user has overridden this scope. */
@Composable
private fun ResetDot(r: ResolvedSetting, onReset: () -> Unit) {
    if (r.origin == Origin.USER_GLOBAL || r.origin == Origin.USER_GAME) {
        Icon(
            Icons.Filled.Replay, "Reset",
            tint = PulsarTextFaint,
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .clickable(onClick = onReset)
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
private fun GoldRow(isGold: Boolean, onUpgrade: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onUpgrade)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RowIcon(Icons.Filled.WorkspacePremium, PulsarYellow, if (isGold) "Gold active" else "Upgrade to Pulsar Gold")
        if (isGold) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = PulsarTeal, modifier = Modifier.size(16.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Unlock", fontSize = 12.sp, color = PulsarTextFaint, fontFamily = ChakraPetch)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = PulsarTextDim, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun BiosRow(label: String, installed: Boolean, onImport: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onImport)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RowIcon(Icons.Filled.Memory, PulsarPrimary, "$label BIOS")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (installed) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = PulsarTeal, modifier = Modifier.size(16.dp))
                Text("Installed", fontSize = 12.sp, color = PulsarTeal, fontFamily = ChakraPetch)
            } else {
                Text("Import", fontSize = 12.sp, color = PulsarTextFaint, fontFamily = ChakraPetch)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = PulsarTextDim, modifier = Modifier.size(18.dp))
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
