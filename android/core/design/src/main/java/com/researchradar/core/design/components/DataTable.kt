package com.researchradar.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.ResearchRadarTheme

/**
 * Column definition for [DataTable].
 *
 * @param header Column header text (e.g. "Leakage").
 * @param unit Unit displayed under the header (e.g. "pW/cell").
 * @param width Column width.
 * @param monospace Set values in the tabular mono face; turn off for free-text columns.
 */
data class DataColumn(
    val header: String,
    val unit: String = "",
    val width: Dp = 100.dp,
    val monospace: Boolean = true,
)

/**
 * Data table with frozen first column, horizontal scroll, sortable headers.
 *
 * - Frozen first column (paper short name) stays visible during scroll.
 * - Units displayed under headers in ink2 mono.
 * - Sortable: tap header to sort.
 * - Best value per column can be marked via [highlightedCells].
 * - 1dp rule grid lines.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DataTable(
    frozenColumn: DataColumn,
    columns: List<DataColumn>,
    rows: List<List<String>>,
    frozenValues: List<String>,
    modifier: Modifier = Modifier,
    highlightedCells: Set<Pair<Int, Int>> = emptySet(),
    onHeaderClick: (Int) -> Unit = {},
    onFrozenCellClick: ((Int) -> Unit)? = null,
    sortedColumn: Int? = null,
    sortAscending: Boolean = true,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    // One horizontal scroll state shared by the header and every row, so the
    // scrolling columns move together and stay aligned with the frozen column.
    val hScroll = rememberScrollState()
    val rowHeight = 60.dp
    val scrolled = hScroll.value > 0

    androidx.compose.foundation.lazy.LazyColumn(modifier = modifier) {
        stickyHeader {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(colors.surface),
            ) {
                FrozenCell(frozenColumn.width, scrolled) {
                    HeaderLabel(frozenColumn.header, frozenColumn.unit, active = false, arrow = null)
                }
                Row(Modifier.horizontalScroll(hScroll)) {
                    columns.forEachIndexed { colIdx, column ->
                        Box(
                            Modifier
                                .width(column.width)
                                .fillMaxHeight()
                                .clickable { onHeaderClick(colIdx) }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            HeaderLabel(
                                column.header,
                                column.unit,
                                active = sortedColumn == colIdx,
                                arrow = if (sortedColumn == colIdx) (if (sortAscending) "↑" else "↓") else null,
                            )
                        }
                    }
                }
            }
            HairlineDivider()
        }

        items(frozenValues.size) { rowIdx ->
            val bg = if (rowIdx % 2 == 0) colors.surface else colors.raised
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .background(bg),
            ) {
                FrozenCell(
                    frozenColumn.width,
                    scrolled,
                    background = bg,
                    onClick = onFrozenCellClick?.let { { it(rowIdx) } },
                ) {
                    Text(
                        text = frozenValues[rowIdx],
                        style = type.title,
                        color = if (onFrozenCellClick != null) colors.accent else colors.ink,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Row(Modifier.horizontalScroll(hScroll)) {
                    columns.forEachIndexed { colIdx, column ->
                        val raw = rows.getOrNull(rowIdx)?.getOrElse(colIdx) { "" }.orEmpty()
                        val (value, detail) = raw.split("\n", limit = 2).let { it[0] to it.getOrNull(1) }
                        val highlighted = Pair(rowIdx, colIdx) in highlightedCells
                        Column(
                            Modifier
                                .width(column.width)
                                .fillMaxHeight()
                                .padding(horizontal = 12.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        ) {
                            Text(
                                text = value,
                                style = if (column.monospace) type.mono else type.bodySmall,
                                color = when {
                                    value == "—" -> colors.ink3
                                    highlighted -> colors.accent
                                    else -> colors.ink
                                },
                                maxLines = if (detail == null) 2 else 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            if (detail != null) {
                                Text(
                                    text = detail,
                                    style = type.bodySmall.copy(fontSize = type.label.fontSize),
                                    color = colors.ink2,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            HairlineDivider()
        }
    }
}

/** Frozen first-column cell; casts a soft edge once the other columns scroll under it. */
@Composable
private fun FrozenCell(
    width: Dp,
    scrolled: Boolean,
    background: androidx.compose.ui.graphics.Color = RadarTheme.colors.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = RadarTheme.colors
    val edge by androidx.compose.animation.core.animateDpAsState(if (scrolled) 6.dp else 0.dp, label = "frozenEdge")
    Box(
        Modifier
            .zIndex(1f)
            .shadow(edge, androidx.compose.ui.graphics.RectangleShape, clip = false)
            .width(width)
            .fillMaxHeight()
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
        Box(Modifier.align(Alignment.CenterEnd).width(1.dp).fillMaxHeight().background(colors.rule))
    }
}

@Composable
private fun HeaderLabel(title: String, unit: String, active: Boolean, arrow: String?) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Two lines, so "Ionic conductivity" is not cut to "Ionic".
            Text(
                title.uppercase(),
                style = type.label,
                color = if (active) colors.accent else colors.ink2,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (arrow != null) Text(" $arrow", style = type.label, color = colors.accent)
        }
        if (unit.isNotBlank()) {
            Text(unit, style = type.mono.copy(fontSize = type.label.fontSize), color = colors.ink3, maxLines = 1)
        }
    }
}

@Composable
private fun HairlineDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RadarSpacing.hairline)
            .background(RadarTheme.colors.rule),
    )
}

