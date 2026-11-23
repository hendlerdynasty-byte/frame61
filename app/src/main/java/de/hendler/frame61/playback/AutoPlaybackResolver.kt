package de.hendler.frame61.playback

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.net.PlaybackDiagnostics
import de.hendler.frame61.resolver.HosterOutcome
import de.hendler.frame61.resolver.HosterPack
import de.hendler.frame61.resolver.HosterPackValidator
import de.hendler.frame61.resolver.HosterResolver
import de.hendler.frame61.resolver.MirrorCandidate
import de.hendler.frame61.resolver.MirrorSelector
import de.hendler.frame61.resolver.OkHttpBodyFetcher
import de.hendler.frame61.scraper.engine.PackInterpreter
import de.hendler.frame61.scraper.engine.PackLoader
import de.hendler.frame61.scraper.engine.SourcePack
import de.hendler.frame61.scraper.engine.StreamResult
import de.hendler.frame61.ui.player.CatalogPlayback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automatic source resolution. The user never picks a source.
 *
 * 1. Every active source pack is searched in parallel (4 s per source).
 * 2. Stream URLs run through the hoster chain (4 s per candidate).
 * 3. The first usable candidate wins; failures are logged, never shown.
 * 4. The resolved list is cached for 15 minutes per (title, episode, active set).
 */
