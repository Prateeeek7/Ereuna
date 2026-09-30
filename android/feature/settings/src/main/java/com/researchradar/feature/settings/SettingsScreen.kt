package com.researchradar.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.researchradar.core.design.DarkRadarColors
import com.researchradar.core.design.LightRadarColors
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.components.RadarButton
import com.researchradar.core.design.components.RadarButtonStyle
import com.researchradar.core.design.components.RadarCard
import com.researchradar.core.design.components.RadarChip
import com.researchradar.core.design.components.RadarTextField
import com.researchradar.core.design.staggeredEnter

data class DataSourceInfo(
    val name: String,
    val role: String,
    val url: String,
)

/** The services the backend actually calls, and what each is used for. */
val DATA_SOURCES = listOf(
    DataSourceInfo("OpenAlex", "Paper search and citation links", "https://openalex.org"),
    DataSourceInfo("Semantic Scholar", "Paper search, abstracts and reference lists", "https://www.semanticscholar.org"),
    DataSourceInfo("arXiv", "Preprints and their full-text PDFs", "https://arxiv.org"),
    DataSourceInfo("Unpaywall", "Legal open-access copies of journal papers", "https://unpaywall.org"),
    DataSourceInfo("Europe PMC", "Open-access full text of biomedical papers", "https://europepmc.org"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<ConfirmAction?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }

    LaunchedEffect(state.userMessage) {
        if (state.userMessage != null) {
            android.widget.Toast.makeText(context, state.userMessage, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.dismissMessage()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = RadarSpacing.gutter, end = RadarSpacing.gutter, bottom = RadarSpacing.bottomBarClearance),
    ) {
        item {
            Text("Settings", style = type.hero, color = colors.ink, modifier = Modifier.padding(top = RadarSpacing.lg, bottom = RadarSpacing.lg).staggeredEnter(0))
        }

        // Account
        item {
            RadarCard(Modifier.fillMaxWidth().staggeredEnter(1), color = colors.night, border = null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(RadarShape.pill).background(colors.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(initials(state.accountName), style = type.title, color = colors.onAccent)
                    }
                    Spacer(Modifier.width(RadarSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(state.accountName.ifBlank { "Signed in" }, style = type.title, color = colors.onNight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(state.accountEmail, style = type.bodySmall, color = colors.onNight.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(RadarSpacing.md))
                RadarButton(
                    text = "Sign out",
                    icon = RadarIcons.SignOut,
                    onClick = { confirm = ConfirmAction.SignOut },
                    style = RadarButtonStyle.Ghost,
                    contentColor = colors.onNight,
                    height = 42.dp,
                    modifier = Modifier.border(1.dp, colors.nightRule, RadarShape.pill),
                )
            }
        }

        // Appearance
        item { SectionTitle("Appearance") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RadarSpacing.sm)) {
                ThemePreference.entries.forEach { pref ->
                    ThemeTile(
                        pref = pref,
                        selected = state.themePreference == pref,
                        onClick = { viewModel.setThemePreference(pref) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Search defaults
        item { SectionTitle("New maps start with") }
        item {
            RadarCard(Modifier.fillMaxWidth()) {
                Text("Papers per map", style = type.title, color = colors.ink)
                Text("More papers take longer to read.", style = type.bodySmall, color = colors.ink2)
                Spacer(Modifier.height(RadarSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(RadarSpacing.sm)) {
                    listOf(15, 25, 50).forEach { n ->
                        RadarChip("$n", state.defaultPaperCount == n, { viewModel.setDefaultPaperCount(n) })
                    }
                }
                Divider()
                Text("Minimum citations", style = type.title, color = colors.ink)
                Text("Skip papers cited fewer times than this.", style = type.bodySmall, color = colors.ink2)
                Spacer(Modifier.height(RadarSpacing.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(RadarSpacing.sm), verticalArrangement = Arrangement.spacedBy(RadarSpacing.sm)) {
                    listOf(0 to "Any", 5 to "5+", 10 to "10+", 25 to "25+").forEach { (n, label) ->
                        RadarChip(label, state.minCitations == n, { viewModel.setMinCitations(n) })
                    }
                }
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Open access only", style = type.title, color = colors.ink)
                        Text("Only papers with a free full-text PDF.", style = type.bodySmall, color = colors.ink2)
                    }
                    Switch(
                        checked = state.openAccessOnly,
                        onCheckedChange = viewModel::setOpenAccessOnly,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onAccent,
                            checkedTrackColor = colors.accent,
                            uncheckedThumbColor = colors.ink2,
                            uncheckedTrackColor = colors.raised,
                            uncheckedBorderColor = colors.rule,
                        ),
                    )
                }
            }
        }

        // Storage
        item { SectionTitle("On this phone") }
        item {
            RadarCard(Modifier.fillMaxWidth()) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("${state.savedMapCount}", style = type.numeric, color = colors.ink)
                        Text("SAVED MAPS", style = type.label, color = colors.ink2)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("${state.savedPaperCount}", style = type.numeric, color = colors.ink)
                        Text("SAVED PAPERS", style = type.label, color = colors.ink2)
                    }
                }
                Text("Saved items stay readable offline.", style = type.bodySmall, color = colors.ink2, modifier = Modifier.padding(top = RadarSpacing.sm))
                Spacer(Modifier.height(RadarSpacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(RadarSpacing.sm)) {
                    RadarButton("Clear searches", { confirm = ConfirmAction.ClearSearches }, style = RadarButtonStyle.Secondary, height = 42.dp)
                    RadarButton("Clear saved data", { confirm = ConfirmAction.ClearAll }, style = RadarButtonStyle.Secondary, height = 42.dp, contentColor = colors.accent)
                }
            }
        }

        // Data sources
        item { SectionTitle("Data sources") }
        item {
            RadarCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                DATA_SOURCES.forEachIndexed { i, source ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) }
                            }
                            .padding(horizontal = RadarSpacing.lg, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(source.name, style = type.title, color = colors.ink)
                            Text(source.role, style = type.bodySmall, color = colors.ink2)
                        }
                        Icon(RadarIcons.ArrowUpRight, contentDescription = "Open ${source.name} website", tint = colors.ink3, modifier = Modifier.size(18.dp))
                    }
                    if (i < DATA_SOURCES.lastIndex) {
                        Box(Modifier.padding(horizontal = RadarSpacing.lg).fillMaxWidth().height(1.dp).background(colors.rule))
                    }
                }
            }
        }

        // About
        item { SectionTitle("How results are checked") }
        item {
            RadarCard(Modifier.fillMaxWidth()) {
                Text(
                    "Every finding, metric and limitation shown for a paper carries a quote that the server found in that paper's text; items whose quote can't be found are dropped. Sources include preprints (arXiv) as well as journals. Research gaps are built from limitations the papers state themselves. Experiment suggestions are model-generated proposals, not findings.",
                    style = type.bodySmall,
                    color = colors.ink,
                )
                Spacer(Modifier.height(RadarSpacing.md))
                Text("Ereuna 0.1.0", style = type.mono, color = colors.ink3)
            }
        }

        // Account deletion (also possible on the website)
        item { SectionTitle("Delete account") }
        item {
            RadarCard(Modifier.fillMaxWidth()) {
                Text(
                    "Permanently deletes your account, your library, and every map built from your PDFs. Maps from topic searches hold no personal data and stay available to others.",
                    style = type.bodySmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.height(RadarSpacing.md))
                RadarButton(
                    text = "Delete account",
                    icon = RadarIcons.Trash,
                    onClick = { deletePassword = ""; viewModel.clearDeleteError(); deleting = true },
                    style = RadarButtonStyle.Ghost,
                    contentColor = colors.accent,
                    height = 42.dp,
                    modifier = Modifier.border(1.dp, colors.accent.copy(alpha = 0.5f), RadarShape.pill),
                )
            }
        }
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { if (!state.isDeleting) deleting = false },
            containerColor = colors.surface,
            shape = RadarShape.card,
            title = { Text("Delete your account?", style = type.heading, color = colors.ink) },
            text = {
                Column {
                    Text(
                        "This can't be undone. Enter your password to confirm.",
                        style = type.body,
                        color = colors.ink2,
                    )
                    Spacer(Modifier.height(RadarSpacing.md))
                    RadarTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it; viewModel.clearDeleteError() },
                        label = "Password",
                        isPassword = true,
                        error = state.deleteError,
                        enabled = !state.isDeleting,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isDeleting,
                    onClick = { viewModel.deleteAccount(deletePassword) },
                ) { Text(if (state.isDeleting) "Deleting…" else "Delete forever", style = type.title, color = colors.accent) }
            },
            dismissButton = {
                TextButton(enabled = !state.isDeleting, onClick = { deleting = false }) { Text("Cancel", style = type.title, color = colors.ink2) }
            },
        )
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = colors.surface,
            shape = RadarShape.card,
            title = { Text(action.title, style = type.heading, color = colors.ink) },
            text = { Text(action.body, style = type.body, color = colors.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        ConfirmAction.SignOut -> viewModel.signOut()
                        ConfirmAction.ClearSearches -> viewModel.clearRecentTopics()
                        ConfirmAction.ClearAll -> viewModel.clearAllCache()
                    }
                    confirm = null
                }) { Text(action.confirmLabel, style = type.title, color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Cancel", style = type.title, color = colors.ink2) }
            },
        )
    }
}

