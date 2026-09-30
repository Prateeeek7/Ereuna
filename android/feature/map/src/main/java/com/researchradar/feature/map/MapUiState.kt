package com.researchradar.feature.map

import com.researchradar.core.design.components.EvidenceSheetData
import com.researchradar.core.model.ResearchMap

enum class MapTab(val label: String) {
    OVERVIEW("Overview"),
    PAPERS("Papers"),
    COMPARE("Compare"),
    FINDINGS("Findings"),
    CONFLICTS("Conflicts"),
    GAPS("Gaps"),
    EXPERIMENTS("Experiments"),
    TOOLS("Tools"),
    GRAPH("Graph"),
}

enum class PaperSort {
    RELEVANCE,
    CITATIONS,
    RECENCY,
}

data class MapUiState(
    val mapId: String = "",
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val isBookmarked: Boolean = false,
    val researchMap: ResearchMap? = null,
    val selectedTab: MapTab = MapTab.OVERVIEW,
    val activePaperFilter: String = "ALL", // "ALL", "FULL_TEXT"
    val activeSort: PaperSort = PaperSort.RELEVANCE,
    val selectedToolFilter: String? = null,
    val selectedPaperIdsForCompare: Set<String> = emptySet(),
    /** null = default selection derived from the map's own extracted metrics. */
    val visibleCompareColumns: Set<String>? = null,
    val isColumnPickerOpen: Boolean = false,
    val isExportDialogOpen: Boolean = false,
    val activeEvidenceSheet: EvidenceSheetData? = null,
    val errorMessage: String? = null,
)
