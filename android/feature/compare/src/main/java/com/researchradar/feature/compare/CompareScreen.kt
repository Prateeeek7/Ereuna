package com.researchradar.feature.compare

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.ResearchRadarTheme
import com.researchradar.core.design.components.DataColumn
import com.researchradar.core.design.components.DataTable
import com.researchradar.core.model.Paper
import com.researchradar.core.model.ResearchMap
import com.researchradar.core.model.buildCompareColumns
import com.researchradar.core.model.compareColumnWidthDp
import com.researchradar.core.model.compareRowOrder
import com.researchradar.core.model.isTextCompareColumn
import com.researchradar.core.model.compareCell
import com.researchradar.core.model.defaultCompareColumnIds

@Composable
fun CompareScreen(
    mapId: String,
    onNavigateToPaper: (String) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CompareViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CompareScreenContent(
        uiState = uiState,
        onBack = onBack,
        onNavigateToPaper = onNavigateToPaper,
        onOpenColumnPicker = viewModel::openColumnPicker,
        onCloseColumnPicker = viewModel::closeColumnPicker,
        onToggleColumn = viewModel::toggleColumn,
        onRetry = viewModel::loadMap,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreenContent(
    uiState: CompareUiState,
    onBack: () -> Unit,
    onNavigateToPaper: (String) -> Unit,
    onOpenColumnPicker: () -> Unit,
    onCloseColumnPicker: () -> Unit,
    onToggleColumn: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "←",
                    style = type.heading,
                    color = colors.ink,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(end = RadarSpacing.md),
                )

                Column {
                    Text(
                        text = "SIDE-BY-SIDE MATRIX",
                        style = type.sectionLabel,
                        color = colors.accent,
                    )
                    Text(
                        text = uiState.researchMap?.topic ?: "Paper Comparison",
                        style = type.heading,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .border(1.dp, colors.rule, RadarShape.chip)
                    .clickable(onClick = onOpenColumnPicker)
                    .padding(horizontal = RadarSpacing.sm, vertical = RadarSpacing.xs),
            ) {
                Text(
                    text = "COLUMNS ⚙",
                    style = type.mono,
                    color = colors.ink,
                )
            }
        }

        HorizontalDivider(thickness = RadarSpacing.hairline, color = colors.rule)

        if (uiState.isLoading) {
            CompareLoadingSkeleton()
            return
        }

        if (uiState.errorMessage != null && uiState.researchMap == null) {
            CompareErrorView(errorMessage = uiState.errorMessage, onRetry = onRetry)
            return
        }

        val map = uiState.researchMap
        val papers = map?.papers?.filter { it.extraction != null || it.hasFullText } ?: emptyList()

        if (papers.isEmpty()) {
            CompareEmptyView()
            return
        }

        val allColumns = remember(papers) { buildCompareColumns(papers) }
        val activeDefs = remember(allColumns, uiState.visibleColumns) {
            val selected = uiState.visibleColumns ?: defaultCompareColumnIds(allColumns)
            allColumns.filter { it.id in selected }
        }

        val tableColumns = remember(activeDefs) {
            activeDefs.map { def ->
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
        val rowPapers = remember(papers, activeDefs) { compareRowOrder(papers, activeDefs) }

        val frozenValues = remember(rowPapers) {
            rowPapers.map { p -> p.shortLabel.ifBlank { p.authors.firstOrNull()?.name?.take(10) ?: p.title.take(12) } }
        }

        // Values as reported, with stated conditions. No "best" highlighting:
        // whether higher or lower is better depends on the metric.
        val rows = remember(rowPapers, activeDefs) {
            rowPapers.map { paper ->
                activeDefs.map { def ->
                    val cell = compareCell(paper, def)
                    if (cell.condition != null) "${cell.text}\n${cell.condition}" else cell.text
                }
            }
        }
        val highlightedCells = emptySet<Pair<Int, Int>>()

        DataTable(
            frozenColumn = frozenColumn,
            columns = tableColumns,
            rows = rows,
            frozenValues = frozenValues,
            highlightedCells = highlightedCells,
            onFrozenCellClick = { idx ->
                if (idx in rowPapers.indices) {
                    onNavigateToPaper(rowPapers[idx].id)
                }
            },
            modifier = Modifier.weight(1f),
        )

        // Column Picker Sheet
        if (uiState.isColumnPickerOpen) {
            ModalBottomSheet(
                onDismissRequest = onCloseColumnPicker,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                shape = RadarShape.sheet,
                containerColor = colors.surface,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = RadarSpacing.sm)
                            .size(width = 36.dp, height = 3.dp)
                            .background(colors.rule, RadarShape.chip)
                    )
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.md),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "COMPARISON COLUMNS",
                            style = type.sectionLabel,
                            color = colors.accent,
                        )
                        Text(
                            text = "DONE",
                            style = type.button,
                            color = colors.ink,
                            modifier = Modifier.clickable(onClick = onCloseColumnPicker),
                        )
                    }

                    Spacer(modifier = Modifier.height(RadarSpacing.md))
                    HorizontalDivider(thickness = RadarSpacing.hairline, color = colors.rule)

                    val selectedIds = uiState.visibleColumns ?: defaultCompareColumnIds(allColumns)
                    allColumns.forEach { col ->
                        val isChecked = selectedIds.contains(col.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleColumn(col.id) }
                                .padding(vertical = RadarSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleColumn(col.id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colors.accent,
                                    uncheckedColor = colors.rule,
                                    checkmarkColor = colors.paper,
                                ),
                            )
                            Column(modifier = Modifier.padding(start = RadarSpacing.sm)) {
                                Text(text = col.title, style = type.body, color = colors.ink)
                                Text(
                                    text = listOfNotNull(
                                        col.unit.takeIf { it.isNotBlank() }?.let { "Unit: $it" },
                                        "reported by ${col.coverage} paper${if (col.coverage == 1) "" else "s"}",
                                    ).joinToString(" · "),
                                    style = type.label,
                                    color = colors.ink2,
                                )
                            }
                        }
                        HorizontalDivider(thickness = RadarSpacing.hairline, color = colors.rule)
                    }

                    Spacer(modifier = Modifier.height(RadarSpacing.lg))
                }
            }
        }
    }
}

@Composable
private fun CompareLoadingSkeleton() {
    val colors = RadarTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(RadarSpacing.gutter),
    ) {
        repeat(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(colors.rule),
            )
            Spacer(modifier = Modifier.height(RadarSpacing.sm))
        }
    }
}

@Composable
private fun CompareErrorView(errorMessage: String, onRetry: () -> Unit) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(RadarSpacing.gutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "COMPARISON DATA ERROR", style = type.sectionLabel, color = colors.accent)
            Spacer(modifier = Modifier.height(RadarSpacing.sm))
            Text(text = errorMessage, style = type.body, color = colors.ink)
            Spacer(modifier = Modifier.height(RadarSpacing.lg))
            Box(
                modifier = Modifier
                    .border(1.dp, colors.accent, RadarShape.chip)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = RadarSpacing.lg, vertical = RadarSpacing.sm),
            ) {
                Text(text = "RETRY", style = type.button, color = colors.accent)
            }
        }
    }
}

@Composable
private fun CompareEmptyView() {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(RadarSpacing.gutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "NO STRUCTURED DATA FOR COMPARISON", style = type.sectionLabel, color = colors.ink2)
            Spacer(modifier = Modifier.height(RadarSpacing.sm))
            Text(text = "None of the papers in this map have extracted data yet.", style = type.body, color = colors.ink)
        }
    }
}
