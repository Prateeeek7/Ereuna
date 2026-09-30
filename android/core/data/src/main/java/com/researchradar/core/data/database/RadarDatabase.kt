package com.researchradar.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.researchradar.core.data.database.dao.MapDao
import com.researchradar.core.data.database.dao.PaperDao
import com.researchradar.core.data.database.dao.RecentSearchDao
import com.researchradar.core.data.database.entity.RecentSearchEntity
import com.researchradar.core.data.database.entity.SavedMapEntity
import com.researchradar.core.data.database.entity.SavedPaperEntity

@Database(
    entities = [
        SavedMapEntity::class,
        SavedPaperEntity::class,
        RecentSearchEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class RadarDatabase : RoomDatabase() {
    abstract fun mapDao(): MapDao
    abstract fun paperDao(): PaperDao
    abstract fun recentSearchDao(): RecentSearchDao
}
