package com.researchradar.feature.search

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.components.RadarBlip
import com.researchradar.core.design.components.RadarButton
import com.researchradar.core.design.components.RadarButtonStyle
import com.researchradar.core.design.components.RadarCard
import com.researchradar.core.design.components.RadarChip
import com.researchradar.core.design.components.RadarIconButton
import com.researchradar.core.design.components.RadarSweep
import com.researchradar.core.design.pressScale
import com.researchradar.core.design.staggeredEnter
import com.researchradar.core.data.repository.PickedPdf
import com.researchradar.core.model.MapFilters

@Composable
fun SearchRoute(
    onNavigateToMap: (String) -> Unit,
    onNavigateToJob: (jobId: String, topic: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pickPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.onPdfsPicked(uris)
    }

    SearchScreen(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onSubmit = {
            viewModel.submitSearch(
                onNavigateToMap = onNavigateToMap,
                onNavigateToJob = onNavigateToJob,
            )
        },
        onSubmitTopic = { topic ->
            viewModel.onTopicSelect(topic)
            viewModel.submitSearch(
                onNavigateToMap = onNavigateToMap,
                onNavigateToJob = onNavigateToJob,
            )
        },
        onOpenFilterSheet = viewModel::onOpenFilterSheet,
        onCloseFilterSheet = viewModel::onCloseFilterSheet,
        onFiltersUpdated = viewModel::onFiltersUpdated,
        onModeChange = viewModel::onModeChange,
        onAddPdfs = { pickPdfs.launch(arrayOf("application/pdf")) },
        onRemovePdf = viewModel::onRemovePdf,
        onIncludeRelatedChange = viewModel::onIncludeRelatedChange,
    )
}

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSubmit: () -> Unit,
    onSubmitTopic: (String) -> Unit,
    onOpenFilterSheet: () -> Unit,
    onCloseFilterSheet: () -> Unit,
    onFiltersUpdated: (MapFilters) -> Unit,
    modifier: Modifier = Modifier,
    onModeChange: (SearchMode) -> Unit = {},
    onAddPdfs: () -> Unit = {},
    onRemovePdf: (String) -> Unit = {},
    onIncludeRelatedChange: (Boolean) -> Unit = {},
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val focusManager = LocalFocusManager.current
    val myPapers = uiState.mode == SearchMode.MyPapers

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(bottom = RadarSpacing.bottomBarClearance),
        ) {
            // Wordmark row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.md)
                        .staggeredEnter(0, lift = 10f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RadarShape.pill)
                            .background(colors.night),
                        contentAlignment = Alignment.Center,
                    ) {
                        RadarSweep(Modifier.size(26.dp), periodMillis = 2600)
                    }
                    Spacer(Modifier.width(10.dp))
                    com.researchradar.core.design.components.Wordmark(color = colors.ink)
                }
            }

            // Hero headline
            item {
                Column(
                    Modifier
                        .padding(horizontal = RadarSpacing.gutter)
                        .padding(top = RadarSpacing.xl, bottom = RadarSpacing.lg),
                ) {
                    AnimatedContent(
                        targetState = uiState.mode,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "heroText",
                        modifier = Modifier.staggeredEnter(1),
                    ) { mode ->
                        Column {
                            Text(
                                text = if (mode == SearchMode.MyPapers) "Map your own\npapers." else "Map the literature\non any topic.",
                                style = type.hero,
                                color = colors.ink,
                            )
                            Spacer(Modifier.height(RadarSpacing.md))
                            Text(
                                text = if (mode == SearchMode.MyPapers) {
                                    "Upload the PDFs you're working with. Findings, numbers and gaps come quoted from them."
                                } else {
                                    "Papers, findings, gaps and citations, with every claim quoted from its source."
                                },
                                style = type.body,
                                color = colors.ink2,
                            )
                        }
                    }
                    Spacer(Modifier.height(RadarSpacing.lg))
                    ModeSwitch(
                        mode = uiState.mode,
                        enabled = !uiState.isLoading,
                        onModeChange = onModeChange,
                        modifier = Modifier.staggeredEnter(2),
                    )
                }
            }

            // Search card
            item {
                SearchCard(
                    mode = uiState.mode,
                    pdfs = uiState.pdfs,
                    pickNote = uiState.pickNote,
                    includeRelated = uiState.includeRelated,
                    onAddPdfs = onAddPdfs,
                    onRemovePdf = onRemovePdf,
                    onIncludeRelatedChange = onIncludeRelatedChange,
                    query = uiState.query,
                    filters = uiState.filters,
                    isLoading = uiState.isLoading,
                    isSubmitEnabled = uiState.isSubmitEnabled,
                    onQueryChange = onQueryChange,
                    onClearQuery = onClearQuery,
                    onSubmit = {
                        focusManager.clearFocus()
                        onSubmit()
                    },
                    onOpenFilterSheet = onOpenFilterSheet,
                    modifier = Modifier
                        .padding(horizontal = RadarSpacing.gutter)
                        .staggeredEnter(3),
                )
            }

            // Error
            item {
                AnimatedVisibility(
                    visible = uiState.errorMessage != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = RadarSpacing.gutter, vertical = RadarSpacing.md)
                            .fillMaxWidth()
                            .clip(RadarShape.control)
                            .background(colors.accentWash)
                            .padding(RadarSpacing.md),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(RadarIcons.Close, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(RadarSpacing.sm))
                        Text(
                            text = "Couldn't start the map: ${uiState.errorMessage.orEmpty()}",
                            style = type.bodySmall,
                            color = colors.ink,
                        )
                    }
                }
            }

            // How it works: the instrument panel
            item {
                InstrumentPanel(
                    mode = uiState.mode,
                    modifier = Modifier
                        .padding(horizontal = RadarSpacing.gutter)
                        .padding(top = RadarSpacing.xl)
                        .staggeredEnter(4),
                )
            }

            if (myPapers) {
                item { PrivacyNote(Modifier.padding(horizontal = RadarSpacing.gutter).padding(top = RadarSpacing.xl)) }
            }

            // Recent topics
            if (!myPapers && uiState.recentTopics.isNotEmpty()) {
                item {
                    Text(
                        text = "RECENT",
                        style = type.label,
                        color = colors.ink2,
                        modifier = Modifier.padding(start = RadarSpacing.gutter, top = RadarSpacing.xxl, bottom = RadarSpacing.sm),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = RadarSpacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(RadarSpacing.sm),
                    ) {
                        items(uiState.recentTopics.take(8), key = { it.topic }) { item ->
                            RadarChip(
                                text = item.topic,
                                selected = false,
                                leadingIcon = RadarIcons.Clock,
                                onClick = { onSubmitTopic(item.topic) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            // Example topics
            if (!myPapers) item {
                Text(
                    text = "TRY A TOPIC",
                    style = type.label,
                    color = colors.ink2,
                    modifier = Modifier.padding(start = RadarSpacing.gutter, top = RadarSpacing.xxl, bottom = RadarSpacing.sm),
                )
            }
            if (!myPapers) itemsIndexed(uiState.exampleTopics) { index, topic ->
                ExampleTopicCard(
                    index = index + 1,
                    topic = topic,
                    enabled = !uiState.isLoading,
                    onClick = { onSubmitTopic(topic) },
                    modifier = Modifier
                        .padding(horizontal = RadarSpacing.gutter, vertical = 4.dp)
                        .staggeredEnter(5 + index),
                )
            }
        }

        if (uiState.isFilterSheetOpen) {
            FiltersBottomSheet(
                filters = uiState.filters,
                onDismiss = onCloseFilterSheet,
                onApply = onFiltersUpdated,
            )
        }
    }
}

@Composable
private fun SearchCard(
    mode: SearchMode,
    pdfs: List<PickedPdf>,
    pickNote: String?,
    includeRelated: Boolean,
    onAddPdfs: () -> Unit,
    onRemovePdf: (String) -> Unit,
    onIncludeRelatedChange: (Boolean) -> Unit,
    query: String,
    filters: MapFilters,
    isLoading: Boolean,
    isSubmitEnabled: Boolean,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSubmit: () -> Unit,
    onOpenFilterSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val borderColor by animateColorAsState(if (focused) colors.accent else colors.rule, label = "searchBorder")
    val elevation by animateDpAsState(if (focused) 22.dp else 6.dp, label = "searchElevation")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation, RadarShape.hero, ambientColor = colors.ink.copy(alpha = 0.18f), spotColor = colors.ink.copy(alpha = 0.18f))
            .clip(RadarShape.hero)
            .background(colors.surface)
            .border(1.5.dp, borderColor, RadarShape.hero)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focusRequester.requestFocus() }
            .padding(RadarSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                RadarIcons.Search,
                contentDescription = null,
                tint = if (focused) colors.accent else colors.ink2,
                modifier = Modifier.padding(top = 2.dp).size(22.dp),
            )
            Spacer(Modifier.width(RadarSpacing.md))
            Box(Modifier.weight(1f).heightIn(min = 56.dp)) {
                if (query.isEmpty()) {
                    Text(
                        text = if (mode == SearchMode.MyPapers) {
                            "What are these papers about? e.g. dendrite suppression in garnet electrolytes"
                        } else {
                            "Describe a research topic, e.g. solid-state electrolyte dendrite suppression"
                        },
                        style = type.body,
                        color = colors.ink3,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = type.body.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.accent),
                    interactionSource = interaction,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { if (isSubmitEnabled) onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            AnimatedVisibility(
                visible = query.isNotEmpty() && !isLoading,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                RadarIconButton(
                    icon = RadarIcons.Close,
                    contentDescription = "Clear topic",
                    onClick = onClearQuery,
                    size = 32.dp,
                    border = null,
                    background = colors.raised,
                    tint = colors.ink2,
                )
            }
        }

        AnimatedVisibility(
            visible = mode == SearchMode.MyPapers,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            PdfPicker(
                pdfs = pdfs,
                pickNote = pickNote,
                includeRelated = includeRelated,
                enabled = !isLoading,
                onAddPdfs = onAddPdfs,
                onRemovePdf = onRemovePdf,
                onIncludeRelatedChange = onIncludeRelatedChange,
            )
        }

        Spacer(Modifier.height(RadarSpacing.md))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (mode == SearchMode.Literature) {
                RadarChip(
                    text = filterSummary(filters),
                    selected = filters != MapFilters(),
                    leadingIcon = RadarIcons.Sliders,
                    onClick = onOpenFilterSheet,
                )
            } else {
                Text(
                    text = if (pdfs.isEmpty()) "Add at least one PDF" else "${pdfs.size} PDF${if (pdfs.size == 1) "" else "s"}",
                    style = type.mono,
                    color = colors.ink2,
                )
            }
            Spacer(Modifier.weight(1f))
            RadarButton(
                text = "Map it",
                trailingIcon = RadarIcons.ArrowRight,
                onClick = onSubmit,
                enabled = isSubmitEnabled || isLoading,
                loading = isLoading,
                style = RadarButtonStyle.Accent,
                height = 46.dp,
            )
        }
    }
}

private fun filterSummary(filters: MapFilters): String {
    if (filters == MapFilters()) return "${filters.maxPapers} papers"
    val parts = mutableListOf("${filters.maxPapers} papers")
    if (filters.yearMin != null || filters.yearMax != null) {
        parts += "${filters.yearMin ?: "…"}–${filters.yearMax ?: "now"}"
    }
    if (filters.openAccessOnly) parts += "open access"
    if (filters.minCitations > 0) parts += "≥${filters.minCitations} cites"
    return parts.joinToString(" · ")
}

@Composable
private fun InstrumentPanel(mode: SearchMode, modifier: Modifier = Modifier) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val demoBlips = remember {
        listOf(
            RadarBlip("a", 0.12f, 0.35f), RadarBlip("b", 0.31f, 0.7f), RadarBlip("c", 0.47f, 0.52f),
            RadarBlip("d", 0.63f, 0.28f), RadarBlip("e", 0.78f, 0.8f), RadarBlip("f", 0.9f, 0.45f),
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RadarShape.hero)
            .background(colors.night)
            .padding(RadarSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadarSweep(modifier = Modifier.size(112.dp), blips = demoBlips)
        Spacer(Modifier.width(RadarSpacing.lg))
        Column(Modifier.weight(1f)) {
            Text("HOW A MAP IS BUILT", style = type.label, color = colors.accent)
            Spacer(Modifier.height(RadarSpacing.sm))
            val steps = if (mode == SearchMode.MyPapers) {
                listOf(
                    "Reads every page of your PDFs",
                    "Finds each paper's published record",
                    "Checks every quote against the paper",
                )
            } else {
                listOf(
                    "Searches OpenAlex, Semantic Scholar and arXiv",
                    "Reads open-access full text",
                    "Checks every quote against the paper",
                )
            }
            steps.forEach { line ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    Icon(RadarIcons.Check, contentDescription = null, tint = colors.onNight.copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp).size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(line, style = type.bodySmall, color = colors.onNight)
                }
            }
        }
    }
}

