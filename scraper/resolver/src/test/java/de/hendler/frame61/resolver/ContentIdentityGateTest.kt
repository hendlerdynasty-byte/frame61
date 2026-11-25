package de.hendler.frame61.resolver

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gate 1: Kandidatenliste wird nur mit belegter Identität freigegeben. */
class ContentIdentityGateTest {

    /** Fetcher-Attrappe: liefert feste HTML-Antworten und zählt Abrufe. */
    private class FakeFetcher(private val responses: Map<String, String>) : BodyFetcher {
        var calls = 0
        val requested = mutableListOf<String>()
        override suspend fun get(url: String, headers: Map<String, String>): String {
            calls++
            requested += url
            return responses[url] ?: throw java.io.IOException("kein Fixture für $url")
        }
        override suspend fun post(url: String, body: String, headers: Map<String, String>): String =
            throw java.io.IOException("POST nicht erwartet")
    }

    private fun candidate(
        url: String,
        streamUrl: String? = null,
        identity: String? = null,
        kind: String? = null,
        sourceId: String = "moflix"
    ) = MirrorCandidate(
        sourceId = sourceId,
        titleId = 1,
        url = url,
        streamUrl = streamUrl,
        identityTitle = identity,
        identityKind = kind
    )

    @Test
    fun `quellenangabe entscheidet ohne netzabruf`() = runBlocking {
        val fetcher = FakeFetcher(emptyMap())
        val gate = ContentIdentityGate(fetcher)
        val result = gate.verify(
            IdentityRequest(title = "Die Schnetts und die Schmoos"),
            listOf(candidate("https://cdn/x.m3u8", identity = "Die Schnetts und die Schmoos", kind = "source"))
        )
        assertEquals(1, result.size)
        assertTrue(result[0].playable)
        assertEquals(0, fetcher.calls)
    }

    @Test
    fun `fehlende angabe wird aus dem seitentitel geholt`() = runBlocking {
        val page = "https://moflix.stream/watch/abc"
        val fetcher = FakeFetcher(
            mapOf(
                page to "<html><head><meta property=\"og:title\" content=\"Die Schnetts und die Schmoos\"></head></html>"
            )
        )
        val gate = ContentIdentityGate(fetcher)
        val result = gate.verify(
            IdentityRequest(title = "Die Schnetts und die Schmoos"),
            listOf(candidate("https://cdn/x.m3u8", streamUrl = page))
        )
        assertTrue(result[0].playable)
        assertEquals("seite", result[0].evidenceSource)
        assertEquals(1, fetcher.calls)
        // Zweiter Durchlauf nutzt den Cache.
        gate.verify(
            IdentityRequest(title = "Die Schnetts und die Schmoos"),
            listOf(candidate("https://cdn/x.m3u8", streamUrl = page))
        )
        assertEquals(1, fetcher.calls)
    }

    @Test
    fun `falscher seitentitel wird abgelehnt`() = runBlocking {
        val page = "https://moflix.stream/watch/xyz"
        val fetcher = FakeFetcher(mapOf(page to "<title>Man-Eater 2 – Der Menschenfresser ist zurück</title>"))
        val gate = ContentIdentityGate(fetcher)
        val result = gate.verify(
            IdentityRequest(title = "Man-Eater – Der Menschenfresser"),
            listOf(candidate("https://cdn/x.m3u8", streamUrl = page))
        )
        assertFalse(result[0].playable)
        assertEquals(IdentityKind.WRONG_CONTENT, result[0].verdict.kind)
    }

    @Test
    fun `medien-urls werden nie als seite abgerufen`() = runBlocking {
        val fetcher = FakeFetcher(emptyMap())
        val gate = ContentIdentityGate(fetcher)
        val result = gate.verify(
            IdentityRequest(title = "Irgendein Film"),
            listOf(candidate("https://cdn/x.m3u8", streamUrl = "https://cdn/x.m3u8"))
        )
        assertEquals(IdentityKind.UNKNOWN, result[0].verdict.kind)
        assertFalse(result[0].playable)
        assertEquals(0, fetcher.calls)
    }

    @Test
    fun `html seiten erkennung`() {
        assertTrue(ContentIdentityGate.looksLikeHtmlPage("https://moflix.stream/watch/abc"))
        assertFalse(ContentIdentityGate.looksLikeHtmlPage("https://cdn/x/master.m3u8?t=1"))
        assertFalse(ContentIdentityGate.looksLikeHtmlPage("https://cdn/x/film.mp4"))
        assertFalse(ContentIdentityGate.looksLikeHtmlPage("asset:///diagnostics/sample.mp4"))
    }

    @Test
    fun `seitentitel entfernt site-anhang`() {
        assertEquals(
            "Die Schnetts und die Schmoos",
            ContentIdentityGate.extractPageTitle("<title>Die Schnetts und die Schmoos | Aniworld</title>")
        )
        assertEquals(
            "Andor",
            ContentIdentityGate.extractPageTitle("<title>Andor - Serienstream</title>")
        )
        assertNull(ContentIdentityGate.extractPageTitle("<html><body>kein Titel</body></html>"))
    }
}
