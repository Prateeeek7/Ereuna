package com.researchradar.feature.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.researchradar.core.data.repository.MapRepository
import com.researchradar.core.design.components.EvidenceSheetData
import com.researchradar.core.model.buildCompareColumns
import com.researchradar.core.model.defaultCompareColumnIds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mapRepository: MapRepository,
) : ViewModel() {

    private val mapId: String = checkNotNull(savedStateHandle["mapId"])

    private val _uiState = MutableStateFlow(MapUiState(mapId = mapId))
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        observeBookmarkStatus()
        loadMap()
    }

    private fun observeBookmarkStatus() {
        viewModelScope.launch {
            mapRepository.observeIsMapSaved(mapId).collect { isSaved ->
                _uiState.update { it.copy(isBookmarked = isSaved) }
            }
        }
    }

    fun loadMap() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val map = mapRepository.getMap(mapId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        researchMap = map,
                        isOffline = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load research map.",
                    )
                }
            }
        }
    }

    fun toggleBookmark() {
        val currentMap = _uiState.value.researchMap ?: return
        viewModelScope.launch {
            try {
                mapRepository.toggleSaveMap(currentMap)
            } catch (_: Exception) {
                // Ignore DB error
            }
        }
    }

    fun selectTab(tab: MapTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setPaperFilter(filter: String) {
        _uiState.update { it.copy(activePaperFilter = filter) }
    }

    fun setPaperSort(sort: PaperSort) {
        _uiState.update { it.copy(activeSort = sort) }
    }

    fun filterByTool(toolName: String?) {
        _uiState.update {
            it.copy(
                selectedToolFilter = toolName,
                selectedTab = MapTab.PAPERS,
            )
        }
    }

    fun clearToolFilter() {
        _uiState.update { it.copy(selectedToolFilter = null) }
    }

    fun toggleSelectPaperForCompare(paperId: String) {
        _uiState.update { current ->
            val set = current.selectedPaperIdsForCompare.toMutableSet()
            if (set.contains(paperId)) {
                set.remove(paperId)
            } else {
                set.add(paperId)
            }
            current.copy(selectedPaperIdsForCompare = set)
        }
    }

    fun clearSelectedPapersForCompare() {
        _uiState.update { it.copy(selectedPaperIdsForCompare = emptySet()) }
    }

    fun showEvidence(data: EvidenceSheetData) {
        _uiState.update { it.copy(activeEvidenceSheet = data) }
    }

    fun dismissEvidence() {
        _uiState.update { it.copy(activeEvidenceSheet = null) }
    }

    fun openColumnPicker() {
        _uiState.update { it.copy(isColumnPickerOpen = true) }
    }

    fun closeColumnPicker() {
        _uiState.update { it.copy(isColumnPickerOpen = false) }
    }

    fun openExportDialog() {
        _uiState.update { it.copy(isExportDialogOpen = true) }
    }

    fun closeExportDialog() {
        _uiState.update { it.copy(isExportDialogOpen = false) }
    }

    private fun compareCandidates(state: MapUiState) = state.researchMap?.papers.orEmpty().let { papers ->
        if (state.selectedPaperIdsForCompare.isNotEmpty()) {
            papers.filter { it.id in state.selectedPaperIdsForCompare }
        } else {
            papers.filter { it.extraction != null || it.hasFullText }
        }
    }

    fun toggleCompareColumn(columnId: String) {
        _uiState.update { current ->
            val cols = (
                current.visibleCompareColumns
                    ?: defaultCompareColumnIds(buildCompareColumns(compareCandidates(current)))
                ).toMutableSet()
            if (cols.contains(columnId)) {
                if (cols.size > 1) cols.remove(columnId)
            } else {
                cols.add(columnId)
            }
            current.copy(visibleCompareColumns = cols)
        }
    }
}
