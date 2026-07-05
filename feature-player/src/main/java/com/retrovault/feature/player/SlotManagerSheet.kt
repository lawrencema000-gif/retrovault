package com.retrovault.feature.player

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarPrimary
import com.retrovault.core.ui.theme.PulsarStroke
import com.retrovault.core.ui.theme.PulsarTeal
import com.retrovault.core.ui.theme.PulsarText
import com.retrovault.core.ui.theme.PulsarTextDim
import com.retrovault.saves.SaveStateManager
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Save-state slot manager: auto-slot + numbered slots with thumbnails and timestamps,
 * save/load/delete per slot, undo-save and undo-load.
 */
@Composable
fun BoxScope.SlotManagerSheet(
    manager: SaveStateManager,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val populated = remember(refresh) { manager.slots().associateBy { it.slot } }

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Save slots",
                fontFamily = ChakraPetch, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, color = PulsarText, modifier = Modifier.weight(1f)
            )
            if (manager.canUndoLoad()) {
                SmallAction("Undo load", Icons.Filled.Undo, PulsarTeal) {
                    scope.launch { manager.undoLoad(); refresh++ }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (slot in listOf(SaveStateManager.AUTO_SLOT) + (1..SaveStateManager.MAX_SLOTS)) {
                val info = populated[slot]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x0AFFFFFF))
                        .border(1.dp, PulsarStroke, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // thumbnail
                    Box(
                        Modifier
                            .size(width = 84.dp, height = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF11151F)),
                        contentAlignment = Alignment.Center
                    ) {
                        val thumbPath = info?.screenshotPath
                        val bmp = remember(thumbPath, refresh) {
                            thumbPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
                        }
                        if (bmp != null) {
                            Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 84.dp, height = 48.dp))
                        } else {
                            Icon(Icons.Filled.SportsEsports, null, tint = PulsarTextDim, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (slot == SaveStateManager.AUTO_SLOT) "Auto save" else "Slot $slot",
                            color = PulsarText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                        )
                        Text(
                            info?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.updatedAtEpochMs)) }
                                ?: "Empty",
                            color = PulsarTextDim, fontSize = 11.sp
                        )
                    }
                    // actions
                    if (slot != SaveStateManager.AUTO_SLOT) {
                        SmallAction("Save", Icons.Filled.Save, PulsarPrimary) {
                            scope.launch { manager.save(slot); refresh++ }
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    if (info != null) {
                        SmallAction("Load", Icons.Filled.History, PulsarTeal) {
                            scope.launch { manager.load(slot); refresh++ }
                        }
                        Spacer(Modifier.width(6.dp))
                        if (manager.canUndoSave(slot)) {
                            SmallAction("Undo", Icons.Filled.Undo, Color.White) {
                                scope.launch { manager.undoSave(slot); refresh++ }
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        if (slot != SaveStateManager.AUTO_SLOT) {
                            SmallAction("Del", Icons.Filled.Delete, Color(0xFFFF8A8A)) {
                                manager.delete(slot); refresh++
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Close",
            color = PulsarTextDim, fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 22.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SmallAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x14FFFFFF))
            .border(1.dp, PulsarStroke, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(label, color = PulsarText, fontSize = 9.sp)
    }
}
