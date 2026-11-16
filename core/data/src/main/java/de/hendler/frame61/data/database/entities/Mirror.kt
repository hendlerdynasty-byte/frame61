package de.hendler.frame61.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.hendler.frame61.data.database.entities.Title

/**
 * Mirror entity — Site titles are mirrors, not identity
 */
@Entity(
    tableName = "mirrors",
    foreignKeys = [
        ForeignKey(
            entity = Title::class,
            parentColumns = ["id"],
            childColumns = ["titleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["titleId", "sourceId"], unique = true)]
)
data class Mirror(
    @PrimaryKey val id: Long = 0,
    val titleId: Long,
    val sourceId: String,
    val lang: String,
    val quality: String,        // "4K" | "1080p" | "720p" | "480p"
    val hosterId: String,
    val healthScore: Double,    // 0.0 — 1.0
    val lastVerified: Long      // epoch millis
)
