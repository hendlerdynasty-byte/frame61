package de.hendler.frame61.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stable numeric title id must survive restarts and stay unique. */
class CatalogPlaybackTest {

    @Test
    fun `stable id is deterministic`() {
        assertEquals(
            CatalogPlayback.stableId("tvmaze-12345"),
            CatalogPlayback.stableId("tvmaze-12345")
        )
    }

    @Test
    fun `stable id is positive`() {
        assertTrue(CatalogPlayback.stableId("tvmaze-12345") > 0L)
    }

    @Test
    fun `stable id differs between titles`() {
        assertNotEquals(
            CatalogPlayback.stableId("tvmaze-1"),
            CatalogPlayback.stableId("tvmaze-2")
        )
    }

    @Test
    fun `stable id does not collide with hashCode neighbours`() {
        // hashCode is not a stable identifier across Android versions; the SHA-1
        // based id must not depend on it.
        val id = CatalogPlayback.stableId("tvmaze-12345")
        assertNotEquals("tvmaze-12345".hashCode().toLong(), id)
    }
}

/** Search results are merged and de-duplicated across sources. */
class SearchMergeTest {

    private fun hit(id: String, title: String, year: Int?) =
        de.hendler.frame61.meta.SearchHit(id, title, year, "series", null, null)

    @Test
    fun `duplicate titles collapse to one entry`() {
        val a = listOf(hit("1", "Dark", 2017))
        val b = listOf(hit("2", "Dark", 2017))
        val merged = LinkedHashMap<String, de.hendler.frame61.meta.SearchHit>()
        (a + b).forEach { merged.putIfAbsent("${it.title}|${it.year}", it) }
        assertEquals(1, merged.size)
    }

    @Test
    fun `different years keep both entries`() {
        val hits = listOf(hit("1", "Dark", 2017), hit("2", "Dark", 2022))
        val merged = LinkedHashMap<String, de.hendler.frame61.meta.SearchHit>()
        hits.forEach { merged.putIfAbsent("${it.title}|${it.year}", it) }
        assertEquals(2, merged.size)
    }

    @Test
    fun `empty query returns the top rated catalog`() =
        kotlinx.coroutines.runBlocking {
            val repo = de.hendler.frame61.meta.CatalogRepository.fromJson(
                """
                {"version":1,"items":[
                  {"id":"a","title":"A","type":"movie","year":2020,"rating":6.0},
                  {"id":"b","title":"B","type":"movie","year":2021,"rating":9.0}
                ]}
                """.trimIndent()
            )
            val source = de.hendler.frame61.meta.BundledCatalogSource(repo)
            val hits = source.search("", 10)
            assertEquals(listOf("b", "a"), hits.map { it.externalId })
        }
}