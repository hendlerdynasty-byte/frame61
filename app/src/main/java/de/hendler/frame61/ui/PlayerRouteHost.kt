package de.hendler.frame61.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.ui.player.PlayerRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Aktive Profil-ID für den Player — Resume ist ein Profil-Namespace. */
@HiltViewModel
class ProfileSessionViewModel @Inject constructor(
    private val prefs: ProfileDataStore
) : ViewModel() {

    val currentProfileId: StateFlow<Long> = prefs.currentProfileId
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    /** One-shot read: the profile at playback start, no later profile switch. */
    suspend fun profileAtStart(): Long = prefs.currentProfileId.first()
}

/**
 * Resolves the catalog item for [titleId] and hands it to the player.
 *
 * A title that cannot be resolved produces a visible message rather than a crash.
 * Resume is stored per profile: the player waits until the active profile id is
 * known, then starts — before this, playback was hard-wired to profile 0.
 */
@Composable
fun PlayerRouteFor(titleId: String, episodeId: Long = 0L, episodeNumber: Int = 0) {
    val catalog: CatalogRepository = hiltViewModel<DetailViewModel>().catalog
    val session: ProfileSessionViewModel = hiltViewModel()

    val item by produceState<CatalogItem?>(initialValue = null, titleId, catalog) {
        value = catalog.byId(titleId)
    }

    val profileId by produceState<Long?>(initialValue = null, titleId) {
        value = session.profileAtStart()
    }

    val resolved = item
    val resolvedProfile = profileId
    if (resolved != null && resolvedProfile != null) {
        PlayerRoute(
            item = resolved,
            profileId = resolvedProfile,
            episodeId = episodeId,
            episodeNumber = episodeNumber
        )
    } else {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(text = if (resolved == null) "Titel nicht gefunden: $titleId" else "Lade …")
            }
        }
    }
}