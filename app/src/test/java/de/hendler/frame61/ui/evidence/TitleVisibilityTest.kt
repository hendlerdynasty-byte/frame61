package de.hendler.frame61.ui.evidence

import de.hendler.frame61.data.StreamEvidenceRepository
import de.hendler.frame61.data.database.entities.StreamEvidence
import de.hendler.frame61.data.database.entities.StreamEvidenceOutcome
import de.hendler.frame61.meta.Catalog
import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.meta.SearchHit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleVisibilityTest {

    private val evidenceRepository = mockk<StreamEvidenceRepository>()

    @Test
    fun `movie is visible only with exact zero zero evidence`() = runTest {
        val movie = item("movie", "Movie", "movie", 2020)
        val visibility = TitleVisibility(catalog(movie), evidenceRepository)
        coEvery { evidenceRepository.getByOutcome(StreamEvidenceOutcome.CORRECT) } returnsMany listOf(
            listOf(evidence("movie", season = 1, episode = 1)),
            listOf(evidence("movie", season = 0, episode = 1)),
            listOf(evidence("movie", season = 0, episode = 0))
        )

        assertFalse(visibility.isVisible("movie"))
        assertTrue(visibility.visibleCatalog().isEmpty())
        assertEquals(listOf(movie), visibility.visibleCatalog())
        coVerify(exactly = 3) {
            evidenceRepository.getByOutcome(StreamEvidenceOutcome.CORRECT)
        }
    }

    @Test
    fun `series is visible only with a positive episode`() = runTest {
        val series = item("series", "Series", "series", 2020)
        val visibility = TitleVisibility(catalog(series), evidenceRepository)
        coEvery { evidenceRepository.getByOutcome(StreamEvidenceOutcome.CORRECT) } returnsMany listOf(
            listOf(evidence("series", season = 0, episode = 0)),
            listOf(evidence("series", season = 2, episode = 0)),
            listOf(evidence("series", season = 0, episode = 1))
        )

        assertFalse(visibility.isVisible("series"))
        assertFalse(visibility.isVisible("series"))
        assertTrue(visibility.isVisible("series"))
    }

    @Test
    fun `unknown catalog type never becomes visible`() = runTest {
        val unknown = item("unknown", "Unknown", "documentary", 2020)
        val visibility = TitleVisibility(catalog(unknown), evidenceRepository)
        coEvery { evidenceRepository.getByOutcome(StreamEvidenceOutcome.CORRECT) } returns listOf(
            evidence("unknown", season = 0, episode = 0),
            evidence("unknown", season = 1, episode = 1)
        )

        assertFalse(visibility.isVisible("unknown"))
        assertTrue(visibility.visibleCatalog().isEmpty())
    }

    @Test
    fun `only the correct outcome grants visibility`() = runTest {
        val movie = item("movie", "Movie", "movie", 2020)
        val visibility = TitleVisibility(catalog(movie), evidenceRepository)
        coEvery { evidenceRepository.getByOutcome(StreamEvidenceOutcome.CORRECT) } returns listOf(
            evidence("movie", outcome = StreamEvidenceOutcome.WRONG_CONTENT),
            evidence("movie", outcome = StreamEvidenceOutcome.TRAILER_ONLY),
            evidence("movie", outcome = StreamEvidenceOutcome.UNPLAYABLE)
        )

        assertTrue(visibility.visibleCatalog().isEmpty())
        assertFalse(visibility.isVisible("movie"))
        coVerify(exactly = 2) {
            evidenceRepository.getByOutcome(StreamEvidenceOutcome.CORRECT)
        }
    }

    @Test
    fun `evidence is grouped by catalog id and catalog order is preserved`() = runTest {
        val first = item("first", "First", "movie", 2020)
        val second = item("second", "Second", "series", 2021)
        val hidden = item("hidden", "Hidden", "movie", 2022)
        val visibility = TitleVisibility(catalog(first, second, hidden), evidenceRepository)
        coEvery { evidenceRepository.getByOutcome(StreamEvidenceOutcome.CORRECT) } returns listOf(
            evidence("second", season = 1, episode = 1),
            evidence("orphan", season = 0, episode = 0),
            evidence("first", season = 0, episode = 0),
            evidence("first", season = 0, episode = 0),
            evidence("second", season = 1, episode = 2)
        )

        assertEquals(listOf(first, second), visibility.visibleCatalog())
        assertFalse(visibility.isVisible("orphan"))
        assertFalse(visibility.isVisible("missing"))
    }

    @Test
    fun `evidence failure fails closed without raw catalog fallback`() = runTest {
        val movie = item("movie", "Movie", "movie", 2020)
        val visibility = TitleVisibility(catalog(movie), evidenceRepository)
        coEvery { evidenceRepository.getByOutcome(StreamEvidenceOutcome.CORRECT) } throws
            IOException("evidence database unavailable")

        assertTrue(visibility.visibleCatalog().isEmpty())
        assertFalse(visibility.isVisible("movie"))
    }

    @Test
    fun `catalog failure also fails closed`() = runTest {
        val visibility = TitleVisibility(CatalogRepository.fromJson("{"), evidenceRepository)
        coEvery { evidenceRepository.getByOutcome(StreamEvidenceOutcome.CORRECT) } returns listOf(
            evidence("movie", season = 0, episode = 0)
        )

        assertTrue(visibility.visibleCatalog().isEmpty())
        assertFalse(visibility.isVisible("movie"))
    }

    @Test
    fun `evidence coordinates canonicalize movies and first series episodes`() {
        val visibility = TitleVisibility(catalog(), evidenceRepository)

        assertEquals(0 to 0, visibility.evidenceCoordinates(" MOVIE ", 9, 7))
        assertEquals(2 to 1, visibility.evidenceCoordinates("series", 2, 0))
        assertEquals(2 to 4, visibility.evidenceCoordinates("series", 2, 4))
        assertEquals(-1 to -1, visibility.evidenceCoordinates("documentary", 0, 0))
        assertEquals(-1 to -1, visibility.evidenceCoordinates(null, 0, 0))
    }

    @Test
    fun `direct external id wins and returns canonical catalog metadata`() {
        val canonical = item(
            id = "catalog-movie",
            title = "Canonical Movie",
            type = "movie",
            year = 2020,
            poster = "canonical-poster",
            overview = "canonical-overview",
            runtime = 120
        )
        val hit = SearchHit(
            externalId = "catalog-movie",
            title = "Different source title",
            year = 1999,
            type = "series",
            poster = "source-poster",
            overview = "source-overview",
            lang = "de",
            runtime = 42
        )

        assertEquals(
            SearchHit(
                externalId = "catalog-movie",
                title = "Canonical Movie",
                year = 2020,
                type = "movie",
                poster = "canonical-poster",
                overview = "canonical-overview",
                lang = "de",
                runtime = 120
            ),
            TitleVisibility(catalog(canonical), evidenceRepository)
                .canonicalSearchHit(hit, listOf(canonical))
        )
    }

    @Test
    fun `title year and type match after exact normalization`() {
        val canonical = item("amelie", "Amélie", "movie", 2001)
        val hit = SearchHit(
            externalId = "source-42",
            title = "  AMELIE!  ",
            year = 2001,
            type = " MOVIE "
        )

        assertEquals(
            "amelie",
            TitleVisibility(catalog(canonical), evidenceRepository)
                .canonicalSearchHit(hit, listOf(canonical))?.externalId
        )
    }

    @Test
    fun `title fallback requires exact year and type`() {
        val canonical = item("dune", "Dune", "movie", 2021)
        val visibility = TitleVisibility(catalog(canonical), evidenceRepository)

        assertNull(visibility.canonicalSearchHit(hit("source", "Dune", 2022, "movie"), listOf(canonical)))
        assertNull(visibility.canonicalSearchHit(hit("source", "Dune", 2021, "series"), listOf(canonical)))
        assertNull(visibility.canonicalSearchHit(hit("source", "Dune", null, "movie"), listOf(canonical)))
        assertNull(visibility.canonicalSearchHit(hit("source", "Dune", 2021, null), listOf(canonical)))
    }

    @Test
    fun `ambiguous normalized title match is rejected`() {
        val first = item("first", "The Matrix!", "movie", 1999)
        val second = item("second", "the matrix", "movie", 1999)
        val catalog = listOf(first, second)

        assertNull(
            TitleVisibility(catalog(), evidenceRepository)
                .canonicalSearchHit(hit("source", "THE MATRIX", 1999, "movie"), catalog)
        )
        assertEquals(
            "second",
            TitleVisibility(catalog(), evidenceRepository)
                .canonicalSearchHit(hit("second", "Ignored", null, null), catalog)?.externalId
        )
    }

    private fun catalog(vararg items: CatalogItem): CatalogRepository =
        CatalogRepository.fromJson(Json.encodeToString(Catalog(items = items.toList())))

    private fun item(
        id: String,
        title: String,
        type: String,
        year: Int?,
        poster: String = "",
        overview: String = "",
        runtime: Int? = null
    ) = CatalogItem(
        id = id,
        title = title,
        type = type,
        year = year,
        poster = poster,
        overview = overview,
        runtime = runtime
    )

    private fun hit(externalId: String, title: String, year: Int?, type: String?) =
        SearchHit(externalId = externalId, title = title, year = year, type = type)

    private fun evidence(
        catalogId: String,
        season: Int = 0,
        episode: Int = 0,
        outcome: StreamEvidenceOutcome = StreamEvidenceOutcome.CORRECT
    ) = StreamEvidence(
        catalogId = catalogId,
        season = season,
        episode = episode,
        sourceId = "test-source",
        sourceKey = "test-stream",
        checkedAt = 1_000L,
        outcome = outcome,
        identityScore = 1.0,
        httpStatus = 200,
        container = "hls",
        redactedUrlHash = null,
        reason = "test"
    )
}
