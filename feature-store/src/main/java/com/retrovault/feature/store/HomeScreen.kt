package com.retrovault.feature.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.retrovault.core.model.Game
import com.retrovault.download.RomImporter
import com.retrovault.core.ui.coverBrush
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarBlueBrush
import com.retrovault.core.ui.theme.PulsarStroke
import com.retrovault.core.ui.theme.PulsarSurface2
import com.retrovault.core.ui.theme.PulsarText
import com.retrovault.core.ui.theme.PulsarTextDim
import com.retrovault.core.ui.theme.PulsarTextDimmer
import com.retrovault.core.ui.theme.PulsarTextSoft
import com.retrovault.core.ui.component.GameCard

@Composable
fun HomeScreen(
    onGameClick: (String) -> Unit,
    viewModel: CatalogViewModel = viewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    val games = when (val s = uiState) {
        is CatalogUiState.Success -> s.games
        is CatalogUiState.Offline -> s.games
        CatalogUiState.Loading -> emptyList()
    }
    val loading = uiState is CatalogUiState.Loading
    val offline = uiState is CatalogUiState.Offline

    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { RomImporter.import(context, it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 56.dp, bottom = 110.dp)
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Welcome back", fontSize = 12.sp, color = PulsarTextDimmer, fontWeight = FontWeight.SemiBold)
                Text(
                    "Your Library",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PulsarText,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTile(Icons.Filled.FileUpload) { importLauncher.launch(arrayOf("*/*")) }
                IconTile(Icons.Filled.Search) {}
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PulsarBlueBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", color = Color.White, fontFamily = ChakraPetch, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (offline) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Offline · showing sample catalog",
                fontSize = 11.sp,
                color = PulsarTextDimmer,
                modifier = Modifier.padding(horizontal = 22.dp)
            )
        }

        // Featured carousel
        Spacer(Modifier.height(26.dp))
        SectionLabel("FEATURED", Modifier.padding(horizontal = 22.dp))
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(games.take(6), key = { it.id }) { game ->
                FeaturedCard(game) { onGameClick(game.id) }
            }
        }

        // All games grid
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("ALL GAMES")
            Text("${games.size} titles", fontSize = 12.sp, color = PulsarTextDimmer)
        }
        Spacer(Modifier.height(14.dp))

        if (loading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        } else {
            Column(
                Modifier.padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                games.chunked(3).forEach { rowGames ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        rowGames.forEach { game ->
                            GameCard(
                                game = game,
                                onClick = { onGameClick(game.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowGames.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PulsarSurface2)
            .border(1.dp, PulsarStroke, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = PulsarTextSoft, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 2.sp,
        color = PulsarTextDim
    )
}

@Composable
private fun FeaturedCard(game: Game, onClick: () -> Unit) {
    Column(
        Modifier
            .width(210.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(128.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(coverBrush(game.id))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color(0xE604060C)
                        )
                    )
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text(
                game.title,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${game.system.displayName} · ${game.license}",
            fontSize = 11.sp,
            color = PulsarTextDimmer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
