package com.researchradar.feature.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.components.DataColumn
import com.researchradar.core.design.components.DataTable
import com.researchradar.core.design.components.SectionLabel
import com.researchradar.core.model.Paper
import com.researchradar.core.model.buildCompareColumns
import com.researchradar.core.model.compareColumnWidthDp
import com.researchradar.core.model.compareRowOrder
import com.researchradar.core.model.isTextCompareColumn
import com.researchradar.core.model.compareCell
import com.researchradar.core.model.defaultCompareColumnIds

@Composable
fun CompareTab(
    papers: List<Paper>,
    visibleColumns: Set<String>?,
    selectedPaperIds: Set<String>,
    onOpenColumnPicker: () -> Unit,
    onNavigateToPaper: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    // Filter to selected papers or papers with extraction
    val candidatePapers = remember(papers, selectedPaperIds) {
        if (selectedPaperIds.isNotEmpty()) {
            papers.filter { selectedPaperIds.contains(it.id) }
        } else {
            papers.filter { it.extraction != null || it.hasFullText }
        }
    }

    if (candidatePapers.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(RadarSpacing.gutter),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "NO EXTRACTION DATA TO COMPARE",
                    style = type.sectionLabel,
                    color = colors.ink2,
                )
                Spacer(modifier = Modifier.height(RadarSpacing.sm))
                Text(
                    text = "Select 2 or more papers from the Papers tab or wait for structured extraction to finish.",
                    style = type.body,
                    color = colors.ink,
                )
            }
        }
        return
    }

    // Columns come from the metrics actually extracted for these papers.
    val allColumns = remember(candidatePapers) { buildCompareColumns(candidatePapers) }
    val colDefs = remember(allColumns, visibleColumns) {
        val selected = visibleColumns ?: defaultCompareColumnIds(allColumns)
        allColumns.filter { it.id in selected }
    }

    val tableColumns = remember(colDefs) {
        colDefs.map { def ->
            DataColumn(
                header = def.title,
                unit = def.unit,
                width = compareColumnWidthDp(def).dp,
                monospace = !isTextCompareColumn(def),
            )
        }
    }

    val frozenColumn = remember {
        DataColumn(header = "Paper", unit = "", width = 124.dp)
    }

    // Rows with the most reported values first, so the table opens on data.
    val rowPapers = remember(candidatePapers, colDefs) { compareRowOrder(candidatePapers, colDefs) }

    val frozenValues = remember(rowPapers) {
        rowPapers.map { paper ->
            paper.shortLabel.ifBlank {
                paper.authors.firstOrNull()?.name?.take(10) ?: paper.title.take(12)
            }
        }
    }

    // Values are shown as reported, with their stated conditions. No cell is
    // marked "best": whether higher or lower is better depends on the metric.
    val rows = remember(rowPapers, colDefs) {
        rowPapers.map { paper ->
            colDefs.map { def ->
                val cell = compareCell(paper, def)
                if (cell.condition != null) "${cell.text}\n${cell.condition}" else cell.text
            }
        }
    }
    val highlightedCells = emptySet<Pair<Int, Int>>()

    Column(modifier = modifier.fillMaxSize()) {
        // Control bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selectedPaperIds.isNotEmpty()) "${candidatePapers.size} selected papers" else "${candidatePapers.size} papers with extracted data",
                style = type.bodySmall,
                color = colors.ink2,
            )
            com.researchradar.core.design.components.RadarChip(
                text = "Columns",
                selected = false,
                count = tableColumns.size,
                leadingIcon = com.researchradar.core.design.RadarIcons.Sliders,
                onClick = onOpenColumnPicker,
            )
        }
        HorizontalDivider(thickness = RadarSpacing.hairline, color = colors.rule)

        // Comparison Data Table
        DataTable(
            frozenColumn = frozenColumn,
            columns = tableColumns,
            rows = rows,
            frozenValues = frozenValues,
            highlightedCells = highlightedCells,
            onFrozenCellClick = { rowIndex ->
                if (rowIndex in rowPapers.indices) {
                    onNavigateToPaper(rowPapers[rowIndex].id)
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}
