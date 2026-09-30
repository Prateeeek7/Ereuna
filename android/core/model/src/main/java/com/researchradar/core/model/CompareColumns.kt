package com.researchradar.core.model

/**
 * Columns for the paper comparison table, derived from the data actually
 * extracted for a map rather than from a fixed, domain-specific list.
 */
data class CompareColumn(
    val id: String,
    val title: String,
    val unit: String,
    /** Number of papers that have a value for this column. */
    val coverage: Int,
    /** For metric columns: a normalized value divided by this is the value in [unit]. */
    val scale: Double? = null,
)

data class CompareCell(
    val text: String,
    val numeric: Double? = null,
    val condition: String? = null,
)

private const val METRIC_PREFIX = "metric:"
private const val MAX_METRIC_COLUMNS = 12
private const val RESULTS_ID = "results"
private val NAME_NOISE = Regex("[^a-z0-9%\\- ]")

/** The server's comparison key (same quantity, same key across papers), or a local fallback. */
private fun metricKey(metric: Metric): String = metric.key.ifBlank {
    NAME_NOISE.replace(metric.name.lowercase(), " ").split(" ").filter { it.isNotBlank() }.joinToString(" ")
}

private fun displayUnit(metric: Metric): String = metric.canonicalUnit.ifBlank { metric.unit }.trim()

/** Unit after SI prefixes are folded in, so mS/cm and S/cm share a column. */
private fun groupUnit(metric: Metric): String = (metric.normalizedUnit ?: displayUnit(metric)).trim()

private fun metricColumnId(metric: Metric): String = "$METRIC_PREFIX${metricKey(metric)}|${groupUnit(metric)}"

private fun formatNumber(value: Double): String = formatReported(value)

/** Subject and stated conditions, e.g. "LLZO · 25 °C · after 20 cycles". */
private fun metricContext(metric: Metric): String? {
    val other = metric.conditions.other?.takeIf { it.isNotBlank() }
    // "25 °C" is dropped when the free-text condition already says "at 25 °C".
    fun unlessInOther(text: String): String? =
        text.takeUnless { t -> other?.replace(" ", "")?.contains(t.replace(" ", "")) == true }
    return listOfNotNull(
        metric.subject.takeIf { it.isNotBlank() },
        metric.conditions.vdd?.let { unlessInOther("${formatNumber(it)} V") },
        metric.conditions.tempC?.let { unlessInOther("${formatNumber(it)} °C") },
        metric.conditions.corner?.takeIf { it.isNotBlank() },
        other,
    ).joinToString(" · ").ifBlank { null }
}

/** Builds the columns that have data for at least one paper, most widely reported metrics first. */
fun buildCompareColumns(papers: List<Paper>): List<CompareColumn> {
    val columns = mutableListOf<CompareColumn>()

    val device = papers.count { !it.extraction?.technology?.device.isNullOrBlank() }
    if (device > 0) columns += CompareColumn("device", "Platform / Device", "", device)
    val variant = papers.count { !it.extraction?.technology?.cellType.isNullOrBlank() }
    if (variant > 0) columns += CompareColumn("variant", "Configuration", "", variant)
    val node = papers.count { it.extraction?.technology?.nodeNm != null }
    if (node > 0) columns += CompareColumn("node", "Process Node", "nm", node)

    data class Group(val title: String, val members: MutableList<Metric>, val paperIds: MutableSet<String>)
    val groups = linkedMapOf<String, Group>()
    papers.forEach { paper ->
        paper.extraction?.metrics?.forEach { metric ->
            if (metricKey(metric).isBlank()) return@forEach
            val group = groups.getOrPut(metricColumnId(metric)) {
                Group(metric.name.trim().replaceFirstChar { it.uppercase() }, mutableListOf(), mutableSetOf())
            }
            group.members += metric
            group.paperIds += paper.id
        }
    }
    columns += groups.entries
        .sortedByDescending { it.value.paperIds.size }
        .take(MAX_METRIC_COLUMNS)
        .map { (id, g) ->
            // Show the column in the unit most papers use (mAh/g stays mAh/g, not 0.13 Ah/g),
            // converting the others through their normalized values.
            val unit = g.members.groupingBy { displayUnit(it) }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
            val reference = g.members.firstOrNull { displayUnit(it) == unit && it.value != 0.0 && it.normalizedValue != null }
            val scale = reference?.let { it.normalizedValue!! / it.value }?.takeIf { it.isFinite() && it != 0.0 }
            CompareColumn(id, g.title, unit, g.paperIds.size, scale)
        }

    // Each paper's own headline numbers, for when papers report different metrics.
    val withMetrics = papers.count { !it.extraction?.metrics.isNullOrEmpty() }
    if (withMetrics > 0) columns += CompareColumn(RESULTS_ID, "Reported results", "", withMetrics)

    // Context columns last, so the measurements are what the table opens on.
    columns += CompareColumn("year", "Year", "", papers.count { it.year > 0 })
    columns += CompareColumn("citations", "Citations", "", papers.size)

    return columns.filter { it.coverage > 0 }
}

/** Suggested on-screen width in dp: short numbers stay narrow, free-text columns get room. */
fun compareColumnWidthDp(column: CompareColumn): Int = when (column.id) {
    "year" -> 72
    "citations" -> 88
    "node" -> 92
    "device", "variant" -> 156
    RESULTS_ID -> 232
    else -> 148
}

