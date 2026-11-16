package de.hendler.frame61.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.meta.BundledCatalogSource
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.meta.MetadataSource
import de.hendler.frame61.meta.SearchHit
import de.hendler.frame61.meta.TvmazeMetadataSource
import de.hendler.frame61.meta.WikipediaMetadataSource
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
 * Federated search over the individual sources.
 *
 * The sources are injected one by one instead of as a `List<MetadataSource>`:
 * Kotlin's declaration-site variance made the constructor parameter
 * `List<? extends MetadataSource>` in bytecode, which Dagger could not match
 * against a `List<MetadataSource>` provider (`hiltJavaCompileDebug` failure).
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val tvmaze: TvmazeMetadataSource,
    private val wikipedia: WikipediaMetadataSource
) : ViewModel() {

    private val sources: List<MetadataSource> = listOf(
        BundledCatalogSource(catalog),
        tvmaze,
        wikipedia
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
                    merged.putIfAbsent("${hit.title}|${hit.year}", hit)
                }
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    hits = merged.values.take(20).toList(),
                    error = if (needle.isBlank()) "Bitte Suchbegriff eingeben" else null
                )
            }
        }
    }
}