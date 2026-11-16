package de.hendler.frame61.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.data.ProfileDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val autoplay: Boolean = true,
    val watchedPercentage: Float = 0.9f,
    val saved: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: ProfileDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.autoplay.collect { value ->
                _state.update { it.copy(autoplay = value) }
            }
        }
        viewModelScope.launch {
            prefs.watchedPercentage.collect { value ->
                _state.update { it.copy(watchedPercentage = value) }
            }
        }
    }

    fun setAutoplay(enabled: Boolean) {
        _state.update { it.copy(autoplay = enabled, saved = false) }
        viewModelScope.launch { prefs.setAutoplay(enabled) }
    }

    fun setWatchedPercentage(pct: Float) {
        val clamped = pct.coerceIn(0.5f, 1.0f)
        _state.update { it.copy(watchedPercentage = clamped, saved = false) }
        viewModelScope.launch { prefs.setWatchedPercentage(clamped) }
    }

    fun markSaved() = _state.update { it.copy(saved = true) }
}