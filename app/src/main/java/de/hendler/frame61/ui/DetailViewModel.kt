package de.hendler.frame61.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.data.ResumeRepository
import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.meta.SnapshotEpisodeSource
import de.hendler.frame61.playback.PrefetchController
import de.hendler.frame61.ui.player.CatalogPlayback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Exposes catalog, snapshot episodes and the persistent watchlist. */
@HiltViewModel
class DetailViewModel @Inject constructor(
    val catalog: CatalogRepository,
    val episodes: SnapshotEpisodeSource,
    private val prefs: ProfileDataStore,
    private val prefetchController: PrefetchController,
    private val resume: ResumeRepository
) : ViewModel() {

    val profileId: StateFlow<Long> = prefs.currentProfileId.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        0L
    )

    val watchlist: StateFlow<Set<String>> = prefs.watchlist.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptySet()
    )

    fun toggleWatchlist(titleId: String) {
        viewModelScope.launch { prefs.toggleWatchlist(titleId) }
    }

    /**
     * Block 7: Fokus auf einen Titel/eine Folge — Quelle im Hintergrund
     * vorwaermen. [episodeNumber]/[season] kommen aus der Folge (Block 8),
     * damit der Prefetch dieselbe Folge adressiert wie der spätere Start.
     */
    fun prefetch(item: CatalogItem, episodeNumber: Int = 0, season: Int = 1) {
        prefetchController.prefetch(item.title, episodeNumber, season)
    }

    /** Fortschritt genau der Folge, die auch im Player adressiert wird. */
    suspend fun episodeProgress(titleId: String, profileId: Long): Map<Long, Float> =
        withContext(Dispatchers.IO) {
            resume.progressByEpisode(profileId, CatalogPlayback.stableId(titleId))
        }
}