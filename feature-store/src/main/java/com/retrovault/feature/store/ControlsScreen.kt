package com.retrovault.feature.store

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
        // Honest copy: the remap UI hasn't shipped yet — pads auto-map from the SDL database and
        // touch controls are always available in-game; don't invite taps that do nothing.
        Text(
            "Touch controls are always on in-game. Gamepads map automatically " +
                "(SDL controller database) — custom remapping arrives in a later update.",
            fontSize = 12.sp, color = PulsarTextDim
        )

        // ---- Touch skins (P27): portable .pulsarskin layouts with import/export ----
        SkinSection()

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


/** P27: choose / import / export `.pulsarskin` touch layouts. */
@Composable
private fun SkinSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = androidx.compose.runtime.remember { com.retrovault.input.SkinStore(context) }
    var tick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var message by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }
    val active = androidx.compose.runtime.remember(tick) { store.activeSkinName }
    val installed = androidx.compose.runtime.remember(tick) { store.installed() }

    val importPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val tmp = java.io.File(context.cacheDir, "import.pulsarskin")
        val skin = runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            com.retrovault.input.PulsarSkin.readFile(tmp)
        }.getOrNull()
        tmp.delete()
        message = if (skin == null) {
            "That file isn't a valid .pulsarskin."
        } else {
            val name = store.install(skin)
            if (name != null) {
                store.activeSkinName = name
                "Imported and activated \"$name\"."
            } else "Import failed."
        }
        tick++
    }

    val exportPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // Export the ACTIVE skin, or a snapshot of the default layout built offscreen at this
        // display's size (positions are normalized, so it round-trips across devices).
        val skin = store.activeSkin() ?: run {
            val dm = context.resources.displayMetrics
            val w = maxOf(dm.widthPixels, dm.heightPixels)
            val h = minOf(dm.widthPixels, dm.heightPixels)
            val v = com.retrovault.input.TouchOverlayView(context, com.retrovault.input.InputHub(), haptics = null)
            v.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY),
            )
            v.layout(0, 0, w, h)
            v.currentSkin("Pulsar default")
        }
        val tmp = java.io.File(context.cacheDir, "export.pulsarskin")
        val ok = com.retrovault.input.PulsarSkin.writeFile(skin, tmp) && runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { out ->
                tmp.inputStream().use { it.copyTo(out) }
            }
        }.isSuccess
        tmp.delete()
        message = if (ok) "Skin exported." else "Export failed."
        tick++
    }

    Spacer(Modifier.height(22.dp))
    Text(
        "TOUCH SKIN",
        fontFamily = ChakraPetch, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, letterSpacing = 2.sp, color = PulsarTextDim
    )
    Spacer(Modifier.height(10.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PulsarSurface1)
            .border(1.dp, PulsarStrokeSoft, RoundedCornerShape(16.dp))
    ) {
        // Active skin cycler: Default -> each installed skin -> Default.
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    val cycle = listOf("") + installed
                    val next = cycle[(cycle.indexOf(active).coerceAtLeast(0) + 1) % cycle.size]
                    store.activeSkinName = next
                    tick++
                }
                .padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Active skin", fontSize = 14.sp, color = PulsarText)
            Text(
                if (active.isEmpty()) "Default" else active,
                fontSize = 13.sp, color = PulsarTeal, fontFamily = ChakraPetch
            )
        }
        HorizontalDivider(color = PulsarStrokeSoft, thickness = 1.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { importPicker.launch(arrayOf("*/*")) }
                .padding(horizontal = 16.dp, vertical = 15.dp)
        ) { Text("Import skin (.pulsarskin)", fontSize = 14.sp, color = PulsarText) }
        HorizontalDivider(color = PulsarStrokeSoft, thickness = 1.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { exportPicker.launch("pulsar-layout.pulsarskin") }
                .padding(horizontal = 16.dp, vertical = 15.dp)
        ) { Text("Export current layout", fontSize = 14.sp, color = PulsarText) }
        message?.let {
            Text(
                it, fontSize = 11.sp, color = PulsarTextDim,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
