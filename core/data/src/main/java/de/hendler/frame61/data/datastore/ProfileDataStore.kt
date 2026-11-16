package de.hendler.frame61.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Profile preferences via DataStore
 */
object PreferencesKeys {
    val CURRENT_PROFILE_ID = longPreferencesKey("current_profile_id")
    val ACTIVE_SKIN = stringPreferencesKey("active_skin")
    val ACTIVE_LAYOUT = stringPreferencesKey("active_layout")
    val FOCUS_TRACKING = booleanPreferencesKey("focus_tracking")
    val AUTOPLAY = booleanPreferencesKey("autoplay")
    val RESUME_INTERVAL_MS = longPreferencesKey("resume_interval_ms")
    val WATCHED_PERCENTAGE = floatPreferencesKey("watched_percentage")
    val COIL_FORMAT = stringPreferencesKey("coil_format")
    val UPDATE_URL = stringPreferencesKey("update_url")
}

class ProfileDataStore(private val dataStore: DataStore<androidx.datastore.preferences.core.Preferences>) {

    val currentProfileId: Flow<Long> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.CURRENT_PROFILE_ID] ?: 0
    }

    val activeSkin: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.ACTIVE_SKIN] ?: "frame61"
    }

    val autoplay: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.AUTOPLAY] ?: true
    }

    val watchedPercentage: Flow<Float> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.WATCHED_PERCENTAGE] ?: 0.9f
    }

    /** Update manifest URL (sideload updater); blank means "not configured". */
    val updateUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.UPDATE_URL] ?: ""
    }

    /**
     * Layout id per profile. The key carries the profileId, so two profiles can
     * keep different home layouts; "default" means the built-in rail set.
     */
    fun layoutFor(profileId: Long): Flow<String> = dataStore.data.map { prefs ->
        prefs[stringPreferencesKey("layout_$profileId")] ?: "default"
    }

    suspend fun setCurrentProfileId(id: Long) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.CURRENT_PROFILE_ID] = id
        }
    }

    suspend fun setActiveSkin(skinId: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.ACTIVE_SKIN] = skinId
        }
    }

    suspend fun setAutoplay(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.AUTOPLAY] = enabled
        }
    }

    suspend fun setUpdateUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.UPDATE_URL] = url.trim()
        }
    }

    suspend fun setLayout(profileId: Long, layoutId: String) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("layout_$profileId")] = layoutId
        }
    }

    suspend fun setWatchedPercentage(pct: Float) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.WATCHED_PERCENTAGE] = pct
        }
    }
}
