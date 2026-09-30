package com.researchradar.core.data.repository

import com.researchradar.core.data.database.dao.MapDao
import com.researchradar.core.data.database.dao.PaperDao
import com.researchradar.core.data.database.dao.RecentSearchDao
import com.researchradar.core.data.database.entity.RecentSearchEntity
import com.researchradar.core.data.database.entity.SavedMapEntity
import com.researchradar.core.data.database.entity.SavedPaperEntity
import com.researchradar.core.model.CreateMapRequest
import com.researchradar.core.model.CreateMapResponse
import com.researchradar.core.model.JobEvent
import com.researchradar.core.model.MapFilters
import com.researchradar.core.model.Paper
import com.researchradar.core.model.ResearchMap
import com.researchradar.core.network.RadarApi
import com.researchradar.core.network.sse.JobEventSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultMapRepository @Inject constructor(
    private val api: RadarApi,
    private val jobEventSource: JobEventSource,
    private val mapDao: MapDao,
    private val paperDao: PaperDao,
    private val recentSearchDao: RecentSearchDao,
    private val json: Json,
) : MapRepository {

    override suspend fun createMap(topic: String, filters: MapFilters): CreateMapResponse {
        saveRecentTopic(topic)
        val request = CreateMapRequest(topic = topic, filters = filters)
        return api.createMap(request)
    }

    override fun listenJobEvents(jobId: String): Flow<JobEvent> {
        return jobEventSource.listen(jobId)
    }

    override suspend fun getMap(mapId: String): ResearchMap {
        return try {
            val map = api.getMap(mapId)
            // Cache in Room for offline access, keeping the map in the library if it is there.
            val bookmarked = mapDao.getMapById(map.id)?.isBookmarked == true
            mapDao.insertMap(mapEntity(map, bookmarked))
            map
        } catch (e: Exception) {
            // Offline fallback from local database
            val cached = mapDao.getMapById(mapId)
            if (cached != null) {
                json.decodeFromString<ResearchMap>(cached.mapJson)
            } else {
                throw e
            }
        }
    }

    override suspend fun getPaper(paperId: String): Paper {
        return try {
            val paper = api.getPaper(paperId)
            paper
        } catch (e: Exception) {
            val cached = paperDao.getPaperById(paperId)
            if (cached != null) {
                json.decodeFromString<Paper>(cached.paperJson)
            } else {
                throw e
            }
        }
    }

    override fun observeSavedMaps(): Flow<List<ResearchMap>> {
        return mapDao.observeSavedMaps().map { entities ->
            entities.mapNotNull { entity ->
                try {
                    json.decodeFromString<ResearchMap>(entity.mapJson)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    override fun observeIsMapSaved(mapId: String): Flow<Boolean> {
        return mapDao.observeIsBookmarked(mapId)
    }

    override suspend fun toggleSaveMap(map: ResearchMap) {
        val isSaved = mapDao.observeIsBookmarked(map.id).first()
        // The library lives on the server (shared with the website); this device keeps a copy.
        val response = if (isSaved) api.removeSavedMap(map.id) else api.saveMap(map.id)
        check(response.isSuccessful || response.code() == 404) { "Library update failed (${response.code()})" }
        if (isSaved) mapDao.setBookmarked(map.id, false) else mapDao.insertMap(mapEntity(map, bookmarked = true))
    }

    override fun observeSavedPapers(): Flow<List<Paper>> {
        return paperDao.observeSavedPapers().map { entities ->
            entities.mapNotNull { entity ->
                try {
                    json.decodeFromString<Paper>(entity.paperJson)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    override fun observeIsPaperSaved(paperId: String): Flow<Boolean> {
        return paperDao.observeIsPaperSaved(paperId)
    }

    override suspend fun toggleSavePaper(paper: Paper, mapId: String) {
        val isSaved = paperDao.observeIsPaperSaved(paper.id).first()
        val response = if (isSaved) api.removeSavedPaper(paper.id) else api.savePaper(paper.id)
        check(response.isSuccessful || response.code() == 404) { "Library update failed (${response.code()})" }
        if (isSaved) paperDao.deletePaperById(paper.id) else paperDao.insertPaper(paperEntity(paper, mapId))
    }

    override suspend fun syncLibrary() {
        val maps = api.listSavedMaps()
        val papers = api.listSavedPapers()

        val serverMapIds = maps.map { it.id }.toSet()
        mapDao.bookmarkedIds().filterNot { it in serverMapIds }.forEach { mapDao.setBookmarked(it, false) }
        // The server lists newest first; keep that order locally.
        val now = System.currentTimeMillis()
        maps.forEachIndexed { i, map -> mapDao.insertMap(mapEntity(map, bookmarked = true).copy(savedAt = now - i)) }

        val serverPaperIds = papers.map { it.id }.toSet()
        paperDao.savedIds().filterNot { it in serverPaperIds }.forEach { paperDao.deletePaperById(it) }
        papers.forEachIndexed { i, paper ->
            val mapId = paperDao.getPaperById(paper.id)?.mapId.orEmpty()
            paperDao.insertPaper(paperEntity(paper, mapId).copy(savedAt = now - i))
        }
    }

    private fun mapEntity(map: ResearchMap, bookmarked: Boolean) = SavedMapEntity(
        id = map.id,
        topic = map.topic,
        normalizedTopic = map.normalizedTopic,
        createdAt = map.createdAt,
        updatedAt = map.updatedAt,
        totalPapers = map.stats.totalPapers,
        fullTextPapers = map.stats.fullTextPapers,
        mapJson = json.encodeToString(map),
        isBookmarked = bookmarked,
    )

    private fun paperEntity(paper: Paper, mapId: String) = SavedPaperEntity(
        id = paper.id,
        mapId = mapId,
        title = paper.title,
        shortLabel = paper.shortLabel,
        year = paper.year,
        venue = paper.venue,
        citationCount = paper.citationCount,
        hasFullText = paper.hasFullText,
        paperJson = json.encodeToString(paper),
    )

    override suspend fun getRecentTopics(): List<String> {
        return recentSearchDao.getRecentTopics()
    }

    override suspend fun saveRecentTopic(topic: String) {
        val clean = topic.trim()
        if (clean.isNotEmpty()) {
            recentSearchDao.insertTopic(RecentSearchEntity(topic = clean))
        }
    }

    override suspend fun clearRecentTopics() {
        recentSearchDao.clearRecentTopics()
    }

    override suspend fun clearAllCache() {
        mapDao.clearAllMaps()
        paperDao.clearAllPapers()
        recentSearchDao.clearRecentTopics()
    }

    override fun observeSavedMapCount(): Flow<Int> {
        return mapDao.observeMapCount()
    }

    override fun observeSavedPaperCount(): Flow<Int> {
        return paperDao.observePaperCount()
    }

    override suspend fun getMapGraph(mapId: String): com.researchradar.core.model.GraphData {
        return try {
            api.getMapGraph(mapId)
        } catch (e: Exception) {
            // Fallback to local saved map graph if offline
            try {
                val entity = mapDao.getMapById(mapId)
                if (entity != null) {
                    val savedMap = json.decodeFromString<ResearchMap>(entity.mapJson)
                    savedMap.graph ?: com.researchradar.core.model.GraphData()
                } else {
                    com.researchradar.core.model.GraphData()
                }
            } catch (localEx: Exception) {
                com.researchradar.core.model.GraphData()
            }
        }
    }

    override suspend fun exportMap(mapId: String, format: String): com.researchradar.core.model.ExportResponse {
        return api.exportMap(mapId, com.researchradar.core.model.ExportRequest(format = format))
    }
}
