package com.researchradar.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_maps")
data class SavedMapEntity(
    @PrimaryKey val id: String,
    val topic: String,
    val normalizedTopic: String,
    val createdAt: String,
    val updatedAt: String,
    val totalPapers: Int,
    val fullTextPapers: Int,
    val mapJson: String,
    val isBookmarked: Boolean = true,
    val savedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "saved_papers")
data class SavedPaperEntity(
    @PrimaryKey val id: String,
    val mapId: String,
    val title: String,
    val shortLabel: String,
    val year: Int,
    val venue: String,
    val citationCount: Int,
    val hasFullText: Boolean,
    val paperJson: String,
    val savedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val topic: String,
    val searchedAt: Long = System.currentTimeMillis(),
)
