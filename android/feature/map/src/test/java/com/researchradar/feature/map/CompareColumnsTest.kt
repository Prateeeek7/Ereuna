package com.researchradar.feature.map

import com.researchradar.core.model.Evidence
import com.researchradar.core.model.Metric
import com.researchradar.core.model.MetricConditions
import com.researchradar.core.model.Paper
import com.researchradar.core.model.PaperExtraction
import com.researchradar.core.model.buildCompareColumns
import com.researchradar.core.model.compareCell
import com.researchradar.core.model.defaultCompareColumnIds
import com.researchradar.core.model.formatReported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareColumnsTest {

    private fun metric(
        name: String,
        value: Double,
        unit: String,
        key: String,
        canonical: String,
        normalized: Double,
        normalizedUnit: String,
        subject: String = "",
        other: String? = null,
    ) = Metric(
        name = name, value = value, unit = unit, subject = subject, key = key, canonicalUnit = canonical,
        normalizedValue = normalized, normalizedUnit = normalizedUnit,
        conditions = MetricConditions(other = other), evidence = Evidence(id = "e", quote = "q"),
    )

    private fun paper(id: String, vararg metrics: Metric) = Paper(
        id = id, title = id, year = 2024, shortLabel = id,
        extraction = PaperExtraction(paperId = id, metrics = metrics.toList()),
    )

    @Test
    fun sameQuantityInDifferentWordingAndPrefixSharesOneColumn() {
        val papers = listOf(
            paper("a", metric("Ionic conductivity", 0.74, "mS cm−1", "ionic conductivity", "mS/cm", 7.4e-4, "S/cm", subject = "LLZO")),
            paper("b", metric("Li-ion conductivity", 2.0e-4, "S cm−1", "ionic conductivity", "S/cm", 2.0e-4, "S/cm")),
            paper("c", metric("Ionic conductivity", 1.1, "mS/cm", "ionic conductivity", "mS/cm", 1.1e-3, "S/cm", other = "25 °C")),
        )
        val columns = buildCompareColumns(papers)
        val conductivity = columns.single { it.id.startsWith("metric:") }
        assertEquals(3, conductivity.coverage)
        assertEquals("mS/cm", conductivity.unit) // the unit most papers use
        assertTrue(conductivity.id in defaultCompareColumnIds(columns))

        // Every value is shown in the column's unit, with its subject and conditions underneath.
        assertEquals("0.74", compareCell(papers[0], conductivity).text) // unit is in the header
        assertEquals("LLZO", compareCell(papers[0], conductivity).condition)
        assertEquals("0.2", compareCell(papers[1], conductivity).text)
        assertEquals("25 °C", compareCell(papers[2], conductivity).condition)
    }

    @Test
    fun reportedResultsShowDifferentQuantities() {
        val p = paper(
            "a",
            metric("Ionic conductivity", 7.4e-4, "S cm−1", "ionic conductivity", "S/cm", 7.4e-4, "S/cm", subject = "LLZO"),
            metric("Ionic conductivity", 6.3e-4, "S cm−1", "ionic conductivity", "S/cm", 6.3e-4, "S/cm", subject = "LLTO"),
            metric("Capacity retention", 88.6, "%", "capacity retention", "%", 88.6, "%"),
        )
        val results = buildCompareColumns(listOf(p)).single { it.id == "results" }
        val cell = compareCell(p, results)
        assertEquals("Ionic conductivity: 7.4 × 10⁻⁴ S/cm", cell.text)
        assertEquals("Capacity retention: 88.6 %", cell.condition)
    }

    @Test
    fun conditionsAreNotRepeated() {
        val m = metric("Ionic conductivity", 1.7e-4, "S/cm", "ionic conductivity", "S/cm", 1.7e-4, "S/cm", subject = "LLZO")
            .let { it.copy(conditions = it.conditions.copy(tempC = 25.0, other = "bulk conductivity at 25 °C")) }
        val p = paper("a", m)
        val column = buildCompareColumns(listOf(p)).first { it.id.startsWith("metric:") }
        assertEquals("LLZO · bulk conductivity at 25 °C", compareCell(p, column).condition)
    }

    @Test
    fun numbersReadTheWayPapersPrintThem() {
        assertEquals("130", formatReported(130.0))
        assertEquals("88.6", formatReported(88.6))
        assertEquals("0.0074", formatReported(0.0074))
        assertEquals("7.4 × 10⁻⁴", formatReported(7.4e-4))
        assertEquals("10⁻⁵", formatReported(1e-5))
        assertEquals("2.5 × 10⁷", formatReported(2.5e7))
    }
}
