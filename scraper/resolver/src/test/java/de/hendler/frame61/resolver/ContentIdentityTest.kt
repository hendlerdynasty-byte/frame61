package de.hendler.frame61.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate 1 (1.6.0): Identitätsprüfung.
 *
 * Die Fälle sind aus echten Quellentiteln abgeleitet (Katalog und Treffer der
 * Läufe 9–11), nicht erfunden: Jahreszahlen, Staffelanhänge, Umlaute,
 * Trailer-Seiten und die Zwei-Titel-Falle („Man-Eater" gegen „Man-Eater 2").
 */
class ContentIdentityTest {

    @Test
    fun `normalisierung entfernt artikel jahre umlaute und satzzeichen`() {
        assertEquals(
            "schnetts schmoos",
            ContentIdentity.normalize("Die Schnetts und die Schmoos (2023)")
        )
        assertEquals(
            "monte verita rausch freiheit",
            ContentIdentity.normalize("Monte Verità – Der Rausch der Freiheit")
        )
        assertEquals(
            "rad zeit",
            ContentIdentity.normalize("Das Rad der Zeit")
        )
        assertEquals(
            "andor",
            ContentIdentity.normalize("Andor – Staffel 1")
        )
        assertEquals(
            "man eater 2",
            ContentIdentity.normalize("Man-Eater 2 – Der Menschenfresser ist zurück")
        )
    }

    @Test
    fun `identischer titel ergibt 1-0`() {
        assertEquals(
            1.0,
            ContentIdentity.similarity(
                "Die Schnetts und die Schmoos",
                "Die Schnetts und die Schmoos"
            ),
            0.0001
        )
    }

    @Test
    fun `artikel und staffelzusatz bleiben ueber der schwelle`() {
        assertTrue(
            ContentIdentity.similarity("Andor", "Andor – Staffel 1") >= 0.75
        )
        assertTrue(
            ContentIdentity.similarity(
                "Der Gesang der Flusskrebse",
                "Gesang der Flusskrebse"
            ) >= 0.75
        )
    }

    @Test
    fun `anderer titel faellt unter die schwelle`() {
        assertTrue(
            ContentIdentity.similarity("Man-Eater", "Man-Eater 2") < 0.75
        )
        assertTrue(
            ContentIdentity.similarity(
                "Der Gesang der Flusskrebse",
                "Der Fluss der Krebse"
            ) < 0.75
        )
    }

    @Test
    fun `trailer marker werden erkannt`() {
        assertTrue(ContentIdentity.looksLikeTrailer("Andor – Offizieller Trailer"))
        assertTrue(ContentIdentity.looksLikeTrailer("Der Film – Teaser (Deutsch)"))
        assertTrue(ContentIdentity.looksLikeTrailer("Preview: Staffel 2"))
        assertFalse(ContentIdentity.looksLikeTrailer("Die Schnetts und die Schmoos"))
        assertFalse(ContentIdentity.looksLikeTrailer("Ein Clip aus dem Alltag"))
    }

    @Test
    fun `trailer gewinnt nie gegen einen echten titel`() {
        val verdict = ContentIdentity.verdict(
            IdentityRequest(title = "Andor", type = "series", season = 1, episode = 3),
            evidenceTitle = "Andor – Offizieller Trailer"
        )
        assertEquals(IdentityKind.TRAILER_ONLY, verdict.kind)
        assertFalse(verdict.playable)
        assertTrue(verdict.lastResort)
    }

    @Test
    fun `richtiger titel ergibt CORRECT`() {
        val verdict = ContentIdentity.verdict(
            IdentityRequest(title = "Die Schnetts und die Schmoos"),
            evidenceTitle = "Die Schnetts und die Schmoos"
        )
        assertEquals(IdentityKind.CORRECT, verdict.kind)
        assertTrue(verdict.playable)
    }

