package de.hendler.frame61.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Title entity — identity = TMDB ID
 */
@Entity(
    tableName = "titles",
    indices = [Index(value = ["tmdbId"], unique = true)]
)
data class Title(
    @PrimaryKey val id: Long = 0,
    val tmdbId: Long,
    val type: String,          // "movie" | "series"
    val year: Int,
    val runtime: Int            // minutes
)
