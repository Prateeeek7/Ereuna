package com.researchradar.feature.search

import com.researchradar.core.model.PaperSelectedEventData

/** Pipeline stages as emitted by the backend (stage numbers 1..8). */
val PipelineStages = listOf(
    "Expanding the query",
    "Searching three databases",
    "Ranking candidates",
    "Fetching full text",
    "Extracting and verifying",
    "Comparing across papers",
    "Building citation graph",
    "Drafting experiments",
)

/** Stages when the map is built from the user's PDFs (the server names them read_uploads, …). */
val UploadStages = listOf(
    "Reading your PDFs",
    "Identifying your papers",
    "Finding related papers",
    "Fetching their full text",
) + PipelineStages.drop(4)

private val UploadStageNames = setOf("read_uploads", "identify_papers", "find_related", "fetch_related")

/** The step list a job uses, told apart by the server's stage names. */
fun stagesFor(stageName: String, current: List<String>): List<String> =
    if (stageName in UploadStageNames) UploadStages else current

data class RunningJobUiState(
    val jobId: String = "",
    val topic: String = "",
    val currentStage: Int = 1,
    val stages: List<String> = PipelineStages,
    val stageName: String = "Starting",
    val progress: Float = 0.02f,
    val logs: List<Pair<String, String>> = emptyList(),
    val candidateCount: Int = 0,
    val selectedCount: Int = 0,
    val fullTextCount: Int = 0,
    /** Papers read from the user's own PDFs (upload mode). */
    val uploadedCount: Int = 0,
    val selectedPapers: List<PaperSelectedEventData> = emptyList(),
    val startedAtMs: Long = System.currentTimeMillis(),
    val isComplete: Boolean = false,
    val completedMapId: String? = null,
    val errorMessage: String? = null,
    val isCancelled: Boolean = false,
) {
    val totalStages: Int get() = stages.size
    val isUpload: Boolean get() = stages === UploadStages
}
