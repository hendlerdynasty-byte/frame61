package de.hendler.frame61.scraper.engine

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bewusst synthetisches Fixture: zwei Staffeln, nicht durchgehende interne IDs
 * und eine Zeile ohne Staffelbeleg. Es bildet nur die verwendeten Marker ab und
 * behauptet keinen Live-Abgriff von aniworld.to.
 */
class AniworldEpisodeParserTest {
    private val interpreter = PackInterpreter()
    private val pack = requireNotNull(
        PackLoaderValidator.parse(
            """
            {
              "id":"aniworld","name":"aniworld.to","baseUrl":"https://aniworld.to","kind":"html",
              "detail":{"endpoint":"/anime/stream/{titleId}","format":"html","resultSelector":"body","titleSelector":"h1","yearSelector":"span.year"},
              "episodes":{
                "endpoint":"/anime/stream/{titleId}","format":"html",
                "episodeSelector":"table.seasonEpisodesList tbody tr",
                "episodeTitleSelector":"td.seasonEpisodeTitle",
                "episodeLinkSelector":"td.seasonEpisodeTitle a",
                "episodeIndexSelector":"td[class*=\"EpisodeID\"]"
              },
              "streams":{"endpoint":"/anime/stream/{titleId}/staffel-{season}/episode-{episodeId}","selector":"li"}
            }
            """.trimIndent()
        )
    )

    private val html = """
        <html><body>
        <h1>Demo Anime</h1><span class="year">2022</span>
        <table class="seasonEpisodesList" data-season="1"><tbody>
          <tr><td class="season1EpisodeID">1</td><td class="seasonEpisodeTitle"><a href="/anime/stream/demo/staffel-1/episode-7">Start</a></td></tr>
          <tr><td class="season1EpisodeID">3</td><td class="seasonEpisodeTitle"><a href="/anime/stream/demo/staffel-1/episode-41">Dritte Folge</a></td></tr>
        </tbody></table>
        <table class="seasonEpisodesList" data-season="2"><tbody>
          <tr><td class="season2EpisodeID">1</td><td class="seasonEpisodeTitle"><a href="/anime/stream/demo/staffel-2/episode-509">Neue Staffel</a></td></tr>
          <tr><td class="season2EpisodeID">4</td><td class="seasonEpisodeTitle"><a href="/anime/stream/demo/staffel-2/episode-900">Vierte Folge</a></td></tr>
        </tbody></table>
        <table class="seasonEpisodesList"><tbody>
          <tr><td class="seasonEpisodeID">2</td><td class="seasonEpisodeTitle"><a href="/anime/stream/demo/episode-77">Unbelegte Staffel</a></td></tr>
        </tbody></table>
        </body></html>
    """.trimIndent()

    @Test
    fun `zwei staffeln behalten interne ids und sichtbare folgennummern`() {
        val episodes = interpreter.parseEpisodesHtml(pack, html)
        assertEquals(5, episodes.size)
        assertEquals(
            listOf("7", "41", "509", "900", "77"),
            episodes.map { it.id }
        )
        assertEquals(listOf(1, 1, 2, 2, 0), episodes.map { it.season })
        assertEquals(listOf(1, 3, 1, 4, 2), episodes.map { it.episode })
        assertTrue(episodes.none { it.id.startsWith("episode-") })
        assertEquals(
            "https://aniworld.to/anime/stream/demo/staffel-2/episode-509",
            episodes[2].watchUrl
        )
    }

    @Test
    fun `episodenseite liefert titel jahr und episoden mit einem request`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(html))
            val page = PackInterpreter(PackHttpClient(OkHttpClient())).episodesWithTitle(pack, "demo")
            assertEquals("Demo Anime", page?.title)
            assertEquals(2022, page?.year)
            assertEquals(5, page?.episodes?.size)
            assertEquals(1, server.requestCount)
            assertEquals("/anime/stream/demo", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `api episoden id wird ebenfalls um episode praefix bereinigt`() {
        val apiPack = requireNotNull(
            PackLoaderValidator.parse(
                """
                {"id":"aniworld","name":"aniworld.to","baseUrl":"https://aniworld.to","kind":"api",
                 "episodes":{"endpoint":"/episodes/{titleId}","jsonPath":".","idPath":"id","titlePath":"title","episodeSeasonPath":"season","episodeNumberPath":"number"}}
                """.trimIndent()
            )
        )
        val episode = interpreter.parseEpisodesApi(
            apiPack,
            """[{"id":"episode-509","title":"Folge","season":2,"number":4}]"""
        ).single()
        assertEquals("509", episode.id)
        assertEquals(2, episode.season)
        assertEquals(4, episode.episode)
    }
}
