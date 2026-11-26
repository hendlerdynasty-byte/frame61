package de.hendler.frame61.data

import de.hendler.frame61.data.database.StreamEvidenceDao
import de.hendler.frame61.data.database.entities.StreamEvidence
import de.hendler.frame61.data.database.entities.StreamEvidenceOutcome
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data-only API for persistent title/episode stream evidence.
 *
 * No resolver or UI integration is implied. In particular, this API accepts a
 * redacted URL hash rather than a URL, and [StreamEvidence] has no credential
 * fields. Re-checking the same catalog/source/episode key replaces that row.
 */
@Singleton
class StreamEvidenceRepository @Inject constructor(
    private val dao: StreamEvidenceDao
) {
    suspend fun record(evidence: StreamEvidence): StreamEvidence {
        require(evidence.catalogId.isNotBlank()) { "catalogId must not be blank" }
        require(evidence.sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(evidence.sourceKey.isNotBlank()) { "sourceKey must not be blank" }
        require(evidence.season >= 0 && evidence.episode >= 0) {
            "season and episode must be non-negative"
        }
        require(evidence.checkedAt >= 0L) { "checkedAt must be non-negative" }
        require(evidence.identityScore.isFinite() && evidence.identityScore in 0.0..1.0) {
            "identityScore must be between 0.0 and 1.0"
        }
        require(evidence.httpStatus == null || evidence.httpStatus in 100..599) {
            "httpStatus must be null or between 100 and 599"
        }

        val redactedUrlHash = evidence.redactedUrlHash?.lowercase(Locale.ROOT)
        require(redactedUrlHash == null || REDACTED_URL_HASH.matches(redactedUrlHash)) {
            "redactedUrlHash must be a SHA-256 hex digest"
        }

        val stored = evidence.copy(
            id = streamEvidenceId(
                catalogId = evidence.catalogId,
                season = evidence.season,
                episode = evidence.episode,
                sourceId = evidence.sourceId,
                sourceKey = evidence.sourceKey
            ),
            redactedUrlHash = redactedUrlHash
        )
        dao.upsert(stored)
        return stored
    }

    suspend fun getById(id: Long): StreamEvidence? = dao.getById(id)

    suspend fun getByCatalogId(catalogId: String): List<StreamEvidence> =
        dao.getByCatalogId(catalogId)

    suspend fun getByEpisode(
        catalogId: String,
        season: Int,
        episode: Int
    ): List<StreamEvidence> = dao.getByEpisode(catalogId, season, episode)

    suspend fun getByEpisodeAndOutcome(
        catalogId: String,
        season: Int,
        episode: Int,
        outcome: StreamEvidenceOutcome
    ): List<StreamEvidence> =
        dao.getByEpisodeAndOutcome(catalogId, season, episode, outcome)

    suspend fun getByOutcome(outcome: StreamEvidenceOutcome): List<StreamEvidence> =
        dao.getByOutcome(outcome)

    private companion object {
        val REDACTED_URL_HASH = Regex("[0-9a-f]{64}")
    }
}

/**
 * Stable positive row id for the natural evidence key. Length-prefixing keeps
 * identifiers containing separators unambiguous before hashing.
 */
internal fun streamEvidenceId(
    catalogId: String,
    season: Int,
    episode: Int,
    sourceId: String,
    sourceKey: String
): Long {
    val identity = buildString {
        appendPart(catalogId)
        appendPart(season.toString())
        appendPart(episode.toString())
        appendPart(sourceId)
        appendPart(sourceKey)
    }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8))
    var value = 0L
    for (index in 0 until 8) {
        value = (value shl 8) or (digest[index].toLong() and 0xFF)
    }
    return (value and Long.MAX_VALUE).takeIf { it != 0L } ?: 1L
}

private fun StringBuilder.appendPart(value: String) {
    append(value.length).append(':').append(value).append('|')
}
