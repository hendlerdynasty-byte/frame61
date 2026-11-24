package de.hendler.frame61.playback

/**
 * Cache-Regeln der Auflösung.
 *
 * Ausgelagerte eigene Klasse, damit die Regeln ohne Android und ohne
 * Netzwerk prüfbar sind (Unit-Test im App-Modul).
 */
internal object PlaybackCachePolicy {

    /** Ein Treffer hält 15 Minuten. */
    const val SUCCESS_TTL_MS = 15L * 60L * 1000L

    /**
     * Ein **Fehlschlag** (keine Quelle gefunden) hält nur eine Minute.
     *
     * Grund (gemessen 24.11.2026, Lauf 7): Die Anbieterliste der Indexseiten
     * rotiert zwischen den Minuten. Mit 15 Minuten blieb ein Titel, der
     * einmal keine Quelle hatte, bis zum Neustart der App gesperrt — der
     * Nutzer sah "Keine Quelle verfügbar", obwohl der Anbieter längst
     * wieder etwas geliefert hätte.
     */
    const val EMPTY_TTL_MS = 60L * 1000L

    /** Wie lange ein Ergebnis mit [candidateCount] Kandidaten gilt. */
    fun ttlMs(candidateCount: Int): Long =
        if (candidateCount == 0) EMPTY_TTL_MS else SUCCESS_TTL_MS

    /** Ist ein Cache-Eintrag mit [ageMs] Alter noch gültig? */
    fun isFresh(ageMs: Long, candidateCount: Int): Boolean =
        ageMs < ttlMs(candidateCount)
}
