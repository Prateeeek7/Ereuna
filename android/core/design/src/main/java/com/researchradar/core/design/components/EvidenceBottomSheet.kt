package com.researchradar.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.staggeredEnter
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.ResearchRadarTheme
import com.researchradar.core.model.Evidence

/**
 * Data payload for displaying evidence in [EvidenceBottomSheet].
 */
data class EvidenceSheetData(
    val citationIndex: Int,
    val quote: String,
    val section: String,
    val page: Int?,
    val shortLabel: String,
    val paperTitle: String,
    val paperId: String,
    val venue: String = "",
    val year: Int? = null,
    val doi: String? = null,
)

/**
 * Lab-notebook styled Evidence Bottom Sheet.
 *
 * Appears when tapping any [CitationMarker] or evidence tag.
 * Strict adherence to Design Spec: 6dp sheet radius, warm paper/surface background,
 * 2dp accent left rule on verbatim quotes, mono labels, no heavy shadows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceBottomSheet(
    data: EvidenceSheetData,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onViewPaper: ((String) -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RadarShape.sheet,
        containerColor = colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = RadarSpacing.md)
                    .size(width = 40.dp, height = 4.dp)
                    .background(colors.rule, RadarShape.pill)
            )
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = RadarSpacing.gutter)
                .padding(bottom = RadarSpacing.lg),
        ) {
            Text(
                text = if (data.citationIndex > 0) "Source ${data.citationIndex}" else "Source passage",
                style = type.label,
                color = colors.accent,
                modifier = Modifier.staggeredEnter(0, lift = 12f),
            )
            Spacer(modifier = Modifier.height(RadarSpacing.xs))
            Text(
                text = data.paperTitle,
                style = type.heading,
                color = colors.ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.staggeredEnter(1, lift = 12f),
            )
            val meta = listOfNotNull(
                data.shortLabel.takeIf { it.isNotBlank() },
                data.venue.takeIf { it.isNotBlank() },
                data.year?.takeIf { it > 0 }?.toString(),
            ).joinToString("  ·  ")
            if (meta.isNotBlank()) {
                Spacer(modifier = Modifier.height(RadarSpacing.xs))
                Text(text = meta, style = type.mono, color = colors.ink2, modifier = Modifier.staggeredEnter(1, lift = 12f))
            }

            Spacer(modifier = Modifier.height(RadarSpacing.lg))

            EvidenceQuote(
                quote = data.quote,
                section = data.section,
                page = data.page,
                shortLabel = "",
                modifier = Modifier.staggeredEnter(2, lift = 16f),
            )

            if (!data.doi.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(RadarSpacing.md))
                Text(text = "doi:${data.doi}", style = type.mono, color = colors.ink2)
            }

            Spacer(modifier = Modifier.height(RadarSpacing.xl))

            if (onViewPaper != null && data.paperId.isNotBlank()) {
                RadarButton(
                    text = "Open paper",
                    trailingIcon = RadarIcons.ArrowRight,
                    onClick = {
                        onDismissRequest()
                        onViewPaper(data.paperId)
                    },
                    modifier = Modifier.fillMaxWidth().staggeredEnter(3, lift = 16f),
                )
            }
        }
    }
}

