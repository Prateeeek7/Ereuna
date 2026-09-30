package com.researchradar.feature.search

import androidx.compose.runtime.Immutable
import com.researchradar.core.data.repository.PickedPdf
import com.researchradar.core.model.MapFilters

/** How a map is built: from a literature search, or from PDFs the user uploads. */
enum class SearchMode { Literature, MyPapers }

const val MAX_PDFS = 15
const val MAX_PDF_BYTES = 25L * 1024 * 1024

@Immutable
data class SearchUiState(
    val query: String = "",
    val filters: MapFilters = MapFilters(),
    val isFilterSheetOpen: Boolean = false,
    val recentTopics: List<RecentSearchItem> = emptyList(),
    val exampleTopics: List<String> = listOf(
        "Low-leakage SRAM using FinFET",
        "Solid-state battery electrolyte degradation",
        "Direct air capture MOF sorbents",
        "Transformer KV cache compression",
        "Neuromorphic spike-timing dependent plasticity",
    ),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val mode: SearchMode = SearchMode.Literature,
    val pdfs: List<PickedPdf> = emptyList(),
    /** In "my papers" mode, also add related papers from the literature. */
    val includeRelated: Boolean = false,
    /** A note about picked files that were left out (too large, not PDFs, over the limit). */
    val pickNote: String? = null,
) {
    val isSubmitEnabled: Boolean
        get() = query.isNotBlank() && !isLoading && (mode == SearchMode.Literature || pdfs.isNotEmpty())
}

@Immutable
data class RecentSearchItem(
    val topic: String,
    val date: String,
    val paperCount: Int? = null,
)
