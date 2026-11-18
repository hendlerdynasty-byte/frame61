package de.hendler.frame61.ui.skins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.design.SkinDefinition
import de.hendler.frame61.design.SkinManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SkinsUiState(
    val skins: List<SkinDefinition> = emptyList(),
    val activeSkinId: String = "frame61",
    val error: String? = null
)

@HiltViewModel
class SkinsViewModel @Inject constructor(
    private val skinManager: SkinManager,
    private val prefs: ProfileDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(SkinsUiState())
    val state: StateFlow<SkinsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val all = runCatching { skinManager.getAllSkins() }.getOrDefault(emptyList())
            val defs = runCatching { skinManager.getAvailableSkins() }.getOrDefault(emptyList())
            val active = runCatching { prefs.activeSkin.first() }.getOrDefault("frame61")
            _state.update {
                it.copy(
                    skins = defs.ifEmpty { all.map { s -> SkinDefinition(s.id, s.name, "", "") } },
                    activeSkinId = active
                )
            }
        }
    }

    fun select(skinId: String) {
        viewModelScope.launch {
            val ok = runCatching { skinManager.getSkin(skinId) }.isSuccess
            if (ok) {
                // DataStore is the single source; the shell observes it and
                // re-renders immediately (no restart).
                prefs.setActiveSkin(skinId)
            }
            _state.update {
                it.copy(
                    activeSkinId = if (ok) skinId else it.activeSkinId,
                    error = if (ok) null else "Skin '$skinId' konnte nicht geladen werden"
                )
            }
        }
    }
}