package com.researchradar.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.researchradar.core.data.database.entity.RecentSearchEntity
import com.researchradar.core.data.database.entity.SavedMapEntity
import com.researchradar.core.data.database.entity.SavedPaperEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MapDao {
    /** Maps in the library. The same table also caches recently opened maps (isBookmarked = 0). */
    @Query("SELECT * FROM saved_maps WHERE isBookmarked = 1 ORDER BY savedAt DESC")
    fun observeSavedMaps(): Flow<List<SavedMapEntity>>

    @Query("SELECT id FROM saved_maps WHERE isBookmarked = 1")
    suspend fun bookmarkedIds(): List<String>

    @Query("SELECT * FROM saved_maps WHERE id = :mapId LIMIT 1")
    suspend fun getMapById(mapId: String): SavedMapEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMap(map: SavedMapEntity)

    @Query("DELETE FROM saved_maps WHERE id = :mapId")
    suspend fun deleteMapById(mapId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_maps WHERE id = :mapId AND isBookmarked = 1)")
    fun observeIsBookmarked(mapId: String): Flow<Boolean>

    @Query("UPDATE saved_maps SET isBookmarked = :isBookmarked WHERE id = :mapId")
    suspend fun setBookmarked(mapId: String, isBookmarked: Boolean)

    @Query("SELECT COUNT(*) FROM saved_maps WHERE isBookmarked = 1")
    fun observeMapCount(): Flow<Int>

    @Query("DELETE FROM saved_maps")
    suspend fun clearAllMaps()
}

@Dao
interface PaperDao {
    @Query("SELECT * FROM saved_papers ORDER BY savedAt DESC")
    fun observeSavedPapers(): Flow<List<SavedPaperEntity>>

    @Query("SELECT * FROM saved_papers WHERE id = :paperId LIMIT 1")
    suspend fun getPaperById(paperId: String): SavedPaperEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaper(paper: SavedPaperEntity)

    @Query("DELETE FROM saved_papers WHERE id = :paperId")
    suspend fun deletePaperById(paperId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_papers WHERE id = :paperId)")
    fun observeIsPaperSaved(paperId: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM saved_papers")
    fun observePaperCount(): Flow<Int>

    @Query("DELETE FROM saved_papers")
    suspend fun clearAllPapers()

    @Query("SELECT id FROM saved_papers")
    suspend fun savedIds(): List<String>
}

@Dao
interface RecentSearchDao {
    @Query("SELECT topic FROM recent_searches ORDER BY searchedAt DESC LIMIT 10")
    suspend fun getRecentTopics(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(entity: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE topic = :topic")
    suspend fun deleteTopic(topic: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearRecentTopics()
}
