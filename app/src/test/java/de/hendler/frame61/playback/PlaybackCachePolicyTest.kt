package de.hendler.frame61.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cache-Regeln der Auflösung.
 *
 * Der Fehlschlag-Fall ist der wichtige: die Anbieterliste der Indexseiten
 * rotiert (gemessen 24.11.2026), ein leeres Ergebnis darf deshalb nicht
 * 15 Minuten stehen bleiben — sonst sieht der Nutzer "Keine Quelle
 * verfügbar", obwohl der Anbieter längst wieder etwas liefert.
 */
class PlaybackCachePolicyTest {

    @Test
    fun `ein treffer haelt 15 minuten`() {
        assertEquals(15L * 60L * 1000L, PlaybackCachePolicy.ttlMs(3))
        assertEquals(15L * 60L * 1000L, PlaybackCachePolicy.ttlMs(1))
    }

    @Test
    fun `ein fehlschlag haelt nur eine minute`() {
        assertEquals(60L * 1000L, PlaybackCachePolicy.ttlMs(0))
    }

    @Test
    fun `frische wird nach der ttl geprueft`() {
        assertTrue(PlaybackCachePolicy.isFresh(0, 0))
        assertTrue(PlaybackCachePolicy.isFresh(59_999, 0))
        assertFalse(PlaybackCachePolicy.isFresh(60_000, 0))
        assertFalse(PlaybackCachePolicy.isFresh(60_001, 0))
    }

    @Test
    fun `ein treffer bleibt laenger frisch`() {
        assertTrue(PlaybackCachePolicy.isFresh(60_000, 2))
        assertTrue(PlaybackCachePolicy.isFresh(899_999, 2))
        assertFalse(PlaybackCachePolicy.isFresh(900_000, 2))
    }
}
