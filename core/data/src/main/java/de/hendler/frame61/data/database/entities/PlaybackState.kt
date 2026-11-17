package de.hendler.frame61.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_states",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        // Resume is per episode: a series must not lose S01E02's position when
        // S01E03 is watched. The old index (profileId,titleId) did exactly that.
        Index(value = ["profileId", "titleId", "episodeId"], unique = true)
    ]
)
data class PlaybackState(
    @PrimaryKey val id: Long = 0,
    val profileId: Long,
    val titleId: Long,
    val episodeId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
)