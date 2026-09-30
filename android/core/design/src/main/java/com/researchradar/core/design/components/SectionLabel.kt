package com.researchradar.core.design.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme

/** Small uppercase label that introduces a block of content. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = RadarSpacing.lg, bottom = RadarSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = RadarTheme.typography.label,
            color = RadarTheme.colors.ink2,
        )
    }
}
