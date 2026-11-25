package de.hendler.frame61.ui.player

import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.resolver.MirrorCandidate
import java.security.MessageDigest

/**
 * Katalog-Hilfen für die Wiedergabe.
 *
 * Wichtig für 1.6.0: Hier liegt **kein** Platzhalter-Stream mehr. Der frühere
 * Mux-Teststream ist entfernt; ein Titel ohne aufgelöste und identifizierte
 * Quelle zeigt eine Fehlermeldung statt eines fremden Videos.
 */
object CatalogPlayback {

    /**
     * Stable positive Long for a catalog id, so Room's `titleId` matches across
     * sessions. First 8 bytes of the SHA-256 of the id — not a hash of user
     * data, just a deterministic key. SHA-1 is deliberately not used here.
     */
    fun stableId(key: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (digest[i].toLong() and 0xFF)
        }
        return value and Long.MAX_VALUE
    }

    /**
     * Gate 1 (1.6.0): Es gibt **keinen** Platzhalter-Kandidaten mehr.
     *
     * Vorher lieferte diese Funktion den Mux-Teststream für jeden Titel; wenn
     * die Auflösung scheiterte, sah der Nutzer „irgendein Beispielvideo"
     * statt einer Fehlermeldung. Das widerspricht der Identitätsregel: kein
     * Kandidat ohne belegten Inhalt. Der Player zeigt stattdessen eine
     * verständliche Meldung mit Wiederholen-Option.
     *
     */
    fun candidates(@Suppress("UNUSED_PARAMETER") item: CatalogItem): List<MirrorCandidate> =
        emptyList()
}