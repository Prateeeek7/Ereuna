package com.researchradar.feature.paper

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.researchradar.core.data.repository.MapRepository
import com.researchradar.core.design.components.EvidenceSheetData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaperViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mapRepository: MapRepository,
) : ViewModel() {

    private val paperId: String = checkNotNull(savedStateHandle["paperId"])

    private val _uiState = MutableStateFlow(PaperUiState(paperId = paperId))
    val uiState: StateFlow<PaperUiState> = _uiState.asStateFlow()

    init {
        observeBookmarkStatus()
        loadPaper()
    }

    private fun observeBookmarkStatus() {
        viewModelScope.launch {
            mapRepository.observeIsPaperSaved(paperId).collect { isSaved ->
                _uiState.update { it.copy(isBookmarked = isSaved) }
            }
        }
    }

    fun loadPaper() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val paper = mapRepository.getPaper(paperId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        paper = paper,
                        isOffline = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load paper details.",
                    )
                }
            }
        }
    }

    fun toggleBookmark() {
        val currentPaper = _uiState.value.paper ?: return
        viewModelScope.launch {
            try {
                mapRepository.toggleSavePaper(currentPaper)
            } catch (_: Exception) {
                // Ignore DB error
            }
        }
    }

    fun toggleAbstractExpanded() {
        _uiState.update { it.copy(isAbstractExpanded = !it.isAbstractExpanded) }
    }

    fun showEvidence(data: EvidenceSheetData) {
        _uiState.update { it.copy(activeEvidenceSheet = data) }
    }

    fun dismissEvidence() {
        _uiState.update { it.copy(activeEvidenceSheet = null) }
    }
}
