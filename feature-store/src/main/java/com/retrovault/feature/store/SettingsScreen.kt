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
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Verified
import com.retrovault.feature.store.about.UpdateResult
import com.retrovault.feature.store.about.checkForUpdate
import com.retrovault.feature.store.about.crashReportToggleAvailable
import com.retrovault.feature.store.about.updateCheckAvailable
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Language
import kotlinx.coroutines.launch
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
import com.retrovault.billing.createBillingManager
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
    var biosMessage by remember { mutableStateOf<String?>(null) }

    // Validate BIOS imports by size — real PS1 BIOSes are 512 KB, PS2 4 MB. Without this, ANY
    // picked file (a photo, a PDF) earned a green "Installed" check and quietly broke the setup.
    fun importValidatedBios(system: GameSystem, uri: android.net.Uri, expectedBytes: Long) {
        val file = RomImporter.importBios(context, system, uri)
        if (file == null) {
            biosMessage = "Couldn't read that file."
        } else if (file.length() != expectedBytes) {
            file.delete()
            biosMessage = "That file doesn't look like a ${system.name} BIOS " +
                "(expected ${expectedBytes / 1024} KB). Dump it from your own console."
        } else {
            biosMessage = "${system.name} BIOS installed."
        }
        biosTick++
    }
    val ps1Bios = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importValidatedBios(GameSystem.PS1, it, 512L * 1024) }
    }
    val ps2Bios = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importValidatedBios(GameSystem.PS2, it, 4L * 1024 * 1024) }
    }
    val ps1BiosInstalled = remember(biosTick) { BiosStatus.isInstalled(context, GameSystem.PS1) }
    val ps2BiosInstalled = remember(biosTick) { BiosStatus.isInstalled(context, GameSystem.PS2) }

    // Games folder (the onboarding step promised "you can always do this later in Settings" —
    // this row keeps that promise).
    var folderTick by remember { mutableIntStateOf(0) }
    val gamesFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            com.retrovault.core.ui.AppPrefs.setGamesFolderUri(uri.toString())
            folderTick++
        }
    }

    // Flavor-resolved: PlayBillingManager (full) or FreeBillingManager (foss). Main never names a
    // proprietary type — createBillingManager is declared in each flavor's source set.
    val billing = remember { createBillingManager(context) }
    val activity = context.findActivity()
    var goldTick by remember { mutableIntStateOf(0) }
    val isGold = remember(goldTick) { billing.isGold }

    // P22 About state: licenses sheet + (foss-only) manual update check.
    var showLicenses by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateAvailable by remember { mutableStateOf(false) }
    var goldMessage by remember { mutableStateOf<String?>(null) }

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

        // Hidden entirely in foss (purchaseSupported == false) — no upgrade path there.
        if (gameKey == null && billing.purchaseSupported) {
            Section("PULSAR GOLD") {
                GoldRow(isGold) {
                    if (isGold) return@GoldRow
                    if (!billing.purchaseReady) {
                        // No dead taps: without a Play listing the flow can't start — say so.
                        goldMessage = "Purchases aren't available in this build yet — " +
                            "Pulsar Gold arrives with the Play Store release."
                    } else if (activity != null) {
                        billing.purchaseGold(activity)
                        goldTick++
                    }
                }
                goldMessage?.let {
                    Text(
                        it, fontSize = 11.sp, color = PulsarTextDim,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
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
            Text(
                "Emulation and video changes apply the next time a game starts.",
                fontSize = 11.sp, color = PulsarTextFaint,
                modifier = Modifier.padding(top = 10.dp)
            )

            Section("LIBRARY") {
                val folderSet = remember(folderTick) {
                    com.retrovault.core.ui.AppPrefs.gamesFolderUri.isNotEmpty()
                }
                ActionRow(
                    Icons.Filled.FolderOpen, PulsarTeal,
                    if (folderSet) "Games folder — change" else "Add your games folder",
                ) { gamesFolderPicker.launch(null) }
                Text(
                    "Point Pulsar at a folder of your own PSP game files and they appear in the " +
                        "library. You can also import single files from the library screen.",
                    fontSize = 11.sp, lineHeight = 15.sp, color = PulsarTextDim,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            Section("BIOS") {
                BiosRow("PlayStation", ps1BiosInstalled) { ps1Bios.launch(arrayOf("*/*")) }
                Divider()
                BiosRow("PlayStation 2", ps2BiosInstalled) { ps2Bios.launch(arrayOf("*/*")) }
                biosMessage?.let {
                    Text(
                        it, fontSize = 11.sp, color = PulsarTextDim,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                Text(
                    "PSP needs no BIOS and plays today. PS1 and PS2 support is in development — " +
                        "you can import your BIOS now, and it will be used when those systems arrive.",
                    fontSize = 11.sp, lineHeight = 15.sp, color = PulsarTextDim,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            Section("CHEATS") {
                CheatDbRow()
            }

            Section("APPEARANCE") {
                ToggleRow(
                    Icons.Filled.Contrast, PulsarTeal, "OLED black theme",
                    com.retrovault.core.ui.AppPrefs.oledBlack
                ) { com.retrovault.core.ui.AppPrefs.setOledBlack(!com.retrovault.core.ui.AppPrefs.oledBlack) }
                Divider()
                LanguageRow()
            }

            Section("SYSTEM INFO") {
                val versionName = remember {
                    runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull() ?: "?"
                }
                StatusRow(Icons.Filled.Info, PulsarTextBody, "Version", "PULSAR $versionName", PulsarTextDim, showCheck = false)
                Divider()
                StatusRow(
                    Icons.Filled.Memory, PulsarTextBody, "Device class",
                    DeviceClass.family().name.takeIf { it != "UNKNOWN" } ?: "Standard", PulsarTextDim, showCheck = false
                )
            }

            // P22 GPL compliance surface: the license texts ship INSIDE the APK (assets/legal/)
            // and are viewable here; the source link is the GPLv3 §6d corresponding-source offer.
            Section("ABOUT") {
                StatusRow(
                    Icons.Filled.Verified, PulsarTeal, "Free software",
                    "GNU GPLv3", PulsarTextDim, showCheck = false
                )
                Divider()
                ActionRow(Icons.Filled.Code, PulsarTextBody, "Source code") {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lawrencema000-gif/retrovault"))
                        )
                    }
                }
                Divider()
                ActionRow(Icons.AutoMirrored.Filled.Article, PulsarTextBody, "Open-source licenses") {
                    showLicenses = true
                }
                if (crashReportToggleAvailable()) {
                    Divider()
                    ToggleRow(
                        Icons.Filled.BugReport, PulsarTextBody, "Share crash reports",
                        com.retrovault.core.ui.AppPrefs.crashReportsOptIn
                    ) {
                        com.retrovault.core.ui.AppPrefs.setCrashReportsOptIn(
                            !com.retrovault.core.ui.AppPrefs.crashReportsOptIn
                        )
                    }
                    Text(
                        "Off by default. Takes effect the next time the app starts.",
                        fontSize = 11.sp, color = PulsarTextDim,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                if (updateCheckAvailable()) {
                    Divider()
                    ActionRow(
                        Icons.Filled.SystemUpdateAlt, PulsarTextBody,
                        updateStatus ?: "Check for updates",
                    ) {
                        if (updateAvailable) {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(UpdateCheck.RELEASES_PAGE))
                                )
                            }
                        } else {
                            updateStatus = "Checking…"
                            val versionName = runCatching {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName
                            }.getOrNull() ?: "0"
                            checkForUpdate(versionName) { result ->
                                when (result) {
                                    is UpdateResult.UpdateAvailable -> {
                                        updateAvailable = true
                                        updateStatus = "${result.tag} available — tap to open"
                                    }
                                    UpdateResult.UpToDate, UpdateResult.NoReleases ->
                                        updateStatus = "Up to date"
                                    UpdateResult.Error ->
                                        updateStatus = "Check failed — try again"
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showLicenses) {
            LicensesSheet(onDismiss = { showLicenses = false })
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

/** NavRow with a real click action (P22 About rows). */
@Composable
private fun ActionRow(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RowIcon(icon, tint, label)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = PulsarTextDim, modifier = Modifier.size(20.dp))
    }
}

/**
 * Open-source licenses viewer (P22). Renders the legal texts that ship INSIDE the APK —
 * NOTICE.md + GPLv3 are copied from the repo root at build time (drift-proof), the third-party
 * license texts are static assets. This is what makes the APK itself GPL-compliant (§4/§6:
 * recipients get a copy of the license with the program; a URL is not a copy).
 */
@Composable
private fun LicensesSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val files = listOf(
        "Pulsar — notices & provenance" to "legal/NOTICE.md",
        "GNU General Public License v3 (Pulsar, and GPL-2.0-or-later components via the \"or later\" election)" to "legal/gpl-3.0.txt",
        "GNU General Public License v2 (PPSSPP core, compat.ini, ppge_atlas.zim — licensed GPL-2.0-or-later)" to "legal/gpl-2.0.txt",
        "Apache License 2.0 (AndroidX, Compose, Kotlin, OkHttp, Coil, Oboe, Swappy)" to "legal/apache-2.0.txt",
        "MIT License (rcheevos, libretro.h, AMD FidelityFX FSR RCAS port)" to "legal/mit.txt",
        "SIL Open Font License 1.1 (Chakra Petch, Manrope)" to "legal/ofl-1.1.txt",
        "BSD 3-Clause (libchdr, in the CI-built PPSSPP core)" to "legal/bsd-3-clause.txt",
        "zlib License (SDL gamecontrollerdb.txt)" to "legal/zlib.txt",
    )
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(PulsarSurface1)
                .padding(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Open-source licenses",
                    fontFamily = ChakraPetch, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, color = PulsarText
                )
                Text(
                    "Close", fontSize = 14.sp, color = PulsarPrimary,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                files.forEach { (title, path) ->
                    item(key = path) {
                        val text = remember(path) {
                            runCatching {
                                context.assets.open(path).bufferedReader().readText()
                            }.getOrElse { "(missing: $path)" }
                        }
                        Text(
                            title,
                            fontFamily = ChakraPetch, fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = PulsarTeal,
                            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
                        )
                        Text(text, fontSize = 11.sp, lineHeight = 15.sp, color = PulsarTextBody)
                    }
                }
            }
        }
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

/** Per-app language: cycles System → English → 日本語, applied via the platform LocaleManager. */
@Composable
private fun LanguageRow() {
    val context = LocalContext.current
    val tags = listOf("" to "System default", "en" to "English", "ja" to "日本語")
    var tag by remember { mutableStateOf(com.retrovault.core.ui.AppPrefs.languageTag) }
    val label = tags.firstOrNull { it.first == tag }?.second ?: "System default"
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                val next = tags[(tags.indexOfFirst { it.first == tag } + 1).mod(tags.size)].first
                tag = next
                com.retrovault.core.ui.AppPrefs.setLanguageTag(next)
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    val lm = context.getSystemService(android.app.LocaleManager::class.java)
                    lm?.applicationLocales =
                        if (next.isEmpty()) android.os.LocaleList.getEmptyLocaleList()
                        else android.os.LocaleList.forLanguageTags(next)
                }
            }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RowIcon(Icons.Filled.Language, PulsarPrimary, "Language")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 12.sp, color = PulsarTeal, fontFamily = ChakraPetch)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = PulsarTextDim, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CheatDbRow() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val manager = remember { com.retrovault.cheats.CheatManager(context) }
    var imported by remember { mutableStateOf(manager.isDbImported) }
    var busy by remember { mutableStateOf(false) }
    var cheatError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            cheatError = null
            scope.launch {
                val ok = manager.importFromUri(uri)
                imported = ok
                // A silent failure looked like "Importing… forever" — say what went wrong.
                if (!ok) cheatError = "That file isn't a CWCheat cheat.db — nothing was imported."
                busy = false
            }
        }
    }

    Column(Modifier.padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RowIcon(Icons.Filled.Bolt, PulsarPrimary, "CWCheat database")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (imported) {
                    Icon(Icons.Filled.CheckCircle, null, tint = PulsarTeal, modifier = Modifier.size(16.dp))
                    Text("Imported", fontSize = 12.sp, color = PulsarTeal, fontFamily = ChakraPetch)
                }
            }
        }
        cheatError?.let {
            Text(it, fontSize = 11.sp, color = PulsarYellow, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PulsarSurface3)
                    .border(1.dp, PulsarStroke, RoundedCornerShape(12.dp))
                    .clickable(enabled = !busy) { picker.launch(arrayOf("*/*")) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (busy) "Importing…" else "Import cheat.db", fontSize = 12.sp, color = PulsarText, fontFamily = ChakraPetch)
            }
            if (imported) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1FFF5A5A))
                        .border(1.dp, Color(0x4DFF5A5A), RoundedCornerShape(12.dp))
                        .clickable { manager.deleteDb(); imported = false }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Remove", fontSize = 12.sp, color = Color(0xFFFF8A8A), fontFamily = ChakraPetch) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Import a community CWCheat cheat.db from your device. Pulsar never bundles cheat " +
                "data. Per-game cheat toggles appear in the in-game menu.",
            fontSize = 10.sp, lineHeight = 14.sp, color = PulsarTextFaint
        )
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
