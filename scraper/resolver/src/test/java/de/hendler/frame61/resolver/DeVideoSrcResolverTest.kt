package de.hendler.frame61.resolver

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Antwort der Player-Seite bzw. der API, unverändert wie am 23.11.2026 erhoben. */
private const val MOVIE_PAGE = """
<html><body><script>
        fetch('/api/embed-links', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ type: 'movie', id: "tt0111161", token: "bW92aWV8dHQwMTExMTYxfDB8MHwxNzk1NDcxMjAw.429623e441215d70e4c6b7a0f348b9a32969f3f70f576b2c3ef167dde709b47f" })
        }).then(function (r) { return r.json(); }).then(function (data) {});
</script></body></html>
"""

private const val MOVIE_ANSWER = """
{
 "ok": true,
 "type": "movie",
 "sources": [
  {"id":"dropload-io-15","name":"dropload.io","url":"https://dr0pstream.com/e/0491e1wi4xt4","embed":true,"kind":"embed","rank":1},
  {"id":"supervideo-tv-5","name":"supervideo.tv","url":"https://superplayer.ws/e/hdt9dkeh23i6","embed":true,"kind":"embed","rank":2},
  {"id":"doodstream-com-6","name":"doodstream.com","url":"https://doodstream.com/e/pa8mnbfskecj","embed":true,"kind":"embed","rank":3},
  {"id":"mixdrop-ag-1","name":"mixdrop.ag","url":"https://mxdrop.to/e/r696kd9xte38v9","embed":true,"kind":"embed","rank":4}
 ]
}
"""

private const val SERIAL_PAGE = """
<html><body><script>
      fetch('/api/embed-links', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type: 'tv', id: "tt0903747", token: "dHZ8dHQwOTAzNzQ3fDB8MHwxNzk1NDcxMjc0.7637029761251933ca0f2ddd0defbb183a6943a9a025f738eaac1cde6afd00e6" })
      }).then(function (r) { return r.json(); }).then(function (data) {});
</script></body></html>
"""

/** Zwei Staffeln mit zwei Folgen: Staffel 2, Folge 2 hat eigene Quellen. */
private const val SERIAL_ANSWER = """
{
 "ok": true,
 "type": "tv",
 "tv": {
  "title": "Breaking Bad",
  "seasons": [
   {"season_number": 1, "episodes": [
     {"episode_number": 1, "sources": [{"name":"dropload.io","url":"https://dr0pstream.com/e/s1e1","rank":1}]},
     {"episode_number": 2, "sources": [{"name":"dropload.io","url":"https://dr0pstream.com/e/s1e2","rank":1}]}
   ]},
   {"season_number": 2, "episodes": [
     {"episode_number": 1, "sources": [{"name":"supervideo.tv","url":"https://superplayer.ws/e/s2e1","rank":1}]},
     {"episode_number": 2, "sources": [
       {"name":"dropload.io","url":"https://dr0pstream.com/e/s2e2","rank":2},
       {"name":"supervideo.tv","url":"https://superplayer.ws/e/s2e2","rank":1},
       {"name":"mixdrop.ag","url":"https://mxdrop.to/e/s2e2","rank":4}
     ]}
   ]}
  ]
 }
}
"""

private class ScriptedFetcher(
    val pages: Map<String, String> = emptyMap(),
    val answers: Map<String, String> = emptyMap()
) : BodyFetcher {
    val calls = mutableListOf<String>()

    override suspend fun get(url: String, headers: Map<String, String>): String {
        calls += "GET $url"
        return pages[url] ?: ""
    }

    override suspend fun post(url: String, body: String, headers: Map<String, String>): String {
        calls += "POST $url $body"
        return answers[url] ?: ""
    }
}

class DeVideoSrcResolverTest {

    @Test
    fun `domain match covers subdomains and rejects lookalikes`() {
        assertTrue(DeVideoSrcResolver.matches("https://devideosrc.co/movie/550"))
        assertTrue(DeVideoSrcResolver.matches("https://www.devideosrc.com/movie/550"))
        assertTrue(DeVideoSrcResolver.matches("https://cdn.devideosrc.net/serial/tt0903747"))
        assertFalse(DeVideoSrcResolver.matches("https://notdevideosrc.co/movie/550"))
        assertFalse(DeVideoSrcResolver.matches("https://devideosrc.co.evil.io/movie/550"))
        assertFalse(DeVideoSrcResolver.matches("https://aniworld.to/redirect/2461081"))
    }

    @Test
    fun `page request carries type id and token`() {
        val request = DeVideoSrcResolver.parsePageRequest(MOVIE_PAGE)
        assertEquals("movie", request?.kind)
        assertEquals("tt0111161", request?.id)
        assertTrue(request?.token?.startsWith("bW92aWV8") == true)
    }

    @Test
    fun `page request falls back to loose fields`() {
        // Umschriebene Seite: kein JSON.stringify-Block, aber die Felder stehen.
        val loose = """<script>var type = 'tv'; var id: "tt0903747"; token: 'abc123';</script>"""
        val request = DeVideoSrcResolver.parsePageRequest(loose)
        assertEquals("tv", request?.kind)
        assertEquals("tt0903747", request?.id)
        assertEquals("abc123", request?.token)
    }

