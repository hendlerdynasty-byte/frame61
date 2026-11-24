package de.hendler.frame61.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Adressierung einer Folge innerhalb einer Quelle.
 *
 * Der Staffel-Schritt ist der Kern des Tests: vor Block 8 adressierten bs.to,
 * ARD und die Packs s.to/aniworld eine angefragte Staffel-2-Folge als
 * Staffel 1 — die Folge wurde also beim Quellenwechsel stillschweigend
 * ausgetauscht (und der Resume-Punkt landete in einer anderen Zeitachse).
 */
class EpisodeSelectionTest {

    private val episodes = listOf(
        EpisodeTarget("a1", season = 1, number = 1),
        EpisodeTarget("a2", season = 1, number = 2),
        EpisodeTarget("a3", season = 1, number = 3),
        EpisodeTarget("b1", season = 2, number = 1),
        EpisodeTarget("b2", season = 2, number = 2)
    )

    @Test
    fun `staffel und nummer treffen exakt`() {
        assertEquals("b2", EpisodeSelection.select(episodes, season = 2, number = 2)?.id)
        assertEquals("a3", EpisodeSelection.select(episodes, season = 1, number = 3)?.id)
    }

    @Test
    fun `staffel-1-treffer kommt nicht fuer staffel-2-anfrage`() {
        // Der alte bs.to-Abgleich war `season == 1 && number == n` — S2E1 traf
        // nie und der Nummer-Fallback spielte S1E1.
        assertEquals("b1", EpisodeSelection.select(episodes, season = 2, number = 1)?.id)
    }

    @Test
    fun `quelle ohne staffelangabe faellt auf die nummer zurueck`() {
        val withoutSeason = episodes.map { it.copy(season = 0) }
        assertEquals("a3", EpisodeSelection.select(withoutSeason, season = 1, number = 3)?.id)
    }

    @Test
    fun `unbekannte nummer faellt auf die erste folge zurueck`() {
        assertEquals("a1", EpisodeSelection.select(episodes, season = 2, number = 99)?.id)
    }

    @Test
    fun `keine gefragte folge nimmt die erste der quelle`() {
        assertEquals("a1", EpisodeSelection.select(episodes, season = 0, number = 0)?.id)
    }

    @Test
    fun `leere liste liefert null`() {
        assertNull(EpisodeSelection.select(emptyList(), season = 1, number = 1))
    }
}