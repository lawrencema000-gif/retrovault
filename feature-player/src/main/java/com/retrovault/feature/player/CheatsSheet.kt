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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovault.cheats.CheatEntry
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarPrimary
import com.retrovault.core.ui.theme.PulsarStroke
import com.retrovault.core.ui.theme.PulsarText
import com.retrovault.core.ui.theme.PulsarTextDim
import com.retrovault.core.ui.theme.PulsarTextFaint

/**
 * In-game cheats list: per-game CWCheat toggles with search. Empty states cover "no cheat.db
 * imported" and "no cheats for this game". Cheats come from a user-imported database only.
 */
@Composable
fun BoxScope.CheatsSheet(
    dbImported: Boolean,
    hasSerial: Boolean,
    entries: List<CheatEntry>,
    onToggle: (name: String, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(entries, query) {
        if (query.isBlank()) entries
        else entries.filter { it.cheat.name.contains(query, true) }
    }

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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Bolt, null, tint = PulsarPrimary, modifier = Modifier.size(20.dp))
            Text("Cheats", fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PulsarText)
        }

        when {
            !dbImported -> EmptyNote(
                "No cheat database imported.",
                "Import a CWCheat cheat.db in Settings → Cheats. Pulsar never bundles cheat data."
            )
            !hasSerial -> EmptyNote(
                "Cheats need a game disc ID.",
                "This title has no readable serial (homebrew), so no cheat lookup is possible."
            )
            entries.isEmpty() -> EmptyNote(
                "No cheats for this game.",
                "Your cheat.db has no entry for this game's serial."
            )
            else -> {
                Spacer(Modifier.height(12.dp))
                SearchBox(query) { query = it }
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    visible.forEach { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x0AFFFFFF))
                                .border(1.dp, PulsarStroke, RoundedCornerShape(12.dp))
                                .clickable { onToggle(entry.cheat.name, !entry.enabled) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                entry.cheat.name,
                                fontSize = 13.sp, color = PulsarText,
                                modifier = Modifier.fillMaxWidth(0.8f)
                            )
                            CheatToggle(entry.enabled)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enabling a cheat disables achievements for this session.",
                    fontSize = 9.sp, color = PulsarTextFaint
                )
            }
        }

        Spacer(Modifier.height(12.dp))
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

/** A compact pill toggle (feature-player has no shared switch); parent row handles the click. */
@Composable
private fun CheatToggle(on: Boolean) {
    Box(
        Modifier
            .size(width = 40.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (on) PulsarPrimary else Color(0x22FFFFFF))
            .border(1.dp, PulsarStroke, RoundedCornerShape(11.dp))
            .padding(2.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
        )
    }
}

@Composable
private fun EmptyNote(title: String, body: String) {
    Column(Modifier.padding(top = 16.dp, bottom = 6.dp)) {
        Text(title, fontSize = 13.sp, color = PulsarText, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 11.sp, lineHeight = 16.sp, color = PulsarTextFaint)
    }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = PulsarText, fontSize = 13.sp),
        cursorBrush = SolidColor(PulsarPrimary),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x0AFFFFFF))
                    .border(1.dp, PulsarStroke, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Search, null, tint = PulsarTextDim, modifier = Modifier.size(17.dp))
                Box(Modifier.fillMaxWidth()) {
                    if (query.isEmpty()) Text("Search cheats…", fontSize = 13.sp, color = PulsarTextFaint)
                    inner()
                }
            }
        }
    )
}