/** Whether a column holds free text (names, phrases) rather than a single number. */
fun isTextCompareColumn(column: CompareColumn): Boolean = column.id in setOf("device", "variant", RESULTS_ID)

/**
 * Default selection: technology and metric columns that at least two papers
 * report (a column filled for one paper is mostly dashes), up to four metrics,
 * plus year and citations as context. When no metric is shared, the per-paper
 * "Reported results" column stands in for the metric columns.
 */
fun defaultCompareColumnIds(columns: List<CompareColumn>): Set<String> {
    fun shared(ids: List<CompareColumn>) = ids.filter { it.coverage >= 2 }.ifEmpty { ids.take(1) }
    val metricColumns = columns.filter { it.id.startsWith(METRIC_PREFIX) }
    val tech = shared(columns.filter { it.id in setOf("device", "node") }).map { it.id }
    // No metric is shared: per-metric columns would hold one value each, so show
    // every paper's own results in one column instead.
    val measured = if (metricColumns.none { it.coverage >= 2 }) {
        columns.filter { it.id == RESULTS_ID }.map { it.id }
    } else {
        shared(metricColumns).take(4).map { it.id }
    }
    val base = listOf("year", "citations").filter { id -> columns.any { it.id == id } }
    return (tech + measured + base).toSet()
}

/** Papers ordered so rows with the most filled cells in [columns] come first; ties keep rank order. */
fun compareRowOrder(papers: List<Paper>, columns: List<CompareColumn>): List<Paper> {
    val informative = columns.filter { it.id != "year" && it.id != "citations" }
    return papers.sortedByDescending { paper -> informative.count { compareCell(paper, it).text != "—" } }
}

fun compareCell(paper: Paper, column: CompareColumn): CompareCell {
    val ext = paper.extraction
    return when {
        column.id == "year" -> if (paper.year > 0) CompareCell(paper.year.toString(), paper.year.toDouble()) else CompareCell("—")
        column.id == "citations" -> CompareCell(paper.citationCount.toString(), paper.citationCount.toDouble())
        column.id == "device" -> CompareCell(ext?.technology?.device?.takeIf { it.isNotBlank() } ?: "—")
        column.id == "variant" -> CompareCell(ext?.technology?.cellType?.takeIf { it.isNotBlank() } ?: "—")
        column.id == "node" -> ext?.technology?.nodeNm?.let { CompareCell("$it nm", it.toDouble()) } ?: CompareCell("—")
        column.id == RESULTS_ID -> {
            // Two different quantities, not the same one twice.
            val shown = ext?.metrics.orEmpty().distinctBy { metricKey(it) }.take(2).map { m ->
                "${m.name.trim().replaceFirstChar { c -> c.uppercase() }}: ${formatNumber(m.value)} ${displayUnit(m)}".trim()
            }
            if (shown.isEmpty()) CompareCell("—") else CompareCell(shown.first(), condition = shown.getOrNull(1))
        }
        column.id.startsWith(METRIC_PREFIX) -> {
            val matching = ext?.metrics?.filter { metricColumnId(it) == column.id }.orEmpty()
            val metric = matching.firstOrNull() ?: return CompareCell("—")
            val converted = column.scale?.let { scale -> metric.normalizedValue?.let { it / scale } }
            val (value, unit) = if (converted != null) converted to column.unit else metric.value to displayUnit(metric)
            val more = (matching.size - 1).takeIf { it > 0 }?.let { "+$it more" }
            val detail = listOfNotNull(metricContext(metric), more).joinToString(" · ").ifBlank { null }
            // The header already shows the column's unit; repeat it only when this value differs.
            val text = if (unit == column.unit) formatNumber(value) else "${formatNumber(value)} $unit".trim()
            CompareCell(text, value, detail)
        }
        else -> CompareCell("—")
    }
}

/** One paper's reported value for a quantity, in the quantity's display unit. */
data class RangePoint(
    val paper: Paper,
    val value: Double,
    val subject: String,
    val context: String?,
    val evidence: Evidence,
)

/** Every value reported for one quantity across papers, lowest first. */
data class ReportedRange(
    val title: String,
    val unit: String,
    val paperCount: Int,
    val points: List<RangePoint>,
) {
    val min: Double get() = points.first().value
    val max: Double get() = points.last().value

    /** Values spanning more than 20x are easier to read on a log scale. */
    val logScale: Boolean get() = min > 0 && max / min > 20
}

/**
 * Quantities reported by at least two papers, with every reported value converted
 * to the unit most papers use; most widely reported first.
 */
fun reportedRanges(papers: List<Paper>, maxRanges: Int = 8): List<ReportedRange> =
    buildCompareColumns(papers)
        .filter { it.id.startsWith(METRIC_PREFIX) && it.coverage >= 2 }
        .take(maxRanges)
        .map { column ->
            val points = papers.flatMap { paper ->
                paper.extraction?.metrics.orEmpty()
                    .filter { metricColumnId(it) == column.id }
                    .map { metric ->
                        val value = column.scale?.let { s -> metric.normalizedValue?.let { it / s } } ?: metric.value
                        RangePoint(paper, value, metric.subject, metricContext(metric), metric.evidence)
                    }
            }.filter { it.value > 0 }.sortedBy { it.value }
            ReportedRange(column.title, column.unit, points.map { it.paper.id }.toSet().size, points)
        }
        .filter { it.paperCount >= 2 }