@Composable
private fun ExampleTopicCard(
    index: Int,
    topic: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(RadarShape.card)
            .background(colors.surface)
            .border(1.dp, colors.rule, RadarShape.card)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = RadarSpacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(index.toString().padStart(2, '0'), style = type.mono, color = colors.accent)
        Spacer(Modifier.width(RadarSpacing.md))
        Text(
            topic,
            style = type.paperTitle,
            color = colors.ink,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(RadarSpacing.sm))
        Box(
            Modifier.size(32.dp).clip(RadarShape.pill).background(colors.raised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(RadarIcons.ArrowRight, contentDescription = null, tint = colors.ink, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersBottomSheet(
    filters: MapFilters,
    onDismiss: () -> Unit,
    onApply: (MapFilters) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var yearMin by remember { mutableStateOf(filters.yearMin?.toString() ?: "") }
    var yearMax by remember { mutableStateOf(filters.yearMax?.toString() ?: "") }
    var openAccessOnly by remember { mutableStateOf(filters.openAccessOnly) }
    var maxPapers by remember { mutableFloatStateOf(filters.maxPapers.toFloat()) }
    var minCitations by remember { mutableFloatStateOf(filters.minCitations.toFloat()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RadarTheme.shapes.sheet,
        containerColor = RadarTheme.colors.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(RadarTheme.colors.rule, RadarTheme.shapes.pill),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RadarSpacing.gutter, vertical = 24.dp)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Search filters",
                    style = RadarTheme.typography.heading,
                    color = RadarTheme.colors.ink,
                )
                Text(
                    text = "Reset",
                    style = RadarTheme.typography.title,
                    color = RadarTheme.colors.accent,
                    modifier = Modifier.clickable {
                        yearMin = ""
                        yearMax = ""
                        openAccessOnly = false
                        maxPapers = 25f
                        minCitations = 0f
                    },
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Year range
            Text(
                text = "Publication Year Range",
                style = RadarTheme.typography.body,
                color = RadarTheme.colors.ink,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, RadarTheme.colors.hairline, RadarTheme.shapes.control)
                        .background(RadarTheme.colors.surfaceSecondary, RadarTheme.shapes.control)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (yearMin.isEmpty()) {
                        Text("From (e.g. 2018)", style = RadarTheme.typography.mono, color = RadarTheme.colors.ink3)
                    }
                    BasicTextField(
                        value = yearMin,
                        onValueChange = { yearMin = it.filter { ch -> ch.isDigit() }.take(4) },
                        textStyle = RadarTheme.typography.mono.copy(color = RadarTheme.colors.ink),
                        cursorBrush = SolidColor(RadarTheme.colors.accent),
                        singleLine = true,
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, RadarTheme.colors.hairline, RadarTheme.shapes.control)
                        .background(RadarTheme.colors.surfaceSecondary, RadarTheme.shapes.control)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (yearMax.isEmpty()) {
                        Text("To (e.g. 2024)", style = RadarTheme.typography.mono, color = RadarTheme.colors.ink3)
                    }
                    BasicTextField(
                        value = yearMax,
                        onValueChange = { yearMax = it.filter { ch -> ch.isDigit() }.take(4) },
                        textStyle = RadarTheme.typography.mono.copy(color = RadarTheme.colors.ink),
                        cursorBrush = SolidColor(RadarTheme.colors.accent),
                        singleLine = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Max papers slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Maximum Papers",
                    style = RadarTheme.typography.body,
                    color = RadarTheme.colors.ink,
                )
                Text(
                    text = "${maxPapers.toInt()}",
                    style = RadarTheme.typography.mono,
                    color = RadarTheme.colors.ink2,
                )
            }
            Slider(
                value = maxPapers,
                onValueChange = { maxPapers = it },
                valueRange = 10f..50f,
                steps = 7,
                colors = SliderDefaults.colors(
                    thumbColor = RadarTheme.colors.accent,
                    activeTrackColor = RadarTheme.colors.accent,
                    inactiveTrackColor = RadarTheme.colors.hairline,
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Open access only switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Open Access Only",
                        style = RadarTheme.typography.body,
                        color = RadarTheme.colors.ink,
                    )
                    Text(
                        text = "Require full text availability",
                        style = RadarTheme.typography.mono,
                        color = RadarTheme.colors.ink3,
                    )
                }
                Switch(
                    checked = openAccessOnly,
                    onCheckedChange = { openAccessOnly = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = RadarTheme.colors.surface,
                        checkedTrackColor = RadarTheme.colors.accent,
                        uncheckedThumbColor = RadarTheme.colors.ink3,
                        uncheckedTrackColor = RadarTheme.colors.surfaceSecondary,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Apply button
            RadarButton(
                text = "Apply filters",
                style = RadarButtonStyle.Accent,
                onClick = {
                    onApply(
                        filters.copy(
                            yearMin = yearMin.toIntOrNull(),
                            yearMax = yearMax.toIntOrNull(),
                            openAccessOnly = openAccessOnly,
                            maxPapers = maxPapers.toInt(),
                            minCitations = minCitations.toInt(),
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}



/** Two-way switch between searching the literature and using the user's own PDFs. */
@Composable
private fun ModeSwitch(
    mode: SearchMode,
    enabled: Boolean,
    onModeChange: (SearchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val options = listOf(SearchMode.Literature to "Search the literature", SearchMode.MyPapers to "Use my papers")
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RadarShape.pill)
            .background(colors.raised)
            .border(1.dp, colors.rule, RadarShape.pill)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val indicatorOffset by animateDpAsState(segment * options.indexOfFirst { it.first == mode }, label = "modeIndicator")
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(segment)
                .height(40.dp)
                .shadow(3.dp, RadarShape.pill, ambientColor = colors.ink.copy(alpha = 0.2f), spotColor = colors.ink.copy(alpha = 0.2f))
                .clip(RadarShape.pill)
                .background(colors.surface),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEach { (option, label) ->
                val selected = option == mode
                val fg by animateColorAsState(if (selected) colors.ink else colors.ink2, label = "modeFg")
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RadarShape.pill)
                        .semantics { this.selected = selected }
                        .clickable(enabled = enabled, role = Role.Tab) { onModeChange(option) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (option == SearchMode.MyPapers) RadarIcons.Upload else RadarIcons.Search,
                        contentDescription = null,
                        tint = if (selected) colors.accent else fg,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = type.title.copy(fontSize = type.bodySmall.fontSize), color = fg, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PdfPicker(
    pdfs: List<PickedPdf>,
    pickNote: String?,
    includeRelated: Boolean,
    enabled: Boolean,
    onAddPdfs: () -> Unit,
    onRemovePdf: (String) -> Unit,
    onIncludeRelatedChange: (Boolean) -> Unit,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    Column(Modifier.fillMaxWidth().padding(top = RadarSpacing.md)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Spacer(Modifier.height(RadarSpacing.md))
        pdfs.forEach { pdf ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(32.dp).clip(RadarShape.control).background(colors.accentWash),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(RadarIcons.Document, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(RadarSpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(pdf.name, style = type.bodySmall, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (pdf.sizeBytes >= 0) Text(formatBytes(pdf.sizeBytes), style = type.mono, color = colors.ink3)
                }
                RadarIconButton(
                    icon = RadarIcons.Close,
                    contentDescription = "Remove ${pdf.name}",
                    onClick = { onRemovePdf(pdf.uri) },
                    size = 32.dp,
                    border = null,
                    background = colors.raised,
                    tint = colors.ink2,
                )
            }
        }
        if (pdfs.size < MAX_PDFS) {
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .padding(top = if (pdfs.isEmpty()) 0.dp else RadarSpacing.sm)
                    .fillMaxWidth()
                    .pressScale(interaction, 0.98f)
                    .clip(RadarShape.control)
                    .background(colors.surfaceSecondary)
                    .border(1.dp, colors.rule, RadarShape.control)
                    .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onAddPdfs)
                    .padding(horizontal = RadarSpacing.md, vertical = if (pdfs.isEmpty()) 18.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (pdfs.isEmpty()) RadarIcons.Upload else RadarIcons.Plus, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(RadarSpacing.sm))
                Column {
                    Text(if (pdfs.isEmpty()) "Choose PDFs" else "Add more PDFs", style = type.title.copy(fontSize = type.bodySmall.fontSize), color = colors.ink)
                    if (pdfs.isEmpty()) Text("Up to $MAX_PDFS papers, 25 MB each", style = type.mono, color = colors.ink3)
                }
            }
        }
        if (pickNote != null) {
            Text(pickNote, style = type.bodySmall, color = colors.accent, modifier = Modifier.padding(top = RadarSpacing.sm))
        }
        Spacer(Modifier.height(RadarSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Also find related papers", style = type.bodySmall, color = colors.ink)
                Text(
                    if (includeRelated) "Adds papers from the literature for context" else "Only your PDFs are used",
                    style = type.mono,
                    color = colors.ink3,
                )
            }
            Switch(
                checked = includeRelated,
                onCheckedChange = onIncludeRelatedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.surface,
                    checkedTrackColor = colors.accent,
                    uncheckedThumbColor = colors.ink3,
                    uncheckedTrackColor = colors.surfaceSecondary,
                ),
            )
        }
    }
}

@Composable
private fun PrivacyNote(modifier: Modifier = Modifier) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RadarShape.card)
            .border(1.dp, colors.rule, RadarShape.card)
            .padding(RadarSpacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(RadarIcons.Lock, contentDescription = null, tint = colors.ink2, modifier = Modifier.padding(top = 2.dp).size(18.dp))
        Spacer(Modifier.width(RadarSpacing.md))
        Column {
            Text("YOUR FILES", style = type.label, color = colors.ink2)
            Spacer(Modifier.height(4.dp))
            Text(
                "PDFs are read on the server to build this map, then discarded. The map is visible only to your account.",
                style = type.bodySmall,
                color = colors.ink,
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
