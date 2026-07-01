package com.retrovault.feature.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retrovault.core.model.Game
import com.retrovault.data.CatalogRepository
import com.retrovault.data.SupabaseCatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Success(val games: List<Game>) : CatalogUiState
    /** Network failed — falls back to the bundled placeholder list so the store still renders. */
    data class Offline(val games: List<Game>, val message: String) : CatalogUiState
}

class CatalogViewModel : ViewModel() {

    private val _state = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = CatalogUiState.Loading
            _state.value = try {
                CatalogUiState.Success(SupabaseCatalogRepository.fetchGames())
            } catch (e: Exception) {
                CatalogUiState.Offline(CatalogRepository.all(), e.message ?: "Offline")
            }
        }
    }
}
