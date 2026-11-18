package de.hendler.frame61.data.database

import androidx.room.*
import de.hendler.frame61.data.database.entities.Episode
import de.hendler.frame61.data.database.entities.PlaybackState
import de.hendler.frame61.data.database.entities.Profile
import de.hendler.frame61.data.database.entities.RecVector
import de.hendler.frame61.data.database.entities.Title

@Dao
interface TitleDao {

    @Query("SELECT * FROM titles WHERE tmdbId = :tmdbId")
    suspend fun getByTmdbId(tmdbId: Long): Title?

    @Query("SELECT * FROM titles WHERE tmdbId IN (:tmdbIds)")
    suspend fun getByTmdbIds(tmdbIds: List<Long>): List<Title>

    @Query("SELECT * FROM titles WHERE type = :type")
    suspend fun getAllByType(type: String): List<Title>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(title: Title)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(titles: List<Title>)

    @Query("DELETE FROM titles WHERE tmdbId = :tmdbId")
    suspend fun remove(tmdbId: Long)
}

@Dao
interface EpisodeDao {

    @Query("SELECT * FROM episodes WHERE titleId = :titleId ORDER BY season, episode")
    suspend fun getAllByTitle(titleId: Long): List<Episode>

    @Query("SELECT * FROM episodes WHERE tmdbId = :tmdbId")
    suspend fun getByTmdbId(tmdbId: Long): Episode?

    @Query("SELECT * FROM episodes WHERE titleId = :titleId AND season = :season ORDER BY episode")
    suspend fun getBySeason(titleId: Long, season: Int): List<Episode>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(episode: Episode)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(episodes: List<Episode>)
}

@Dao
interface PlaybackStateDao {

    @Query(
        "SELECT * FROM playback_states WHERE profileId = :profileId AND titleId = :titleId " +
            "ORDER BY updatedAt DESC LIMIT 1"
    )
    suspend fun getForProfileAndTitle(profileId: Long, titleId: Long): PlaybackState?

    @Query(
        "SELECT * FROM playback_states WHERE profileId = :profileId " +
            "AND titleId = :titleId AND episodeId = :episodeId"
    )
    suspend fun getForProfileTitleAndEpisode(
        profileId: Long,
        titleId: Long,
        episodeId: Long
    ): PlaybackState?

    @Query("DELETE FROM playback_states WHERE profileId = :profileId AND titleId = :titleId")
    suspend fun deleteForTitle(profileId: Long, titleId: Long)

    @Query("SELECT * FROM playback_states WHERE profileId = :profileId")
    suspend fun getByProfile(profileId: Long): List<PlaybackState>

    @Query("SELECT * FROM playback_states WHERE positionMs > 0 AND positionMs < durationMs * 0.9")
    suspend fun getUnwatched(): List<PlaybackState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PlaybackState)

    @Query("UPDATE playback_states SET positionMs = :positionMs, durationMs = :durationMs WHERE id = :id")
    suspend fun updatePosition(id: Long, positionMs: Long, durationMs: Long)
}

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): Profile?

    @Query("SELECT * FROM profiles")
    suspend fun getAll(): List<Profile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: Profile)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun remove(id: Long)
}

@Dao
interface RecVectorDao {

    @Query("SELECT * FROM rec_vectors WHERE profileId = :profileId ORDER BY weight DESC")
    suspend fun getByProfile(profileId: Long): List<RecVector>

    @Query("SELECT * FROM rec_vectors WHERE tag = :tag")
    suspend fun getByTag(tag: String): List<RecVector>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recVector: RecVector)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(recVectors: List<RecVector>)

    @Query("DELETE FROM rec_vectors WHERE profileId = :profileId AND tag = :tag")
    suspend fun removeTag(profileId: Long, tag: String)
}
