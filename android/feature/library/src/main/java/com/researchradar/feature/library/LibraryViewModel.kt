package com.researchradar.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.researchradar.core.data.repository.MapRepository
import com.researchradar.core.model.Paper
import com.researchradar.core.model.ResearchMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mapRepository: MapRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        observeSavedMaps()
        observeSavedPapers()
        refresh()
    }

    /** Pulls the account's library from the server (saved on the website or another phone). */
    fun refresh() {
        viewModelScope.launch {
            try {
                mapRepository.syncLibrary()
            } catch (_: Exception) {
                // Offline: the copy on this device is shown.
            }
        }
    }

    private fun observeSavedMaps() {
        viewModelScope.launch {
            mapRepository.observeSavedMaps().collect { maps ->
                _uiState.update {
                    it.copy(
                        savedMaps = maps,
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun observeSavedPapers() {
        viewModelScope.launch {
            mapRepository.observeSavedPapers().collect { papers ->
                _uiState.update {
                    it.copy(
                        savedPapers = papers,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun unsaveMap(map: ResearchMap) {
        viewModelScope.launch {
            try {
                mapRepository.toggleSaveMap(map)
            } catch (_: Exception) {
            }
        }
    }

    fun unsavePaper(paper: Paper) {
        viewModelScope.launch {
            try {
                mapRepository.toggleSavePaper(paper)
            } catch (_: Exception) {
            }
        }
    }
}
