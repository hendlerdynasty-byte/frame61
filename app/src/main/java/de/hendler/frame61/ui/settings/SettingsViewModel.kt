package de.hendler.frame61.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.BuildConfig
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.scraper.engine.PackLoader
import de.hendler.frame61.scraper.engine.SourcePack
import de.hendler.frame61.update.UpdateCheck
import de.hendler.frame61.update.UpdateInstall
import de.hendler.frame61.update.UpdateManifest
import de.hendler.frame61.update.UpdaterManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row in Settings → Erweitert → Quellen. */
data class SourceStatus(
    val id: String,
    val name: String,
    val verified: Boolean,
    val lastCheck: String?,
    val status: String?,
    val reason: String?
)

data class SettingsUiState(
    val autoplay: Boolean = true,
    val watchedPercentage: Float = 0.9f,
    val saved: Boolean = false,
    val updateUrl: String = "",
    val updateStatus: String = "",
    val updateAvailable: Boolean = false,
    val uiDensity: Int = 1,
    val subtitleScale: Float = 1.0f,
    val hosterPriority: String = "",
    val preferredQuality: String = "auto",
    val disabledSources: Set<String> = emptySet(),
    val sources: List<SourceStatus> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val prefs: ProfileDataStore,
    private val packLoader: PackLoader
) : AndroidViewModel(application) {

    private val updater = UpdaterManager(application, BuildConfig.VERSION_CODE)
    private var pending: UpdateManifest? = null

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
        viewModelScope.launch {
            prefs.updateUrl.collect { value ->
                _state.update { it.copy(updateUrl = value) }
            }
        }
        viewModelScope.launch {
            prefs.uiDensity.collect { value -> _state.update { it.copy(uiDensity = value) } }
        }
        viewModelScope.launch {
            prefs.subtitleScale.collect { value -> _state.update { it.copy(subtitleScale = value) } }
        }
        viewModelScope.launch {
            prefs.hosterPriority.collect { value -> _state.update { it.copy(hosterPriority = value) } }
        }
        viewModelScope.launch {
            prefs.preferredQuality.collect { value -> _state.update { it.copy(preferredQuality = value) } }
        }
        viewModelScope.launch {
            prefs.disabledSources.collect { value -> _state.update { it.copy(disabledSources = value) } }
        }
        loadSources()
    }

    fun loadSources() {
        viewModelScope.launch {
            val packs = runCatching { packLoader.loadAllPacks() }.getOrDefault(emptyList())
            _state.update { it.copy(sources = packs.map { pack -> pack.toStatus() }) }
        }
    }

    private fun SourcePack.toStatus() = SourceStatus(
        id = id,
        name = name,
        verified = verified,
        lastCheck = lastCheck,
        status = check?.status,
        reason = check?.reason
    )

    fun setUiDensity(value: Int) {
        _state.update { it.copy(uiDensity = value.coerceIn(0, 2)) }
        viewModelScope.launch { prefs.setUiDensity(value) }
    }

    fun setSubtitleScale(value: Float) {
        _state.update { it.copy(subtitleScale = value) }
        viewModelScope.launch { prefs.setSubtitleScale(value) }
    }

    fun setHosterPriority(value: String) {
        _state.update { it.copy(hosterPriority = value) }
        viewModelScope.launch { prefs.setHosterPriority(value) }
    }

    fun setPreferredQuality(value: String) {
        _state.update { it.copy(preferredQuality = value) }
        viewModelScope.launch { prefs.setPreferredQuality(value) }
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { prefs.setSourceEnabled(id, enabled) }
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

    fun setUpdateUrl(url: String) {
        _state.update { it.copy(updateUrl = url) }
        viewModelScope.launch { prefs.setUpdateUrl(url) }
    }

    /** Reads the manifest; never throws, the result is shown in the UI. */
    fun checkUpdate() {
        val url = _state.value.updateUrl
        _state.update { it.copy(updateStatus = "Prüfe …", updateAvailable = false) }
        pending = null
        viewModelScope.launch {
            when (val result = updater.check(url)) {
                is UpdateCheck.UpToDate -> _state.update {
                    it.copy(updateStatus = "Aktuell (Version ${BuildConfig.VERSION_CODE})", updateAvailable = false)
                }
                is UpdateCheck.Available -> {
                    pending = result.manifest
                    _state.update {
                        it.copy(
                            updateStatus = "Version ${result.manifest.versionCode} verfügbar",
                            updateAvailable = true
                        )
                    }
                }
                is UpdateCheck.Failed -> _state.update {
                    it.copy(updateStatus = result.message, updateAvailable = false)
                }
            }
        }
    }

    /** Downloads the pending APK and hands it to the system installer. */
    fun installUpdate() {
        val manifest = pending ?: return
        _state.update { it.copy(updateStatus = "Lade …") }
        viewModelScope.launch {
            when (val result = updater.downloadAndInstall(manifest)) {
                is UpdateInstall.Installed -> _state.update {
                    it.copy(updateStatus = "Installer geöffnet", updateAvailable = false)
                }
                is UpdateInstall.Failed -> _state.update { it.copy(updateStatus = result.message) }
            }
        }
    }

    fun markSaved() = _state.update { it.copy(saved = true) }
}