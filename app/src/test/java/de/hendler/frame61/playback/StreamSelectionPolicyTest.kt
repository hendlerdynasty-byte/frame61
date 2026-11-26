package de.hendler.frame61.playback

import de.hendler.frame61.resolver.HosterOutcome
import de.hendler.frame61.scraper.engine.PackLoaderValidator
import de.hendler.frame61.scraper.engine.StreamResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSelectionPolicyTest {
    private val pack = requireNotNull(
        PackLoaderValidator.parse(
            """
            {"id":"aniworld","name":"aniworld.to","baseUrl":"https://aniworld.to","kind":"html",
             "streams":{"endpoint":"/watch/{titleId}","selector":"li"}}
            """.trimIndent()
        )
    )

    @Test
    fun `zwei redirects bleiben trotz quellhost sperre einzeln retrybar`() {
        val first = StreamResult("https://aniworld.to/redirect/1", hosterId = "VOE")
        val second = StreamResult("https://aniworld.to/redirect/2", hosterId = "Filemoon")
        assertTrue(
            StreamSelectionPolicy.isExcluded(
                first, pack, excludeUrls = setOf(first.url), excludeHosts = setOf("aniworld.to")
            )
        )
        assertFalse(
            StreamSelectionPolicy.isExcluded(
                second, pack, excludeUrls = setOf(first.url), excludeHosts = setOf("aniworld.to")
            )
        )
    }

    @Test
    fun `fremdhost bleibt ueber host sperrbar`() {
        val stream = StreamResult("https://dead-hoster.invalid/embed/1")
        assertTrue(
            StreamSelectionPolicy.isExcluded(
                stream, pack, emptySet(), setOf("dead-hoster.invalid")
            )
        )
    }

    @Test
    fun `prioritaet ist filemoon vidmoly dood andere voe dann unbekannt`() {
        val streams = listOf(
            stream("unknown", null),
            stream("voe", "VOE"),
            stream("other", "SuperVideo"),
            stream("dood", "Doodstream"),
            stream("vidmoly", "Vidmoly"),
            stream("filemoon", "Filemoon")
        )
        val needsWebView: (String) -> Boolean = { it.contains("voe") || it.contains("unknown") }
        assertEquals(
            listOf("filemoon", "vidmoly", "doodstream", "supervideo", "voe", "unknown"),
            StreamSelectionPolicy.order(streams, needsWebView).map { it.hosterId?.lowercase() ?: "unknown" }
        )
    }

    @Test
    fun `http tier geht vor webview tier auch bei niedrigerer hosterprioritaet`() {
        val streams = listOf(
            stream("voe", "VOE"),
            stream("other", "SuperVideo")
        )
        val ordered = StreamSelectionPolicy.order(streams) { it.contains("voe") }
        assertEquals("SuperVideo", ordered.first().hosterId)
    }

    @Test
    fun `unknown ist kein spielkandidat`() {
        assertFalse(
            StreamSelectionPolicy.playable(
                HosterOutcome.Unknown("https://aniworld.to/redirect/1")
            )
        )
        assertFalse(StreamSelectionPolicy.playable(null))
        assertTrue(
            StreamSelectionPolicy.playable(
                HosterOutcome.Resolved("https://cdn.invalid/master.m3u8", usedSteps = 1)
            )
        )
    }

    private fun stream(name: String, hosterId: String?) =
        StreamResult("https://$name.invalid/embed", hosterId = hosterId)
}