@Singleton
class AutoPlaybackResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packLoader: PackLoader,
    private val interpreter: PackInterpreter,
    private val ardResolver: ArdPlaybackResolver,
    private val captchaBroker: CaptchaBroker,
    private val jsEvaluator: WebViewJsEvaluator,
    private val headlessCaptchaSolver: HeadlessCaptchaSolver,
    private val mediaInterceptor: WebViewMediaInterceptor,
    private val prefs: ProfileDataStore
) {

    private data class CacheEntry(val at: Long, val mirrors: List<MirrorCandidate>)
    private data class ResolvedStream(
        val url: String,
        val headers: Map<String, String>,
        val cookies: String?
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Scope für die parallelen Quellen-Calls. Er gehört nicht zum Aufrufer:
     * nach der kurzen Frist darf der Player starten, während die langsamen
     * Quellen im Hintergrund noch bis zu ihrem Timeout laufen.
     */
    private val resolveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val hosterPacks: List<HosterPack> by lazy {
        val assets = context.assets
        (assets.list("hosters") ?: emptyArray())
            .filter { it.endsWith(".json") && !it.startsWith("._") }
            .mapNotNull { file ->
                runCatching {
                    assets.open("hosters/$file").bufferedReader().use { reader ->
                        HosterPackValidator.parse(reader.readText())
                    }
                }.getOrNull()
            }
    }

    private val hosterResolver: HosterResolver by lazy {
        HosterResolver(
            packs = hosterPacks,
            fetcher = OkHttpBodyFetcher(),
            jsEvaluator = jsEvaluator,
            captchaSolver = headlessCaptchaSolver,
            mediaInterceptor = mediaInterceptor
        )
    }

    /**
     * Hoster mit Captcha still vorwärmen, damit der erste Titel nicht auf eine
     * Challenge wartet. Wird bewusst **nicht** beim Anlegen des Resolvers
     * aufgerufen (das erzeugte mehrere WebViews gleichzeitig und blockierte den
     * Hauptthread → ANR), sondern verzögert vom Einstiegspunkt der App.
     */
    fun warmUpCaptchaHosts() {
        val urls = hosterPacks
            .filter { !it.captcha.isNullOrBlank() }
            .mapNotNull { pack -> pack.domains.firstOrNull()?.let { "https://$it/" } }
            .take(MAX_WARMUP_HOSTS)
        if (urls.isNotEmpty()) headlessCaptchaSolver.warmUp(urls)
    }

    suspend fun resolve(
        title: String,
        episodeNumber: Int = 0,
        sessionId: Long = PlaybackDiagnostics.currentSessionId,
        fresh: Boolean = false
    ): List<MirrorCandidate> = resolveInternal(title, episodeNumber, sessionId, fresh, 0L)

    /**
     * Wie [resolve], kehrt aber schon [graceMs] nach dem ersten Kandidaten
     * zurück, statt auf die langsamste Quelle zu warten.
     *
     * Motivation aus der Messung: die ARD-Kette liefert in ~1 s einen
     * spielbaren Stream, der Lauf wartete aber 4 s auf den Timeout der
     * langsamsten Quelle (gemessen p95 5,6 s bis zum ersten Bild).
     */
    suspend fun resolveFast(
        title: String,
        episodeNumber: Int = 0,
        sessionId: Long = PlaybackDiagnostics.currentSessionId,
        fresh: Boolean = false,
        graceMs: Long = FIRST_CANDIDATE_GRACE_MS
    ): List<MirrorCandidate> = resolveInternal(title, episodeNumber, sessionId, fresh, graceMs)

    private suspend fun resolveInternal(
        title: String,
        episodeNumber: Int,
        sessionId: Long,
        fresh: Boolean,
        graceMs: Long
    ): List<MirrorCandidate> =
        withContext(Dispatchers.IO) {
            val disabled = runCatching { prefs.disabledSources.first() }.getOrDefault(emptySet())
            val key = "$title|$episodeNumber|${disabled.hashCode()}"
            val now = System.currentTimeMillis()
            if (!fresh) cache[key]?.takeIf { now - it.at < CACHE_MS }?.let {
                Log.i(TAG, "cache hit '$title' (${it.mirrors.size} mirrors)")
                trace(sessionId, "cache", "Treffer mit ${it.mirrors.size} Kandidaten")
                return@withContext it.mirrors
            }

            val allPacks = packLoader.loadAllPacks().filter { it.id !in disabled }
            val packs = allPacks.filter { it.captcha == null }
            Log.i(
                TAG,
                "resolve '$title' ep=$episodeNumber active=${allPacks.size} " +
                    "immediate=${packs.size} deferredCaptcha=${allPacks.size - packs.size} " +
                    "disabled=${disabled.sorted()} grace=$graceMs"
            )

            val candidates = collectCandidates(packs, title, episodeNumber, sessionId, graceMs)

            // Captcha-Quellen sind die letzte Runde und nur im vollständigen
            // Lauf (graceMs == 0). Im schnellen Pfad würden sie den Start um
            // mehrere 9-s-Blöcke verlängern (gemessen 27 s ohne Kandidat).
            val captchaCandidates = if (candidates.isEmpty() && graceMs == 0L) {
                allPacks.filter { it.captcha != null }.flatMap { pack ->
                    resolveWithTimeout(pack, title, episodeNumber, sessionId)
                }
            } else {
                emptyList()
            }
            val ordered = order(candidates + captchaCandidates)
            if (ordered.isEmpty()) {
                Log.w(TAG, "all sources exhausted for '$title' ep=$episodeNumber")
            }
            cache[key] = CacheEntry(now, ordered)
            ordered
        }

    private suspend fun resolveWithTimeout(
        pack: SourcePack,
        title: String,
        episodeNumber: Int,
        sessionId: Long
    ): List<MirrorCandidate> {
        val started = System.currentTimeMillis()
        val result = runCatching {
            withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                resolvePack(pack, title, episodeNumber, sessionId)
            } ?: run {
                trace(sessionId, pack.id, "Zeitüberschreitung nach $SOURCE_TIMEOUT_MS ms")
                emptyList()
            }
        }.getOrElse { error ->
            trace(sessionId, pack.id, "Resolverfehler: ${error.javaClass.simpleName}")
            emptyList()
        }
        trace(sessionId, pack.id, "${result.size} Kandidaten")
        Log.i(
            TAG,
            "${pack.id}: ${result.size} candidate(s) in " +
                "${System.currentTimeMillis() - started} ms" +
                if (result.isEmpty()) " (no usable stream)" else ""
        )
        return result
    }

    /**
     * Alle Packs parallel befragen, Ergebnisse einsammeln.
     *
     * Mit [graceMs] > 0 endet das Einsammeln, sobald nach dem ersten
     * Kandidaten die Frist abgelaufen ist — die übrigen Quellen laufen dann
     * nicht weiter und der Player startet sofort.
     */
    private suspend fun collectCandidates(
        packs: List<SourcePack>,
        title: String,
        episodeNumber: Int,
        sessionId: Long,
        graceMs: Long
    ): List<MirrorCandidate> {
        if (packs.isEmpty()) return emptyList()
        // Eigener Scope statt coroutineScope: die Quellen laufen nach dem Abbruch
        // im Hintergrund weiter (blockierende OkHttp-Calls lassen sich nicht
        // abbrechen), der Player wartet aber nicht auf sie.
        val channel = Channel<List<MirrorCandidate>>(packs.size)
        packs.forEach { pack ->
            resolveScope.launch {
                val result = resolveWithTimeout(pack, title, episodeNumber, sessionId)
                // trySend statt send: die Kapazitaet entspricht der Zahl der
                // Packs, also geht kein Ergebnis verloren. Ein suspendierendes
                // send wuerde beim Abbruch eine ClosedSendChannelException in
                // einem Kind-Coroutine werfen — die die App beendet.
                channel.trySend(result)
            }
        }
        val collected = mutableListOf<MirrorCandidate>()
        var received = 0
        var firstAt = 0L
        val start = System.currentTimeMillis()
        // Obergrenze für den ganzen Durchgang: eine Quelle mit JS-Kette darf
        // bis zu 12 s brauchen, danach wird ohne Kandidaten zurückgekehrt.
        val hardDeadline = HARD_DEADLINE_MS
        while (received < packs.size) {
            val now = System.currentTimeMillis()
            // Nach dem ersten Kandidaten gilt die kurze Frist — nicht der
            // Timeout der langsamsten Quelle. Ohne diese Grenze wartet der
            // Lauf weiter auf den naechsten Empfang (gemessen 4,7 s statt 1,6 s).
            val budget = if (firstAt != 0L && graceMs > 0L) {
                graceMs - (now - firstAt)
            } else {
                hardDeadline - (now - start)
            }
            if (budget <= 0L) break
            val batch = withTimeoutOrNull(budget) { channel.receive() } ?: break
            received++
            if (batch.isEmpty()) continue
            collected += batch
            if (firstAt == 0L) firstAt = System.currentTimeMillis()
        }
        if (firstAt != 0L && graceMs > 0L) {
            Log.i(TAG, "erste Kandidaten nach ${firstAt - start} ms, ${collected.size} gesammelt")
        }
        return collected
    }

    private suspend fun resolvePack(
        pack: SourcePack,
        title: String,
        episodeNumber: Int,
        sessionId: Long
    ): List<MirrorCandidate> {
        if (pack.id == ArdPlaybackResolver.PACK_ID) {
            return ardResolver.resolve(title, episodeNumber)
        }
        var cookies: String? = null
        var hits = runCatching { interpreter.search(pack, title) }.getOrDefault(emptyList())
        val sourceCaptcha = pack.captcha
        if (hits.isEmpty() && sourceCaptcha != null) {
            Log.i(TAG, "${pack.id}: source captcha $sourceCaptcha -> WebView")
            val solved = captchaBroker.await(pack.baseUrl, sourceCaptcha, pack.id)
            cookies = solved?.cookies
            if (!cookies.isNullOrBlank()) {
                hits = runCatching { interpreter.search(pack, title, cookies = cookies) }
                    .getOrDefault(emptyList())
            }
        }
        val hit = hits.firstOrNull { it.title.contains(title, ignoreCase = true) }
            ?: return emptyList()
        val streams = interpreter.streams(
            pack,
            hit.playbackId ?: hit.id,
            episodeNumber.toString(),
            cookies
        )
        // Hoster parallel aufloesen: eine Seite mit sechs Zielen kostete
        // bisher sechs Ketten hintereinander (gemessen 11 s ohne Kandidat).
        val resolved = coroutineScope {
            streams.map { stream ->
                async { stream to resolveHoster(pack, stream, sessionId) }
            }.awaitAll()
        }
        return resolved.mapNotNull { (stream, hoster) ->
            hoster?.let {
                MirrorCandidate(
                    sourceId = pack.id,
                    titleId = CatalogPlayback.stableId(hit.id),
                    episodeId = if (episodeNumber > 0) episodeNumber.toLong() else null,
                    url = it.url,
                    lang = stream.lang?.take(2) ?: "de",
                    quality = stream.quality ?: "auto",
                    hosterId = stream.hosterId,
                    healthScore = 1.0,
                    legalFree = pack.legalFree,
                    requestHeaders = it.headers,
                    cookies = it.cookies
                )
            }
        }
    }

    private suspend fun resolveHoster(
        pack: SourcePack,
        stream: StreamResult,
        sessionId: Long
    ): ResolvedStream? {
        trace(sessionId, pack.id, "Hosterauflösung ${stream.hosterId ?: "unknown"}", stream.url)
        if (!stream.url.startsWith("https://")) {
            Log.w(TAG, "${pack.id}: non-https stream rejected")
            trace(sessionId, pack.id, "Nicht-HTTPS-Ziel verworfen", stream.url)
            return null
        }
        val hosterCaptcha = pack.captcha
        if (hosterCaptcha != null) {
            Log.i(TAG, "${pack.id}: captcha $hosterCaptcha -> WebView")
            val result = captchaBroker.await(stream.url, hosterCaptcha, pack.id)
            result?.streamUrl?.takeIf { it.startsWith("https://") }?.let {
                return ResolvedStream(
                    it,
                    mapOf(
                        "User-Agent" to HosterResolver.DEFAULT_USER_AGENT,
                        "Referer" to stream.url
                    ),
                    result.cookies
                )
            }
            val cookies = result?.cookies
            if (cookies.isNullOrBlank()) {
                Log.w(TAG, "${pack.id}: captcha ohne Cookie/Stream beendet")
                return null
            }
            val second = runCatching {
                withTimeoutOrNull(HOSTER_TIMEOUT_MS * 5) {
                    hosterResolver.resolve(stream.url, cookies)
                }
            }.getOrNull()
            return when (second) {
                is HosterOutcome.Resolved -> ResolvedStream(
                    second.url,
                    second.requestHeaders,
                    second.cookies
                )
                else -> {
                    Log.w(TAG, "${pack.id}: Kette nach Captcha ohne Ergebnis")
                    null
                }
            }
        }
        val outcome = runCatching {
            withTimeoutOrNull(HOSTER_TIMEOUT_MS) { hosterResolver.resolve(stream.url) }
        }.getOrNull()
        return when (outcome) {
            is HosterOutcome.Resolved -> ResolvedStream(
                outcome.url,
                outcome.requestHeaders,
                outcome.cookies
            ).also {
                trace(sessionId, pack.id, "Hoster aufgelöst", it.url)
            }
            is HosterOutcome.Unknown -> ResolvedStream(
                outcome.url,
                outcome.requestHeaders,
                outcome.cookies
            ).also {
                trace(sessionId, pack.id, "Unbekannter Hoster als Kandidat durchgereicht", it.url)
            }
            is HosterOutcome.CaptchaRequired -> {
                trace(sessionId, pack.id, "Captcha erforderlich", stream.url)
                null
            }
            is HosterOutcome.Failed -> {
                Log.w(TAG, "${pack.id}: hoster failed (${outcome.reason})")
                trace(sessionId, pack.id, "Hoster fehlgeschlagen: ${outcome.reason}", outcome.url)
                null
            }
            null -> {
                Log.w(TAG, "${pack.id}: hoster timeout")
                trace(sessionId, pack.id, "Hoster-Zeitüberschreitung", stream.url)
                null
            }
        }
    }

    private fun trace(sessionId: Long, sourceId: String, message: String, url: String? = null) {
        PlaybackDiagnostics.record(
            category = "RESOLVER",
            message = message,
            sessionId = sessionId,
            url = url,
            details = mapOf("sourceId" to sourceId)
        )
    }

    private fun order(candidates: List<MirrorCandidate>): List<MirrorCandidate> =
        MirrorSelector.order(candidates.distinctBy { it.url })

    companion object {
        private const val TAG = "Frame61Resolve"
        /**
         * Budget je Quelle. Packs mit JS-Kette (serienstream-Redirect, devideosrc,
         * vidmoly) brauchen länger als ein reiner HTTP-Pfad. Der Player wartet
         * trotzdem nicht darauf — resolveFast kehrt 600 ms nach dem ersten
         * Kandidaten zurück.
         */
        private const val SOURCE_TIMEOUT_MS = 10_000L

        /** Hoster-Kette inkl. stiller WebView-Interception (VOE/Doodstream). */
        private const val HOSTER_TIMEOUT_MS = 20_000L

        private const val CACHE_MS = 15L * 60L * 1000L

        /** Vorlauf nach dem ersten Kandidaten, bevor der Rest verworfen wird. */
        const val FIRST_CANDIDATE_GRACE_MS = 600L

        /** Obergrenze des Einsammelns, wenn gar kein Kandidat kommt. */
        private const val HARD_DEADLINE_MS = 22_000L

        /** Mehrere gleichzeitige WebViews blockieren den Hauptthread (ANR). */
        private const val MAX_WARMUP_HOSTS = 2
    }
}
