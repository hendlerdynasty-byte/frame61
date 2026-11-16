package de.hendler.frame61.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.meta.CatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val content: HomeContent = HomeContent(billboard = null, rails = emptyList()),
    val layout: Set<RailKind> = HomeContentBuilder.DEFAULT_LAYOUT,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val catalog: CatalogRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load(seed: Int = 0) {
        viewModelScope.launch {
            val items = runCatching { catalog.all() }.getOrDefault(emptyList())
            if (items.isEmpty()) {
                _state.update { it.copy(isLoading = false, error = "Katalog leer") }
                return@launch
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    content = HomeContentBuilder.build(
                        catalog = items,
                        layout = it.layout,
                        seed = seed
                    ),
                    error = null
                )
            }
        }
    }

    /** The layout builder flips the rail set at runtime. */
    fun applyLayout(layout: Set<RailKind>) {
        viewModelScope.launch {
            val items = runCatching { catalog.all() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    layout = layout,
                    content = HomeContentBuilder.build(items, layout, seed = it.content.rails.size)
                )
            }
        }
    }

    fun openRandom() {
        viewModelScope.launch {
            catalog.all().randomOrNull()?.let { _state.update { s -> s.copy(error = null) } }
        }
    }
}