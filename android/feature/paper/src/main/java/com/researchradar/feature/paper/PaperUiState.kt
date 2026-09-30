package com.researchradar.feature.paper

import com.researchradar.core.design.components.EvidenceSheetData
import com.researchradar.core.model.Paper

data class PaperUiState(
    val paperId: String = "",
    val isLoading: Boolean = true,
    val isBookmarked: Boolean = false,
    val isOffline: Boolean = false,
    val isAbstractExpanded: Boolean = false,
    val paper: Paper? = null,
    val activeEvidenceSheet: EvidenceSheetData? = null,
    val errorMessage: String? = null,
)
