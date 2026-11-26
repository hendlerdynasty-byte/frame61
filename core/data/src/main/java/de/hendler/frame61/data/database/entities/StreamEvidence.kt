package de.hendler.frame61.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class StreamEvidenceOutcome {
    CORRECT,
    WRONG_CONTENT,
    TRAILER_ONLY,
    UNPLAYABLE
}

/**
 * Persistent result of checking one source stream for one catalog episode.
 *
 * This deliberately has no URL, header, cookie or token column. Callers must
 * reduce a URL to a SHA-256 hash of its redacted form before creating a row.
 * [sourceKey] is an opaque source-side identifier, not a playback credential.
 */
@Entity(
    tableName = "stream_evidence",
    indices = [
        Index(
            value = ["catalogId", "season", "episode", "sourceId", "sourceKey"],
            unique = true
        ),
        Index(value = ["catalogId", "season", "episode", "outcome"]),
        Index(value = ["outcome"])
    ]
)
data class StreamEvidence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val catalogId: String,
    val season: Int,
    val episode: Int,
    val sourceId: String,
    val sourceKey: String,
    val checkedAt: Long,
    val outcome: StreamEvidenceOutcome,
    val identityScore: Double,
    val httpStatus: Int?,
    val container: String?,
    /** SHA-256 of the caller-redacted URL; never the URL itself. */
    val redactedUrlHash: String?,
    /** Sanitized diagnostic text; must not contain credentials or signed URLs. */
    val reason: String
)
