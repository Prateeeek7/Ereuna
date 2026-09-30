package com.researchradar.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.researchradar.core.data.repository.MapRepository
import com.researchradar.core.data.repository.PaperUploadRepository
import com.researchradar.core.model.MapFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    private val preferences: com.researchradar.core.data.preferences.UserPreferencesRepository,
    private val uploads: PaperUploadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        loadRecentSearches()
        // Start each search from the defaults chosen in Settings.
        viewModelScope.launch {
            preferences.preferences.collect { p ->
                _uiState.update {
                    it.copy(
                        filters = it.filters.copy(
                            maxPapers = p.defaultPaperCount,
                            openAccessOnly = p.openAccessOnly,
                            minCitations = p.minCitations,
                        ),
                    )
                }
            }
        }
    }

    private fun loadRecentSearches() {
        viewModelScope.launch {
            try {
                val recentTopics = mapRepository.getRecentTopics()
                _uiState.update { current ->
                    current.copy(
                        recentTopics = recentTopics.map { it.split(Regex("\\s+")).filter(String::isNotBlank).joinToString(" ") }.distinct().map { topic ->
                            RecentSearchItem(
                                topic = topic,
                                date = "Recent",
                                paperCount = null,
                            )
                        }
                    )
                }
            } catch (_: Exception) {
                // Ignore local storage error on first load
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        // Topics are single-line: a pasted or typed line break becomes a space.
        _uiState.update { it.copy(query = newQuery.replace(Regex("[\\r\\n]+"), " "), errorMessage = null) }
    }

    fun onTopicSelect(topic: String) {
        _uiState.update { it.copy(query = topic, errorMessage = null) }
    }

    fun onClearQuery() {
        _uiState.update { it.copy(query = "", errorMessage = null) }
    }

    fun onOpenFilterSheet() {
        _uiState.update { it.copy(isFilterSheetOpen = true) }
    }

    fun onCloseFilterSheet() {
        _uiState.update { it.copy(isFilterSheetOpen = false) }
    }

    fun onFiltersUpdated(filters: MapFilters) {
        _uiState.update { it.copy(filters = filters, isFilterSheetOpen = false) }
    }

    fun onModeChange(mode: SearchMode) {
        _uiState.update { it.copy(mode = mode, errorMessage = null) }
    }

    fun onIncludeRelatedChange(include: Boolean) {
        _uiState.update { it.copy(includeRelated = include) }
    }

    fun onPdfsPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val picked = uris.mapNotNull { runCatching { uploads.describe(it) }.getOrNull() }
            _uiState.update { state ->
                val known = state.pdfs.map { it.uri }.toSet()
                val fresh = picked.filter { it.uri !in known }
                val tooLarge = fresh.filter { it.sizeBytes > MAX_PDF_BYTES }
                val accepted = (state.pdfs + fresh.filter { it.sizeBytes <= MAX_PDF_BYTES }).take(MAX_PDFS)
                val overLimit = state.pdfs.size + fresh.size - tooLarge.size - accepted.size
                val notes = buildList {
                    if (tooLarge.isNotEmpty()) add("${tooLarge.joinToString { it.name }} ${if (tooLarge.size == 1) "is" else "are"} over 25 MB")
                    if (overLimit > 0) add("$overLimit left out: up to $MAX_PDFS PDFs per map")
                }
                state.copy(pdfs = accepted, pickNote = notes.joinToString(". ").ifEmpty { null }, errorMessage = null)
            }
        }
    }

    fun onRemovePdf(uri: String) {
        _uiState.update { s -> s.copy(pdfs = s.pdfs.filterNot { it.uri == uri }, pickNote = null) }
    }

    fun submitSearch(
        onNavigateToMap: (String) -> Unit,
        onNavigateToJob: (jobId: String, topic: String) -> Unit,
    ) {
        val topic = _uiState.value.query.split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ")
        if (topic.isBlank() || _uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val state = _uiState.value
                val response = if (state.mode == SearchMode.MyPapers) {
                    uploads.createMap(topic, state.pdfs, state.includeRelated)
                } else {
                    mapRepository.saveRecentTopic(topic)
                    mapRepository.createMap(topic, state.filters)
                }
                _uiState.update { it.copy(isLoading = false, pdfs = if (it.mode == SearchMode.MyPapers) emptyList() else it.pdfs) }
                if (response.status == "ready") {
                    onNavigateToMap(response.mapId)
                } else {
                    val targetId = response.jobId ?: response.mapId
                    onNavigateToJob(targetId, topic)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to initiate research map generation.",
                    )
                }
            }
        }
    }
}
