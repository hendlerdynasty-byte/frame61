package de.hendler.frame61.ui.layout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.ui.home.HomeContentBuilder
import de.hendler.frame61.ui.home.LayoutPrefs
import de.hendler.frame61.ui.home.RailKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LayoutBuilderUiState(
    val selected: Set<RailKind> = HomeContentBuilder.DEFAULT_LAYOUT,
    val profileId: Long = 0L,
    val saved: Boolean = false,
    val message: String = ""
)

/**
 * Layout builder state.
 *
 * The rail set is edited in memory and only written to DataStore on save, so a
 * half-finished edit cannot break the home screen. The saved id is the profile's
 * `layoutId`; [LayoutPrefs] keeps it stable and human readable.
 */
@HiltViewModel
class LayoutBuilderViewModel @Inject constructor(
    private val prefs: ProfileDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(LayoutBuilderUiState())
    val state: StateFlow<LayoutBuilderUiState> = _state.asStateFlow()

    /** Every rail the user can switch on, in canonical order. */
    val selectable: List<RailKind> = RailKind.entries.filter { it != RailKind.BILLBOARD }

    init {
        viewModelScope.launch {
            prefs.currentProfileId.collect { profileId ->
                prefs.layoutFor(profileId).collect { layoutId ->
                    _state.update {
                        it.copy(profileId = profileId, selected = LayoutPrefs.decode(layoutId))
                    }
                }
            }
        }
    }

    fun toggle(kind: RailKind) {
        if (kind == RailKind.BILLBOARD) return
        _state.update {
            val next = if (kind in it.selected) it.selected - kind else it.selected + kind
            it.copy(selected = next, saved = false, message = "")
        }
    }

    fun selectAll() {
        _state.update { it.copy(selected = selectable.toSet(), saved = false, message = "") }
    }

    fun resetDefault() {
        _state.update {
            it.copy(selected = HomeContentBuilder.DEFAULT_LAYOUT, saved = false, message = "")
        }
    }

    /** Persists the selection for the active profile; the home follows on return. */
    fun save() {
        val profileId = _state.value.profileId
        val layoutId = LayoutPrefs.profileLayoutId(_state.value.selected)
        viewModelScope.launch {
            prefs.setLayout(profileId, layoutId)
            _state.update { it.copy(saved = true, message = "Layout gespeichert") }
        }
    }
}