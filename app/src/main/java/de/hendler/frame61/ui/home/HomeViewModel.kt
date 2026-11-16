package de.hendler.frame61.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.data.ProfileDataStore
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
    val profileId: Long = 0L,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val catalog: CatalogRepository,
    private val prefs: ProfileDataStore
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
        // Profile wechseln heißt Layout wechseln: der Layout-Builder schreibt
        // pro Profil, Home liest genau das Profil, das gerade aktiv ist.
        viewModelScope.launch {
            prefs.currentProfileId.collect { profileId ->
                prefs.layoutFor(profileId).collect { layoutId ->
                    _state.update {
                        it.copy(profileId = profileId, layout = LayoutPrefs.decode(layoutId))
                    }
                    rebuild(seed = 0)
                }
            }
        }
    }

    fun load(seed: Int = 0) {
        viewModelScope.launch {
            val items = runCatching { catalog.all() }.getOrDefault(emptyList())
            if (items.isEmpty()) {
                _state.update { it.copy(isLoading = false, error = "Katalog leer") }
                return@launch
            }
            rebuild(seed)
        }
    }

    private fun rebuild(seed: Int) {
        val items = runCatching { catalog.all() }.getOrDefault(emptyList())
        if (items.isEmpty()) {
            _state.update { it.copy(isLoading = false, error = "Katalog leer") }
            return
        }
        val layout = _state.value.layout
        _state.update {
            it.copy(
                isLoading = false,
                content = HomeContentBuilder.build(
                    catalog = items,
                    layout = layout,
                    seed = seed
                ),
                error = null
            )
        }
    }

    /** The layout builder flips the rail set at runtime. */
    fun applyLayout(layout: Set<RailKind>) {
        val seed = _state.value.content.rails.size
        _state.update { it.copy(layout = layout) }
        rebuild(seed)
    }

    fun openRandom() {
        viewModelScope.launch {
            catalog.all().randomOrNull()?.let { _state.update { s -> s.copy(error = null) } }
        }
    }
}