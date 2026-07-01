package com.retrovault.feature.store

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarPrimary
import com.retrovault.core.ui.theme.PulsarSurface2
import com.retrovault.core.ui.theme.PulsarText
import com.retrovault.core.ui.theme.PulsarTextDim

@Composable
fun SavesScreen() = PlaceholderScreen(
    icon = Icons.Filled.Save,
    title = "Save States",
    subtitle = "Your cloud save states appear here once you start playing. Coming with the emulator."
)

@Composable
fun ControlsScreen() = PlaceholderScreen(
    icon = Icons.Filled.VideogameAsset,
    title = "Controls",
    subtitle = "Customize your touch layout and button mapping. Available with the emulator."
)

@Composable
private fun PlaceholderScreen(icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(PulsarSurface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = PulsarPrimary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = PulsarText)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            fontSize = 13.sp,
            color = PulsarTextDim,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