    @Test
    fun `page request stays null without token`() {
        assertNull(DeVideoSrcResolver.parsePageRequest("<html>kein Token</html>"))
        assertNull(DeVideoSrcResolver.parsePageRequest("""type: 'movie'; id: "tt01";"""))
    }

    @Test
    fun `movie answer keeps the documented order`() {
        val sources = DeVideoSrcResolver.parseSources(MOVIE_ANSWER)
        assertEquals(4, sources.size)
        assertEquals("https://dr0pstream.com/e/0491e1wi4xt4", sources.first().url)
        assertEquals("dropload.io", sources.first().name)
        assertEquals(listOf(1, 2, 3, 4), sources.map { it.rank })
    }

    @Test
    fun `serial answer selects the requested episode`() {
        val second = DeVideoSrcResolver.parseSources(SERIAL_ANSWER, episodeNumber = 2)
        // Die App kennt nur die Episodennummer, nicht die Staffel — deshalb
        // zaehlt die erste Staffel. Innerhalb der Staffel muss aber die
        // passende Folge kommen, nicht die erste der Liste.
        assertEquals(listOf("https://dr0pstream.com/e/s1e2"), second.map { it.url })
    }

    @Test
    fun `serial answer falls back to the first episode`() {
        val first = DeVideoSrcResolver.parseSources(SERIAL_ANSWER, episodeNumber = 0)
        assertEquals(listOf("https://dr0pstream.com/e/s1e1"), first.map { it.url })
    }

    @Test
    fun `unknown episode falls back to the first available one`() {
        val fallback = DeVideoSrcResolver.parseSources(SERIAL_ANSWER, episodeNumber = 99)
        assertEquals(listOf("https://dr0pstream.com/e/s1e1"), fallback.map { it.url })
    }

    @Test
    fun `broken answers produce no source`() {
        assertEquals(emptyList<DeVideoSrcSource>(), DeVideoSrcResolver.parseSources("nicht json"))
        assertEquals(
            emptyList<DeVideoSrcSource>(),
            DeVideoSrcResolver.parseSources("""{"ok":false,"type":"movie","sources":[]}""")
        )
        assertEquals(
            emptyList<DeVideoSrcSource>(),
            DeVideoSrcResolver.parseSources("""{"ok":true,"type":"tv","tv":{}}""")
        )
        // Quellen ohne https-URL taugen nichts.
        assertEquals(
            emptyList<DeVideoSrcSource>(),
            DeVideoSrcResolver.parseSources("""{"sources":[{"name":"x","url":"http://nicht.https/e"}]}""")
        )
    }

    @Test
    fun `expand calls page then embed-links and returns ranked providers`() = runTest {
        val fetcher = ScriptedFetcher(
            pages = mapOf("https://devideosrc.co/movie/tt0111161" to MOVIE_PAGE),
            answers = mapOf("https://devideosrc.co/api/embed-links" to MOVIE_ANSWER)
        )
        val resolver = DeVideoSrcResolver(fetcher)
        val sources = resolver.expand("https://devideosrc.co/movie/tt0111161")
        assertEquals(4, sources.size)
        assertEquals("https://dr0pstream.com/e/0491e1wi4xt4", sources.first().url)
        assertEquals(2, fetcher.calls.size)
        assertTrue(fetcher.calls[1].startsWith("POST https://devideosrc.co/api/embed-links "))
        // Der Token der Seite geht im Körper mit — sonst 403 invalid_token.
        assertTrue(fetcher.calls[1].contains("\"token\":\"bW92aWV8"))
        assertTrue(fetcher.calls[1].contains("\"type\":\"movie\""))
        assertTrue(fetcher.calls[1].contains("\"id\":\"tt0111161\""))
    }

    @Test
    fun `expand selects the episode for serials`() = runTest {
        val fetcher = ScriptedFetcher(
            pages = mapOf("https://devideosrc.co/serial/tt0903747" to SERIAL_PAGE),
            answers = mapOf("https://devideosrc.co/api/embed-links" to SERIAL_ANSWER)
        )
        val resolver = DeVideoSrcResolver(fetcher)
        val sources = resolver.expand("https://devideosrc.co/serial/tt0903747", episodeNumber = 2)
        assertEquals("https://dr0pstream.com/e/s1e2", sources.first().url)
        assertTrue(fetcher.calls[1].contains("\"type\":\"tv\""))
    }

    @Test
    fun `expand without an API answer stays empty and never throws`() = runTest {
        val fetcher = ScriptedFetcher()
        val resolver = DeVideoSrcResolver(fetcher)
        assertEquals(emptyList<DeVideoSrcSource>(), resolver.expand("https://devideosrc.co/movie/tt0111161"))
        assertEquals(1, fetcher.calls.size) // nur die Seite, kein Post
    }

    @Test
    fun `expand ignores foreign hosts without any request`() = runTest {
        val fetcher = ScriptedFetcher()
        val resolver = DeVideoSrcResolver(fetcher)
        assertEquals(
            emptyList<DeVideoSrcSource>(),
            resolver.expand("https://aniworld.to/redirect/2461081")
        )
        assertTrue(fetcher.calls.isEmpty())
    }
}