    @Test
    fun `falscher titel ergibt WRONG_CONTENT`() {
        val verdict = ContentIdentity.verdict(
            IdentityRequest(title = "Man-Eater – Der Menschenfresser"),
            evidenceTitle = "Man-Eater 2 – Der Menschenfresser ist zurück"
        )
        assertEquals(IdentityKind.WRONG_CONTENT, verdict.kind)
        assertFalse(verdict.playable)
    }

    @Test
    fun `ohne quellenangabe ist der kandidat nicht spielbar`() {
        val verdict = ContentIdentity.verdict(
            IdentityRequest(title = "Ein Titel"),
            evidenceTitle = null
        )
        assertEquals(IdentityKind.UNKNOWN, verdict.kind)
        assertFalse(verdict.playable)
    }

    @Test
    fun `serie mit falscher staffel in der adresse wird abgelehnt`() {
        val verdict = ContentIdentity.verdict(
            IdentityRequest(title = "Andor", type = "series", season = 2, episode = 3),
            evidenceTitle = "Andor",
            evidenceUrl = "https://s.to/serie/andor/staffel-1/folge-3"
        )
        assertEquals(IdentityKind.WRONG_CONTENT, verdict.kind)
        assertTrue(verdict.reason.contains("Staffel"))
    }

    @Test
    fun `serie mit passender episodenadresse bleibt CORRECT`() {
        val verdict = ContentIdentity.verdict(
            IdentityRequest(title = "Andor", type = "series", season = 2, episode = 3),
            evidenceTitle = "Andor",
            evidenceUrl = "https://s.to/serie/andor/staffel-2/folge-3"
        )
        assertEquals(IdentityKind.CORRECT, verdict.kind)
    }

    @Test
    fun `episodenmarker ohne angabe blockiert nicht`() {
        assertNull(ContentIdentity.episodeMarkerMismatch("https://s.to/serie/andor", 2, 3))
        assertNull(ContentIdentity.episodeMarkerMismatch(null, 2, 3))
    }

    @Test
    fun `sxxexx form wird gelesen`() {
        assertNull(ContentIdentity.episodeMarkerMismatch("https://x/s02e03/x.mp4", 2, 3))
        assertTrue(
            ContentIdentity.episodeMarkerMismatch("https://x/s01e03/x.mp4", 2, 3)!!.contains("Staffel")
        )
    }

    @Test
    fun `laufzeitpruefung greift nur wenn beide seiten sie kennen`() {
        val ohneKatalog = ContentIdentity.verdict(
            IdentityRequest(title = "Ein Film", runtimeMin = null),
            evidenceTitle = "Ein Film",
            evidenceRuntimeSec = 5400
        )
        assertEquals(IdentityKind.CORRECT, ohneKatalog.kind)
        assertTrue(ohneKatalog.runtimeUnknown)

        val zuKurz = ContentIdentity.verdict(
            IdentityRequest(title = "Ein Film", runtimeMin = 100),
            evidenceTitle = "Ein Film",
            evidenceRuntimeSec = 60
        )
        assertEquals(IdentityKind.WRONG_CONTENT, zuKurz.kind)
        assertTrue(zuKurz.reason.contains("Laufzeit"))

        val passt = ContentIdentity.verdict(
            IdentityRequest(title = "Ein Film", runtimeMin = 100),
            evidenceTitle = "Ein Film",
            evidenceRuntimeSec = 6000
        )
        assertEquals(IdentityKind.CORRECT, passt.kind)
        assertFalse(passt.runtimeUnknown)
    }

    @Test
    fun `logzeile nennt klasse score und grund`() {
        val line = ContentIdentity.verdict(
            IdentityRequest(title = "A", runtimeMin = null),
            evidenceTitle = "B"
        ).asLogLine()
        assertTrue(line.contains("ident=WRONG_CONTENT"))
        assertTrue(line.contains("score="))
        assertTrue(line.contains("runtime_unknown"))
    }
}
