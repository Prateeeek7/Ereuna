package com.researchradar.feature.graph

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.researchradar.core.data.repository.MapRepository
import com.researchradar.core.model.GraphData
import com.researchradar.core.model.GraphNode
import com.researchradar.core.model.Paper
import com.researchradar.core.model.ResearchMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GraphUiState(
    val mapId: String = "",
    val topic: String = "",
    val isLoading: Boolean = true,
    val graphData: GraphData = GraphData(),
    val papers: List<Paper> = emptyList(),
    val selectedNode: GraphNode? = null,
    val selectedPaper: Paper? = null,
    val inMapOnly: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class GraphViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mapId: String = savedStateHandle.get<String>("mapId").orEmpty()

    private val _uiState = MutableStateFlow(GraphUiState(mapId = mapId))
    val uiState: StateFlow<GraphUiState> = _uiState.asStateFlow()

    init {
        if (mapId.isNotEmpty()) {
            loadGraph(mapId)
        }
    }

    fun loadGraph(id: String = mapId) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val map = mapRepository.getMap(id)
                val mapGraph = map.graph
                val graph = if (mapGraph != null && mapGraph.nodes.isNotEmpty()) {
                    mapGraph
                } else {
                    mapRepository.getMapGraph(id)
                }

                _uiState.update {
                    it.copy(
                        mapId = id,
                        topic = map.topic,
                        isLoading = false,
                        graphData = graph,
                        papers = map.papers,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Unable to load citation graph: ${e.localizedMessage ?: "Unknown error"}",
                    )
                }
            }
        }
    }

    fun selectNode(node: GraphNode?) {
        val paper = if (node != null) {
            _uiState.value.papers.find { it.id == node.paperId }
        } else {
            null
        }
        _uiState.update {
            it.copy(selectedNode = node, selectedPaper = paper)
        }
    }

    fun toggleInMapOnly() {
        _uiState.update {
            it.copy(inMapOnly = !it.inMapOnly)
        }
    }

    fun dismissSelectedNode() {
        _uiState.update {
            it.copy(selectedNode = null, selectedPaper = null)
        }
    }
}
