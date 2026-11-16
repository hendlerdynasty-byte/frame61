package de.hendler.frame61.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.data.PinHasher
import de.hendler.frame61.data.database.ProfileDao
import de.hendler.frame61.data.database.entities.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val profiles: List<Profile> = emptyList(),
    val selectedId: Long = 0L,
    val error: String? = null
)

/**
 * Profile picker.
 *
 * Every profile keeps its own namespace: resume, recommendation vectors and
 * allowed sources are keyed by profileId, so two users on one device do not
 * see each other's state.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileDao: ProfileDao
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val all = runCatching { profileDao.getAll() }.getOrDefault(emptyList())
            _state.update { it.copy(profiles = all) }
        }
    }

    fun select(id: Long) {
        _state.update { it.copy(selectedId = id) }
    }

    /** Creates a profile with an optional PIN. Empty name is refused. */
    fun create(name: String, pin: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _state.update { it.copy(error = "Name darf nicht leer sein") }
            return
        }
        // Primary key is not auto-generated: take the next free id so several
        // profiles coexist instead of overwriting each other on id = 0.
        val nextId = (_state.value.profiles.maxOfOrNull { it.id } ?: 0L) + 1
        val salt = nextId.toString()
        val hash = if (pin.isBlank()) "" else PinHasher.hash(pin, salt)
        val profile = Profile(
            id = nextId,
            name = trimmed,
            pinHash = hash,
            allowedSources = "",
            skinId = "frame61",
            layoutId = "default"
        )
        viewModelScope.launch {
            profileDao.upsert(profile)
            load()
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch {
            profileDao.remove(id)
            load()
        }
    }

    /** Verifies a PIN against the stored hash; unknown profile fails closed. */
    fun verify(id: Long, pin: String): Boolean {
        val profile = _state.value.profiles.firstOrNull { it.id == id } ?: return false
        if (profile.pinHash.isBlank()) return false
        // Salt is the stable profile id, so equal PINs hash differently per profile
        return PinHasher.verify(pin, profile.id.toString(), profile.pinHash)
    }
}