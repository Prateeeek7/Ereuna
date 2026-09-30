package com.researchradar.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Settings the user chooses once and expects to persist. */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultPaperCount: Int = 25,
    val openAccessOnly: Boolean = false,
    val minCitations: Int = 0,
)

private val Context.userPrefsStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/** Persisted user settings. */
interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun current(): UserPreferences = preferences.first()
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDefaultPaperCount(count: Int)
    suspend fun setOpenAccessOnly(enabled: Boolean)
    suspend fun setMinCitations(min: Int)
}

@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserPreferencesRepository {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val paperCount = intPreferencesKey("default_paper_count")
        val openAccess = booleanPreferencesKey("open_access_only")
        val minCitations = intPreferencesKey("min_citations")
    }

    override val preferences: Flow<UserPreferences> = context.userPrefsStore.data.map { p ->
        UserPreferences(
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            defaultPaperCount = p[Keys.paperCount] ?: 25,
            openAccessOnly = p[Keys.openAccess] ?: false,
            minCitations = p[Keys.minCitations] ?: 0,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) { context.userPrefsStore.edit { it[Keys.theme] = mode.name } }
    override suspend fun setDefaultPaperCount(count: Int) { context.userPrefsStore.edit { it[Keys.paperCount] = count } }
    override suspend fun setOpenAccessOnly(enabled: Boolean) { context.userPrefsStore.edit { it[Keys.openAccess] = enabled } }
    override suspend fun setMinCitations(min: Int) { context.userPrefsStore.edit { it[Keys.minCitations] = min } }
}
