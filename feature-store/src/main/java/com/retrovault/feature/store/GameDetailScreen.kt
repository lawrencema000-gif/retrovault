package com.retrovault.feature.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import com.retrovault.download.DownloadStatus
import com.retrovault.settings.PspSettings
import com.retrovault.settings.SettingsResolver
import com.retrovault.download.DownloadManager
import com.retrovault.download.GameInstaller
import com.retrovault.saves.SaveStateManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovault.core.model.GameSystem
import com.retrovault.core.model.formatBytes
import com.retrovault.core.ui.coverBrush
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarAccentBrush
import com.retrovault.core.ui.theme.PulsarBg
import com.retrovault.core.ui.theme.PulsarFav
import com.retrovault.core.ui.theme.PulsarOnAccent
import com.retrovault.core.ui.theme.PulsarPrimary
import com.retrovault.core.ui.theme.PulsarStroke
import com.retrovault.core.ui.theme.PulsarStrokeSoft
import com.retrovault.core.ui.theme.PulsarSurface1
import com.retrovault.core.ui.theme.PulsarSurface2
import com.retrovault.core.ui.theme.PulsarText
import com.retrovault.core.ui.theme.PulsarTextBody
import com.retrovault.core.ui.theme.PulsarTextDim
import com.retrovault.core.ui.theme.PulsarTextFaint
import com.retrovault.core.ui.theme.PulsarTextSoft
import com.retrovault.core.ui.theme.PulsarTeal
import com.retrovault.core.ui.theme.PulsarYellow
import com.retrovault.data.CatalogRepository
import com.retrovault.data.SupabaseCatalogRepository

