package de.hendler.frame61.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.hendler.frame61.data.database.entities.Profile

/**
 * Recommendation vector
 */
@Entity(
    tableName = "rec_vectors",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profileId", "tag"], unique = true)
    ]
)
data class RecVector(
    @PrimaryKey val id: Long = 0,
    val profileId: Long,
    val tag: String,
    val weight: Double
)
