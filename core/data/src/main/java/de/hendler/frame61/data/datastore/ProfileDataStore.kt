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
    val WATCHLIST = stringSetPreferencesKey("watchlist")
    val UI_DENSITY = intPreferencesKey("ui_density")
    val SUBTITLE_SCALE = floatPreferencesKey("subtitle_scale")
    val HOSTER_PRIORITY = stringPreferencesKey("hoster_priority")
    val PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
    val DISABLED_SOURCES = stringSetPreferencesKey("disabled_sources")
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

    /** Persisted "Meine Liste" (catalog ids). */
    val watchlist: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.WATCHLIST] ?: emptySet()
    }

    /** 0 = compact, 1 = regular, 2 = roomy. */
    val uiDensity: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.UI_DENSITY] ?: 1
    }

    /** Subtitle text scale: 0.85 / 1.0 / 1.2. */
    val subtitleScale: Flow<Float> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SUBTITLE_SCALE] ?: 1.0f
    }

    /** Preferred hoster id; blank = no preference (priority order from packs). */
    val hosterPriority: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.HOSTER_PRIORITY] ?: ""
    }

    /** "auto" | "1080" | "720" | "480". */
    val preferredQuality: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.PREFERRED_QUALITY] ?: "auto"
    }

    /** Sources the user switched off; empty = all active. */
    val disabledSources: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DISABLED_SOURCES] ?: emptySet()
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

    suspend fun toggleWatchlist(titleId: String) {
        dataStore.edit { prefs ->
            val current = prefs[PreferencesKeys.WATCHLIST] ?: emptySet()
            prefs[PreferencesKeys.WATCHLIST] =
                if (titleId in current) current - titleId else current + titleId
        }
    }

    suspend fun setUiDensity(value: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.UI_DENSITY] = value.coerceIn(0, 2)
        }
    }

    suspend fun setSubtitleScale(value: Float) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.SUBTITLE_SCALE] = value.coerceIn(0.7f, 1.5f)
        }
    }

    suspend fun setHosterPriority(hosterId: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.HOSTER_PRIORITY] = hosterId.trim()
        }
    }

    suspend fun setPreferredQuality(quality: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.PREFERRED_QUALITY] = quality
        }
    }

    suspend fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[PreferencesKeys.DISABLED_SOURCES] ?: emptySet()
            prefs[PreferencesKeys.DISABLED_SOURCES] =
                if (enabled) current - sourceId else current + sourceId
        }
    }
}
