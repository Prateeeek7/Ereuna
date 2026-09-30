package com.researchradar.feature.search

import androidx.lifecycle.SavedStateHandle
import com.researchradar.core.data.repository.MapRepository
import com.researchradar.core.model.CreateMapResponse
import com.researchradar.core.model.DoneEventData
import com.researchradar.core.model.ErrorEventData
import com.researchradar.core.model.JobEvent
import com.researchradar.core.model.LogEventData
import com.researchradar.core.model.MapFilters
import com.researchradar.core.model.Paper
import com.researchradar.core.model.PaperSelectedEventData
import com.researchradar.core.model.ResearchMap
import com.researchradar.core.model.StageEventData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RunningJobViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val eventFlow = MutableSharedFlow<JobEvent>()

    private val fakeRepository = object : MapRepository {
        override suspend fun createMap(topic: String, filters: MapFilters): CreateMapResponse =
            CreateMapResponse("job-test", "map-test", "queued")

        override fun listenJobEvents(jobId: String): Flow<JobEvent> = eventFlow.asSharedFlow()

        override suspend fun getMap(mapId: String): ResearchMap =
            ResearchMap(id = mapId, topic = "Test Topic", normalizedTopic = "test topic")

        override suspend fun getPaper(paperId: String): Paper =
            Paper(id = paperId, title = "Test Paper", year = 2023)

        override fun observeSavedMaps(): Flow<List<ResearchMap>> = flowOf(emptyList())
        override fun observeIsMapSaved(mapId: String): Flow<Boolean> = flowOf(false)
        override suspend fun toggleSaveMap(map: ResearchMap) {}
        override fun observeSavedPapers(): Flow<List<Paper>> = flowOf(emptyList())
        override fun observeIsPaperSaved(paperId: String): Flow<Boolean> = flowOf(false)
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
    fun testStageEventUpdatesState() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("jobId" to "job-100", "topic" to "SRAM FinFET"))
        val viewModel = RunningJobViewModel(savedStateHandle, fakeRepository)

        advanceUntilIdle()

        eventFlow.emit(JobEvent.Stage(StageEventData(stage = 3, name = "Ranking candidates", progress = 0.6f)))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.currentStage)
        assertEquals("Ranking candidates", state.stageName)
        assertEquals(0.6f, state.progress, 0.001f)
    }

    @Test
    fun testLogEventParsesCounts() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("jobId" to "job-100"))
        val viewModel = RunningJobViewModel(savedStateHandle, fakeRepository)

        advanceUntilIdle()

        eventFlow.emit(JobEvent.Log(LogEventData("Retrieved 187 unique papers after DOI and title deduplication.")))
        eventFlow.emit(JobEvent.Log(LogEventData("Full text parsed (pymupdf): 19 papers. 6 used abstracts.")))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(187, state.candidateCount)
        assertEquals(19, state.fullTextCount)
        assertTrue(state.logs.any { it.second.contains("Retrieved 187 unique papers") })
    }

    @Test
    fun testPaperSelectedEventAppends() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("jobId" to "job-100"))
        val viewModel = RunningJobViewModel(savedStateHandle, fakeRepository)

        advanceUntilIdle()

        eventFlow.emit(
            JobEvent.PaperSelected(
                PaperSelectedEventData(
                    paperId = "p1",
                    title = "Low Power FinFET SRAM",
                    shortLabel = "Kim '23",
                    score = 0.95,
                    citations = 142,
                    year = 2023,
                )
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.selectedPapers.size)
        assertEquals("p1", state.selectedPapers[0].paperId)
        assertEquals("Kim '23", state.selectedPapers[0].shortLabel)
    }

    @Test
    fun testDoneEventMarksComplete() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("jobId" to "job-100"))
        val viewModel = RunningJobViewModel(savedStateHandle, fakeRepository)

        advanceUntilIdle()

        eventFlow.emit(
            JobEvent.Done(
                DoneEventData(
                    mapId = "map-done-1",
                    papersCount = 25,
                    fullTextCount = 18,
                    verificationPassRate = 0.98,
                )
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isComplete)
        assertEquals("map-done-1", state.completedMapId)
        assertEquals(1.0f, state.progress, 0.001f)
    }

    @Test
    fun testErrorEventSetsErrorMessage() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("jobId" to "job-100"))
        val viewModel = RunningJobViewModel(savedStateHandle, fakeRepository)

        advanceUntilIdle()

        eventFlow.emit(
            JobEvent.Error(
                ErrorEventData(
                    error = "RetrievalError",
                    message = "Rate limit reached on external API",
                )
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertEquals("Rate limit reached on external API", state.errorMessage)
    }

    @Test
    fun testCancelJobSetsCancelled() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("jobId" to "job-100"))
        val viewModel = RunningJobViewModel(savedStateHandle, fakeRepository)

        advanceUntilIdle()
        viewModel.cancelJob()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCancelled)
    }
}
