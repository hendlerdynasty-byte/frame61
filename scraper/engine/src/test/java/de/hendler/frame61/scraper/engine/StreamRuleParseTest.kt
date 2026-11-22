package de.hendler.frame61.scraper.engine

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Stream rules of index pages that link through their own redirect endpoint.
 *
 * `aniworld.to` delivers `/redirect/<id>` relative and carries language and
 * hoster in attributes. Without absolutising the link and reading the
 * attributes the chain stays an HTML page and playback fails with 3003.
 */
class StreamRuleParseTest {

    private fun pack(json: String): SourcePack = requireNotNull(PackLoaderValidator.parse(json))

    private val interpreter = PackInterpreter(PackHttpClient(OkHttpClient()))

    @Test
    fun `relative redirect links become absolute and attributes are read`() {
        val source = pack(
            """
            {
              "id":"aniworld","name":"aniworld.to","baseUrl":"https://aniworld.to","kind":"html",
              "streams":{
                "endpoint":"/anime/stream/{titleId}",
                "selector":"li[data-link-target]",
                "urlAttr":"data-link-target",
                "langAttr":"data-lang-key",
                "langMap":{"1":"de","2":"en"},
                "hosterSelector":"h4"
              }
            }
            """.trimIndent()
        )
        val body = """
            <html><body><ul>
              <li data-lang-key="1" data-link-target="/redirect/2626179">
                <a href="/redirect/2626179"><i class="icon VOE"></i><h4>VOE</h4></a>
              </li>
              <li data-lang-key="2" data-link-target="https://cdn.example/x">
                <a href="https://cdn.example/x"><i class="icon Vidmoly"></i><h4>Vidmoly</h4></a>
              </li>
            </ul></body></html>
        """.trimIndent()

        val streams = interpreter.parseStreams(source, body)

        assertEquals(2, streams.size)
        assertEquals("https://aniworld.to/redirect/2626179", streams[0].url)
        assertEquals("de", streams[0].lang)
        assertEquals("VOE", streams[0].hosterId)
        assertEquals("https://cdn.example/x", streams[1].url)
        assertEquals("en", streams[1].lang)
        assertEquals("Vidmoly", streams[1].hosterId)
    }

    @Test
    fun `an absolute url is left untouched`() {
        val source = pack(
            """
            {
              "id":"direct","name":"direct","baseUrl":"https://cdn.example","kind":"html",
              "streams":{"endpoint":"/watch/{titleId}","selector":"a.play","urlAttr":"href"}
            }
            """.trimIndent()
        )
        val body = "<html><body><a class='play' href='https://other.example/x.mp4'>x</a></body></html>"

        val streams = interpreter.parseStreams(source, body)

        assertEquals(1, streams.size)
        assertEquals("https://other.example/x.mp4", streams.single().url)
    }
}