package com.researchradar.feature.map.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarMotion
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.components.EmptyState
import com.researchradar.core.design.components.RadarButton
import com.researchradar.core.design.components.RadarButtonStyle
import com.researchradar.core.design.components.RadarCard
import com.researchradar.core.design.staggeredEnter
import com.researchradar.core.model.Experiment

@Composable
fun ExperimentsTab(
    experiments: List<Experiment>,
    topic: String = "",
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val context = LocalContext.current

    if (experiments.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "No experiment suggestions",
                body = "Suggestions are drafted by the server's language model from this map's research gaps. None were produced for this map (no gaps, or no language model configured).",
                icon = RadarIcons.Document,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = RadarSpacing.xxxl),
    ) {
        item {
            Row(
                Modifier.padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Model-generated proposals for closing the gaps above. Treat them as starting points, not results.",
                    style = type.bodySmall,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(RadarSpacing.md))
                RadarButton(
                    text = "Copy all",
                    onClick = { copyToClipboard(context, generateAllProtocolsMarkdown(experiments, topic), "all protocols") },
                    style = RadarButtonStyle.Secondary,
                    height = 38.dp,
                )
            }
        }
        itemsIndexed(experiments, key = { _, e -> e.id }) { index, exp ->
            ExperimentCard(
                number = index + 1,
                experiment = exp,
                onCopy = { copyToClipboard(context, generateSingleProtocolMarkdown(exp), "protocol") },
                modifier = Modifier.staggeredEnter(index),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExperimentCard(
    number: Int,
    experiment: Experiment,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    var expanded by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, RadarMotion.snappy(), label = "expChevron")
    val difficulty = experiment.difficulty.uppercase()
    val (difficultyWord, difficultyColor) = when (difficulty) {
        "HIGH" -> "Hard" to colors.conflict
        "LOW" -> "Easy" to colors.positive
        else -> "Moderate" to colors.accent
    }

    RadarCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RadarSpacing.gutter, vertical = 5.dp),
        onClick = { expanded = !expanded },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Experiment $number", style = type.label, color = colors.accent)
            if (experiment.gapId.isNotBlank()) {
                Spacer(Modifier.width(RadarSpacing.sm))
                Text(
                    "for ${experiment.gapId.replace("gap_", "G")}",
                    style = type.mono,
                    color = colors.ink2,
                    modifier = Modifier.clip(RadarShape.pill).background(colors.raised).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RadarShape.pill).border(1.dp, difficultyColor.copy(alpha = 0.5f), RadarShape.pill).padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(RadarShape.pill).background(difficultyColor))
                Spacer(Modifier.width(6.dp))
                Text(difficultyWord, style = type.bodySmall, color = colors.ink)
            }
        }
        Spacer(Modifier.height(RadarSpacing.sm))
        Text(experiment.hypothesis, style = type.paperTitle, color = colors.ink)

        if (experiment.expectedResult.isNotBlank()) {
            Spacer(Modifier.height(RadarSpacing.md))
            Column(
                Modifier.fillMaxWidth().clip(RadarShape.control).background(colors.accentWash).padding(RadarSpacing.md),
            ) {
                Text("WHAT TO MEASURE", style = type.label, color = colors.accent)
                Spacer(Modifier.height(4.dp))
                Text(experiment.expectedResult, style = type.bodySmall, color = colors.ink)
            }
        }

        Spacer(Modifier.height(RadarSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (expanded) "Hide protocol" else "Show protocol", style = type.bodySmall, color = colors.ink2, modifier = Modifier.weight(1f))
            Icon(RadarIcons.ChevronDown, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(18.dp).rotate(chevron))
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(RadarMotion.snappy()) + fadeIn(),
            exit = shrinkVertically(RadarMotion.snappy()) + fadeOut(),
        ) {
            Column(Modifier.padding(top = RadarSpacing.sm)) {
                if (experiment.setup.isNotBlank()) {
                    Text("SETUP", style = type.label, color = colors.ink2)
                    Spacer(Modifier.height(4.dp))
                    Text(experiment.setup, style = type.body, color = colors.ink)
                    Spacer(Modifier.height(RadarSpacing.md))
                }
                if (experiment.tools.isNotEmpty()) {
                    Text("TOOLS", style = type.label, color = colors.ink2)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        experiment.tools.forEach { tool ->
                            Text(
                                tool,
                                style = type.bodySmall,
                                color = colors.ink,
                                modifier = Modifier.clip(RadarShape.pill).border(1.dp, colors.rule, RadarShape.pill).padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(RadarSpacing.md))
                }
                if (experiment.variables.isNotEmpty()) {
                    Text("VARIABLES", style = type.label, color = colors.ink2)
                    Spacer(Modifier.height(4.dp))
                    experiment.variables.forEach { v ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Box(Modifier.padding(top = 8.dp).size(4.dp).clip(RadarShape.pill).background(colors.ink2))
                            Spacer(Modifier.width(8.dp))
                            Text(v, style = type.bodySmall, color = colors.ink)
                        }
                    }
                    Spacer(Modifier.height(RadarSpacing.md))
                }
                RadarButton(text = "Copy protocol", icon = RadarIcons.Document, onClick = onCopy, style = RadarButtonStyle.Secondary, height = 42.dp)
            }
        }
    }
}

private fun generateSingleProtocolMarkdown(exp: Experiment): String {
    return """
        # Scientific Protocol: ${exp.id.uppercase()} (Targeting ${exp.gapId.uppercase()})
        
        ## Hypothesis
        > *${exp.hypothesis}*
        
        ## Experimental Setup
        ${exp.setup}
        
        ## Tools & EDA Suite
        ${exp.tools.joinToString("\n") { "- $it" }}
        
        ## Variables Under Test
        ${exp.variables.joinToString("\n") { "- $it" }}
        
        ## Target Expected Metric
        **${exp.expectedResult}**
        
        - **Difficulty**: ${exp.difficulty}
        - **Referenced Papers**: ${exp.paperIds.joinToString(", ")}
    """.trimIndent()
}

private fun generateAllProtocolsMarkdown(experiments: List<Experiment>, topic: String): String {
    val sb = StringBuilder()
    sb.append("# Research Protocols: $topic\n\n")
    experiments.forEach { exp ->
        sb.append(generateSingleProtocolMarkdown(exp))
        sb.append("\n\n---\n\n")
    }
    return sb.toString()
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
}
