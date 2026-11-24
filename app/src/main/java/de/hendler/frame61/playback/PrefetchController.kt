package de.hendler.frame61.playback

import android.util.Log
import de.hendler.frame61.resolver.MirrorCandidate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Vorwaermen des gefokusten Titels (Block 7).
 *
 * Auf der Fernbedienung wird zuerst navigiert, **dann** gestartet. Die Zeit
 * zwischen Fokus und "OK" ist der einzige Vorlauf, den die App hat — genau
 * dort wird die Quelle des Titels aufgeloest, damit der Player nicht auf die
 * Kette warten muss.
 *
 * Die Regeln:
 *  - **Entprellt:** erst 400 ms ohne Fokuswechsel loest der Vorwaermlauf aus.
 *    Ein schneller Durchlauf durch die Kacheln loest damit nichts aus.
 *  - **Ein Lauf zur Zeit:** ein Semaphor serialisiert die Aufloesungen; ein
 *    neuer Fokus verdraengt den wartenden, nicht den laufenden.
 *  - **Ohne WebView:** [AutoPlaybackResolver.prefetch] benutzt einen eigenen
 *    Hoster-Resolver ohne WebView-Bestandteile. Der Vorwaermlauf kann den
 *    WebView-Slot des Players also nicht wegnehmen.
 *  - **Kurzlebiger Cache:** 60 s. Danach ist der Kandidat weg — Tokens der
 *    Hoster sind kurzlebig, ein alter Kandidat waere schlimmer als keiner.
 *  - **Waehrend der Player laeuft:** kein Vorwaermlauf. Die Netzlast gehoert
 *    dann der Wiedergabe.
 */
@Singleton
class PrefetchController @Inject constructor(
    private val resolver: AutoPlaybackResolver
) {

    @Suppress("unused")
    internal constructor(resolver: AutoPlaybackResolver, scope: CoroutineScope) : this(resolver) {
        this.scope = scope
    }

    private data class Entry(val at: Long, val candidates: List<MirrorCandidate>)

    /** Eigener Scope: der Vorwaermlauf gehoert nie zum Aufrufer. */
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cache = ConcurrentHashMap<String, Entry>()
    private val gate = Semaphore(1)

    /** Nur ein Vorwaermlauf gleichzeitig; ein neuer verdraengt den wartenden. */
    private var pendingJob: Job? = null

    /**
     * Der Player hat den Vordergrund — waehrenddessen wird nichts vorgewaermt.
     */
    @Volatile
    private var foregroundBusy: Boolean = false

    /**
     * Fokus auf einen Titel: Quelle im Hintergrund aufloesen.
     */
    fun prefetch(title: String, episodeNumber: Int = 0, season: Int = 1) {
        if (foregroundBusy) return
        if (title.isBlank()) return
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(DEBOUNCE_MS)
            if (!isActive) return@launch
            gate.withPermit {
                if (foregroundBusy) return@withPermit
                val key = key(title, episodeNumber, season)
                val started = System.currentTimeMillis()
                val candidates = try {
                    resolver.prefetch(title, episodeNumber, season)
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    // Abbruch ist kein Fehler des Vorwaermlaufs: der Aufrufer
                    // hat inzwischen woanders hingezeigt.
                    throw cancel
                } catch (error: Throwable) {
                    Log.w(TAG, "prefetch '$title' fehlgeschlagen: ${error.javaClass.simpleName}")
                    emptyList()
                }
                val took = System.currentTimeMillis() - started
                if (candidates.isEmpty()) {
                    cache.remove(key)
                    Log.i(TAG, "prefetch '$title' s${season}e$episodeNumber: kein Kandidat (${took} ms)")
                } else {
                    cache[key] = Entry(System.currentTimeMillis(), candidates)
                    Log.i(
                        TAG,
                        "prefetch '$title' s${season}e$episodeNumber: ${candidates.size} Kandidat(en) " +
                            "(${took} ms, ${candidates.first().sourceId})"
                    )
                }
            }
        }
    }

    /**
     * Vorbereiteter Kandidat fuer den Player — oder leer.
     *
     * Der Player fragt einmal beim Start und uebernimmt den Kandidaten, wenn
     * einer da ist. Der Cache bleibt bestehen: ein erneuter Start desselben
     * Titels (z. B. nach einem Quellenwechsel) darf davon profitieren.
     */
    fun peek(title: String, episodeNumber: Int = 0, season: Int = 1): List<MirrorCandidate> {
        val cacheKey = key(title, episodeNumber, season)
        val entry = cache[cacheKey] ?: return emptyList()
        if (!fresh(entry.at)) {
            cache.remove(cacheKey)
            return emptyList()
        }
        return entry.candidates
    }

    /**
     * Kandidat **uebernehmen**: wird ausgegeben und aus dem Cache entfernt.
     *
     * Ein vorbereiteter Kandidat wird genau einmal verwendet. Danach loest der
     * Player frisch auf — sonst wuerde ein alter Zwischenstand der
     * Quellenrotation dem frischen Ergebnis vorgezogen.
     */
    fun take(title: String, episodeNumber: Int = 0, season: Int = 1): List<MirrorCandidate> {
        val candidates = peek(title, episodeNumber, season)
        if (candidates.isNotEmpty()) {
            cache.remove(key(title, episodeNumber, season))
        }
        return candidates
    }

    /**
     * Der Player laeuft: Vorwaermen aussetzen.
     *
     * Der Cache wird **nicht** geleert: der Player holt sich seinen Kandidaten
     * danach ab. Ein laufender Vorwaermlauf wird abgebrochen — er wuerde dem
     * Start nur die Bandbreite wegnehmen.
     */
    fun markForegroundBusy(busy: Boolean) {
        foregroundBusy = busy
        if (busy) {
            pendingJob?.cancel()
        }
    }

    companion object {
        private const val TAG = "Frame61Prefetch"

        /** Entprellung: so lange muss der Fokus stehen, bevor vorgewaermt wird. */
        const val DEBOUNCE_MS = 400L

        /** Gueltigkeit eines vorbereiteten Kandidaten. */
        const val TTL_MS = 60_000L

        /**
         * Cache-Schluessel: Titel **und** Folge (Block 7 + Block 8).
         *
         * Ohne Staffel und Nummer wuerde ein vorbereiteter Kandidat der
         * Staffel 1 auch fuer die Staffel 2 gelten — genau der Fehler, den
         * Block 8 in der Adressierung behebt.
         */
        fun key(title: String, episodeNumber: Int, season: Int): String =
            "$title|s${season}e$episodeNumber"

        /** Ist der vorbereitete Kandidat noch verwendbar? */
        fun fresh(at: Long, now: Long = System.currentTimeMillis()): Boolean =
            now - at < TTL_MS
    }
}