private enum class ConfirmAction(val title: String, val body: String, val confirmLabel: String) {
    SignOut("Sign out?", "Saved maps and search history on this phone will be removed. Sign in again to continue.", "Sign out"),
    ClearSearches("Clear search history?", "Recent topics will be removed from the search screen.", "Clear"),
    ClearAll("Clear saved data?", "All saved maps, bookmarked papers and search history on this phone will be deleted.", "Clear all"),
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = RadarTheme.typography.label,
        color = RadarTheme.colors.ink2,
        modifier = Modifier.padding(top = RadarSpacing.xxl, bottom = RadarSpacing.sm),
    )
}

@Composable
private fun Divider() {
    Box(Modifier.padding(vertical = RadarSpacing.lg).fillMaxWidth().height(1.dp).background(RadarTheme.colors.rule))
}

/** Theme option with a tiny preview of that theme's paper, ink and accent. */
@Composable
private fun ThemeTile(pref: ThemePreference, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RadarTheme.colors
    val border by animateColorAsState(if (selected) colors.accent else colors.rule, label = "themeBorder")
    val preview = when (pref) {
        ThemePreference.LIGHT -> listOf(LightRadarColors.paper, LightRadarColors.ink, LightRadarColors.accent)
        ThemePreference.DARK -> listOf(DarkRadarColors.paper, DarkRadarColors.ink, DarkRadarColors.accent)
        ThemePreference.SYSTEM -> listOf(LightRadarColors.paper, DarkRadarColors.paper, LightRadarColors.accent)
    }
    Column(
        modifier
            .clip(RadarShape.card)
            .background(colors.surface)
            .border(if (selected) 2.dp else 1.dp, border, RadarShape.card)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(RadarSpacing.md),
    ) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).clip(RadarShape.control).background(preview[0]).border(1.dp, colors.rule, RadarShape.control).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pref == ThemePreference.SYSTEM) {
                Box(Modifier.weight(1f).fillMaxWidth().height(28.dp).clip(RadarShape.chip).background(preview[1]))
            } else {
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.8f).height(5.dp).clip(RadarShape.pill).background(preview[1]))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth(0.5f).height(5.dp).clip(RadarShape.pill).background(preview[1].copy(alpha = 0.5f)))
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(12.dp).clip(RadarShape.pill).background(preview[2]))
        }
        Spacer(Modifier.height(RadarSpacing.sm))
        Text(pref.label, style = RadarTheme.typography.title.copy(fontSize = RadarTheme.typography.bodySmall.fontSize), color = colors.ink)
    }
}

private fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "·" }
