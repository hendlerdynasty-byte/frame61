package de.hendler.frame61.playback

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.net.PlaybackDiagnostics
import de.hendler.frame61.resolver.DeVideoSrcResolver
import de.hendler.frame61.resolver.HosterOutcome
import de.hendler.frame61.resolver.HosterPack
import de.hendler.frame61.resolver.HosterPackValidator
import de.hendler.frame61.resolver.HosterResolver
import de.hendler.frame61.resolver.MirrorCandidate
import de.hendler.frame61.resolver.MirrorSelector
import de.hendler.frame61.resolver.OkHttpBodyFetcher
import de.hendler.frame61.resolver.WebViewBudget
import de.hendler.frame61.resolver.hostOf
import de.hendler.frame61.scraper.engine.PackInterpreter
import de.hendler.frame61.scraper.engine.PackLoader
import de.hendler.frame61.scraper.engine.SearchResult
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val bsToResolver: BsToPlaybackResolver,
    private val catalog: CatalogRepository,
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
     * Sperre je Auflösungsschlüssel — **Single-Flight**.
     *
     * Die versteckte WebView ist app-weit serialisiert ([WebViewGate]). Zwei
     * gleichzeitige Auflösungen desselben Titels (Player-Start und Fehler-
     * recovery, oder zwei Klicks auf denselben Titel) verdoppeln nicht nur
     * die Netzlast, sie **stehlen sich gegenseitig den WebView-Slot**: der
     * zweite Versuch stand in der Warteschlange und lief in den 20-s-Deckel,
     * ohne eine einzige Anfrage abzusetzen (gemessen 23.11.: 7 von 14
     * Versuchen mit 0 Anfragen). Der zweite Aufrufer wartet jetzt unter der
     * Sperre und findet das Ergebnis des ersten im Cache.
     */
    private val resolutionLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * Scope für die parallelen Quellen-Calls. Er gehört nicht zum Aufrufer:
     * nach der kurzen Frist darf der Player starten, während die langsamen
     * Quellen im Hintergrund noch bis zu ihrem Timeout laufen.
     */
    private val resolveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Eigener Scope für das Vorwärmen: es darf den Start nie blockieren. */
    private val warmUpScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Katalog-Titel → Quellengruppe und Quellenschlüssel.
     *
     * Der Katalog wird aus den Quellen geerntet; deshalb kennt er die
     * Adressierung **innerhalb** der Quelle (Slug, API-Id). Ohne diese
     * Zuordnung ist z. B. bs.to nicht adressierbar: die Suche dort liefert
     * interne Nummern, keine URLs.
     */
    private val catalogIndex: Map<String, de.hendler.frame61.meta.CatalogItem> by lazy {
        catalog.all()
            .filter { it.title.isNotBlank() }
            .associateBy { normalizeTitle(it.title) }
    }

    private fun normalizeTitle(title: String): String =
        title.trim().lowercase().replace(Regex("\\s+"), " ")

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
            mediaInterceptor = mediaInterceptor,
            devideosrc = DeVideoSrcResolver(OkHttpBodyFetcher())
        )
    }

    /**
     * Hoster-Resolver **ohne** WebView-Bestandteile (Block 7).
     *
     * Kein `jsEvaluator`, kein Captcha-Loeser, kein Media-Interceptor: ein
     * `js`-Schritt scheitert damit mit "kein WebView verfuegbar", ein
     * Captcha-Pack mit "CaptchaRequired" — beide **ohne** die stille WebView zu
     * beruehren. Genau das macht den Prefetch gefahrlos: er kann den
     * WebView-Slot des Players nicht wegnehmen.
     */
    private val prefetchHosterResolver: HosterResolver by lazy {
        HosterResolver(
            packs = hosterPacks,
            fetcher = OkHttpBodyFetcher(),
            jsEvaluator = null,
            captchaSolver = null,
            mediaInterceptor = null,
            devideosrc = null
        )
    }

    /**
     * Hoster mit Captcha still vorwärmen, damit der erste Titel nicht auf eine
     * Challenge wartet. Wird bewusst **nicht** beim Anlegen des Resolvers
     * aufgerufen (das erzeugte mehrere WebViews gleichzeitig und blockierte den
     * Hauptthread → ANR), sondern verzögert vom Einstiegspunkt der App.
     *
     * Vorbwärmt werden zwei Wege:
     *  - die Hoster mit Challenge (Cookie-Ernte über die versteckte WebView),
     *  - alle Hosts, über die die **Interception** läuft: VOE (der Hoster von
     *    bs.to, streamcloud und aniworld) sowie die Indexseiten selbst.
     */
    fun warmUpCaptchaHosts() {
        val urls = hosterPacks
            .filter { !it.captcha.isNullOrBlank() }
            .mapNotNull { pack -> pack.domains.firstOrNull()?.let { "https://$it/" } }
            .take(MAX_WARMUP_HOSTS)
        if (urls.isNotEmpty()) headlessCaptchaSolver.warmUp(urls)
        warmUpScope.launch {
            runCatching { mediaInterceptor.warmUp(INTERCEPTION_WARMUP_URLS) }
        }
    }

    suspend fun resolve(
        title: String,
        episodeNumber: Int = 0,
        season: Int = 1,
        sessionId: Long = PlaybackDiagnostics.currentSessionId,
        fresh: Boolean = false
    ): List<MirrorCandidate> =
        resolveInternal(title, episodeNumber, season, sessionId, fresh, 0L)

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
        season: Int = 1,
        sessionId: Long = PlaybackDiagnostics.currentSessionId,
        fresh: Boolean = false,
        graceMs: Long = FIRST_CANDIDATE_GRACE_MS
    ): List<MirrorCandidate> =
        resolveInternal(title, episodeNumber, season, sessionId, fresh, graceMs)

    /**
     * Vorwaermen fuer den **fokussierten** Titel (Block 7).
     *
     * Der Katalog weiss, aus welcher Quelle ein Titel stammt ([CatalogItem.group]).
     * Diese Quelle wird hier **allein** aufgeloest — ein bis zwei HTTP-Anfragen
     * statt der 33 Pack-Suchen des vollen Laufs. Das Ergebnis dient dem Player
     * als Sofort-Kandidat: der Start wartet dann nicht mehr auf die Kette.
     *
     * **Ohne WebView.** Der Prefetch benutzt einen eigenen Hoster-Resolver ohne
     * JS-Auswertung, ohne Captcha-Loeser und ohne Media-Interceptor. Damit ist
     * der Vorwaermlauf nachweisbar WebView-frei: die stille WebView ist
     * app-weit serialisiert ([WebViewGate]) — ein Prefetch, der dort mitfaehrt,
     * wuerde dem echten Start den Slot wegnehmen. Hosters, die nur im Browser
     * loesbar sind (VOE, Doodstream, jwplayer), liefern im Prefetch deshalb
     * nichts; das ist der gewollte Preis fuer die Sicherheit, den
     * Player-Start nicht zu blockieren.
     *
     * Der volle Cache wird **nicht** geschrieben: der Prefetch ist ein
     * Vorlauf, kein Ergebnis. Was der Player daraus uebernimmt, entscheidet der
     * Player.
     */
    suspend fun prefetch(
        title: String,
        episodeNumber: Int = 0,
        season: Int = 1
    ): List<MirrorCandidate> = withContext(Dispatchers.IO) {
        val item = catalogIndex[normalizeTitle(title)] ?: return@withContext emptyList()
        val group = item.group ?: return@withContext emptyList()
        val wanted = PrefetchScope.packsFor(group)
        if (wanted.isEmpty()) return@withContext emptyList()
        val disabled = runCatching { prefs.disabledSources.first() }.getOrDefault(emptySet())
        val packs = packLoader.loadAllPacks().filter { it.id in wanted && it.id !in disabled }
        if (packs.isEmpty()) return@withContext emptyList()
        val candidates = mutableListOf<MirrorCandidate>()
        for (pack in packs) {
            // Abbruch (Fokus ist weitergewandert) landet als leeres Ergebnis
            // hier — der Vorwaermlauf schreibt dann nichts in den Cache.
            // Der Typ ist ausdruecklich: `runCatching` + `withTimeoutOrNull`
            // inferieren sonst `List<Any?>` (CI 45471455585).
            val result: List<MirrorCandidate> = runCatching {
                withTimeoutOrNull<List<MirrorCandidate>>(PREFETCH_TIMEOUT_MS) {
                    resolvePackPrefetch(pack, item, episodeNumber, season)
                }
            }.getOrNull().orEmpty()
            candidates.addAll(result)
        }
        val ordered = order(candidates)
        Log.i(
            TAG,
            "prefetch '$title' s${season}e$episodeNumber gruppe=$group " +
                "packs=${packs.joinToString(",") { it.id }} kandidaten=${ordered.size}"
        )
        ordered
    }

    /**
     * Ein Pack **ohne WebView** aufloesen — Suche, Stream-Liste, Hoster.
     *
     * Nur [HosterOutcome.Resolved] zaehlt: "Unknown" ist die HTML-Seite selbst
     * und damit kein spielbarer Kandidat.
     */
    private suspend fun resolvePackPrefetch(
        pack: SourcePack,
        item: de.hendler.frame61.meta.CatalogItem,
        episodeNumber: Int,
        season: Int
    ): List<MirrorCandidate> {
        if (pack.id == ArdPlaybackResolver.PACK_ID) {
            // ARD laeuft ueber HTTP, ohne Hoster-Kette.
            return ardResolver.resolve(item.title, episodeNumber, season)
        }
        if (pack.captcha != null) return emptyList()
        val hits = runCatching { interpreter.search(pack, item.title) }.getOrDefault(emptyList())
        val hit = hits.firstOrNull { it.title.contains(item.title, ignoreCase = true) }
            ?: hits.firstOrNull()
            ?: return emptyList()
        val streams = runCatching {
            interpreter.streams(
                pack,
                hit.playbackId ?: hit.id,
                episodeNumber.toString(),
                null,
                season
            )
        }.getOrDefault(emptyList())
        val out = mutableListOf<MirrorCandidate>()
        for (stream in streams.take(PREFETCH_MAX_STREAMS)) {
            if (!stream.url.startsWith("https://")) continue
            val outcome = runCatching {
                withTimeoutOrNull(PREFETCH_HOSTER_TIMEOUT_MS) {
                    prefetchHosterResolver.resolve(
                        stream.url,
                        budget = WebViewBudget.none(),
                        episodeNumber = episodeNumber
                    )
                }
            }.getOrNull()
            val resolved = outcome as? HosterOutcome.Resolved ?: continue
            out += toCandidate(
                pack,
                hit,
                stream,
                ResolvedStream(resolved.url, resolved.requestHeaders, resolved.cookies),
                episodeNumber,
                season
            )
        }
        return out
    }

    private suspend fun resolveInternal(
        title: String,
        episodeNumber: Int,
        season: Int,
        sessionId: Long,
        fresh: Boolean,
        graceMs: Long
    ): List<MirrorCandidate> = withContext(Dispatchers.IO) {
        val disabled = runCatching { prefs.disabledSources.first() }.getOrDefault(emptySet())
        // Die Staffel gehört zur Adresse: S2E1 ist eine andere Folge als
        // S1E1 und braucht ihren eigenen Cache-Eintrag (Block 8).
        val key = "$title|s${season}e$episodeNumber|${disabled.hashCode()}"
        val cached = cacheHit(key, title, sessionId, fresh)
        if (cached != null) {
            cached
        } else {
            // Single-Flight: unter der Sperre prüft der zweite Aufrufer den
            // Cache erneut — der erste hat inzwischen aufgelöst.
            val lock = resolutionLocks.getOrPut(key) { Mutex() }
            lock.withLock {
                val second = cacheHit(key, title, sessionId, fresh)
                second ?: resolveNow(title, episodeNumber, season, sessionId, graceMs, disabled, key)
            }
        }
    }

    /**
     * Cache-Treffer — [fresh] umgeht ihn bewusst (Fehler-recovery).
     */
    private suspend fun cacheHit(
        key: String,
        title: String,
        sessionId: Long,
        fresh: Boolean
    ): List<MirrorCandidate>? {
        if (fresh) return null
        val entry = cache[key] ?: return null
        val age = System.currentTimeMillis() - entry.at
        if (age >= ttlFor(entry.mirrors)) return null
        Log.i(TAG, "cache hit '$title' (${entry.mirrors.size} mirrors, ${age} ms alt)")
        trace(sessionId, "cache", "Treffer mit ${entry.mirrors.size} Kandidaten")
        return entry.mirrors
    }

    /**
     * Die eigentliche Auflösung — ohne Cache, ohne Single-Flight.
     */
    private suspend fun resolveNow(
        title: String,
        episodeNumber: Int,
        season: Int,
        sessionId: Long,
        graceMs: Long,
        disabled: Set<String>,
        key: String
    ): List<MirrorCandidate> {
        val allPacks = packLoader.loadAllPacks().filter { it.id !in disabled }
        val packs = allPacks.filter { it.captcha == null }
        Log.i(
            TAG,
            "resolve '$title' s${season}e$episodeNumber active=${allPacks.size} " +
                "immediate=${packs.size} deferredCaptcha=${allPacks.size - packs.size} " +
                "disabled=${disabled.sorted()} grace=$graceMs"
        )

        val candidates = collectCandidates(packs, title, episodeNumber, season, sessionId, graceMs)

        // Captcha-Quellen sind die letzte Runde und nur im vollständigen
        // Lauf (graceMs == 0). Im schnellen Pfad würden sie den Start um
        // mehrere 9-s-Blöcke verlängern (gemessen 27 s ohne Kandidat).
        //
        // Bewusst eine Schleife statt `filter{}.flatMap{}`: der Aufruf von
        // [resolveWithTimeout] ist suspend und lief damit in einer Inline-
        // Lambda — die Kotlin-2.1-Inferenz hat genau daran gehangen
        // (CI 45358019986: "Missing return statement" am Funktionsende).
        val captchaCandidates = if (candidates.isEmpty() && graceMs == 0L) {
            val deferred = mutableListOf<MirrorCandidate>()
            for (deferredPack in allPacks) {
                if (deferredPack.captcha == null) continue
                deferred += resolveWithTimeout(deferredPack, title, episodeNumber, season, sessionId)
            }
            deferred
        } else {
            emptyList()
        }
        val ordered = order(candidates + captchaCandidates)
        if (ordered.isEmpty()) {
            Log.w(TAG, "all sources exhausted for '$title' s${season}e$episodeNumber")
        }
        cache[key] = CacheEntry(System.currentTimeMillis(), ordered)
        return ordered
    }

    /**
     * Wie lange ein Cache-Eintrag gilt.
     *
     * Ein **leeres** Ergebnis wird nur kurz gehalten: die Anbieterliste der
     * Indexseiten rotiert (gemessen 24.11.), ein Titel, der heute keine
     * Quelle hat, kann Minuten später wieder eine haben. Mit 15 Minuten
     * blieb ein Fehlschlag bis zum Neustart der App gesperrt.
     */
    private fun ttlFor(mirrors: List<MirrorCandidate>): Long =
        PlaybackCachePolicy.ttlMs(mirrors.size)

    private suspend fun resolveWithTimeout(
        pack: SourcePack,
        title: String,
        episodeNumber: Int,
        season: Int,
        sessionId: Long
    ): List<MirrorCandidate> {
        val started = System.currentTimeMillis()
        // bs.to braucht länger: Challenge + Klick + Medienabruf laufen in der
        // versteckten WebView. Mit dem Standardbudget wäre die Quelle immer
        // abgeschnitten — genau der Fehler, der die Quote gedrückt hat.
        val budget = if (pack.id == BS_TO_PACK_ID) BS_TO_TIMEOUT_MS else SOURCE_TIMEOUT_MS
        val result = runCatching {
            withTimeoutOrNull(budget) {
                resolvePack(pack, title, episodeNumber, season, sessionId)
            } ?: run {
                trace(sessionId, pack.id, "Zeitüberschreitung nach $budget ms")
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
        season: Int,
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
                val result = resolveWithTimeout(pack, title, episodeNumber, season, sessionId)
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
        season: Int,
        sessionId: Long
    ): List<MirrorCandidate> {
        if (pack.id == ArdPlaybackResolver.PACK_ID) {
            return ardResolver.resolve(title, episodeNumber, season)
        }
        if (pack.id == BS_TO_PACK_ID) {
            // bs.to ist nur über den Sitemap-Slug adressierbar; die Suche der
            // Seite liefert interne Nummern statt URLs. Ohne Katalogeintrag
            // bleibt die Quelle leer — lieber keine als die falsche Serie.
            val item = catalogIndex[normalizeTitle(title)]
            val slug = item?.sourceKey
            if (item?.group != GROUP_BSTO || slug.isNullOrBlank()) {
                Log.i(TAG, "bs-to: kein Slug für '$title' (Gruppe=${item?.group ?: "-"})")
                return emptyList()
            }
            trace(sessionId, pack.id, "bs.to $slug", null)
            return bsToResolver.resolve(
                slug = slug,
                episodeNumber = episodeNumber,
                season = season,
                titleId = item.id
            )
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
        // Der Katalog wird aus den Quellen geerntet (1.5.0): Der Titel kann
        // dort länger/anders geschrieben sein als im Treffer. Passt kein
        // Treffer exakt, zählt der erste — sonst liefe jede Suche ohne Ergebnis
        // zurück, obwohl die Quelle den Titel führt.
        val hit = hits.firstOrNull { it.title.contains(title, ignoreCase = true) }
            ?: hits.firstOrNull()
            ?: return emptyList()
        val streams = interpreter.streams(
            pack,
            hit.playbackId ?: hit.id,
            episodeNumber.toString(),
            cookies,
            season
        )
        // Hoster parallel aufloesen: eine Seite mit sechs Zielen kostete
        // bisher sechs Ketten hintereinander (gemessen 11 s ohne Kandidat).
        //
        // Die versteckte WebView ist app-weit serialisiert. Deshalb bekommt nur
        // der **erste** WebView-taugliche Stream den Versuch; alle anderen
        // laufen parallel über den günstigen Weg (Pack-Schritte ohne WebView).
        // Messung 23.11. (A52, be45079): mit neun parallelen Versuchen kam die
        // Hälfte nie in die WebView (0 Anfragen), der Rest lief in den 20-s-
        // Deckel — der Titel hatte weder Stream noch Zeit.
        val webBudget = WebViewBudget.of(
            attempts = WEBVIEW_ATTEMPTS_PER_TITLE,
            deadlineMs = System.currentTimeMillis() + WEBVIEW_BUDGET_MS
        )
        return resolveStreams(pack, hit, streams, episodeNumber, season, sessionId, webBudget)
    }

    /**
     * Hoster-Kette für eine Stream-Liste — gemeinsam genutzt vom Fast-Path
     * ([resolvePack]) und von der Alternative-Kette ([resolveAlternative]).
     *
     * Die versteckte WebView ist app-weit serialisiert ([WebViewGate]): nur der
     * erste WebView-taugliche Stream bekommt den Versuch, alle anderen laufen
     * parallel über den günstigen Weg (Pack-Schritte ohne WebView). Hoster ohne
     * Gast-Wiedergabe (VOE: Session/302 /login) kommen zuletzt — der eine
     * WebView-Versuch soll an einen Hoster gehen, der ohne Konto auskommt.
     */
    private suspend fun resolveStreams(
        pack: SourcePack,
        hit: SearchResult,
        streams: List<StreamResult>,
        episodeNumber: Int,
        season: Int,
        sessionId: Long,
        webBudget: WebViewBudget
    ): List<MirrorCandidate> {
        val (webFirst, plain) = streams.partition { needsWebView(it.url) }
        val orderedWeb = webFirst.sortedBy { if (guestCapable(it.url)) 0 else 1 }
        val resolved = coroutineScope {
            val webJobs = orderedWeb.take(MAX_WEBVIEW_STREAMS).map { stream ->
                async {
                    stream to resolveHoster(pack, stream, episodeNumber, season, sessionId, webBudget)
                }
            }
            val plainJobs = plain.map { stream ->
                async {
                    stream to resolveHoster(pack, stream, episodeNumber, season, sessionId, WebViewBudget.none())
                }
            }
            (webJobs + plainJobs).awaitAll()
        }
        return resolved.mapNotNull { (stream, hoster) ->
            hoster?.let { toCandidate(pack, hit, stream, it, episodeNumber, season) }
        }
    }

    private fun toCandidate(
        pack: SourcePack,
        hit: SearchResult,
        stream: StreamResult,
        resolved: ResolvedStream,
        episodeNumber: Int,
        season: Int
    ): MirrorCandidate = MirrorCandidate(
        sourceId = pack.id,
        titleId = CatalogPlayback.stableId(hit.id),
        // Block 8: die Folgennummer allein ist mehrdeutig (S1E3 == S2E3 == 3).
        // Der Kandidat trägt deshalb den aus der Adresse abgeleiteten,
        // eindeutigen Schlüssel — ein Quellenwechsel kann so nie eine
        // andere Folge unter derselben Id mitführen.
        episodeId = if (episodeNumber > 0) {
            CatalogPlayback.stableId("s${season}e$episodeNumber")
        } else {
            null
        },
        url = resolved.url,
        lang = stream.lang?.take(2) ?: "de",
        quality = stream.quality ?: "auto",
        hosterId = stream.hosterId,
        healthScore = 1.0,
        legalFree = pack.legalFree,
        requestHeaders = resolved.headers,
        cookies = resolved.cookies,
        // Die Watch-URL bleibt erhalten: nur sie verrät, welcher
        // Hoster zu diesem Kandidaten gehört (Alternative-Kette).
        streamUrl = stream.url
    )

    /**
     * Nächste Alternative derselben Quelle — für den Fall, dass der Kandidat
     * aufgelöst wurde, aber **nicht abspielbar** ist (ExoPlayer 3003).
     *
     * **Messung (A52, Lauf 7, 24.11.2026):** Der Fast-Path löst je Quelle nur
     * den ersten Hoster auf (`MAX_WEBVIEW_STREAMS = 1`). Die moflix-Watch-Seite
     * bietet aber 3–8 weitere Hoster an (`veev.to`, `moflix.upns.xyz`,
     * `moflix-stream.click`, `vidaraa.cc`, …) — **12 der 34 Fehlschläge**
     * scheiterten an `moflix.rpmplay.xyz`, einem toten Ad-Stub, **obwohl**
     * daneben spielbare Alternativen standen. Sie wurden nie aufgelöst, weil sie
     * verworfen wurden, bevor sie an die Reihe kamen.
     *
     * **Der Ausschluss läuft über die Watch-URL, nicht über die Medien-URL:**
     * die aufgelöste m3u8 sieht anders aus als die Hoster-Seite, die sie
     * erzeugt hat. Ein Vergleich der Medien-URLs würde den toten Hoster
     * erneut auflösen — genau der Fehler, den der erste Entwurf hatte.
     * Deshalb wird der **Host** der Watch-URL gesperrt; bei moflix trägt jedes
     * Alternativziel einen eigenen Host.
     *
     * **Aufgelöst wird sequenziell** ([resolveAlternatives]): die
     * WebView-tauglichen Ziele bekommen **nacheinander** je einen Slot der
     * versteckten WebView, die reinen HTTP-Ziele laufen parallel. Im
     * Parallelbetrieb (Lauf 8) kam kein einziges dieser Ziele in die
     * WebView — sie standen nur in der Warteschlange.
     */
    suspend fun resolveAlternative(
        title: String,
        episodeNumber: Int,
        season: Int = 1,
        sourceId: String,
        excludeUrls: Set<String>,
        excludeHosts: Set<String> = emptySet(),
        sessionId: Long = PlaybackDiagnostics.currentSessionId
    ): List<MirrorCandidate> = withContext(Dispatchers.IO) {
        val pack = packLoader.loadAllPacks().firstOrNull { it.id == sourceId }
            ?: return@withContext emptyList()
        // ARD und bs.to haben keine Hoster-Alternativen (direkte Streams).
        if (pack.id == ArdPlaybackResolver.PACK_ID || pack.id == BS_TO_PACK_ID) {
            return@withContext emptyList()
        }
        val hits = runCatching { interpreter.search(pack, title) }.getOrDefault(emptyList())
        val hit = hits.firstOrNull { it.title.contains(title, ignoreCase = true) }
            ?: hits.firstOrNull()
            ?: return@withContext emptyList()
        val streams = runCatching {
            interpreter.streams(
                pack,
                hit.playbackId ?: hit.id,
                episodeNumber.toString(),
                null,
                season
            )
        }.getOrDefault(emptyList())
        val pending = streams.filter { stream ->
            stream.url.startsWith("https://") &&
                stream.url !in excludeUrls &&
                hostOf(stream.url) !in excludeHosts
        }
        if (pending.isEmpty()) {
            trace(sessionId, pack.id, "keine weitere Alternative", null)
            return@withContext emptyList()
        }
        trace(
            sessionId,
            pack.id,
            "${pending.size} Alternativen offen (Hosts ${pending.map { hostOf(it.url) }.distinct()})",
            null
        )
        resolveAlternatives(
            pack = pack,
            hit = hit,
            pending = pending,
            episodeNumber = episodeNumber,
            season = season,
            sessionId = sessionId,
            deadlineMs = System.currentTimeMillis() + ALTERNATIVE_DEADLINE_MS
        )
    }

    /**
     * Alternative-Kette: die WebView-tauglichen Kandidaten **nacheinander**,
     * die reinen HTTP-Kandidaten parallel.
     *
     * **Warum nacheinander:** Die versteckte WebView ist app-weit serialisiert
     * ([WebViewGate]). Im Parallelbetrieb standen die Alternativen im
     * WebView-Slot hintereinander und liefen dabei in ihre **eigenen**
     * Zeitdeckel, ohne je eine Anfrage abzusetzen. Messung Lauf 8 (A52,
     * 24.11.2026): alle sechs Alternativen eines moflix-Titels scheiterten
     * mit `js: kein Rückgabewert` — der erste Kandidat hielt den Slot 8 s
     * lang, die übrigen fünf wurden währenddessen abgeschnitten. Kein
     * einziger kam zu einer Anfrage.
     *
     * **Warum ein eigenes Budget je Kandidat:** Ein gescheitertes Pack darf
     * den generischen Weg nicht mehr verdecken (Block 4f im Resolver). Die
     * Interception nach dem Scheitern braucht ihr eigenes Stück Budget —
     * sonst hätte der zweite Kandidat wieder nichts, womit er arbeiten
     * könnte.
     *
     * **Was am Ende herauskommt, bleibt:** Ein Kandidat, der die ganze
     * Kette braucht, kostet die Kette — aber nicht das Ergebnis. Vorher
     * wurde die Alternative-Kette bei Zeitüberschreitung komplett
     * verworfen, auch wenn der erste Kandidat längst spielbar war.
     */
    private suspend fun resolveAlternatives(
        pack: SourcePack,
        hit: SearchResult,
        pending: List<StreamResult>,
        episodeNumber: Int,
        season: Int,
        sessionId: Long,
        deadlineMs: Long
    ): List<MirrorCandidate> = coroutineScope {
        val (webFirst, plain) = pending.partition { needsWebView(it.url) }
        val plainJobs = plain.map { stream ->
            async {
                stream to resolveHoster(
                    pack,
                    stream,
                    episodeNumber,
                    season,
                    sessionId,
                    WebViewBudget.none()
                )
            }
        }
        val webResults = mutableListOf<Pair<StreamResult, ResolvedStream?>>()
        var started = System.currentTimeMillis()
        for (stream in webFirst) {
            val remaining = deadlineMs - started
            if (remaining <= MIN_ALTERNATIVE_MS) {
                trace(
                    sessionId,
                    pack.id,
                    "Alternative-Kette nach ${ALTERNATIVE_DEADLINE_MS - remaining} ms beendet " +
                        "(${webResults.size} WebView-Kandidaten geprüft)",
                    stream.url
                )
                break
            }
            val slice = minOf(ALTERNATIVE_STREAM_MS, remaining)
            val budget = WebViewBudget.of(
                attempts = WEBVIEW_ATTEMPTS_PER_ALTERNATIVE,
                deadlineMs = started + slice
            )
            val resolved = resolveHoster(
                pack = pack,
                stream = stream,
                episodeNumber = episodeNumber,
                season = season,
                sessionId = sessionId,
                webBudget = budget,
                timeoutMs = slice
            )
            webResults += stream to resolved
            started = System.currentTimeMillis()
        }
        (plainJobs.awaitAll() + webResults).mapNotNull { (stream, hoster) ->
            hoster?.let { toCandidate(pack, hit, stream, it, episodeNumber, season) }
        }
    }

    /**
     * Braucht dieser Stream die versteckte WebView? Entweder weil sein
     * Hoster-Pack eine Challenge meldet, weil er einen `js`-Schritt hat
     * (der laeuft in derselben WebView) oder weil das Ziel eine Embed-Form
     * ohne eigenes Pack ist (Embed-Gate des Resolvers).
     *
     * Ohne die `js`-Regel landet z. B. ein jwplayer-Host ohne Embed-Form
     * im "guenstigen" Zweig, bekommt **kein** WebView-Budget und kann nach
     * dem Scheitern seines Packs nicht auf die Interception zurueckfallen
     * (Messung Lauf 8, 24.11.2026).
     */
    private fun needsWebView(url: String): Boolean {
        val hosterPack = runCatching { hosterResolver.findPack(url) }.getOrNull()
        if (hosterPack != null && !hosterPack.captcha.isNullOrBlank()) return true
        if (hosterPack != null && hosterPack.steps.any { it.action == "js" }) return true
        // DEVIDEOSRC ist selbst keine Embed-Form, liefert aber Provider-Embeds
        // (Dropload/SuperVideo). Deren Medienabruf laeuft ueber jwplayer/hls.js
        // — der sichtbare `video.src` ist dann ein blob:, nur der **Abruf**
        // zeigt die m3u8. Also braucht der devideosrc-Kandidat den WebView-
        // Versuch des Titels.
        if (DeVideoSrcResolver.matches(url)) return true
        // uqload.vc/flyfile.app: JS-Player ohne Embed-Form (gemessen Lauf 3).
        return HosterResolver.isWebFirst(url)
    }

    /** Gast-Wiedergabe möglich? Packs mit `guest: false` (VOE) nicht. */
    private fun guestCapable(url: String): Boolean {
        val hosterPack = runCatching { hosterResolver.findPack(url) }.getOrNull() ?: return true
        return hosterPack.guest
    }

    private suspend fun resolveHoster(
        pack: SourcePack,
        stream: StreamResult,
        episodeNumber: Int,
        season: Int,
        sessionId: Long,
        webBudget: WebViewBudget,
        timeoutMs: Long = HOSTER_TIMEOUT_MS
    ): ResolvedStream? {
        trace(
            sessionId,
            pack.id,
            "Hosterauflösung ${stream.hosterId ?: "unknown"} s${season}e$episodeNumber",
            stream.url
        )
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
                withTimeoutOrNull(timeoutMs) {
                    hosterResolver.resolve(stream.url, cookies, budget = webBudget, episodeNumber = episodeNumber)
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
            withTimeoutOrNull(timeoutMs) {
                hosterResolver.resolve(stream.url, budget = webBudget, episodeNumber = episodeNumber)
            }
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

        /** Pack-ID von bs.to; die Quelle braucht einen eigenen Pfad. */
        private const val BS_TO_PACK_ID = "bs-to"

        /** Kataloggruppe von bs.to. */
        private const val GROUP_BSTO = "bsto"

        /**
         * Hosts, über die die Medien-Interception läuft. Sie werden beim Start
         * vorgewärmt: VOE ist der Hoster von bs.to, streamcloud und aniworld;
         * die Indexseiten selbst setzen Cloudflare-/DDoS-Guard-Cookies.
         */
        private val INTERCEPTION_WARMUP_URLS = listOf(
            "https://voe.sx/",
            "https://burningseries.ac/",
            "https://aniworld.to/",
            "https://streamcloud.date/",
            "https://serienstream.to/"
        )
        /**
         * Budget je Quelle. Packs mit JS-Kette (serienstream-Redirect, devideosrc,
         * vidmoly) brauchen länger als ein reiner HTTP-Pfad. Der Player wartet
         * trotzdem nicht darauf — resolveFast kehrt 600 ms nach dem ersten
         * Kandidaten zurück.
         *
         * Messung 23.11. (A52, 448109a): Mit 10 s schnitt das Budget die
         * Hoster-Kette ab, **bevor** die WebView-Interception fertig war —
         * 34 Titel endeten mit „hoster timeout" (moflix, filmpalast, s-to,
         * streamcloud, aniworld). Das Budget muss deshalb Quellenschritt und
         * Hoster-Schritt **beide** umfassen.
         */
        private const val SOURCE_TIMEOUT_MS = 32_000L

        /** Budget für bs.to (Challenge + Klick + Medienabruf in der WebView). */
        private const val BS_TO_TIMEOUT_MS = 34_000L

        /**
         * Hoster-Kette inkl. stiller WebView-Interception (VOE/Doodstream).
         * Die Interception allein darf 20 s brauchen — der Schnitt darf nicht
         * vorher fallen, sonst geht das Ergebnis verloren, nicht nur die Zeit.
         */
        private const val HOSTER_TIMEOUT_MS = 26_000L

        /**
         * WebView-Budget je Titel. Ein Versuch, mit Frist: die versteckte
         * WebView ist app-weit serialisiert, mehrere parallele Versuche eines
         * Titels stehen nur in der Warteschlange (Messung be45079: 7 von 14
         * Versuchen ohne eine einzige Anfrage in der WebView).
         */
        private const val WEBVIEW_ATTEMPTS_PER_TITLE = 1
        private val WEBVIEW_BUDGET_MS = SOURCE_TIMEOUT_MS
        private const val MAX_WEBVIEW_STREAMS = 1

        /**
         * Frist der gesamten Alternative-Kette (Block 4f).
         *
         * Die Kette läuft **nacheinander**: jeder Kandidat bekommt seinen
         * eigenen WebView-Slot. 60 s sind zwei Kandidaten mit je ~30 s —
         * mehr passt nicht durch die eine versteckte WebView.
         */
        private const val ALTERNATIVE_DEADLINE_MS = 60_000L

        /**
         * Frist je Kandidat der Alternative-Kette: Interception (20 s) plus
         * `js`-Stufe des Packs (8 s) plus Reserve — sonst wird das Ergebnis
         * der Interception mitten im Lauf abgeschnitten und ist verloren.
         */
        private const val ALTERNATIVE_STREAM_MS = 34_000L

        /** Unter dieser Restzeit startet kein weiterer Kandidat. */
        private const val MIN_ALTERNATIVE_MS = 8_000L

        /**
         * WebView-Versuche je Alternativ-Kandidat: einer für den Pack-Pfad,
         * einer für die generische Interception, die danach läuft.
         */
        private const val WEBVIEW_ATTEMPTS_PER_ALTERNATIVE = 2

        /** Vorlauf nach dem ersten Kandidaten, bevor der Rest verworfen wird. */
        const val FIRST_CANDIDATE_GRACE_MS = 600L

        /** Obergrenze des Einsammelns, wenn gar kein Kandidat kommt. */
        private const val HARD_DEADLINE_MS = 34_000L

        // Kataloggruppe → Pack(s) fuer den Vorwaermlauf: siehe [PrefetchScope]
        // (dort auch die Begruendung, warum `bsto` nicht vorgewaermt wird).

        /** Gesamtfrist des Vorwaermlaufs je Titel. */
        private const val PREFETCH_TIMEOUT_MS = 10_000L

        /** Frist je Hoster im Vorwaermlauf. */
        private const val PREFETCH_HOSTER_TIMEOUT_MS = 6_000L

        /**
         * Hoster je Pack im Vorwaermlauf.
         *
         * Die Watch-Seite listet oft acht und mehr Ziele; der Vorwaermlauf
         * soll den Fokus nicht mit einem Dutzend Anfragen belasten. Drei
         * Ziele reichen fuer den Sofort-Kandidaten.
         */
        private const val PREFETCH_MAX_STREAMS = 3

        /** Mehrere gleichzeitige WebViews blockieren den Hauptthread (ANR). */
        private const val MAX_WARMUP_HOSTS = 2
    }
}
