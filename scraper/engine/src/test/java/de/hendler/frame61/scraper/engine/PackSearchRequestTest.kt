package de.hendler.frame61.scraper.engine

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The request side of a pack rule.
 *
 * Two failure modes are locked down here:
 *  - a rule with its own `queryParam` name must actually receive the query,
 *  - the status code of an unsuccessful answer must stay visible.
 */
class PackSearchRequestTest {

    private fun pack(json: String): SourcePack = requireNotNull(PackLoaderValidator.parse(json))

    @Test
    fun `search sends the query under the rule's own parameter name`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"shows":[{"name":"Black Doves","url":"/serie/black-doves"}]}""")
        )
        val client = PackHttpClient(OkHttpClient())
        val source = pack(
            """
            {
              "id":"s-to","name":"s.to","baseUrl":"${server.url("/")}",
              "kind":"api",
              "search":{"endpoint":"/api/search/suggest","queryParam":"term","jsonPath":"shows","idPath":"url","titlePath":"name"}
            }
            """.trimIndent()
        )

        val hits = PackInterpreter(client).search(source, "black doves")

        val recorded = server.takeRequest()
        assertEquals("/api/search/suggest?term=black%20doves", recorded.path)
        assertEquals(1, hits.size)
        assertEquals("Black Doves", hits.single().title)
        server.shutdown()
    }

    @Test
    fun `fetch keeps the status code while get fails with it`() {
        val server = MockWebServer()
        server.start()
        // Two answers: one for the fetch, one for the get. Without the second
        // one the server would keep the request open until the client times out.
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        val client = PackHttpClient(OkHttpClient())
        val source = pack(
            """
            {
              "id":"kinox","name":"KINO.to","baseUrl":"${server.url("/")}",
              "kind":"html",
              "search":{"endpoint":"/suche/{query}","resultSelector":".film-item"}
            }
            """.trimIndent()
        )
        val rule = requireNotNull(source.search)

        val response = client.fetch(source, rule, mapOf("query" to "inception"))

        assertEquals(404, response.status)
        assertTrue(response.body.contains("not found"))
        val failure = runCatching { client.get(source, rule, mapOf("query" to "inception")) }
            .exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue(failure?.message?.contains("HTTP 404") == true)
        server.shutdown()
    }

    @Test
    fun `a post body carries the form content type`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"hits":[]}"""))
        val client = PackHttpClient(OkHttpClient())
        val source = pack(
            """
            {
              "id":"aniworld","name":"aniworld.to","baseUrl":"${server.url("/")}",
              "kind":"api",
              "search":{"endpoint":"/ajax/search","method":"POST","body":"keyword={{query}}","jsonPath":".","idPath":"link","titlePath":"title"}
            }
            """.trimIndent()
        )
        val rule = requireNotNull(source.search)

        val response = client.fetch(source, rule, mapOf("query" to "naruto"))

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        // OkHttp haengt beim POST den Zeichensatz an den Typ.
        assertEquals(
            "application/x-www-form-urlencoded",
            recorded.getHeader("Content-Type")?.substringBefore(';')
        )
        assertEquals("keyword=naruto", recorded.body.readUtf8())
        assertEquals(200, response.status)
        server.shutdown()
    }

    @Test
    fun `a rule content type survives the post default`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"hits":[]}"""))
        val client = PackHttpClient(OkHttpClient())
        val source = pack(
            """
            {
              "id":"jsonpost","name":"JSON","baseUrl":"${server.url("/")}",
              "kind":"api",
              "search":{"endpoint":"/api","method":"POST","headers":{"Content-Type":"application/json"},"body":"{\"q\":\"{{query}}\"}","jsonPath":".","idPath":"id","titlePath":"title"}
            }
            """.trimIndent()
        )
        val rule = requireNotNull(source.search)

        client.fetch(source, rule, mapOf("query" to "naruto"))

        val recorded = server.takeRequest()
        assertEquals(
            "application/json",
            recorded.getHeader("Content-Type")?.substringBefore(';')
        )
        assertEquals("""{"q":"naruto"}""", recorded.body.readUtf8())
        server.shutdown()
    }

    @Test
    fun `a query with spaces stays one path segment`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val client = PackHttpClient(OkHttpClient())
        val source = pack(
            """
            {
              "id":"term-path","name":"Term","baseUrl":"${server.url("/")}",
              "kind":"html",
              "search":{"endpoint":"/suche/{term}","queryParam":"term","resultSelector":".item"}
            }
            """.trimIndent()
        )

        PackInterpreter(client).search(source, "black doves")

        assertEquals("/suche/black%20doves", server.takeRequest().path)
        server.shutdown()
    }

    @Test
    fun `streams carries the season into the endpoint placeholder`() {
        // Block 8: s.to/aniworld had a hardcoded `staffel-1` — a season-2
        // episode was requested as season 1. The rule now reads
        // `staffel-{season}` and the interpreter must fill it.
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html></html>"))
        val client = PackHttpClient(OkHttpClient())
        val source = pack(
            """
            {
              "id":"s-to","name":"s.to","baseUrl":"${server.url("/")}",
              "kind":"html",
              "search":{"endpoint":"/api/search/suggest","queryParam":"term"},
              "streams":{"endpoint":"/serie/{titleId}/staffel-{season}/episode-{episodeId}","selector":"button"}
            }
            """.trimIndent()
        )

        PackInterpreter(client).streams(source, "black-doves", "7", season = 2)

        assertEquals("/serie/black-doves/staffel-2/episode-7", server.takeRequest().path)
        server.shutdown()
    }

    @Test
    fun `streams defaults to season 1 so existing packs stay unchanged`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html></html>"))
        val client = PackHttpClient(OkHttpClient())
        val source = pack(
            """
            {
              "id":"s-to","name":"s.to","baseUrl":"${server.url("/")}",
              "kind":"html",
              "streams":{"endpoint":"/serie/{titleId}/staffel-{season}/episode-{episodeId}","selector":"button"}
            }
            """.trimIndent()
        )

        PackInterpreter(client).streams(source, "black-doves", "7")

        assertEquals("/serie/black-doves/staffel-1/episode-7", server.takeRequest().path)
        server.shutdown()
    }
}
