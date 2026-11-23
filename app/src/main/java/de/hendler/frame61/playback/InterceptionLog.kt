package de.hendler.frame61.playback

import java.util.concurrent.atomic.AtomicInteger

/**
 * Prozesslokales Protokoll der WebView-Interception.
 *
 * Die Interception läuft in einer versteckten WebView. Was dort passiert,
 * ist nur im Logcat zu sehen — und das ist auf dem Gerät stark gekürzt und
 * von Fremdlogs überlagert. Dieser Ringpuffer hält die letzten Versuche im
 * Prozess, damit Messharness und Diagnose sie anzeigen können:
 *
 *  - [Record.outcome] sagt, ob eine Medien-URL entstanden ist,
 *  - [Record.elapsedMs] ist die **gemessene** Zeit, nicht der Deckel,
 *  - [Record.requests] zeigt die Request-Kette der Seite (welche Station
 *    der Kette erreicht wurde und wo sie stehen blieb).
 */
object InterceptionLog {

    /** Ein Versuch, über die WebView eine Medien-URL zu erzeugen. */
    data class Record(
        val seq: Int,
        val url: String,
        val outcome: String,
        val elapsedMs: Long,
        val mediaUrl: String?,
        val scriptResult: String?,
        val lastPage: String?,
        val requests: List<String>
    )

    private const val MAX_RECORDS = 16
    private val sequence = AtomicInteger(0)
    private val records = ArrayDeque<Record>()

    /** Fortlaufende Nummer für den nächsten Versuch. */
    fun nextSeq(): Int = sequence.incrementAndGet()

    /** Nummer des letzten vergebenen Versuchs (Merkmal für [since]). */
    fun currentSeq(): Int = sequence.get()

    @Synchronized
    fun add(record: Record) {
        records.addLast(record)
        while (records.size > MAX_RECORDS) records.removeFirst()
    }

    @Synchronized
    fun all(): List<Record> = records.toList()

    /** Alle Versuche mit einer höheren Nummer als [seq]. */
    @Synchronized
    fun since(seq: Int): List<Record> = records.filter { it.seq > seq }

    @Synchronized
    fun clear() {
        records.clear()
    }
}
