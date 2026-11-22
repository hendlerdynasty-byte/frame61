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
}
