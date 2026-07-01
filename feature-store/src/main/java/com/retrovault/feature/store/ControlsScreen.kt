package com.retrovault.feature.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarBlueSoft
import com.retrovault.core.ui.theme.PulsarPink
import com.retrovault.core.ui.theme.PulsarRed
import com.retrovault.core.ui.theme.PulsarStrokeSoft
import com.retrovault.core.ui.theme.PulsarSurface1
import com.retrovault.core.ui.theme.PulsarTeal
import com.retrovault.core.ui.theme.PulsarText
import com.retrovault.core.ui.theme.PulsarTextDim
import com.retrovault.core.ui.theme.PulsarTextFaint
import com.retrovault.core.ui.theme.PulsarTextGhost
import com.retrovault.core.ui.theme.PulsarTextSoft

private data class Binding(val symbol: String, val color: Color, val action: String, val mapped: String)
private data class BindGroup(val title: String, val rows: List<Binding>)

private val bindGroups = listOf(
    BindGroup(
        "FACE BUTTONS",
        listOf(
            Binding("△", PulsarTeal, "Triangle", "Menu"),
            Binding("○", PulsarRed, "Circle", "Back"),
            Binding("✕", PulsarBlueSoft, "Cross", "Confirm"),
            Binding("□", PulsarPink, "Square", "Action"),
        )
    ),
    BindGroup(
        "SHOULDER",
        listOf(
            Binding("L", PulsarTextSoft, "Left Trigger", "Target"),
            Binding("R", PulsarTextSoft, "Right Trigger", "Fire"),
        )
    ),
    BindGroup(
        "SYSTEM",
        listOf(
            Binding("≡", PulsarTextSoft, "Start", "Pause"),
            Binding("·", PulsarTextSoft, "Select", "Map"),
        )
    ),
)

@Composable
fun ControlsScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 56.dp, bottom = 110.dp)
            .padding(horizontal = 22.dp)
    ) {
        Text("Controls", fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = PulsarText)
        Text("Tap a binding to remap", fontSize = 12.sp, color = PulsarTextDim)

        // layout preview
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF14203A), Color(0xFF0B1120))))
                .border(1.dp, PulsarStrokeSoft, RoundedCornerShape(20.dp))
        ) {
            // decorative d-pad
            Box(Modifier.align(Alignment.CenterStart).padding(start = 26.dp).size(54.dp)) {
                Box(Modifier.align(Alignment.Center).fillMaxWidth().height(18.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x24FFFFFF)))
                Box(Modifier.align(Alignment.Center).width(18.dp).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(Color(0x24FFFFFF)))
            }
            // decorative face dots
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 30.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Dot(PulsarPink); Dot(PulsarBlueSoft); Dot(PulsarTeal); Dot(PulsarRed)
            }
            Text(
                "TOUCH LAYOUT",
                fontFamily = ChakraPetch,
                fontSize = 11.sp,
                letterSpacing = 3.sp,
                color = PulsarTextGhost,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        bindGroups.forEach { group ->
            Spacer(Modifier.height(22.dp))
            Text(
                group.title,
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
            ) {
                group.rows.forEachIndexed { i, row ->
                    BindingRow(row)
                    if (i < group.rows.lastIndex) HorizontalDivider(color = PulsarStrokeSoft, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.6f))
    )
}

@Composable
private fun BindingRow(binding: Binding) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(binding.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(binding.symbol, color = binding.color, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = ChakraPetch)
            }
            Text(binding.action, fontSize = 14.sp, color = PulsarTextSoft)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(binding.mapped, fontSize = 13.sp, color = PulsarTextFaint)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = PulsarTextFaint, modifier = Modifier.size(18.dp))
        }
    }
}
