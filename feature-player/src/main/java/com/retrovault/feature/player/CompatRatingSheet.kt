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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarAccentBrush
import com.retrovault.core.ui.theme.PulsarOnAccent
import com.retrovault.core.ui.theme.PulsarStroke
import com.retrovault.core.ui.theme.PulsarText
import com.retrovault.core.ui.theme.PulsarTextDim

/**
 * Post-session compatibility prompt (P13): a 5-tier "how did it run?" with optional
 * per-aspect sub-scores. Shown once per game+version after ≥10 minutes of play.
 */
@Composable
fun BoxScope.CompatRatingSheet(
    gameTitle: String,
    onSubmit: (rating: Int, subScores: Map<String, Int>) -> Unit,
    onSkip: () -> Unit,
) {
    var rating by remember { mutableIntStateOf(0) }
    var gfx by remember { mutableIntStateOf(0) }
    var audio by remember { mutableIntStateOf(0) }
    var speed by remember { mutableIntStateOf(0) }

    // Back-to-dismiss is the universal expectation on a modal sheet — back = skip.
    androidx.activity.compose.BackHandler { onSkip() }

    Box(
        Modifier
            .matchParentSize()
            .background(Color(0xCC030508))
    )
    Column(
        Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0A0D14))
            .border(1.dp, PulsarStroke, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Text(
            "How did it run?",
            fontFamily = ChakraPetch, fontWeight = FontWeight.Bold,
            fontSize = 17.sp, color = PulsarText
        )
        Text(gameTitle, fontSize = 11.sp, color = PulsarTextDim)
        Spacer(Modifier.height(14.dp))

        val tiers = listOf("Broken", "Poor", "Playable", "Great", "Perfect")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tiers.forEachIndexed { i, label ->
                val value = i + 1
                val selected = rating == value
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (selected) Modifier.background(PulsarAccentBrush)
                            else Modifier.background(Color(0x0AFFFFFF)).border(1.dp, PulsarStroke, RoundedCornerShape(12.dp))
                        )
                        .clickable { rating = value }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label,
                        fontSize = 9.sp,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) PulsarOnAccent else PulsarTextDim
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SubScoreRow("Graphics", gfx) { gfx = it }
        SubScoreRow("Audio", audio) { audio = it }
        SubScoreRow("Speed", speed) { speed = it }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x0AFFFFFF))
                    .border(1.dp, PulsarStroke, RoundedCornerShape(14.dp))
                    .clickable(onClick = onSkip)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) { Text("Skip", color = PulsarTextDim, fontSize = 13.sp) }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (rating > 0) Modifier.background(PulsarAccentBrush)
                        else Modifier.background(Color(0x0AFFFFFF)).border(1.dp, PulsarStroke, RoundedCornerShape(14.dp))
                    )
                    .clickable(enabled = rating > 0) {
                        val subs = buildMap {
                            if (gfx > 0) put("graphics", gfx)
                            if (audio > 0) put("audio", audio)
                            if (speed > 0) put("speed", speed)
                        }
                        onSubmit(rating, subs)
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Submit",
                    color = if (rating > 0) PulsarOnAccent else PulsarTextDim,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Anonymous device + settings info is included to help other players.",
            fontSize = 9.sp, color = PulsarTextDim
        )
    }
}

@Composable
private fun SubScoreRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = PulsarTextDim, modifier = Modifier.width(70.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 1..5) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (i <= value) Color(0xFF2A7FFF) else Color(0x14FFFFFF))
                        .border(1.dp, PulsarStroke, CircleShape)
                        .clickable { onChange(if (value == i) 0 else i) }
                )
            }
        }
    }
}
