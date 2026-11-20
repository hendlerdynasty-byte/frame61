package de.hendler.frame61.scraper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaybackPackParserTest {
    private val interpreter = PackInterpreter()

    @Test
    fun `moflix playback id and nested alternative videos are parsed`() {
        val pack = requireNotNull(
            PackLoaderValidator.parse(
                """
                {
                  "id":"moflix","name":"Moflix","baseUrl":"https://moflix.invalid","kind":"api",
                  "search":{"endpoint":"/search/{query}","jsonExtractRegex":"\"results\"\\s*:(\\[.*?\\])","jsonPath":".","idPath":"id","titlePath":"name","playbackIdPath":"primary_video.id"},
                  "streams":{"endpoint":"/watch/{titleId}","format":"api","jsonEmbedded":true,"streamPath":"watchPage.alternative_videos","streamQualityPath":"quality","streamLangPath":"language"}
                }
                """.trimIndent()
            )
        )
        val searchBody = """{"results":[{"id":8972,"name":"Inception","primary_video":{"id":1620235}}]}"""
        val hit = interpreter.parseSearch(pack, searchBody).single()
        assertEquals("1620235", hit.playbackId)

        val watchBody = """
            <script>window.__={"loaders":{"watchPage":{"alternative_videos":[
            {"id":1,"name":"Premium","src":"","premium_locked":true},
            {"id":2,"name":"Mirror","src":"https://cdn.invalid/movie.m3u8","quality":"1080p","language":"de","title":{"keywords":[1,2,3]}}
            ]}},"rendered_ssr":false};</script>
        """.trimIndent()
        val streams = interpreter.parseStreams(pack, watchBody)
        assertEquals(1, streams.size)
        assertEquals("https://cdn.invalid/movie.m3u8", streams.single().url)
        assertEquals("1080p", streams.single().quality)
        assertEquals("de", streams.single().lang)
    }

    @Test
    fun `streamcloud iframe becomes an embed candidate not a media proof`() {
        val pack = requireNotNull(
            PackLoaderValidator.parse(
                """
                {
                  "id":"streamcloud","name":"StreamCloud","baseUrl":"https://streamcloud.invalid","kind":"html",
                  "search":{"endpoint":"/suche","resultSelector":"a.cv","titleSelector":".cv-t","idAttr":"href"},
                  "streams":{"endpoint":"/filme/{titleId}","format":"html","selector":"iframe","urlAttr":"src"}
                }
                """.trimIndent()
            )
        )
        val streams = interpreter.parseStreams(
            pack,
            """<html><body><iframe src="https://devideosrc.invalid/movie/tt1375666"></iframe></body></html>"""
        )
        assertEquals("https://devideosrc.invalid/movie/tt1375666", streams.single().url)
        assertNotNull(streams.single().url)
    }
}
