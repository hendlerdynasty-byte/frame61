package de.hendler.frame61.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.meta.SnapshotEpisodeSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Exposes catalog, snapshot episodes and the persistent watchlist. */
@HiltViewModel
class DetailViewModel @Inject constructor(
    val catalog: CatalogRepository,
    val episodes: SnapshotEpisodeSource,
    private val prefs: ProfileDataStore
) : ViewModel() {

    val watchlist: StateFlow<Set<String>> = prefs.watchlist.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptySet()
    )

    fun toggleWatchlist(titleId: String) {
        viewModelScope.launch { prefs.toggleWatchlist(titleId) }
    }
}