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
        sourceId: String = "moflix",
        sourceSeason: Int? = null,
        sourceEpisode: Int? = null,
        sourceWatchUrl: String? = null,
        identityYear: Int? = null
    ) = MirrorCandidate(
        sourceId = sourceId,
        titleId = 1,
        url = url,
        streamUrl = streamUrl,
        identityTitle = identity,
        identityKind = kind,
        identityYear = identityYear,
        sourceSeason = sourceSeason,
        sourceEpisode = sourceEpisode,
        sourceWatchUrl = sourceWatchUrl
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
    fun `aniworld ohne quellen evidenz ist unknown ohne netzabruf`() = runBlocking {
        val fetcher = FakeFetcher(emptyMap())
        val result = ContentIdentityGate(fetcher).verify(
            IdentityRequest(title = "Demo Anime", type = "series", season = 2, episode = 4),
            listOf(
                candidate(
                    "https://cdn.invalid/master.m3u8",
                    streamUrl = "https://aniworld.to/redirect/1",
                    identity = "Demo Anime",
                    kind = "source",
                    sourceId = "aniworld"
                )
            )
        ).single()
        assertEquals(IdentityKind.UNKNOWN, result.verdict.kind)
        assertFalse(result.playable)
        assertEquals(0, fetcher.calls)
    }

    @Test
    fun `aniworld abweichende staffel oder folge ist wrong content`() = runBlocking {
        val request = IdentityRequest(title = "Demo Anime", type = "series", season = 2, episode = 4)
        val base = candidate(
            "https://cdn.invalid/master.m3u8",
            identity = "Demo Anime",
            kind = "source",
            sourceId = "aniworld",
            sourceSeason = 2,
            sourceEpisode = 4,
            sourceWatchUrl = "https://aniworld.to/anime/stream/demo/staffel-2/episode-509"
        )
        val wrongSeason = base.copy(sourceSeason = 1)
        val wrongEpisode = base.copy(sourceEpisode = 3)
        val gate = ContentIdentityGate(FakeFetcher(emptyMap()))
        assertEquals(
            IdentityKind.WRONG_CONTENT,
            gate.verify(request, listOf(wrongSeason)).single().verdict.kind
        )
        assertEquals(
            IdentityKind.WRONG_CONTENT,
            gate.verify(request, listOf(wrongEpisode)).single().verdict.kind
        )
    }

    @Test
    fun `aniworld abweichende watch url ist wrong content`() = runBlocking {
        val result = ContentIdentityGate(FakeFetcher(emptyMap())).verify(
            IdentityRequest(title = "Demo Anime", type = "series", season = 2, episode = 4),
            listOf(
                candidate(
                    "https://cdn.invalid/master.m3u8",
                    identity = "Demo Anime",
                    kind = "source",
                    sourceId = "aniworld",
                    sourceSeason = 2,
                    sourceEpisode = 4,
                    sourceWatchUrl = "https://aniworld.to/anime/stream/demo/staffel-1/episode-509"
                )
            )
        ).single()
        assertEquals(IdentityKind.WRONG_CONTENT, result.verdict.kind)
        assertFalse(result.playable)
    }

    @Test
    fun `aniworld prueft source watch url statt hoster stream url`() = runBlocking {
        val result = ContentIdentityGate(FakeFetcher(emptyMap())).verify(
            IdentityRequest(title = "Demo Anime", type = "series", season = 2, episode = 4),
            listOf(
                candidate(
                    "https://cdn.invalid/master.m3u8",
                    streamUrl = "https://aniworld.to/redirect/1",
                    identity = "Demo Anime",
                    kind = "source",
                    sourceId = "aniworld",
                    sourceSeason = 2,
                    sourceEpisode = 4,
                    sourceWatchUrl = "https://aniworld.to/anime/stream/demo/staffel-2/episode-509"
                )
            )
        ).single()
        assertEquals(IdentityKind.CORRECT, result.verdict.kind)
        assertTrue(result.playable)
    }

    @Test
    fun `aniworld bekanntes abweichendes jahr ist wrong content`() = runBlocking {
        val result = ContentIdentityGate(FakeFetcher(emptyMap())).verify(
            IdentityRequest(title = "Demo Anime", year = 2020, type = "series", season = 2, episode = 4),
            listOf(
                candidate(
                    "https://cdn.invalid/master.m3u8",
                    identity = "Demo Anime",
                    kind = "source",
                    sourceId = "aniworld",
                    sourceSeason = 2,
                    sourceEpisode = 4,
                    sourceWatchUrl = "https://aniworld.to/anime/stream/demo/staffel-2/episode-509",
                    identityYear = 2022
                )
            )
        ).single()
        assertEquals(IdentityKind.WRONG_CONTENT, result.verdict.kind)
        assertTrue(result.verdict.reason.contains("Jahr"))
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
