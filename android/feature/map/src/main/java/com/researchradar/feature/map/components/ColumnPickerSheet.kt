package com.researchradar.feature.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.model.CompareColumn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnPickerSheet(
    columns: List<CompareColumn>,
    visibleColumns: Set<String>,
    onToggleColumn: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
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
        modifier = modifier,
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
                    text = "SELECT COMPARISON COLUMNS",
                    style = type.sectionLabel,
                    color = colors.accent,
                )

                Text(
                    text = "DONE",
                    style = type.button,
                    color = colors.ink,
                    modifier = Modifier.clickable(onClick = onDismissRequest),
                )
            }

            Spacer(modifier = Modifier.height(RadarSpacing.md))
            HorizontalDivider(thickness = RadarSpacing.hairline, color = colors.rule)

            columns.forEach { col ->
                val isChecked = visibleColumns.contains(col.id)

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

                    Column(modifier = Modifier.weight(1f).padding(start = RadarSpacing.sm)) {
                        Text(
                            text = col.title,
                            style = type.body,
                            color = colors.ink,
                        )
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
