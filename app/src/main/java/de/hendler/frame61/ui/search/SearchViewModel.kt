package de.hendler.frame61.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.meta.MetadataSource
import de.hendler.frame61.meta.SearchHit
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

@HiltViewModel
class SearchViewModel @Inject constructor(
    // Kotlin's declaration-site variance makes this List<? extends MetadataSource>
    // in bytecode; the @Provides binding is List<MetadataSource>. Dagger sees
    // different types without @JvmSuppressWildcards (documented in the error).
    @JvmSuppressWildcards private val sources: List<MetadataSource>
) : ViewModel() {

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