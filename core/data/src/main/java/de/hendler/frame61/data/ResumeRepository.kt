package de.hendler.frame61.data

import de.hendler.frame61.data.database.PlaybackStateDao
import de.hendler.frame61.data.database.ProfileDao
import de.hendler.frame61.data.database.entities.PlaybackState
import de.hendler.frame61.data.database.entities.Profile
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Where playback should continue, plus whether the title counts as watched. */
data class ResumePoint(
    val profileId: Long,
    val titleId: Long,
    val episodeId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) {
    val fraction: Float get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val isWatched: Boolean get() = isWatched(positionMs, durationMs)

    companion object {
        /** >90 % is watched, per the Frame61 playback rule. */
        fun isWatched(positionMs: Long, durationMs: Long): Boolean =
            durationMs > 0 && positionMs >= durationMs * WATCHED_THRESHOLD

        const val WATCHED_THRESHOLD = 0.9
        const val SAVE_INTERVAL_MS = 5_000L
    }
}

/**
 * Room-backed resume.
 *
 * Writes are throttled to every [ResumePoint.SAVE_INTERVAL_MS]; the final write
 * on stop/pause is always flushed so a kill does not lose the last seconds.
 */
@Singleton
class ResumeRepository @Inject constructor(
    private val dao: PlaybackStateDao,
    private val profiles: ProfileDao
) {

    private val lastSave = HashMap<String, Long>()

    suspend fun load(profileId: Long, titleId: Long, episodeId: Long): ResumePoint? {
        val state = dao.getForProfileTitleAndEpisode(profileId, titleId, episodeId)
            ?: return null
        return state.toPoint()
    }

    /** Saves if [SAVE_INTERVAL_MS] elapsed since the last write for this key. */
    suspend fun saveThrottled(
        profileId: Long,
        titleId: Long,
        episodeId: Long,
        positionMs: Long,
        durationMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val key = "$profileId:$titleId:$episodeId"
        val previous = lastSave[key]
        if (previous != null && nowMs - previous < ResumePoint.SAVE_INTERVAL_MS) return false
        lastSave[key] = nowMs
        save(profileId, titleId, episodeId, positionMs, durationMs, nowMs)
        return true
    }

    /** Unthrottled write — used on pause, stop and when the player is released. */
    suspend fun save(
        profileId: Long,
        titleId: Long,
        episodeId: Long,
        positionMs: Long,
        durationMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ) {
        ensureProfile(profileId)
        dao.upsert(
            PlaybackState(
                // Without a stable per-key id every row is id=0 and REPLACE
                // overwrites the previous title; resume then only knew the last
                // played item. Measured on the A52 (only one resume row).
                id = resumeRowId(profileId, titleId, episodeId),
                profileId = profileId,
                titleId = titleId,
                episodeId = episodeId,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                updatedAt = nowMs
            )
        )
        lastSave["$profileId:$titleId:$episodeId"] = nowMs
    }

    /**
     * A PlaybackState carries a foreign key to profiles. The app starts with
     * profileId 0 before anyone created a profile; the first save then failed
     * with SQLiteConstraintException(787) and killed the process. The default
     * profile is created lazily here, once.
     */
    private suspend fun ensureProfile(profileId: Long) {
        if (profiles.getById(profileId) != null) return
        profiles.upsert(
            Profile(
                id = profileId,
                name = if (profileId == 0L) "Standard" else "Profil $profileId",
                pinHash = "",
                allowedSources = "",
                skinId = "frame61",
                layoutId = "default"
            )
        )
    }

    suspend fun clear(profileId: Long, titleId: Long) {
        lastSave.keys.removeAll { it.startsWith("$profileId:$titleId:") }
        dao.deleteForTitle(profileId, titleId)
    }

    private fun PlaybackState.toPoint() = ResumePoint(
        profileId = profileId,
        titleId = titleId,
        episodeId = episodeId,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = updatedAt
    )
}

/**
 * Deterministic positive row id for (profile, title, episode).
 *
 * The first 8 bytes of SHA-256, with 0 mapped to 1 so Room never sees "not set".
 * Not a security function — it only has to be stable and collision-resistant
 * enough for local resume rows.
 */
internal fun resumeRowId(profileId: Long, titleId: Long, episodeId: Long): Long {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("resume:$profileId:$titleId:$episodeId".toByteArray(Charsets.UTF_8))
    var value = 0L
    for (index in 0 until 8) {
        value = (value shl 8) or (digest[index].toLong() and 0xFF)
    }
    return (value and Long.MAX_VALUE).takeIf { it != 0L } ?: 1L
}