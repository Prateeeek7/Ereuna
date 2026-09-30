package com.researchradar.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.researchradar.core.data.repository.MapRepository
import com.researchradar.core.model.JobEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class RunningJobViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mapRepository: MapRepository,
) : ViewModel() {

    private val jobId: String = checkNotNull(savedStateHandle["jobId"])
    private val initialTopic: String = savedStateHandle["topic"] ?: ""

    private val _uiState = MutableStateFlow(
        RunningJobUiState(
            jobId = jobId,
            topic = initialTopic,
            startedAtMs = System.currentTimeMillis(),
        )
    )
    val uiState: StateFlow<RunningJobUiState> = _uiState.asStateFlow()

    private var eventStreamJob: Job? = null
    private val startTimestampMs = System.currentTimeMillis()

    init {
        startListening()
    }

    fun startListening() {
        eventStreamJob?.cancel()
        _uiState.update {
            it.copy(
                errorMessage = null,
                isCancelled = false,
            )
        }

        eventStreamJob = viewModelScope.launch {
            val initialLog = formatElapsed(System.currentTimeMillis() - startTimestampMs) to "Connecting to research pipeline stream..."
            _uiState.update { it.copy(logs = it.logs + initialLog) }

            mapRepository.listenJobEvents(jobId)
                .catch { exception ->
                    val errorLog = formatElapsed(System.currentTimeMillis() - startTimestampMs) to "Stream connection error: ${exception.message}"
                    _uiState.update { current ->
                        current.copy(
                            logs = current.logs + errorLog,
                            errorMessage = exception.message ?: "Lost connection to pipeline stream.",
                        )
                    }
                }
                .collect { event ->
                    handleEvent(event)
                }
        }
    }

    private fun handleEvent(event: JobEvent) {
        val elapsed = formatElapsed(System.currentTimeMillis() - startTimestampMs)
        when (event) {
            is JobEvent.Stage -> {
                val stageData = event.data
                val stages = stagesFor(stageData.name, _uiState.value.stages)
                val stage = stageData.stage.coerceIn(1, stages.size)
                val label = if (stageData.status == "completed") "Complete" else stages[stage - 1]
                val logEntry = elapsed to "Stage $stage/${stages.size}: $label"
                _uiState.update { current ->
                    current.copy(
                        stages = stages,
                        currentStage = if (stageData.status == "completed") stages.size + 1 else stage,
                        stageName = label,
                        progress = stageData.progress.coerceIn(0f, 1f),
                        logs = current.logs + logEntry,
                    )
                }
            }

            is JobEvent.Log -> {
                val msg = event.data.message
                val logEntry = elapsed to msg

                // Pull live counts out of the backend's progress messages.
                fun count(pattern: String): Int? =
                    Regex(pattern, RegexOption.IGNORE_CASE).find(msg)?.groupValues?.get(1)?.toIntOrNull()

                _uiState.update { current ->
                    current.copy(
                        logs = current.logs + logEntry,
                        candidateCount = count("retrieved (\\d+)") ?: current.candidateCount,
                        selectedCount = count("selected top (\\d+)") ?: current.selectedCount,
                        fullTextCount = count("full text parsed[^:]*: (\\d+)") ?: current.fullTextCount,
                        uploadedCount = count("read (\\d+) of your papers") ?: current.uploadedCount,
                    )
                }
            }

            is JobEvent.PaperSelected -> {
                val paper = event.data
                val logEntry = elapsed to "Selected [${paper.shortLabel}]: ${paper.title}"
                _uiState.update { current ->
                    val updatedPapers = current.selectedPapers + paper
                    current.copy(
                        selectedPapers = updatedPapers,
                        logs = current.logs + logEntry,
                    )
                }
            }

            is JobEvent.Done -> {
                val doneData = event.data
                val logEntry = elapsed to "Map ready: ${doneData.papersCount} papers."
                _uiState.update { current ->
                    current.copy(
                        currentStage = current.stages.size + 1,
                        fullTextCount = doneData.fullTextCount.takeIf { it > 0 } ?: current.fullTextCount,
                        isComplete = true,
                        completedMapId = doneData.mapId.ifBlank { current.jobId },
                        progress = 1.0f,
                        logs = current.logs + logEntry,
                    )
                }
            }

            is JobEvent.Error -> {
                val err = event.data
                val errMsg = err.message.ifBlank { err.error }.ifBlank { "Pipeline processing error." }
                val logEntry = elapsed to "ERROR: $errMsg"
                _uiState.update { current ->
                    current.copy(
                        errorMessage = errMsg,
                        logs = current.logs + logEntry,
                    )
                }
            }
        }
    }

    fun cancelJob() {
        eventStreamJob?.cancel()
        val elapsed = formatElapsed(System.currentTimeMillis() - startTimestampMs)
        _uiState.update {
            it.copy(
                isCancelled = true,
                logs = it.logs + (elapsed to "Pipeline cancelled by user."),
            )
        }
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSecs = (elapsedMs / 1000).coerceAtLeast(0)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
