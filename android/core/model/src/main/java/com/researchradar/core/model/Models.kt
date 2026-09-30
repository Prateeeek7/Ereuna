package com.researchradar.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResearchMap(
    val id: String,
    val topic: String,
    @SerialName("normalized_topic") val normalizedTopic: String,
    val filters: MapFilters = MapFilters(),
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val stats: MapStats = MapStats(),
    val synthesis: Synthesis = Synthesis(),
    val papers: List<Paper> = emptyList(),
    val findings: List<Finding> = emptyList(),
    val gaps: List<Gap> = emptyList(),
    val contradictions: List<Contradiction> = emptyList(),
    val experiments: List<Experiment> = emptyList(),
    val tools: List<ToolEntry> = emptyList(),
    val graph: GraphData? = null,
    /** "search" (from a literature search) or "upload" (built from the user's own PDFs; private). */
    val source: String = "search",
) {
    val isFromUploads: Boolean get() = source == "upload"
}

@Serializable
data class MapFilters(
    @SerialName("year_min") val yearMin: Int? = null,
    @SerialName("year_max") val yearMax: Int? = null,
    val fields: List<String> = emptyList(),
    @SerialName("min_citations") val minCitations: Int = 0,
    @SerialName("open_access_only") val openAccessOnly: Boolean = false,
    @SerialName("max_papers") val maxPapers: Int = 25,
)

@Serializable
data class MapStats(
    @SerialName("total_papers") val totalPapers: Int = 0,
    @SerialName("full_text_papers") val fullTextPapers: Int = 0,
    @SerialName("abstract_only_papers") val abstractOnlyPapers: Int = 0,
    @SerialName("year_range") val yearRange: List<Int> = emptyList(),
    @SerialName("gaps_count") val gapsCount: Int = 0,
    @SerialName("contradictions_count") val contradictionsCount: Int = 0,
    @SerialName("uploaded_papers") val uploadedPapers: Int = 0,
)

@Serializable
data class Synthesis(
    val text: String = "",
    @SerialName("citation_ids") val citationIds: List<String> = emptyList(),
)

@Serializable
data class Author(
    val name: String,
    val affiliation: String? = null,
)

@Serializable
data class Evidence(
    val id: String = "",
    @SerialName("paper_id") val paperId: String = "",
    val quote: String,
    val section: String = "",
    val page: Int? = null,
)

@Serializable
data class ExtractedField(
    val text: String,
    val evidence: Evidence,
)

@Serializable
data class MetricConditions(
    val vdd: Double? = null,
    @SerialName("temp_c") val tempC: Double? = null,
    val corner: String? = null,
    /** Other stated conditions in the paper's words ("after 20 cycles at 0.1 C"). */
    val other: String? = null,
)

@Serializable
data class Metric(
    val name: String,
    val value: Double,
    val unit: String,
    /** What the value was measured on when a paper reports several (material, sample, variant). */
    val subject: String = "",
    @SerialName("normalized_value") val normalizedValue: Double? = null,
    @SerialName("normalized_unit") val normalizedUnit: String? = null,
    val conditions: MetricConditions = MetricConditions(),
    val evidence: Evidence,
    val confidence: String = "HIGH",
    /** Server-side comparison key: equal for the same quantity across papers. */
    val key: String = "",
    /** The unit in one spelling ("S cm−1" -> "S/cm"). */
    @SerialName("canonical_unit") val canonicalUnit: String = "",
)

@Serializable
data class Finding(
    val id: String,
    val text: String,
    val theme: String = "",
    @SerialName("paper_ids") val paperIds: List<String> = emptyList(),
    val evidence: Evidence,
    val confidence: String = "HIGH",
)

@Serializable
data class Limitation(
    val text: String,
    val evidence: Evidence,
    val confidence: String = "HIGH",
)

@Serializable
data class ToolRef(
    val name: String,
    val category: String = "",
    val evidence: Evidence,
)

@Serializable
data class DatasetRef(
    val name: String,
    val category: String = "",
    val evidence: Evidence,
)

@Serializable
data class Technology(
    @SerialName("node_nm") val nodeNm: Int? = null,
    val device: String? = null,
    @SerialName("cell_type") val cellType: String? = null,
    val evidence: Evidence,
)

@Serializable
data class PaperExtraction(
    @SerialName("paper_id") val paperId: String,
    val problem: ExtractedField? = null,
    val method: ExtractedField? = null,
    val technology: Technology? = null,
    val tools: List<ToolRef> = emptyList(),
    val datasets: List<DatasetRef> = emptyList(),
    val metrics: List<Metric> = emptyList(),
    val findings: List<Finding> = emptyList(),
    val limitations: List<Limitation> = emptyList(),
    /** "llm:<model>" or "heuristic": how the items were extracted. */
    @SerialName("extracted_by") val extractedBy: String = "",
)

