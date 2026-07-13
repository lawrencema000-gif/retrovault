package com.retrovault.feature.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarPrimary
import com.retrovault.core.ui.theme.PulsarStrokeSoft
import com.retrovault.core.ui.theme.PulsarSurface1
import com.retrovault.core.ui.theme.PulsarSurface2
import com.retrovault.core.ui.theme.PulsarText
import com.retrovault.core.ui.theme.PulsarTextDim
import com.retrovault.core.ui.theme.PulsarTextFaint
import com.retrovault.saves.SaveStore
import java.io.File
import java.text.DateFormat
import java.util.Date

/** One real save state on disk (any game). */
private data class SaveRow(
    val gameKey: String,
    val slotLabel: String,
    val isAuto: Boolean,
    val updatedAt: Long,
    val screenshotPath: String?,
)

/**
 * Lists the save states that actually exist on this device, per game and slot — with the honest
 * hint that saving/loading happens from the in-game quick menu (this screen is an overview).
 */
@Composable
fun SavesScreen() {
    val context = LocalContext.current
    val saves by remember { mutableStateOf(loadSaves(context)) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 56.dp, bottom = 110.dp)
            .padding(horizontal = 22.dp)
    ) {
        Text("Save States", fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = PulsarText)
        Text(
            if (saves.isEmpty()) "Play a game and use the quick menu to create save states"
            else "Created from the in-game quick menu · load them there too",
            fontSize = 12.sp, color = PulsarTextDim
        )
        Spacer(Modifier.height(22.dp))

        if (saves.isEmpty()) {
            Text(
                "No save states yet. While playing, open the quick menu and tap \"Slots\" to " +
                    "save your progress — Pulsar also auto-saves when you quit a game.",
                fontSize = 12.sp, lineHeight = 17.sp, color = PulsarTextFaint
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                saves.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { s -> SaveCard(s, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Walk saves/<gameKey>/slotN.state on disk (the SaveStore layout) into display rows. */
private fun loadSaves(context: android.content.Context): List<SaveRow> {
    val store = SaveStore(context)
    val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "saves")
    val gameDirs = root.listFiles()?.filter { it.isDirectory } ?: emptyList()
    return gameDirs.flatMap { dir ->
        store.listSlots(dir.name).filter { !it.isEmpty }.map { slot ->
            SaveRow(
                gameKey = dir.name,
                slotLabel = slot.label,
                isAuto = slot.isAuto,
                updatedAt = slot.updatedAtEpochMs,
                screenshotPath = slot.screenshotPath,
            )
        }
    }.sortedByDescending { it.updatedAt }
}

@Composable
private fun SaveCard(save: SaveRow, modifier: Modifier = Modifier) {
    val border = if (save.isAuto) PulsarPrimary.copy(alpha = 0.4f) else PulsarStrokeSoft
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PulsarSurface1)
            .border(1.dp, border, RoundedCornerShape(16.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .background(PulsarSurface2)
        ) {
            val shot = save.screenshotPath
            if (shot != null && File(shot).exists()) {
                AsyncImage(
                    model = File(shot),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (save.isAuto) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PulsarPrimary.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("AUTO", fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.sp, color = PulsarPrimary)
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    save.slotLabel,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = PulsarText
                )
                Text(
                    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(save.updatedAt)),
                    fontSize = 10.sp, color = PulsarTextFaint
                )
            }
            Text(
                save.gameKey, fontSize = 11.sp, color = PulsarTextDim,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
