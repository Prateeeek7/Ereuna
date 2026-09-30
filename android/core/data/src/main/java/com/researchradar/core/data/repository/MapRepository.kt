package com.researchradar.core.data.repository

import com.researchradar.core.model.CreateMapResponse
import com.researchradar.core.model.JobEvent
import com.researchradar.core.model.MapFilters
import com.researchradar.core.model.Paper
import com.researchradar.core.model.ResearchMap
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for research map operations, job tracking, and offline persistence.
 */
interface MapRepository {

    /** Create a new research map or return a cached one. */
    suspend fun createMap(topic: String, filters: MapFilters): CreateMapResponse

    /** Stream progress events for an active background job via SSE. */
    fun listenJobEvents(jobId: String): Flow<JobEvent>

    /** Get a complete research map by ID (fetches network, caches in Room, falls back to Room). */
    suspend fun getMap(mapId: String): ResearchMap

    /** Get a single paper by ID. */
    suspend fun getPaper(paperId: String): Paper

    /** Observe all saved maps for the Library screen. */
    fun observeSavedMaps(): Flow<List<ResearchMap>>

    /** Observe whether a map is bookmarked. */
    fun observeIsMapSaved(mapId: String): Flow<Boolean>

    /** Toggle map bookmark status. */
    suspend fun toggleSaveMap(map: ResearchMap)

    /** Observe all saved papers for the Library screen. */
    fun observeSavedPapers(): Flow<List<Paper>>

    /** Observe whether a paper is bookmarked. */
    fun observeIsPaperSaved(paperId: String): Flow<Boolean>

    /** Toggle paper bookmark status. */
    suspend fun toggleSavePaper(paper: Paper, mapId: String = "")

    /** Get recent search topics from local storage. */
    suspend fun getRecentTopics(): List<String>

    /** Save a topic to recent searches. */
    suspend fun saveRecentTopic(topic: String)

    /** Clear recent searches. */
    suspend fun clearRecentTopics()

    /** Clear all cached maps, papers, and search history from local database. */
    suspend fun clearAllCache() {}

    /** Replaces the local library with the account's library on the server. */
    suspend fun syncLibrary() {}

    /** Observe total number of saved maps in local storage. */
    fun observeSavedMapCount(): Flow<Int> = kotlinx.coroutines.flow.flowOf(0)

    /** Observe total number of saved papers in local storage. */
    fun observeSavedPaperCount(): Flow<Int> = kotlinx.coroutines.flow.flowOf(0)

    /** Fetch precomputed citation network graph. */
    suspend fun getMapGraph(mapId: String): com.researchradar.core.model.GraphData

    /** Request export for a research map. */
    suspend fun exportMap(mapId: String, format: String): com.researchradar.core.model.ExportResponse
}
