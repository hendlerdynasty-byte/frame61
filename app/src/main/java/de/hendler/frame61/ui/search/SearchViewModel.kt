package de.hendler.frame61.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.meta.BundledCatalogSource
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.meta.MetadataSource
import de.hendler.frame61.meta.SearchHit
import de.hendler.frame61.meta.SnapshotMetadataSource
import de.hendler.frame61.search.AggregatedIndexSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    val sources: List<String> = emptyList(),
    val error: String? = null
)

/**
 * Federated search over the local snapshot index and the bundled catalog.
 *
 * There is no live source here: `assets/meta/` is refreshed by the fetch worker
 * (tools/fetch/tvmaze_snapshots.py), so a search never waits on an API.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    snapshots: SnapshotMetadataSource,
    index: AggregatedIndexSource
) : ViewModel() {

    private val sources: List<MetadataSource> = listOf(
        index,
        snapshots,
        BundledCatalogSource(catalog)
    )

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(sources = sources.map { it.id })
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun search(query: String) {
        val needle = query.trim()
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val merged = LinkedHashMap<String, SearchHit>()
            for (source in sources) {
                val hits = runCatching { source.search(needle, 20) }.getOrDefault(emptyList())
                for (hit in hits) {
                    // getOrPut statt HashMap.putIfAbsent: putIfAbsent ist API 24,
                    // die App läuft ab minSdk 23 (Lint NewApi).
                    merged.getOrPut("${hit.title}|${hit.year}|${hit.runtime ?: 0}") { hit }
                }
            }
            val ordered = merged.values.sortedWith(
                compareByDescending<SearchHit> { it.lang == "de" }
                    .thenBy { it.title.lowercase() }
            ).take(20)
            _state.update {
                it.copy(
                    isLoading = false,
                    hits = ordered,
                    error = if (needle.isBlank()) "Bitte Suchbegriff eingeben" else null
                )
            }
        }
    }
}