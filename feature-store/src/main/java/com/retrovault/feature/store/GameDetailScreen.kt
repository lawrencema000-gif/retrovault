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
import kotlinx.coroutines.delay
import com.retrovault.download.DownloadManager
import com.retrovault.download.GameInstaller
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
    onPlay: (String, String, GameSystem, String?) -> Unit = { _, _, _, _ -> },
) {
    val game = remember(gameId) {
        SupabaseCatalogRepository.cachedById(gameId) ?: CatalogRepository.byId(gameId)
    }

    if (game == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Game not found", color = PulsarTextDim)
        }
        return
    }

    val context = LocalContext.current
    var installedPath by remember(gameId) {
        mutableStateOf(GameInstaller.installedPlayable(context, game.system, game.slug)?.absolutePath)
    }
    var downloading by remember(gameId) { mutableStateOf(false) }
    LaunchedEffect(downloading) {
        // Poll for install completion while the WorkManager job runs.
        while (downloading) {
            delay(700)
            val p = GameInstaller.installedPlayable(context, game.system, game.slug)
            if (p != null) {
                installedPath = p.absolutePath
                downloading = false
            }
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
                RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundIconButton(Icons.Filled.FavoriteBorder, onClick = {}, tint = PulsarFav)
                    RoundIconButton(Icons.Filled.MoreHoriz, onClick = {})
                }
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

            // primary CTA
            Spacer(Modifier.height(22.dp))
            // CTA state machine: Play (installed) / Downloading / Download (hosted) / Coming soon.
            val installed = installedPath != null
            val ctaEnabled = installed || (!downloading && game.downloadable)
            val ctaLabel = when {
                installed -> "PLAY"
                downloading -> "DOWNLOADING…"
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
                            onPlay(game.id, game.title, game.system, installedPath)
                        } else if (game.downloadable) {
                            DownloadManager.enqueue(
                                context, game.id, game.slug, game.system,
                                wifiOnly = false, // P11 settings expose the Wi-Fi-only toggle
                            )
                            downloading = true
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

            // action row
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(Icons.Filled.Save, PulsarPrimary, "Save States", Modifier.weight(1f))
                ActionTile(Icons.Filled.VideogameAsset, PulsarTeal, "Controls", Modifier.weight(1f))
                ActionTile(Icons.Filled.Tune, PulsarYellow, "Settings", Modifier.weight(1f))
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
            Spacer(Modifier.height(14.dp))
            Text(
                "Only legally distributable games are hosted. Commercial titles must be imported from your own copy.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = PulsarTextFaint
            )
        }
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
private fun ActionTile(icon: ImageVector, tint: Color, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PulsarSurface1)
            .border(1.dp, PulsarStrokeSoft, RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 11.sp, color = PulsarTextSoft)
    }
}
