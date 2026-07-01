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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarPrimary
import com.retrovault.core.ui.theme.PulsarStrokeSoft
import com.retrovault.core.ui.theme.PulsarSurface1
import com.retrovault.core.ui.theme.PulsarSurface2
import com.retrovault.core.ui.theme.PulsarText
import com.retrovault.core.ui.theme.PulsarTextDim
import com.retrovault.core.ui.theme.PulsarTextFaint
import com.retrovault.core.ui.theme.PulsarTextGhost

@Composable
fun SavesScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 56.dp, bottom = 110.dp)
            .padding(horizontal = 22.dp)
    ) {
        Text("Save States", fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = PulsarText)
        Text("Play a game to create cloud save states", fontSize = 12.sp, color = PulsarTextDim)
        Spacer(Modifier.height(22.dp))

        val slots = (0 until 6).toList()
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            slots.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { i -> SlotCard(i, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SlotCard(index: Int, modifier: Modifier = Modifier) {
    val border = if (index == 0) PulsarPrimary.copy(alpha = 0.4f) else PulsarStrokeSoft
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = PulsarTextGhost, modifier = Modifier.height(30.dp))
            }
            if (index == 0) {
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
                    if (index == 0) "Auto Save" else "Slot $index",
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = PulsarText
                )
                Text("—", fontSize = 10.sp, color = PulsarTextFaint)
            }
            Text("Tap to save", fontSize = 11.sp, color = PulsarTextDim)
        }
    }
}
