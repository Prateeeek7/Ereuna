package com.researchradar.core.data.di

import android.content.Context
import androidx.room.Room
import com.researchradar.core.data.database.RadarDatabase
import com.researchradar.core.data.database.dao.MapDao
import com.researchradar.core.data.database.dao.PaperDao
import com.researchradar.core.data.database.dao.RecentSearchDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideRadarDatabase(
        @ApplicationContext context: Context,
    ): RadarDatabase {
        return Room.databaseBuilder(
            context,
            RadarDatabase::class.java,
            "research_radar.db",
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideMapDao(database: RadarDatabase): MapDao = database.mapDao()

    @Provides
    fun providePaperDao(database: RadarDatabase): PaperDao = database.paperDao()

    @Provides
    fun provideRecentSearchDao(database: RadarDatabase): RecentSearchDao = database.recentSearchDao()
}
