package com.researchradar.feature.map.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.model.ResearchMap
import com.researchradar.feature.map.ExportHelper

enum class ExportFormat(val extension: String, val label: String, val target: String, val mimeType: String) {
    MARKDOWN("md", "Markdown (.md)", "Obsidian, Notion, GitHub lab notes", "text/markdown"),
    BIBTEX("bib", "BibTeX (.bib)", "LaTeX, Zotero, Mendeley, Overleaf", "application/x-bibtex"),
    CSV("csv", "Spreadsheet (.csv)", "Excel, Google Sheets, Pandas dataframe", "text/csv"),
}

@Composable
fun ExportDialog(
    researchMap: ResearchMap,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(ExportFormat.MARKDOWN) }

    val exportContent = remember(selectedFormat, researchMap) {
        when (selectedFormat) {
            ExportFormat.MARKDOWN -> ExportHelper.generateMarkdown(researchMap)
            ExportFormat.BIBTEX -> ExportHelper.generateBibTeX(researchMap)
            ExportFormat.CSV -> ExportHelper.generateCsv(researchMap)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "EXPORT RESEARCH MAP",
                    style = RadarTheme.typography.subheading,
                    color = RadarTheme.colors.ink,
                )
                Text(
                    text = "Field Notebook Scientific Export",
                    style = RadarTheme.typography.caption,
                    color = RadarTheme.colors.ink3,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Select citation and evidence export format:",
                    style = RadarTheme.typography.body,
                    color = RadarTheme.colors.ink2,
                )

                Spacer(modifier = Modifier.height(4.dp))

                ExportFormat.entries.forEach { format ->
                    val isSelected = selectedFormat == format
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .selectable(
                                selected = isSelected,
                                onClick = { selectedFormat = format },
                                role = Role.RadioButton,
                            )
                            .background(
                                color = if (isSelected) RadarTheme.colors.accentWash else RadarTheme.colors.surface,
                                shape = RadarShape.card,
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) RadarTheme.colors.accent else RadarTheme.colors.hairline,
                                shape = RadarShape.card,
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = format.label,
                                style = RadarTheme.typography.body,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) RadarTheme.colors.accent else RadarTheme.colors.ink,
                            )
                            Text(
                                text = format.target,
                                style = RadarTheme.typography.caption,
                                color = RadarTheme.colors.ink3,
                            )
                        }
                        if (isSelected) {
                            Text(
                                text = "✓",
                                style = RadarTheme.typography.body,
                                color = RadarTheme.colors.accent,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Ereuna export", exportContent)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied ${selectedFormat.label} to clipboard", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                    shape = RadarShape.chip,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RadarTheme.colors.ink,
                    ),
                ) {
                    Text(
                        text = "COPY",
                        style = RadarTheme.typography.label,
                    )
                }

                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, exportContent)
                            putExtra(Intent.EXTRA_SUBJECT, "Ereuna: ${researchMap.topic}")
                            type = selectedFormat.mimeType
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Export Research Map"))
                        onDismiss()
                    },
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                    shape = RadarShape.chip,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RadarTheme.colors.accent,
                    ),
                ) {
                    Text(
                        text = "SHARE",
                        style = RadarTheme.typography.label,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text(
                    text = "CANCEL",
                    style = RadarTheme.typography.label,
                    color = RadarTheme.colors.ink3,
                )
            }
        },
        containerColor = RadarTheme.colors.surface,
        shape = RadarShape.menu,
        modifier = modifier.semantics {
            contentDescription = "Export research map dialog"
        },
    )
}
