package com.researchradar.feature.graph

import androidx.lifecycle.SavedStateHandle
import com.researchradar.core.data.repository.MapRepository
import com.researchradar.core.model.CreateMapResponse
import com.researchradar.core.model.ExportResponse
import com.researchradar.core.model.GraphData
import com.researchradar.core.model.GraphEdge
import com.researchradar.core.model.GraphNode
import com.researchradar.core.model.JobEvent
import com.researchradar.core.model.MapFilters
import com.researchradar.core.model.Paper
import com.researchradar.core.model.ResearchMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GraphViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sampleNodes = listOf(
        GraphNode(paperId = "p1", x = 100f, y = 200f, size = 15f, year = 2021, inMap = true, label = "Smith+21", citationCount = 120),
        GraphNode(paperId = "p2", x = 300f, y = 400f, size = 12f, year = 2019, inMap = true, label = "Brown 19", citationCount = 85),
        GraphNode(paperId = "foundational_1", x = 500f, y = 500f, size = 25f, year = 2000, inMap = false, label = "Hu et al.", citationCount = 1800),
    )

    private val sampleEdges = listOf(
        GraphEdge(source = "p1", target = "p2", weight = 1.0f),
        GraphEdge(source = "p2", target = "foundational_1", weight = 1.0f),
    )

    private val sampleGraph = GraphData(nodes = sampleNodes, edges = sampleEdges)

    private val sampleMap = ResearchMap(
        id = "map-graph-1",
        topic = "FinFET SRAM Design",
        normalizedTopic = "finfet sram design",
        papers = listOf(
            Paper(id = "p1", title = "Sub-threshold FinFET SRAM", year = 2021, citationCount = 120, shortLabel = "Smith+21"),
            Paper(id = "p2", title = "High-Speed FinFET Cache", year = 2019, citationCount = 85, shortLabel = "Brown 19"),
        ),
        graph = sampleGraph,
    )

    private var shouldFail = false

    private val fakeRepository = object : MapRepository {
        override suspend fun createMap(topic: String, filters: MapFilters): CreateMapResponse =
            CreateMapResponse("job-1", "map-graph-1", "ready")

        override fun listenJobEvents(jobId: String): Flow<JobEvent> = emptyFlow()

        override suspend fun getMap(mapId: String): ResearchMap {
            if (shouldFail) throw RuntimeException("Failed to fetch map")
            return sampleMap
        }

        override suspend fun getPaper(paperId: String): Paper =
            Paper(id = paperId, title = "Paper", year = 2021)

        override fun observeSavedMaps(): Flow<List<ResearchMap>> = emptyFlow()
        override fun observeIsMapSaved(mapId: String): Flow<Boolean> = emptyFlow()
        override suspend fun toggleSaveMap(map: ResearchMap) {}
        override fun observeSavedPapers(): Flow<List<Paper>> = emptyFlow()
        override fun observeIsPaperSaved(paperId: String): Flow<Boolean> = emptyFlow()
        override suspend fun toggleSavePaper(paper: Paper, mapId: String) {}
        override suspend fun getRecentTopics(): List<String> = emptyList()
        override suspend fun saveRecentTopic(topic: String) {}
        override suspend fun clearRecentTopics() {}
        override suspend fun getMapGraph(mapId: String): GraphData = sampleGraph
        override suspend fun exportMap(mapId: String, format: String): ExportResponse =
            ExportResponse(downloadUrl = "/v1/maps/$mapId/download", format = format)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        shouldFail = false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialLoadPopulatesGraphAndPapers() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("mapId" to "map-graph-1"))
        val viewModel = GraphViewModel(fakeRepository, savedStateHandle)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals("FinFET SRAM Design", state.topic)
        assertEquals(3, state.graphData.nodes.size)
        assertEquals(2, state.graphData.edges.size)
        assertEquals(2, state.papers.size)
    }

    @Test
    fun testSelectNodeUpdatesState() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("mapId" to "map-graph-1"))
        val viewModel = GraphViewModel(fakeRepository, savedStateHandle)

        advanceUntilIdle()

        val targetNode = sampleNodes[0]
        viewModel.selectNode(targetNode)

        val state = viewModel.uiState.value
        assertEquals("p1", state.selectedNode?.paperId)
        assertNotNull(state.selectedPaper)
        assertEquals("Sub-threshold FinFET SRAM", state.selectedPaper?.title)
    }

    @Test
    fun testToggleInMapOnly() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("mapId" to "map-graph-1"))
        val viewModel = GraphViewModel(fakeRepository, savedStateHandle)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.inMapOnly)
        viewModel.toggleInMapOnly()
        assertTrue(viewModel.uiState.value.inMapOnly)
        viewModel.toggleInMapOnly()
        assertFalse(viewModel.uiState.value.inMapOnly)
    }

    @Test
    fun testDismissSelectedNode() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("mapId" to "map-graph-1"))
        val viewModel = GraphViewModel(fakeRepository, savedStateHandle)

        advanceUntilIdle()

        viewModel.selectNode(sampleNodes[0])
        assertNotNull(viewModel.uiState.value.selectedNode)

        viewModel.dismissSelectedNode()
        assertNull(viewModel.uiState.value.selectedNode)
        assertNull(viewModel.uiState.value.selectedPaper)
    }

    @Test
    fun testNetworkErrorSetsErrorMessage() = runTest(testDispatcher) {
        shouldFail = true
        val savedStateHandle = SavedStateHandle(mapOf("mapId" to "map-graph-1"))
        val viewModel = GraphViewModel(fakeRepository, savedStateHandle)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage?.contains("Failed to fetch map") == true)
    }
}
