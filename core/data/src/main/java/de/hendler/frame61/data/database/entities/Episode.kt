package de.hendler.frame61.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.hendler.frame61.data.database.entities.Title

/**
 * Episode entity — links to Title via tmdbId
 */
@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = Title::class,
            parentColumns = ["id"],
            childColumns = ["titleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["titleId", "season", "episode"], unique = true),
        Index(value = ["tmdbId"])
    ]
)
data class Episode(
    @PrimaryKey val id: Long = 0,
    val titleId: Long,
    val season: Int,
    val episode: Int,
    val tmdbId: Long
)
