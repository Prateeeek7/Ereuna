package com.researchradar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.researchradar.core.data.preferences.ThemeMode
import com.researchradar.core.data.preferences.UserPreferencesRepository
import com.researchradar.core.data.repository.MapRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ThemePreference(val label: String, val description: String, val mode: ThemeMode) {
    SYSTEM("Match system", "Follow your phone's light or dark setting", ThemeMode.SYSTEM),
    LIGHT("Paper", "Warm light background", ThemeMode.LIGHT),
    DARK("Night", "Dark background for low light", ThemeMode.DARK),
}

data class SettingsUiState(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val defaultPaperCount: Int = 25,
    val openAccessOnly: Boolean = false,
    val minCitations: Int = 0,
    val savedMapCount: Int = 0,
    val savedPaperCount: Int = 0,
    val accountName: String = "",
    val accountEmail: String = "",
    val userMessage: String? = null,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    private val preferences: UserPreferencesRepository,
    private val session: com.researchradar.core.data.session.SessionRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        session?.let { repo ->
            viewModelScope.launch {
                repo.state.collect { st ->
                    val signedIn = st as? com.researchradar.core.data.session.SessionState.SignedIn
                    _uiState.update {
                        it.copy(accountName = signedIn?.session?.name.orEmpty(), accountEmail = signedIn?.session?.email.orEmpty())
                    }
                }
            }
        }
        viewModelScope.launch {
            preferences.preferences.collect { p ->
                _uiState.update {
                    it.copy(
                        themePreference = ThemePreference.entries.first { t -> t.mode == p.themeMode },
                        defaultPaperCount = p.defaultPaperCount,
                        openAccessOnly = p.openAccessOnly,
                        minCitations = p.minCitations,
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(
                mapRepository.observeSavedMapCount(),
                mapRepository.observeSavedPaperCount(),
            ) { maps, papers ->
                Pair(maps, papers)
            }.collect { (maps, papers) ->
                _uiState.update { it.copy(savedMapCount = maps, savedPaperCount = papers) }
            }
        }
    }

    fun setThemePreference(pref: ThemePreference) {
        viewModelScope.launch { preferences.setThemeMode(pref.mode) }
    }

    fun setDefaultPaperCount(count: Int) {
        viewModelScope.launch { preferences.setDefaultPaperCount(count) }
    }

    fun setOpenAccessOnly(enabled: Boolean) {
        viewModelScope.launch { preferences.setOpenAccessOnly(enabled) }
    }

    fun setMinCitations(min: Int) {
        viewModelScope.launch { preferences.setMinCitations(min) }
    }

    fun clearRecentTopics() {
        viewModelScope.launch {
            mapRepository.clearRecentTopics()
            _uiState.update { it.copy(userMessage = "Search history cleared.") }
        }
    }

    fun clearAllCache() {
        viewModelScope.launch {
            mapRepository.clearAllCache()
            _uiState.update { it.copy(userMessage = "Local cache and saved maps cleared.") }
        }
    }

    fun signOut() {
        viewModelScope.launch { session?.signOut() }
    }

    /** Deletes the account for good; on success the app returns to the sign-in screen. */
    fun deleteAccount(password: String) {
        val repo = session ?: return
        if (password.isBlank()) {
            _uiState.update { it.copy(deleteError = "Enter your password.") }
            return
        }
        _uiState.update { it.copy(isDeleting = true, deleteError = null) }
        viewModelScope.launch {
            try {
                repo.deleteAccount(password)
                _uiState.update { it.copy(isDeleting = false) }
            } catch (e: com.researchradar.core.data.session.AuthException) {
                _uiState.update { it.copy(isDeleting = false, deleteError = e.message) }
            }
        }
    }

    fun clearDeleteError() {
        _uiState.update { it.copy(deleteError = null) }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
