package de.hendler.frame61.meta

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client talks only to these snapshots. The test proves the local index,
 * the missing-file fallback and the episode mapping without any network.
 */
class SnapshotRepositoryTest {

    private val index = """
        {
          "schema": 1,
          "generatedAt": "2026-11-17T00:00:00Z",
          "entries": [
            {"id": "tvmaze-1", "title": "Dark", "year": 2017, "type": "series", "poster": "p1"},
            {"id": "wiki-1", "title": "Dune", "year": 2021, "type": "movie", "poster": "p2"}
          ]
        }
    """.trimIndent()

    private val dark = """
        {
          "schema": 1, "id": "tvmaze-1", "source": "tvmaze", "sourceId": "1",
          "fetchedAt": "2026-11-17T00:00:00Z", "live": true,
          "attribution": "TVmaze (CC BY-SA 4.0)", "title": "Dark", "year": 2017,
          "type": "series", "overview": "Eine Familiensaga in Winden.", "poster": "p1",
          "genres": ["Drama", "Mystery"], "rating": 8.8, "runtime": 60,
          "episodes": [
            {"id": 11, "season": 1, "number": 1, "name": "Geheimnisse", "runtime": 51},
            {"id": 12, "season": 1, "number": 2, "name": "Lügen", "runtime": 45, "image": null}
          ]
        }
    """.trimIndent()

    private val dune = """
        {
          "schema": 1, "id": "wiki-1", "source": "catalog", "live": false,
          "title": "Dune", "year": 2021, "type": "movie",
          "overview": "Ein Wüstenplanet.", "poster": "p2", "genres": ["Sci-Fi"],
          "rating": 8.0, "episodes": []
        }
    """.trimIndent()

    private fun repo(): SnapshotRepository =
        SnapshotRepository.fromJson(index, mapOf("tvmaze-1" to dark, "wiki-1" to dune))

    @Test
    fun `index and lookups work`() {
        val repo = repo()
        assertEquals(2, repo.index().entries.size)
        assertEquals("2026-11-17T00:00:00Z", repo.generatedAt())
        assertNotNull(repo.byId("tvmaze-1"))
        assertNull(repo.byId("fehlt"))
    }

    @Test
    fun `search finds title genre and overview`() {
        val repo = repo()
        assertEquals("tvmaze-1", repo.search("dark").single().id)
        assertEquals("tvmaze-1", repo.search("mystery").single().id)
        assertEquals("wiki-1", repo.search("wüstenplanet").single().id)
        assertTrue(repo.search("gibtsnicht").isEmpty())
    }

    @Test
    fun `blank query returns best rated`() {
        val first = repo().search("", 2).first()
        assertTrue("rating missing", (first.rating ?: 0.0) >= 8.0)
    }

    @Test
    fun `live flag comes from the snapshot`() {
        assertEquals(1, repo().all().count { it.live })
    }

    @Test
    fun `episodes map without network`() = runBlocking {
        val episodes = SnapshotEpisodeSource(repo()).episodes("tvmaze-1")
        assertEquals(2, episodes.size)
        assertEquals("S01 E01 · Geheimnisse", episodes.first().label)
        assertNull(episodes[1].still)
        assertTrue(SnapshotEpisodeSource(repo()).episodes("wiki-1").isEmpty())
    }
}
