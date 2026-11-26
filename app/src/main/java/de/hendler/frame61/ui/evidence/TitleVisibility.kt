package de.hendler.frame61.ui.evidence

import de.hendler.frame61.data.StreamEvidenceRepository
import de.hendler.frame61.data.database.entities.StreamEvidence
import de.hendler.frame61.data.database.entities.StreamEvidenceOutcome
import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.meta.SearchHit
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Catalog visibility derived exclusively from confirmed stream evidence.
 *
 * A missing row, an unreadable evidence store and an unreadable catalog all
 * fail closed. The raw catalog is never used as a visibility fallback.
 */
@Singleton
class TitleVisibility @Inject constructor(
    private val catalog: CatalogRepository,
    private val evidenceRepository: StreamEvidenceRepository
) {

    /** Visible catalog entries in their original catalog order. */
    suspend fun visibleCatalog(): List<CatalogItem> {
        val evidenceById = correctEvidenceById() ?: return emptyList()
        val items = try {
            catalog.all()
        } catch (_: Exception) {
            return emptyList()
        }
        return items.filter { item ->
            evidenceById[item.id].orEmpty().any { row -> row.isVisibleFor(item.type) }
        }
    }

    /** Whether this exact catalog id has qualifying evidence. */
    suspend fun isVisible(catalogId: String): Boolean {
        if (catalogId.isBlank()) return false
        val item = try {
            catalog.byId(catalogId)
        } catch (_: Exception) {
            return false
        } ?: return false
        val evidenceById = correctEvidenceById() ?: return false
        return evidenceById[catalogId].orEmpty().any { row -> row.isVisibleFor(item.type) }
    }

    /**
     * Returns the canonical catalog entry represented by a search hit.
     *
     * An exact external-id match takes precedence. Otherwise title, year and
     * type must all match after normalization, and the match must be unique.
     */
    fun canonicalSearchHit(hit: SearchHit, catalog: List<CatalogItem>): SearchHit? {
        if (hit.externalId.isNotBlank()) {
            val idMatches = catalog.filter { it.id == hit.externalId }
            if (idMatches.size > 1) return null
            idMatches.singleOrNull()?.let { return it.toCanonicalHit(hit) }
        }

        val normalizedTitle = normalizeTitle(hit.title)
        val normalizedType = normalizeType(hit.type) ?: return null
        if (normalizedTitle.isEmpty()) return null

        return catalog.singleOrNull { item ->
            item.year == hit.year &&
                normalizeType(item.type) == normalizedType &&
                normalizeTitle(item.title) == normalizedTitle
        }?.toCanonicalHit(hit)
    }

    /**
     * Canonical evidence coordinates: movies always use 0/0; a series without
     * an episode number uses its first episode. Unknown types have no valid
     * evidence coordinate and return the invalid sentinel (-1, -1).
     */
    fun evidenceCoordinates(
        type: String?,
        season: Int,
        episode: Int
    ): Pair<Int, Int> = when (normalizeType(type)) {
        TYPE_MOVIE -> 0 to 0
        TYPE_SERIES -> season to episode.coerceAtLeast(FIRST_EPISODE)
        else -> INVALID_EVIDENCE_COORDINATES
    }

    private suspend fun correctEvidenceById(): Map<String, List<StreamEvidence>>? = try {
        evidenceRepository.getByOutcome(StreamEvidenceOutcome.CORRECT)
            .filter { it.outcome == StreamEvidenceOutcome.CORRECT && it.catalogId.isNotBlank() }
            .groupBy { it.catalogId }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun StreamEvidence.isVisibleFor(type: String?): Boolean =
        when (normalizeType(type)) {
            TYPE_MOVIE -> season == 0 && episode == 0
            TYPE_SERIES -> season >= 0 && episode > 0
            else -> false
        }

    private fun CatalogItem.toCanonicalHit(source: SearchHit): SearchHit = SearchHit(
        externalId = id,
        title = title,
        year = year,
        type = type,
        poster = poster,
        overview = overview,
        lang = source.lang,
        runtime = runtime
    )

    private fun normalizeType(value: String?): String? =
        value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

    private fun normalizeTitle(value: String): String {
        val decomposed = Normalizer.normalize(
            value.trim().lowercase(Locale.ROOT),
            Normalizer.Form.NFD
        )
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
    }

    private companion object {
        const val TYPE_MOVIE = "movie"
        const val TYPE_SERIES = "series"
        const val FIRST_EPISODE = 1
        val INVALID_EVIDENCE_COORDINATES = -1 to -1
    }
}
