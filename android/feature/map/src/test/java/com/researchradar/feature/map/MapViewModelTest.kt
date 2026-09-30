package com.researchradar.feature.map

import androidx.lifecycle.SavedStateHandle
import com.researchradar.core.data.repository.MapRepository
import com.researchradar.core.model.CreateMapResponse
import com.researchradar.core.model.JobEvent
import com.researchradar.core.model.MapFilters
import com.researchradar.core.model.MapStats
import com.researchradar.core.model.Paper
import com.researchradar.core.model.ResearchMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val isSavedFlow = MutableStateFlow(false)
    private var toggledMap: ResearchMap? = null

    private val sampleMap = ResearchMap(
        id = "map-123",
        topic = "Low-leakage SRAM using FinFET",
        normalizedTopic = "low leakage sram finfet",
        stats = MapStats(totalPapers = 12, fullTextPapers = 10),
        synthesis = com.researchradar.core.model.Synthesis(text = "Synthesis text.", citationIds = listOf("p1")),
        papers = listOf(
            Paper(
                id = "p1", title = "Paper 1", year = 2023, citationCount = 100,
                extraction = com.researchradar.core.model.PaperExtraction(
                    paperId = "p1",
                    metrics = listOf(
                        com.researchradar.core.model.Metric(
                            name = "latency", value = 12.0, unit = "ms",
                            evidence = com.researchradar.core.model.Evidence(quote = "latency of 12 ms"),
                        ),
                    ),
                ),
            ),
            Paper(id = "p2", title = "Paper 2", year = 2022, citationCount = 200),
        ),
    )

    private val fakeRepository = object : MapRepository {
        override suspend fun createMap(topic: String, filters: MapFilters): CreateMapResponse =
            CreateMapResponse("job-1", "map-123", "ready")

        override fun listenJobEvents(jobId: String): Flow<JobEvent> = emptyFlow()

        override suspend fun getMap(mapId: String): ResearchMap = sampleMap

        override suspend fun getPaper(paperId: String): Paper =
            Paper(id = paperId, title = "Paper", year = 2023)

        override fun observeSavedMaps(): Flow<List<ResearchMap>> = emptyFlow()
        override fun observeIsMapSaved(mapId: String): Flow<Boolean> = isSavedFlow
        override suspend fun toggleSaveMap(map: ResearchMap) {
            toggledMap = map
            isSavedFlow.value = !isSavedFlow.value
        }
        override fun observeSavedPapers(): Flow<List<Paper>> = emptyFlow()
        override fun observeIsPaperSaved(paperId: String): Flow<Boolean> = emptyFlow()
        override suspend fun toggleSavePaper(paper: Paper, mapId: String) {}
        override suspend fun getRecentTopics(): List<String> = emptyList()
        override suspend fun saveRecentTopic(topic: String) {}
        override suspend fun clearRecentTopics() {}
        override suspend fun getMapGraph(mapId: String): com.researchradar.core.model.GraphData = com.researchradar.core.model.GraphData()
        override suspend fun exportMap(mapId: String, format: String): com.researchradar.core.model.ExportResponse =
            com.researchradar.core.model.ExportResponse(downloadUrl = "/v1/maps/$mapId/download", format = format)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialLoadPopulatesMap() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("mapId" to "map-123"))
        val viewModel = MapViewModel(savedStateHandle, fakeRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.researchMap)
        assertEquals("Low-leakage SRAM using FinFET", state.researchMap?.topic)
        assertEquals(2, state.researchMap?.papers?.size)
    }

    @Test
    fun testTabSelection() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("mapId" to "map-123"))
        val viewModel = MapViewModel(savedStateHandle, fakeRepository)

        advanceUntilIdle()

        assertEquals(MapTab.OVERVIEW, viewModel.uiState.value.selectedTab)
        viewModel.selectTab(MapTab.COMPARE)
        assertEquals(MapTab.COMPARE, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun testPaperFilterAndSort() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("mapId" to "map-123"))
        val viewModel = MapViewModel(savedStateHandle, fakeRepository)

        advanceUntilIdle()

        viewModel.setPaperFilter("FULL_TEXT")
        assertEquals("FULL_TEXT", viewModel.uiState.value.activePaperFilter)

        viewModel.setPaperSort(PaperSort.CITATIONS)
        assertEquals(PaperSort.CITATIONS, viewModel.uiState.value.activeSort)
    }

    @Test
    fun testCompareSelection() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("mapId" to "map-123"))
        val viewModel = MapViewModel(savedStateHandle, fakeRepository)

        advanceUntilIdle()

        viewModel.toggleSelectPaperForCompare("p1")
        assertTrue(viewModel.uiState.value.selectedPaperIdsForCompare.contains("p1"))

        viewModel.toggleSelectPaperForCompare("p1")
        assertFalse(viewModel.uiState.value.selectedPaperIdsForCompare.contains("p1"))
    }

    @Test
    fun testColumnPickerToggle() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("mapId" to "map-123"))
        val viewModel = MapViewModel(savedStateHandle, fakeRepository)

        advanceUntilIdle()

        // Defaults are derived from the extracted metrics, not a fixed domain list.
        assertEquals(null, viewModel.uiState.value.visibleCompareColumns)
        val columns = com.researchradar.core.model.buildCompareColumns(
            sampleMap.papers.filter { it.extraction != null },
        )
        val latencyId = columns.first { it.title == "Latency" }.id
        val defaults = com.researchradar.core.model.defaultCompareColumnIds(columns)
        // Only one paper reports latency, so the per-paper results column stands in for it.
        assertFalse(defaults.contains(latencyId))
        assertTrue(defaults.contains("results"))
        assertFalse(columns.any { it.id == "node" }) // no paper reports a process node

        viewModel.toggleCompareColumn(latencyId)
        assertTrue(viewModel.uiState.value.visibleCompareColumns!!.contains(latencyId))

        viewModel.toggleCompareColumn(latencyId)
        assertFalse(viewModel.uiState.value.visibleCompareColumns!!.contains(latencyId))
    }

    @Test
    fun testToggleBookmarkCallsRepository() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("mapId" to "map-123"))
        val viewModel = MapViewModel(savedStateHandle, fakeRepository)

        advanceUntilIdle()

        viewModel.toggleBookmark()
        advanceUntilIdle()

        assertNotNull(toggledMap)
        assertEquals("map-123", toggledMap?.id)
        assertTrue(viewModel.uiState.value.isBookmarked)
    }

    @Test
    fun testExportDialogControls() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("mapId" to "map-123"))
        val viewModel = MapViewModel(savedStateHandle, fakeRepository)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isExportDialogOpen)

        viewModel.openExportDialog()
        assertTrue(viewModel.uiState.value.isExportDialogOpen)

        viewModel.closeExportDialog()
        assertFalse(viewModel.uiState.value.isExportDialogOpen)
    }

    @Test
    fun testExportHelperFormats() {
        val md = ExportHelper.generateMarkdown(sampleMap)
        assertTrue(md.contains("# Research Map: Low-leakage SRAM using FinFET"))
        assertTrue(md.contains("## Synthesis"))

        assertFalse(md.contains("Academic Publication"))
        assertFalse(ExportHelper.generateMarkdown(sampleMap.copy(synthesis = com.researchradar.core.model.Synthesis())).contains("## Synthesis"))

        val bib = ExportHelper.generateBibTeX(sampleMap)
        assertTrue(bib.contains("@article{anon2023paper,"))
        assertTrue(bib.contains("Paper 1"))
        assertFalse(bib.contains("Unknown Author"))

        val csv = ExportHelper.generateCsv(sampleMap)
        assertTrue(csv.contains("\"Title\",\"Short Label\",\"Authors\",\"Year\""))
        assertTrue(csv.contains("Paper 1"))
    }
}

