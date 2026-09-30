package com.researchradar.feature.compare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.researchradar.core.data.repository.MapRepository
import com.researchradar.core.model.ResearchMap
import com.researchradar.core.model.buildCompareColumns
import com.researchradar.core.model.defaultCompareColumnIds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompareUiState(
    val mapId: String = "",
    val isLoading: Boolean = true,
    val researchMap: ResearchMap? = null,
    /** null = default selection derived from the map's own extracted metrics. */
    val visibleColumns: Set<String>? = null,
    val isColumnPickerOpen: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CompareViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mapRepository: MapRepository,
) : ViewModel() {

    private val mapId: String = checkNotNull(savedStateHandle["mapId"])

    private val _uiState = MutableStateFlow(CompareUiState(mapId = mapId))
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    init {
        loadMap()
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
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load map for comparison.",
                    )
                }
            }
        }
    }

    fun openColumnPicker() {
        _uiState.update { it.copy(isColumnPickerOpen = true) }
    }

    fun closeColumnPicker() {
        _uiState.update { it.copy(isColumnPickerOpen = false) }
    }

    fun toggleColumn(columnId: String) {
        _uiState.update { current ->
            val papers = current.researchMap?.papers.orEmpty().filter { it.extraction != null || it.hasFullText }
            val set = (current.visibleColumns ?: defaultCompareColumnIds(buildCompareColumns(papers))).toMutableSet()
            if (set.contains(columnId)) {
                if (set.size > 1) set.remove(columnId)
            } else {
                set.add(columnId)
            }
            current.copy(visibleColumns = set)
        }
    }
}
