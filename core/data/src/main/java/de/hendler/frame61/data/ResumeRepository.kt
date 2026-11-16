package de.hendler.frame61.data

import de.hendler.frame61.data.database.PlaybackStateDao
import de.hendler.frame61.data.database.entities.PlaybackState
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
    private val dao: PlaybackStateDao
) {

    private val lastSave = HashMap<String, Long>()

    suspend fun load(profileId: Long, titleId: Long, episodeId: Long): ResumePoint? {
        val state = dao.getForProfileAndTitle(profileId, titleId) ?: return null
        // A different episode must not inherit the movie's position.
        if (state.episodeId != episodeId) return null
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
        dao.upsert(
            PlaybackState(
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