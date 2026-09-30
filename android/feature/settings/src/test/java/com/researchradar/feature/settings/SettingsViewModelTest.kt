package com.researchradar.feature.settings

import com.researchradar.core.data.repository.MapRepository
import com.researchradar.core.model.CreateMapResponse
import com.researchradar.core.model.JobEvent
import com.researchradar.core.model.MapFilters
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mapCountFlow = MutableStateFlow(3)
    private val paperCountFlow = MutableStateFlow(12)
    private var clearedRecentTopicsCalled = false
    private var clearedAllCacheCalled = false

    private val prefsFlow = MutableStateFlow(com.researchradar.core.data.preferences.UserPreferences())
    private val fakePrefs = object : com.researchradar.core.data.preferences.UserPreferencesRepository {
        override val preferences = prefsFlow
        override suspend fun setThemeMode(mode: com.researchradar.core.data.preferences.ThemeMode) { prefsFlow.value = prefsFlow.value.copy(themeMode = mode) }
        override suspend fun setDefaultPaperCount(count: Int) { prefsFlow.value = prefsFlow.value.copy(defaultPaperCount = count) }
        override suspend fun setOpenAccessOnly(enabled: Boolean) { prefsFlow.value = prefsFlow.value.copy(openAccessOnly = enabled) }
        override suspend fun setMinCitations(min: Int) { prefsFlow.value = prefsFlow.value.copy(minCitations = min) }
    }

    private val fakeRepository = object : MapRepository {
        override suspend fun createMap(topic: String, filters: MapFilters): CreateMapResponse =
            CreateMapResponse("job", "map", "ready")

        override fun listenJobEvents(jobId: String): Flow<JobEvent> = emptyFlow()
        override suspend fun getMap(mapId: String): ResearchMap = throw UnsupportedOperationException()
        override suspend fun getPaper(paperId: String): Paper = throw UnsupportedOperationException()
        override fun observeSavedMaps(): Flow<List<ResearchMap>> = emptyFlow()
        override fun observeIsMapSaved(mapId: String): Flow<Boolean> = emptyFlow()
        override suspend fun toggleSaveMap(map: ResearchMap) {}
        override fun observeSavedPapers(): Flow<List<Paper>> = emptyFlow()
        override fun observeIsPaperSaved(paperId: String): Flow<Boolean> = emptyFlow()
        override suspend fun toggleSavePaper(paper: Paper, mapId: String) {}
        override suspend fun getRecentTopics(): List<String> = emptyList()
        override suspend fun saveRecentTopic(topic: String) {}
        override suspend fun clearRecentTopics() {
            clearedRecentTopicsCalled = true
        }

        override suspend fun clearAllCache() {
            clearedAllCacheCalled = true
        }

        override fun observeSavedMapCount(): Flow<Int> = mapCountFlow
        override fun observeSavedPaperCount(): Flow<Int> = paperCountFlow

        override suspend fun getMapGraph(mapId: String): com.researchradar.core.model.GraphData =
            com.researchradar.core.model.GraphData()

        override suspend fun exportMap(mapId: String, format: String): com.researchradar.core.model.ExportResponse =
            com.researchradar.core.model.ExportResponse(downloadUrl = "", format = format)
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
    fun testSettingsInitialValuesAndCounts() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(fakeRepository, fakePrefs)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ThemePreference.SYSTEM, state.themePreference)
        assertEquals(25, state.defaultPaperCount)
        assertEquals(false, state.openAccessOnly)
        assertEquals(0, state.minCitations)
        assertEquals(3, state.savedMapCount)
        assertEquals(12, state.savedPaperCount)
    }

    @Test
    fun testSetPreferences() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(fakeRepository, fakePrefs)
        advanceUntilIdle()

        viewModel.setThemePreference(ThemePreference.DARK)
        advanceUntilIdle()
        assertEquals(ThemePreference.DARK, viewModel.uiState.value.themePreference)

        viewModel.setDefaultPaperCount(50)
        advanceUntilIdle()
        assertEquals(50, viewModel.uiState.value.defaultPaperCount)

        viewModel.setOpenAccessOnly(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.openAccessOnly)

        viewModel.setMinCitations(10)
        advanceUntilIdle()
        assertEquals(10, viewModel.uiState.value.minCitations)
    }

    @Test
    fun testClearOperations() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(fakeRepository, fakePrefs)
        advanceUntilIdle()

        viewModel.clearRecentTopics()
        advanceUntilIdle()
        assertTrue(clearedRecentTopicsCalled)
        assertNotNull(viewModel.uiState.value.userMessage)

        viewModel.clearAllCache()
        advanceUntilIdle()
        assertTrue(clearedAllCacheCalled)
    }
}