@Serializable
data class Paper(
    val id: String,
    val doi: String? = null,
    val title: String,
    val authors: List<Author> = emptyList(),
    val year: Int,
    val venue: String = "",
    @SerialName("citation_count") val citationCount: Int = 0,
    val abstract: String = "",
    @SerialName("oa_pdf_url") val oaPdfUrl: String? = null,
    @SerialName("has_full_text") val hasFullText: Boolean = false,
    @SerialName("relevance_score") val relevanceScore: Double = 0.0,
    @SerialName("relevance_reason") val relevanceReason: String = "",
    @SerialName("short_label") val shortLabel: String = "",
    /** "upload" for a PDF the user provided. */
    val source: String = "search",
    val extraction: PaperExtraction? = null,
) {
    val isUploaded: Boolean get() = source == "upload"
}

@Serializable
data class Gap(
    val id: String,
    val statement: String,
    val pattern: String,
    @SerialName("supporting_paper_ids") val supportingPaperIds: List<String> = emptyList(),
    @SerialName("why_it_matters") val whyItMatters: String = "",
    val confidence: String = "HIGH",
)

@Serializable
data class ContradictionEntry(
    @SerialName("paper_id") val paperId: String,
    /** Reported value for a value disagreement; null when this side is a stated claim. */
    val value: Double? = null,
    val unit: String = "",
    val subject: String = "",
    val conditions: Map<String, String> = emptyMap(),
    val statement: String = "",
    val quote: String = "",
    val section: String = "",
    val page: Int? = null,
)

@Serializable
data class Contradiction(
    val id: String,
    /** "value": different numbers for the same quantity; "claim": opposing findings. */
    val kind: String = "value",
    val metric: String,
    val entries: List<ContradictionEntry> = emptyList(),
    @SerialName("likely_reason") val likelyReason: String = "",
    val confidence: String = "HIGH",
)

@Serializable
data class GraphNode(
    @SerialName("paper_id") val paperId: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val size: Float = 10f,
    val year: Int = 0,
    @SerialName("in_map") val inMap: Boolean = true,
    val label: String = "",
    @SerialName("citation_count") val citationCount: Int = 0,
)

@Serializable
data class GraphEdge(
    val source: String,
    val target: String,
    val weight: Float = 1f,
)

@Serializable
data class GraphData(
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList(),
)

@Serializable
data class Experiment(
    val id: String,
    @SerialName("gap_id") val gapId: String,
    val hypothesis: String,
    val setup: String = "",
    val tools: List<String> = emptyList(),
    val variables: List<String> = emptyList(),
    @SerialName("expected_result") val expectedResult: String = "",
    val difficulty: String = "MED",
    @SerialName("paper_ids") val paperIds: List<String> = emptyList(),
)

@Serializable
data class ToolEntry(
    val name: String,
    val category: String = "",
    val count: Int = 0,
    @SerialName("paper_ids") val paperIds: List<String> = emptyList(),
)

@Serializable
data class LogEntry(
    val timestamp: String,
    val message: String,
)

@Serializable
data class Job(
    val id: String,
    @SerialName("map_id") val mapId: String,
    val status: String = "queued",
    val stage: String? = null,
    val progress: Float = 0f,
    val log: List<LogEntry> = emptyList(),
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class CreateMapRequest(
    val topic: String,
    val filters: MapFilters = MapFilters(),
)

@Serializable
data class CreateMapResponse(
    @SerialName("job_id") val jobId: String?,
    @SerialName("map_id") val mapId: String,
    val status: String,
)

@Serializable
data class StageEventData(
    val stage: Int = 0,
    val name: String = "",
    val progress: Float = 0f,
    val status: String = "",
)

@Serializable
data class LogEventData(
    val message: String = "",
)

@Serializable
data class PaperSelectedEventData(
    @SerialName("paper_id") val paperId: String = "",
    val title: String = "",
    @SerialName("short_label") val shortLabel: String = "",
    val score: Double = 0.0,
    val citations: Int = 0,
    val year: Int = 0,
)

@Serializable
data class DoneEventData(
    @SerialName("map_id") val mapId: String = "",
    @SerialName("papers_count") val papersCount: Int = 0,
    @SerialName("full_text_count") val fullTextCount: Int = 0,
    @SerialName("verification_pass_rate") val verificationPassRate: Double = 0.0,
)

@Serializable
data class ErrorEventData(
    val error: String = "",
    val message: String = "",
)

sealed interface JobEvent {
    data class Stage(val data: StageEventData) : JobEvent
    data class Log(val data: LogEventData) : JobEvent
    data class PaperSelected(val data: PaperSelectedEventData) : JobEvent
    data class Done(val data: DoneEventData) : JobEvent
    data class Error(val data: ErrorEventData) : JobEvent
}

@Serializable
data class ExportRequest(
    val format: String = "md",
)

@Serializable
data class ExportResponse(
    @SerialName("download_url") val downloadUrl: String,
    val format: String,
    @SerialName("expires_at") val expiresAt: String = "",
)


