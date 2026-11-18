package de.hendler.frame61.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.design.DefaultFrame61Skin
import de.hendler.frame61.design.Frame61Skin
import de.hendler.frame61.design.SkinManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The skin the whole app renders with.
 *
 * Read from DataStore, so a choice made in the Skins screen survives restarts.
 * Before this existed the theme loaded "frame61" hardcoded and the parser's
 * result was never used.
 */
@HiltViewModel
class SkinViewModel @Inject constructor(
    private val skinManager: SkinManager,
    private val prefs: ProfileDataStore
) : ViewModel() {

    val activeSkinId: StateFlow<String> = prefs.activeSkin.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), "frame61"
    )

    val uiDensity: StateFlow<Int> = prefs.uiDensity.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 1
    )

    private val _skin = MutableStateFlow(DefaultFrame61Skin)
    val skin: StateFlow<Frame61Skin> = _skin.asStateFlow()

    init {
        viewModelScope.launch {
            activeSkinId.collect { id ->
                val loaded = runCatching { skinManager.getSkin(id) }.getOrNull()
                if (loaded != null) _skin.value = loaded
            }
        }
    }

    fun select(skinId: String) {
        viewModelScope.launch { prefs.setActiveSkin(skinId) }
    }
}