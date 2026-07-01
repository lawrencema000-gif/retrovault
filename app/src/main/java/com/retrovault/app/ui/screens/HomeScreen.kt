package com.retrovault.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retrovault.app.data.model.GameSystem
import com.retrovault.app.ui.components.GameCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onGameClick: (String) -> Unit,
    viewModel: CatalogViewModel = viewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    var selectedSystem by remember { mutableStateOf<GameSystem?>(null) }

    val allGames = when (val s = uiState) {
        is CatalogUiState.Success -> s.games
        is CatalogUiState.Offline -> s.games
        CatalogUiState.Loading -> emptyList()
    }
    val games = remember(selectedSystem, allGames) {
        if (selectedSystem == null) allGames else allGames.filter { it.system == selectedSystem }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "RetroVault",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Free & homebrew games - download and play",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (uiState is CatalogUiState.Offline) {
            Text(
                "Offline - showing sample catalog",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(4.dp))
        }

        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedSystem == null,
                onClick = { selectedSystem = null },
                label = { Text("All") }
            )
            GameSystem.entries.forEach { sys ->
                FilterChip(
                    selected = selectedSystem == sys,
                    onClick = { selectedSystem = sys },
                    label = { Text(sys.shortCode) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (uiState is CatalogUiState.Loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(games, key = { it.id }) { game ->
                    GameCard(game = game, onClick = { onGameClick(game.id) })
                }
            }
        }
    }
}