@Composable
fun GameDetailScreen(
    gameId: String,
    onBack: () -> Unit,
    onPlay: (String, String, GameSystem, String?, Boolean) -> Unit = { _, _, _, _, _ -> },
    onOpenSaves: () -> Unit = {},
    onOpenControls: () -> Unit = {},
    onOpenSettings: (String) -> Unit = {},
) {
    // The in-memory catalog cache dies with the process; after process death (recents restore),
    // refetch before declaring the game missing — and always leave a way back.
    var gameState by remember(gameId) {
        mutableStateOf(SupabaseCatalogRepository.cachedById(gameId) ?: CatalogRepository.byId(gameId))
    }
    var lookedUp by remember(gameId) { mutableStateOf(gameState != null) }
    LaunchedEffect(gameId) {
        if (gameState == null) {
            runCatching { SupabaseCatalogRepository.fetchGames() }
            gameState = SupabaseCatalogRepository.cachedById(gameId) ?: CatalogRepository.byId(gameId)
            lookedUp = true
        }
    }

    val game = gameState
    if (game == null) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!lookedUp) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Text("Game not found", color = PulsarTextDim)
                Spacer(Modifier.height(14.dp))
                Text(
                    "Back to library",
                    color = PulsarPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        return
    }

    val context = LocalContext.current
    var installedPath by remember(gameId) {
        mutableStateOf(GameInstaller.installedPlayable(context, game.system, game.slug)?.absolutePath)
    }
    // Real download state from WorkManager — survives leaving the screen and can say FAILED
    // (the old remembered-bool + filesystem poll could do neither).
    // remember the flow: a fresh instance per recomposition would restart collectAsState's
    // collector each time — with per-percent progress ticks that would tear down and rebuild
    // the WorkManager Room subscription ~100× per download.
    val statusFlow = remember(game.id) { DownloadManager.status(context, game.id) }
    val downloadState by statusFlow
        .collectAsState(initial = com.retrovault.download.DownloadState(DownloadStatus.NONE))
    val downloadStatus = downloadState.status
    val downloading = downloadStatus == DownloadStatus.DOWNLOADING && installedPath == null
    val downloadFailed = downloadStatus == DownloadStatus.FAILED && installedPath == null
    LaunchedEffect(downloadStatus) {
        if (downloadStatus == DownloadStatus.SUCCEEDED) {
            installedPath = GameInstaller.installedPlayable(context, game.system, game.slug)?.absolutePath
        }
    }

    val cover = coverBrush(game.id)

    Box(Modifier.fillMaxSize()) {
        // hero wash + scrim down to the base background
        Box(
            Modifier
                .fillMaxWidth()
                .height(440.dp)
                .background(cover)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(480.dp)
                .background(
                    Brush.verticalGradient(
                        0.05f to Color.Transparent,
                        1f to PulsarBg
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 56.dp, bottom = 40.dp)
                .padding(horizontal = 22.dp)
        ) {
            // top action row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // The favorite/"..." buttons were inert decorations — removed until they do
                // something real (dead taps read as "the app is broken" to a new user).
                RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
            }

            // floating cover
            Box(
                Modifier
                    .padding(top = 26.dp)
                    .width(172.dp)
                    .aspectRatio(3f / 4.2f)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(18.dp))
                    .background(cover)
                    .border(1.dp, PulsarStroke, RoundedCornerShape(18.dp))
            )

            Spacer(Modifier.height(20.dp))
            Text(
                game.title,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 27.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${game.system.displayName} · ${game.developer}",
                fontSize = 13.sp,
                color = PulsarTextDim,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
            )

            // meta chips
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                MetaChip(Icons.Filled.Verified, PulsarTeal, game.license)
                MetaChip(Icons.Filled.SdCard, PulsarPrimary, formatBytes(game.sizeBytes))
                MetaChip(Icons.Filled.Memory, PulsarYellow, game.system.shortCode)
            }

            // Community compatibility (P13): shown when the installed game has a real serial
            // with reports behind it.
            var compat by remember { mutableStateOf<com.retrovault.data.CompatSummary?>(null) }
            LaunchedEffect(installedPath) {
                compat = null
                val path = installedPath ?: return@LaunchedEffect
                val serial = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        com.retrovault.library.GameIdentifier
                            .identify(context, java.io.File(path), game.system)
                            ?.takeIf { !it.fakeId }?.serial
                    }.getOrNull()
                } ?: return@LaunchedEffect
                compat = com.retrovault.data.CompatReporter.summaryFor(serial)
            }
            compat?.let { s ->
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    MetaChip(
                        Icons.Filled.Verified, PulsarYellow,
                        "Community ★ ${"%.1f".format(s.avgRating ?: 0.0)} · ${s.reportCount} " +
                            if (s.reportCount == 1) "report" else "reports"
                    )
                }
            }

            // primary CTA
            Spacer(Modifier.height(22.dp))
            // CTA state machine: Continue (auto-save) / Play (installed) / Downloading /
            // Download (hosted) / Coming soon.
            val installed = installedPath != null
            val hasAutoSave = remember(installed, gameId) {
                installed && SaveStateManager(context, gameId).isPopulated(SaveStateManager.AUTO_SLOT)
            }
            // P27: playtime chip data.
            val playtimeMs = remember(gameId, installedPath) {
                com.retrovault.library.RecentPlays(context).playtimeMs(game.id)
            }

            val ctaEnabled = installed || (!downloading && game.downloadable)
            val ctaLabel = when {
                hasAutoSave -> "CONTINUE"
                installed -> "PLAY"
                downloading -> downloadState.progressPct?.let { "DOWNLOADING… $it%" } ?: "DOWNLOADING…"
                downloadFailed -> "DOWNLOAD FAILED — RETRY"
                game.downloadable -> "DOWNLOAD"
                else -> "COMING SOON"
            }
            val ctaIcon = when {
                installed -> Icons.Filled.PlayArrow
                downloading -> Icons.Filled.HourglassTop
                else -> Icons.Filled.Download
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .then(
                        if (ctaEnabled) Modifier.background(PulsarAccentBrush)
                        else Modifier.background(PulsarSurface2).border(1.dp, PulsarStroke, RoundedCornerShape(18.dp))
                    )
                    .clickable(enabled = ctaEnabled) {
                        if (installed) {
                            onPlay(game.id, game.title, game.system, installedPath, hasAutoSave)
                        } else if (game.downloadable) {
                            // Honor the user's Wi-Fi-only setting (it defaults ON and was being
                            // silently ignored — a real cellular-data cost trap).
                            val wifiOnly = SettingsResolver(context)
                                .resolve(PspSettings.WIFI_ONLY_DOWNLOADS).asBoolean
                            if (downloadFailed) {
                                DownloadManager.retry(context, game.id, game.slug, game.system, wifiOnly)
                            } else {
                                DownloadManager.enqueue(context, game.id, game.slug, game.system, wifiOnly)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        ctaIcon,
                        contentDescription = null,
                        tint = if (ctaEnabled) PulsarOnAccent else PulsarTextFaint,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        ctaLabel,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 2.sp,
                        color = if (ctaEnabled) PulsarOnAccent else PulsarTextFaint
                    )
                }
            }

            if (playtimeMs > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "You've played this for ${com.retrovault.library.RecentPlays.format(playtimeMs)}",
                    fontSize = 12.sp, color = PulsarTextDim,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // action row — real destinations (these were inert decorations; every dead tap
            // erodes a new user's trust that anything works)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(Icons.Filled.Save, PulsarPrimary, "Save States", Modifier.weight(1f)) { onOpenSaves() }
                ActionTile(Icons.Filled.VideogameAsset, PulsarTeal, "Controls", Modifier.weight(1f)) { onOpenControls() }
                ActionTile(Icons.Filled.Tune, PulsarYellow, "Settings", Modifier.weight(1f)) { onOpenSettings(game.id) }
            }

            // about
            Spacer(Modifier.height(20.dp))
            Text(
                "ABOUT",
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                color = PulsarTextDim
            )
            Spacer(Modifier.height(8.dp))
            Text(
                game.description.ifBlank { "No description yet." },
                fontSize = 13.sp,
                lineHeight = 21.sp,
                color = PulsarTextBody
            )
            // LICENSE & SOURCE — shown prominently: transparency is the legal foundation.
            Spacer(Modifier.height(20.dp))
            Text(
                "LICENSE & SOURCE",
                fontFamily = ChakraPetch, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, letterSpacing = 2.sp, color = PulsarTextDim
            )
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PulsarSurface1)
                    .border(1.dp, PulsarStrokeSoft, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                LicenseLine("Author", game.developer)
                Spacer(Modifier.height(8.dp))
                LicenseLine("License", game.license)
                val link = game.sourceUrl ?: game.licenseUrl
                if (link != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                                    )
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.OpenInNew, null, tint = PulsarTeal, modifier = Modifier.size(15.dp))
                        Text("View source / license", fontSize = 12.sp, color = PulsarTeal, fontFamily = ChakraPetch)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Only legally distributable games are hosted, with the author's redistribution license " +
                    "recorded. Commercial titles must be imported from your own copy.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = PulsarTextFaint
            )
        }
    }
}

@Composable
private fun LicenseLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = PulsarTextDim)
        Text(value, fontSize = 12.sp, color = PulsarText, fontFamily = ChakraPetch, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, onClick: () -> Unit, tint: Color = PulsarText) {
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x8C0A0E16))
            .border(1.dp, PulsarStroke, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun MetaChip(icon: ImageVector, iconTint: Color, text: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(PulsarSurface2)
            .border(1.dp, PulsarStroke, RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 12.sp, color = PulsarTextSoft)
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    tint: Color,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .background(PulsarSurface1)
            .border(1.dp, PulsarStrokeSoft, RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 11.sp, color = PulsarTextSoft)
    }
}
