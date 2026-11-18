package de.hendler.frame61.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Test
import java.io.File

/**
 * The bundled catalog is the offline backbone of the app, so it is tested
 * without network. The asset is handed over as the system property
 * `frame61.catalogFile` (set in this module's build file).
 */
class CatalogRepositoryTest {

    private val catalogFile: File? = System.getProperty("frame61.catalogFile")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.isFile }

    private fun repo(): CatalogRepository =
        catalogFile?.let(CatalogRepository::fromFile)
            ?: throw AssumptionViolatedException(
                "frame61.catalogFile not set or missing (value=${System.getProperty("frame61.catalogFile")})"
            )

    @Test
    fun `catalog loads and is usable offline`() {
        val items = repo().all()
        assertTrue("catalog is too small: ${items.size}", items.size >= 1_000)
        assertTrue("every item needs a title", items.all { it.title.isNotBlank() })
        assertTrue("every item needs a type", items.all { it.type == "movie" || it.type == "series" })
        assertTrue("too few movies: ${items.count { it.type == "movie" }}", items.count { it.type == "movie" } >= 400)
        assertTrue(
            "posters must be bundled locally",
            items.all { it.poster.startsWith("file:///android_asset/posters/") }
        )
        assertEquals(
            "title/year duplicates",
            items.size,
            items.distinctBy { it.title.trim().lowercase() + "|" + (it.year ?: 0) }.size
        )
    }

    @Test
    fun `rails return content`() {
        val repo = repo()
        assertTrue("topRated empty", repo.topRated(10).isNotEmpty())
        assertTrue("newest empty", repo.newest(10).isNotEmpty())
        assertTrue("genres empty", repo.genres().isNotEmpty())
        assertTrue("shuffled empty", repo.shuffled(42).isNotEmpty())
        assertEquals(10, repo.topRated(10).size)
    }

    @Test
    fun `genres and lookup work`() {
        val repo = repo()
        val genre = repo.genres().first()
        assertTrue("'${genre}' rail is empty", repo.byGenre(genre).isNotEmpty())

        val first = repo.all().first()
        assertNotNull(repo.byId(first.id))
        assertEquals(first.title, repo.byId(first.id)?.title)
    }

    @Test
    fun `shuffle is deterministic for a seed`() {
        val repo = repo()
        val a = repo.shuffled(7).map { it.id }
        val b = repo.shuffled(7).map { it.id }
        assertEquals(a, b)
        assertEquals(repo.all().size, a.size)
    }

    @Test
    fun `overviews are plain text`() {
        val items = repo().all()
        assertTrue(
            "catalog overviews still contain markup: " +
                items.filter { '<' in it.overview }.take(3).map { it.title },
            items.none { '<' in it.overview || '>' in it.overview }
        )
    }

    @Test
    fun `bundled source finds titles offline`() = kotlinx.coroutines.runBlocking {
        val source = BundledCatalogSource(repo())
        val hits = source.search("dark", 5)
        assertTrue("'dark' found nothing in the catalog", hits.isNotEmpty())
        assertEquals("catalog", source.id)
    }

    @Test
    fun `chained source merges without duplicates`() = kotlinx.coroutines.runBlocking {
        val repo = repo()
        val chained = ChainedMetadataSource(listOf(BundledCatalogSource(repo), BundledCatalogSource(repo)))
        val once = BundledCatalogSource(repo).search("", 10)
        val twice = chained.search("", 10)
        assertEquals("duplicates were merged", once.map { it.externalId }, twice.map { it.externalId })
    }
}