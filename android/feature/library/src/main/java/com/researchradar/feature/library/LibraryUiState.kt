package com.researchradar.feature.library

import com.researchradar.core.model.Paper
import com.researchradar.core.model.ResearchMap

enum class LibraryTab(val label: String) {
    MAPS("Saved Maps"),
    PAPERS("Saved Papers"),
}

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.MAPS,
    val savedMaps: List<ResearchMap> = emptyList(),
    val savedPapers: List<Paper> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
)
