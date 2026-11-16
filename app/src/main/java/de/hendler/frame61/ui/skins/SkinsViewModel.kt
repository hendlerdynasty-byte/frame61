package de.hendler.frame61.ui.skins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.design.SkinDefinition
import de.hendler.frame61.design.SkinManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val skinManager: SkinManager
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
            _state.update {
                it.copy(
                    skins = defs.ifEmpty { all.map { s -> SkinDefinition(s.id, s.name, "", "") } },
                    activeSkinId = all.firstOrNull()?.id ?: "frame61"
                )
            }
        }
    }

    fun select(skinId: String) {
        viewModelScope.launch {
            val ok = runCatching { skinManager.getSkin(skinId) }.isSuccess
            _state.update {
                it.copy(
                    activeSkinId = if (ok) skinId else it.activeSkinId,
                    error = if (ok) null else "Skin '$skinId' konnte nicht geladen werden"
                )
            }
        }
    }
}