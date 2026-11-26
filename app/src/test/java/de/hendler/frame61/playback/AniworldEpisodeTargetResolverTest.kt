package de.hendler.frame61.playback

import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.scraper.engine.EpisodePage
import de.hendler.frame61.scraper.engine.EpisodeResult
import de.hendler.frame61.scraper.engine.PackLoaderValidator
import de.hendler.frame61.scraper.engine.SearchResult
import de.hendler.frame61.scraper.engine.SourcePack
import de.hendler.frame61.scraper.engine.StreamResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AniworldEpisodeTargetResolverTest {
    private val pack = requireNotNull(
        PackLoaderValidator.parse(
            """
            {
              "id":"aniworld","name":"aniworld.to","baseUrl":"https://aniworld.to","kind":"html",
              "search":{"endpoint":"/ajax/search","format":"api","jsonPath":".","idPath":"link","titlePath":"title"},
              "detail":{"endpoint":"/anime/stream/{titleId}","format":"html","resultSelector":"body","titleSelector":"h1"},
              "episodes":{"endpoint":"/anime/stream/{titleId}","format":"html","episodeSelector":"tr"},
              "streams":{"endpoint":"/anime/stream/{titleId}/staffel-{season}/episode-{episodeId}","format":"html","selector":"li"}
            }
            """.trimIndent()
        )
    )
    private val item = CatalogItem(
        id = "aw:demo",
        title = "Demo Anime",
        type = "series",
        year = 2020,
        group = "aniworld",
        sourceKey = "demo"
    )
    private val hits = listOf(
        SearchResult(id = "other", title = "Other Anime"),
        SearchResult(id = "demo", title = "Demo Anime", year = 2020)
    )
    private val episodes = listOf(
        episode(1, 1, "7"),
        episode(1, 3, "41"),
        episode(2, 1, "509"),
        episode(2, 4, "900")
    )

    private fun episode(season: Int, number: Int, id: String) = EpisodeResult(
        id = id,
        season = season,
        episode = number,
        title = "S${season}E$number",
        watchUrl = "https://aniworld.to/anime/stream/demo/staffel-$season/episode-$id"
    )

    private class FakeAccess(
        var hits: List<SearchResult>,
        var episodes: List<EpisodeResult>,
        var pageTitle: String? = "Demo Anime (Quelle)",
        var pageYear: Int? = null
    ) : AniworldEpisodeAccess {
        var searchCalls = 0
        var episodePageCalls = 0
        var episodeTitleId: String? = null
        var streamCall: Triple<String, String, Int>? = null
        override suspend fun search(pack: SourcePack, title: String, cookies: String?): List<SearchResult> {
            searchCalls++
            return hits
        }
        override suspend fun episodePage(pack: SourcePack, titleId: String, cookies: String?): EpisodePage {
            episodePageCalls++
            episodeTitleId = titleId
            return EpisodePage(pageTitle, pageYear, episodes)
        }
        override suspend fun streams(
            pack: SourcePack,
            titleId: String,
            episodeId: String,
            cookies: String?,
            season: Int
        ): List<StreamResult> {
            streamCall = Triple(titleId, episodeId, season)
            return listOf(StreamResult(url = "https://hoster.invalid/$episodeId"))
        }
    }

    @Test
    fun `sourceKey umgeht suche und liefert quellentitel sowie katalogjahr`() = runBlocking {
        val access = FakeAccess(hits, episodes)
        val result = AniworldEpisodeTargetResolver(access).resolve(pack, item, season = 2, number = 4)
        assertEquals("demo", result?.hit?.id)
        assertEquals("Demo Anime (Quelle)", result?.hit?.title)
        assertEquals(2020, result?.hit?.year)
        assertEquals("900", result?.episode?.id)
        assertEquals(2, result?.episode?.season)
        assertEquals(4, result?.episode?.episode)
        assertEquals(0, access.searchCalls)
        assertEquals(1, access.episodePageCalls)
        assertEquals("demo", access.episodeTitleId)
        assertEquals(Triple("demo", "900", 2), access.streamCall)
    }

    @Test
    fun `gueltiger sourceKey braucht keinen katalogtitel fuer den seitenabruf`() = runBlocking {
        val access = FakeAccess(hits, episodes)
        val result = AniworldEpisodeTargetResolver(access).resolve(
            pack, item.copy(title = " "), season = 2, number = 1
        )
        assertEquals("Demo Anime (Quelle)", result?.hit?.title)
        assertEquals(0, access.searchCalls)
        assertEquals("demo", access.episodeTitleId)
    }

    @Test
    fun `quellenjahr auf der episodenseite hat vorrang vor katalogjahr`() = runBlocking {
        val access = FakeAccess(hits, episodes, pageYear = 2022)
        val result = AniworldEpisodeTargetResolver(access).resolve(pack, item, season = 2, number = 1)
        assertEquals(2022, result?.hit?.year)
        assertEquals(0, access.searchCalls)
        assertEquals(1, access.episodePageCalls)
    }

    @Test
    fun `nicht durchgehende id wird statt sichtbarer nummer adressiert`() = runBlocking {
        val access = FakeAccess(hits, episodes)
        val result = AniworldEpisodeTargetResolver(access).resolve(pack, item, season = 1, number = 3)
        assertEquals("41", result?.episode?.id)
        assertEquals(Triple("demo", "41", 1), access.streamCall)
    }

    @Test
    fun `falsche folge hat weder ersten noch nummer fallback`() = runBlocking {
        val access = FakeAccess(hits, episodes)
        val result = AniworldEpisodeTargetResolver(access).resolve(pack, item, season = 2, number = 2)
        assertNull(result)
        assertEquals(1, access.episodePageCalls)
        assertNull(access.streamCall)
    }

    @Test
    fun `falsche staffel wird nicht durch dieselbe folgennummer ersetzt`() = runBlocking {
        val access = FakeAccess(hits, episodes)
        assertNull(AniworldEpisodeTargetResolver(access).resolve(pack, item, season = 3, number = 1))
        assertNull(access.streamCall)
    }

    @Test
    fun `ohne sourceKey zaehlt nur ein exakt normalisierter suchtreffer`() = runBlocking {
        val access = FakeAccess(hits, episodes)
        val foreignItem = item.copy(group = "other", sourceKey = "not-der-aniworld-key")
        val result = AniworldEpisodeTargetResolver(access).resolve(pack, foreignItem, season = 2, number = 1)
        assertEquals("demo", result?.hit?.id)
        assertEquals("Demo Anime (Quelle)", result?.hit?.title)
        assertEquals("509", result?.episode?.id)
        assertEquals(1, access.searchCalls)
        assertEquals(1, access.episodePageCalls)
    }

    @Test
    fun `ungueltiger sourceKey verwendet exakte suche`() = runBlocking {
        val access = FakeAccess(hits, episodes)
        val result = AniworldEpisodeTargetResolver(access).resolve(
            pack, item.copy(sourceKey = "demo/with-path"), season = 2, number = 1
        )
        assertEquals("demo", result?.hit?.id)
        assertEquals(1, access.searchCalls)
        assertEquals(1, access.episodePageCalls)
    }

    @Test
    fun `unbekannter suchtreffer laedt keine episodenseite`() = runBlocking {
        val access = FakeAccess(listOf(SearchResult("other", "Other Anime")), episodes)
        assertNull(
            AniworldEpisodeTargetResolver(access).resolve(
                pack, item.copy(sourceKey = null), season = 1, number = 1
            )
        )
        assertEquals(1, access.searchCalls)
        assertEquals(0, access.episodePageCalls)
        assertNull(access.streamCall)
    }

    @Test
    fun `fehlender quellentitel wird nicht durch katalogtitel ersetzt`() = runBlocking {
        val access = FakeAccess(hits, episodes, pageTitle = " ")
        assertNull(AniworldEpisodeTargetResolver(access).resolve(pack, item, season = 1, number = 1))
        assertEquals(0, access.searchCalls)
        assertEquals(1, access.episodePageCalls)
        assertNull(access.streamCall)
    }

    @Test
    fun `abweichende watch url belegt das ziel nicht`() = runBlocking {
        val access = FakeAccess(
            hits,
            episodes.map { if (it.id == "509") it.copy(watchUrl = "https://aniworld.to/anime/stream/demo/staffel-1/episode-509") else it }
        )
        assertNull(AniworldEpisodeTargetResolver(access).resolve(pack, item, season = 2, number = 1))
        assertNull(access.streamCall)
    }

    @Test
    fun `mehrdeutige exakte episodenzeile wird abgelehnt`() = runBlocking {
        val access = FakeAccess(hits, episodes + episode(2, 1, "510"))
        assertNull(AniworldEpisodeTargetResolver(access).resolve(pack, item, season = 2, number = 1))
        assertNull(access.streamCall)
    }
}
