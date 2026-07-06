package com.retrovault.feature.player

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarPrimary
import com.retrovault.core.ui.theme.PulsarStroke
import com.retrovault.core.ui.theme.PulsarTeal
import com.retrovault.core.ui.theme.PulsarText
import com.retrovault.core.ui.theme.PulsarTextDim
import com.retrovault.core.ui.theme.PulsarYellow
import com.retrovault.saves.ConflictChoice

/**
 * Save-conflict resolution sheet — shown when a save diverged on both this device and the cloud.
 * Nothing is overwritten until the user picks; "Keep both" preserves the cloud copy locally.
 */
@Composable
fun BoxScope.ConflictSheet(
    gameTitle: String,
    savesInConflict: Int,
    onChoice: (ConflictChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .matchParentSize()
            .background(Color(0xCC030508))
            .clickable(onClick = onDismiss)
    )
    Column(
        Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0A0D14))
            .border(1.dp, PulsarStroke, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.CloudSync, null, tint = PulsarYellow, modifier = Modifier.size(22.dp))
            Text("Save conflict", fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = PulsarText)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "$gameTitle changed on this device AND in the cloud since the last sync " +
                "($savesInConflict ${if (savesInConflict == 1) "save" else "saves"}). Nothing is " +
                "overwritten until you choose.",
            fontSize = 12.sp, lineHeight = 17.sp, color = PulsarTextDim
        )
        Spacer(Modifier.height(16.dp))

        ChoiceRow(Icons.Filled.PhoneAndroid, PulsarPrimary, "Keep this device", "Upload the device copy; replace the cloud one.") { onChoice(ConflictChoice.KEEP_DEVICE) }
        Spacer(Modifier.height(8.dp))
        ChoiceRow(Icons.Filled.CloudDownload, PulsarTeal, "Keep the cloud", "Download the cloud copy; replace the device one.") { onChoice(ConflictChoice.KEEP_CLOUD) }
        Spacer(Modifier.height(8.dp))
        ChoiceRow(Icons.Filled.Layers, PulsarYellow, "Keep both", "Upload the device copy and keep the cloud copy as a backup.") { onChoice(ConflictChoice.KEEP_BOTH) }

        Spacer(Modifier.height(12.dp))
        Text(
            "Decide later",
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
private fun ChoiceRow(icon: ImageVector, tint: Color, title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x0AFFFFFF))
            .border(1.dp, PulsarStroke, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(title, fontSize = 14.sp, color = PulsarText, fontWeight = FontWeight.SemiBold)
            Text(body, fontSize = 10.sp, lineHeight = 14.sp, color = PulsarTextDim)
        }
    }
}
