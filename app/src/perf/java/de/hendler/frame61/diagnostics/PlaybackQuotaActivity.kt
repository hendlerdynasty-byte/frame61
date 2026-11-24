package de.hendler.frame61.diagnostics

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import dagger.hilt.android.AndroidEntryPoint
import de.hendler.frame61.net.PlaybackDiagnostics
import de.hendler.frame61.playback.AutoPlaybackResolver
import de.hendler.frame61.playback.InterceptionLog
import de.hendler.frame61.player.Media3Frame61Player
import de.hendler.frame61.resolver.MirrorCandidate
import de.hendler.frame61.resolver.hostOf
import java.io.File
import java.util.Random
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

private const val MODE_TRAIN = "train"
private const val MODE_HOLDOUT = "holdout"

/**
 * Messharness fuer die Titel-Erfolgsquote (1.5.0).
 *
 * Zwei getrennte Stichproben: `train` (Seed 20261122) und `holdout`
 * (Seed 20261123, disjunkt zur Trainings-Stichprobe). Die Holdout-Stichprobe
 * wird erst nach der Trainings-Stichprobe gezogen und nie zum Debuggen
 * verwendet.
 *
 * Pro Titel wird gemessen:
 *  - **Auflösungszeit** (resolveMs): vom Start bis zur Kandidatenliste,
 *  - **Wiedergabezeit** (playMs): vom ersten Kandidaten bis zur ersten
 *    fortschreitenden Position,
 *  - **Startzeit** (firstFrameMs): Summe, ab dem Tippen.
 *
 * Fehlerklassen: `NO_CANDIDATE`, `EMBED_UNRESOLVED`, `CONTAINER_UNSUPPORTED`,
 * `GEO_BLOCKED`, `RATE_LIMITED`, `TIMEOUT`.
 */
@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
open class PlaybackQuotaActivity : ComponentActivity() {

    @Inject lateinit var autoResolver: AutoPlaybackResolver

    private lateinit var engine: Media3Frame61Player
    private lateinit var playerView: PlayerView
    private lateinit var playerContainer: FrameLayout
    private lateinit var statusView: TextView
    private lateinit var scroll: ScrollView
    private var quotaJob: Job? = null
    private var renderedEngine = ""
    private val lines = mutableListOf<String>()
    private var logFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = Media3Frame61Player(this)
        playerView = PlayerView(this).apply {
            useController = false
            player = engine.exoPlayer
        }
        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(16, 16, 16, 16)
        }
        playerContainer = FrameLayout(this)
        scroll = ScrollView(this).apply { addView(statusView) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(
                scroll,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f)
            )
            addView(
                playerContainer,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f)
            )
        }
        setContentView(root)
        engine.attach()
        // Ein Fehler im Harness beendet die App nicht: Ursache steht sichtbar
        // im Statusbereich und im Logcat.
        quotaJob = lifecycleScope.launch {
            try {
                runQuota()
            } catch (error: Throwable) {
                Log.e(TAG, "QUOTA FATAL", error)
                report("QUOTA FATAL ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    private suspend fun runQuota() {
        val mode = sampleMode()
        val seed = if (mode == MODE_HOLDOUT) HOLDOUT_SEED else TRAIN_SEED
        val items = loadCatalog()
        if (items.isEmpty()) {
            report("QUOTA FATAL Katalog nicht lesbar")
            return
        }
        val sample = drawSample(items, mode, seed)
        logFile = File(getExternalFilesDir(null), "quota/playlog-$mode-${System.currentTimeMillis()}.txt")
            .also { it.parentFile?.mkdirs() }
        report(
            "QUOTA START mode=$mode n=${sample.size} seed=$seed katalog=${items.size} " +
                "datei=${logFile?.absolutePath}"
        )

        var ok = 0
        var resolveTotal = 0L
        var firstFrameTotal = 0L
        val resolveTimes = mutableListOf<Long>()
        val firstFrames = mutableListOf<Long>()
        val classes = mutableMapOf<String, Int>()
        // Quote je Quellengruppe: der Mischwert allein verdeckt, welche Gruppe
        // trägt und welche nur den leichten Pfad zählt.
        val groupOk = mutableMapOf<String, Int>()
        val groupTotal = mutableMapOf<String, Int>()
        val groupClasses = mutableMapOf<String, MutableMap<String, Int>>()
        // Fast-Path getrennt: der Vergleich mit Lauf 1-7 bleibt so erhalten,
        // auch wenn der Harness ab jetzt zusaetzlich den Produktionspfad
        // abbildet (Alternative nach Fehlschlag).
        var fastOk = 0
        val fastClasses = mutableMapOf<String, Int>()
        val fastGroupOk = mutableMapOf<String, Int>()
        val fastGroupClasses = mutableMapOf<String, MutableMap<String, Int>>()

        sample.forEachIndexed { index, item ->
            groupTotal[item.group] = (groupTotal[item.group] ?: 0) + 1
            val episode = if (item.type == "series") 1 else 0
            // Ohne aktive Diagnose-Sitzung verwirft `PlaybackDiagnostics`
            // **jedes** Ereignis — der komplette Resolver-Ablauf bliebe
            // unsichtbar (Befund 24.11.2026: `classifyFailure` sah nie etwas,
            // weil keine Sitzung lief). Pro Titel eine Sitzung, damit der
            // Fehlergrund mitgemessen wird.
            val sessionId = PlaybackDiagnostics.beginSession(item.title, episode)
            val interceptMark = InterceptionLog.currentSeq()
            val started = SystemClock.elapsedRealtime()
            val traceStart = PlaybackDiagnostics.events.value.size
            val resolved = runCatching {
                withTimeoutOrNull(RESOLVE_BUDGET_MS) {
                    withContext(Dispatchers.IO) {
                        autoResolver.resolveFast(item.title, episode, fresh = true)
                    }
                }
            }.getOrNull()
            val resolveMs = SystemClock.elapsedRealtime() - started
            resolveTimes += resolveMs
            resolveTotal += resolveMs

            val mirrors = resolved.orEmpty()
            var played = false
            var firstFrameMs = 0L
            var tried = 0
            var usedHost = "-"
            var errorClass = "NO_CANDIDATE"
            val triedHosts = mutableListOf<String>()
            // Fast-Path getrennt mitzaehlen: nur so bleibt die Reihe
            // Lauf 1-7 vergleichbar, wenn der Harness ab jetzt zusaetzlich
            // den Produktionspfad (Alternative nach Fehlschlag) abbildet.
            var fastPlayed = false
            var fastFirstFrameMs = 0L
            var fastTried = 0
            var fastUsedHost = "-"
            var fastErrorClass = "NO_CANDIDATE"
            val fastHosts = mutableListOf<String>()
            val failedUrls = mutableSetOf<String>()
            // Gescheiterte **Hoster** (Host der Watch-URL). Für die
            // Alternative-Kette ist das der richtige Schlüssel: die
            // aufgelöste Medien-URL sieht anders aus als die Hoster-Seite.
            val failedHosts = mutableSetOf<String>()
            var lastSourceId: String? = null

            if (mirrors.isNotEmpty()) {
                for (mirror in mirrors.take(MAX_CANDIDATES)) {
                    fastTried += 1
                    fastHosts += host(mirror.url)
                    failedUrls += mirror.url
                    val watchHost = mirror.streamUrl?.let { hostOf(it) }.orEmpty()
                    if (watchHost.isNotEmpty()) failedHosts += watchHost
                    lastSourceId = mirror.sourceId
                    val attemptStarted = SystemClock.elapsedRealtime()
                    val attempt = playCandidate(mirror)
                    if (attempt.firstFrameMs != null) {
                        fastPlayed = true
                        fastFirstFrameMs = resolveMs + (SystemClock.elapsedRealtime() - attemptStarted)
                        fastUsedHost = host(mirror.url)
                        fastErrorClass = "-"
                        break
                    }
                    fastErrorClass = attempt.errorClass ?: "EMBED_UNRESOLVED"
                }
            } else {
                fastErrorClass = classifyFailure(PlaybackDiagnostics.events.value.drop(traceStart))
            }
            played = fastPlayed
            firstFrameMs = fastFirstFrameMs
            tried = fastTried
            usedHost = fastUsedHost
            errorClass = fastErrorClass
            triedHosts += fastHosts

            // Produktionspfad spiegeln (Block 4e): der Player holt nach einem
            // Fehlschlag die naechste Alternative **derselben Quelle**. Der
            // Fast-Path loest je Quelle nur den ersten Hoster auf — 12 der 34
            // Fehlschlaege in Lauf 7 scheiterten an einem toten Hoster, obwohl
            // 3-8 Alternativen danebenstanden (moflix-Watch-Seite, live geprueft
            // am 24.11.2026).
            //
            // Der Fast-Teil bleibt unveraendert (max. 3 Kandidaten wie Lauf
            // 1-7), damit das Delta **eindeutig** der Alternative-Kette
            // zuzurechnen ist. Die Alternativen kommen obendrauf — sie
            // treffen per Host-Sperre andere Hoster als der Fast-Teil.
            var alternativeRound = 0
            while (!played && alternativeRound < ALTERNATIVE_ROUNDS && lastSourceId != null) {
                val sourceId = lastSourceId ?: break
                val alternativeStarted = SystemClock.elapsedRealtime()
                val alternatives = runCatching {
                    withTimeoutOrNull(ALTERNATIVE_BUDGET_MS) {
                        withContext(Dispatchers.IO) {
                            autoResolver.resolveAlternative(
                                title = item.title,
                                episodeNumber = episode,
                                sourceId = sourceId,
                                excludeUrls = failedUrls.toSet(),
                                excludeHosts = failedHosts.toSet(),
                                sessionId = sessionId
                            )
                        }
                    }
                }.getOrNull().orEmpty()
                val alternativeMs = SystemClock.elapsedRealtime() - alternativeStarted
                if (alternatives.isEmpty()) break
                for (mirror in alternatives.take(ALTERNATIVE_CANDIDATES)) {
                    tried += 1
                    triedHosts += host(mirror.url)
                    failedUrls += mirror.url
                    val watchHost = mirror.streamUrl?.let { hostOf(it) }.orEmpty()
                    if (watchHost.isNotEmpty()) failedHosts += watchHost
                    lastSourceId = mirror.sourceId
                    val attemptStarted = SystemClock.elapsedRealtime()
                    val attempt = playCandidate(mirror)
                    if (attempt.firstFrameMs != null) {
                        played = true
                        firstFrameMs = resolveMs + alternativeMs +
                            (SystemClock.elapsedRealtime() - attemptStarted)
                        usedHost = host(mirror.url)
                        errorClass = "-"
                        break
                    }
                    errorClass = attempt.errorClass ?: "EMBED_UNRESOLVED"
                }
                alternativeRound += 1
            }
            // Die Resolver-Ereignisse dieses Titels: der Grund, warum ein
            // Titel nicht Spielbar war, steht hier — vorher war er nur im
            // Logcat zu finden und dort vom 400-Zeilen-Fenster abgeschnitten.
            val trace = PlaybackDiagnostics.events.value.drop(traceStart)
            PlaybackDiagnostics.endSession(sessionId)

            // Was die WebView-Interception in diesem Titel geleistet hat:
            // Anzahl Versuche, Treffer und die **gemessene** Zeit.
            val intercepts = InterceptionLog.since(interceptMark)
            val interceptHit = intercepts.count { it.outcome == "medien" }
            val interceptMax = intercepts.maxOfOrNull { it.elapsedMs } ?: 0L
            val interceptNote =
                "intercept=${intercepts.size} medien=$interceptHit maxMs=$interceptMax"

            if (fastPlayed) {
                fastOk++
                fastGroupOk[item.group] = (fastGroupOk[item.group] ?: 0) + 1
            } else {
                fastClasses[fastErrorClass] = (fastClasses[fastErrorClass] ?: 0) + 1
                val fastMap = fastGroupClasses.getOrPut(item.group) { mutableMapOf() }
                fastMap[fastErrorClass] = (fastMap[fastErrorClass] ?: 0) + 1
            }
            report(
                "QUOTA FAST $mode idx=${index + 1} id=${item.id} group=${item.group} " +
                    "type=${item.type} title=${item.title.sanitize()} " +
                    "RESULT=${if (fastPlayed) "OK" else "FAIL"} class=${if (fastPlayed) "-" else fastErrorClass} " +
                    "resolveMs=$resolveMs candidates=${mirrors.size} tried=$fastTried " +
                    (if (fastPlayed) "host=$fastUsedHost" else "hosts=[${fastHosts.joinToString(",")}]")
            )

            if (played) {
                ok++
                groupOk[item.group] = (groupOk[item.group] ?: 0) + 1
                firstFrameTotal += firstFrameMs
                firstFrames += firstFrameMs
                report(
                    "QUOTA $mode idx=${index + 1} id=${item.id} group=${item.group} type=${item.type} " +
                        "RESULT=OK class=- resolveMs=$resolveMs playMs=${firstFrameMs - resolveMs} " +
                        "firstFrameMs=$firstFrameMs candidates=${mirrors.size} tried=$tried " +
                        "host=$usedHost $interceptNote"
                )
            } else {
                classes[errorClass] = (classes[errorClass] ?: 0) + 1
                val groupMap = groupClasses.getOrPut(item.group) { mutableMapOf() }
                groupMap[errorClass] = (groupMap[errorClass] ?: 0) + 1
                report(
                    "QUOTA $mode idx=${index + 1} id=${item.id} group=${item.group} type=${item.type} " +
                        "title=${item.title.sanitize()} RESULT=FAIL class=$errorClass " +
                        "resolveMs=$resolveMs candidates=${mirrors.size} tried=$tried " +
                        "hosts=[${triedHosts.joinToString(",")}] $interceptNote " +
                        "cause=${lastResolverCause(trace)}"
                )
                // Die vollstaendige Resolver-Kette des Titels: nur sie zeigt,
                // an welcher Stelle die Aufloesung wirklich gehalten hat.
                report(
                    "QUOTA CAUSE $mode idx=${index + 1} " +
                        trace.filter { it.category == "RESOLVER" }
                            .takeLast(CAUSE_EVENTS)
                            .joinToString(" | ") { "${it.category}:${it.message.compact()}" }
                )
            }
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }

        val resolveSorted = resolveTimes.sorted()
        val frameSorted = firstFrames.sorted()
        report(
            "QUOTA SUMMARY mode=$mode ok=$ok/${sample.size} quote=${ok * 100 / sample.size} " +
                "resolveP50Ms=${percentile(resolveSorted, 50)} " +
                "resolveP95Ms=${percentile(resolveSorted, 95)} " +
                "frameP50Ms=${percentile(frameSorted, 50)} " +
                "frameP95Ms=${percentile(frameSorted, 95)} " +
                "failures=[${classes.entries.sortedByDescending { it.value }.joinToString(",") { "${it.key}:${it.value}" }}]"
        )
        // Quote je Quellengruppe — der Mischwert allein sagt nicht, wo es hakt.
        // Format: Gruppe | Titel | spielbar | Quote | häufigste Fehlerklasse.
        report(
            "QUOTA GROUPEN mode=$mode " +
                groupTotal.keys.sorted().joinToString(" | ") { group ->
                    val total = groupTotal[group] ?: 0
                    val groupOkCount = groupOk[group] ?: 0
                    val errors = groupClasses[group].orEmpty()
                        .entries.sortedByDescending { it.value }
                    val top = errors.firstOrNull()
                        ?.let { "${it.key}:${it.value}" } ?: "-"
                    val quote = if (total > 0) groupOkCount * 100 / total else 0
                    "$group | $total | $groupOkCount | $quote% | $top"
                }
        )
        // Fast-Path separat: die Reihe Lauf 1-7 bleibt damit vergleichbar
        // (gleiche Stichprobe, gleicher Seed, nur erster Hoster je Quelle),
        // waehrend die Hauptzahl den Produktionspfad mit Alternativen zeigt.
        report(
            "QUOTA FAST SUMMARY mode=$mode ok=$fastOk/${sample.size} " +
                "quote=${fastOk * 100 / sample.size} " +
                "failures=[${fastClasses.entries.sortedByDescending { it.value }.joinToString(",") { "${it.key}:${it.value}" }}]"
        )
        report(
            "QUOTA FAST GROUPEN mode=$mode " +
                groupTotal.keys.sorted().joinToString(" | ") { group ->
                    val total = groupTotal[group] ?: 0
                    val groupOkCount = fastGroupOk[group] ?: 0
                    val errors = fastGroupClasses[group].orEmpty()
                        .entries.sortedByDescending { it.value }
                    val top = errors.firstOrNull()
                        ?.let { "${it.key}:${it.value}" } ?: "-"
                    val quote = if (total > 0) groupOkCount * 100 / total else 0
                    "$group | $total | $groupOkCount | $quote% | $top"
                }
        )
        // Die letzten Interception-Versuche mit Request-Kette: nur so ist
        // sichtbar, an welcher Stelle der Weg (Challenge, Hoster-Redirect,
        // Medienabruf) gehalten hat. Versuche, die nie in die WebView kamen
        // (übersprungen/0 Anfragen), sagen nur aus, dass die Sperre belegt
        // war — deshalb werden zuerst die mit einer Kette ausgegeben.
        val detailedIntercepts = InterceptionLog.all()
            .filter { it.requests.isNotEmpty() }
            .takeLast(DETAILED_INTERCEPTS)
            .ifEmpty { InterceptionLog.all().takeLast(DETAILED_INTERCEPTS) }
        detailedIntercepts.forEach { record ->
            report(
                "INTERCEPT #${record.seq} ${record.outcome} ${record.elapsedMs}ms " +
                    "url=${record.url.take(90)} media=${record.mediaUrl?.take(90) ?: "-"} " +
                    "script=${record.scriptResult ?: "-"} last=${record.lastPage?.take(90) ?: "-"}"
            )
            record.requests.take(8).forEach { request ->
                report("INTERCEPT #${record.seq} REQ $request")
            }
        }
        Log.i(TAG, "QUOTA DONE mode=$mode datei=${logFile?.absolutePath}")
    }

    /**
     * Klassifiziert einen Lauf ohne Kandidaten anhand der Diagnose-Ereignisse,
     * die der Resolver waehrend dieser Messung geschrieben hat.
     */
    private fun classifyFailure(events: List<de.hendler.frame61.net.PlaybackTraceEvent>): String {
        val text = events.joinToString(" ") { "${it.category} ${it.message}" }.lowercase()
        return when {
            text.contains("429") -> "RATE_LIMITED"
            text.contains("403") || text.contains("451") -> "GEO_BLOCKED"
            text.contains("captcha") -> "EMBED_UNRESOLVED"
            text.contains("zeitüberschreitung") || text.contains("timeout") -> "TIMEOUT"
            text.contains("hoster") || text.contains("embed") -> "EMBED_UNRESOLVED"
            else -> "NO_CANDIDATE"
        }
    }

    private suspend fun playCandidate(mirror: MirrorCandidate): Attempt {
        val started = SystemClock.elapsedRealtime()
        engine.open(mirror.url, requestHeaders = mirror.requestHeaders, cookies = mirror.cookies)
        var lastError: String? = "NO_PROGRESS"
        repeat(PLAYBACK_POLLS) {
            delay(PLAYBACK_POLL_MS)
            engine.publish()
            val state = engine.state.value
            renderEngine(state.engine)
            if (state.positionMs > 0L) {
                return Attempt(SystemClock.elapsedRealtime() - started, null)
            }
            if (state.error != null) lastError = state.error
        }
        val state = engine.state.value
        if (engine.lastPrimaryErrorName != null && lastError == "NO_PROGRESS") {
            lastError = engine.lastPrimaryErrorName
        }
        Log.w(TAG, "QUOTA ATTEMPT FAILED ${mirror.url.take(90)} error=$lastError")
        engine.stop()
        val mapped = when {
            lastError?.contains("PARSING_CONTAINER") == true -> "CONTAINER_UNSUPPORTED"
            lastError?.contains("timeout", ignoreCase = true) == true -> "TIMEOUT"
            else -> "EMBED_UNRESOLVED"
        }
        return Attempt(null, mapped)
    }

    /** Trainings- und Holdout-Stichprobe, garantiert disjunkt. */
    private fun drawSample(items: List<CatalogItem>, mode: String, seed: Long): List<CatalogItem> {
        if (mode == MODE_HOLDOUT) {
            val trainIds = sample(items, TRAIN_SEED).mapTo(mutableSetOf()) { it.id }
            val rest = items.filter { it.id !in trainIds }
            return sample(rest, seed)
        }
        return sample(items, seed)
    }

    private fun sample(items: List<CatalogItem>, seed: Long): List<CatalogItem> {
        val random = Random(seed)
        val series = items.filter { it.type == "series" }.shuffled(random)
        val movies = items.filter { it.type != "series" }.shuffled(random)
        val targetSeries = (SAMPLE_SIZE.toLong() * series.size / items.size.coerceAtLeast(1)).toInt()
        return (series.take(targetSeries) + movies.take(SAMPLE_SIZE - targetSeries)).shuffled(random)
    }

    private fun loadCatalog(): List<CatalogItem> {
        val text = runCatching {
            assets.open("catalog/catalog.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("items") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val title = entry.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CatalogItem(
                id = entry.optString("id"),
                title = title,
                type = entry.optString("type"),
                group = entry.optString("group").ifBlank { "unbekannt" }
            )
        }
    }

    /** Welche Stichprobe gefahren wird; [PlaybackQuotaHoldoutActivity] überschreibt das. */
    protected open fun sampleMode(): String =
        (intent?.getStringExtra(EXTRA_SAMPLE) ?: MODE_TRAIN).lowercase()

    private fun report(line: String) {
        lines += line
        Log.i(TAG, line)
        statusView.text = lines.joinToString("\n")
        logFile?.appendText("$line\n")
    }

    private fun host(url: String): String =
        url.substringAfter("://", "").substringBefore('/').ifBlank { "-" }

    /**
     * Der letzte Resolver-Grund dieses Titels als einzelnes Token — ohne
     * Leerzeichen, damit die QUOTA-Zeile maschinell lesbar bleibt.
     */
    private fun lastResolverCause(
        events: List<de.hendler.frame61.net.PlaybackTraceEvent>
    ): String {
        val resolver = events.filter { it.category == "RESOLVER" }
        // Der Fehlgrund schlägt die Mengenangabe ("0 Kandidaten") — die steht
        // zwar spät in der Kette, sagt aber nichts über die Ursache.
        val event = resolver.lastOrNull { it.message.contains("fehlgeschlagen") }
            ?: resolver.lastOrNull { it.message.contains("Zeitüberschreitung") }
            ?: resolver.lastOrNull()
        return event?.message?.compact()?.take(140) ?: "-"
    }

    private fun renderEngine(engineName: String) {
        if (engineName == renderedEngine) return
        renderedEngine = engineName
        playerContainer.removeAllViews()
        val view = if (engineName == "Android MediaPlayer") engine.fallbackView else playerView
        playerContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun percentile(sorted: List<Long>, p: Int): Long {
        if (sorted.isEmpty()) return 0L
        val index = ((sorted.size - 1) * p / 100.0).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    override fun onDestroy() {
        quotaJob?.cancel()
        engine.release()
        super.onDestroy()
    }

    private data class CatalogItem(
        val id: String,
        val title: String,
        val type: String,
        val group: String
    )

    private data class Attempt(val firstFrameMs: Long?, val errorClass: String?)

    private companion object {
        const val TAG = "Frame61Quota"
        const val EXTRA_SAMPLE = "sample"
        const val SAMPLE_SIZE = 100
        const val TRAIN_SEED = 20261122L
        const val HOLDOUT_SEED = 20261123L
        const val MAX_CANDIDATES = 3
        const val PLAYBACK_POLL_MS = 250L
        const val PLAYBACK_POLLS = 28
        // bs.to braucht bis zu ~28 s (Challenge + Klick in der WebView).
        const val RESOLVE_BUDGET_MS = 60_000L
        // Block 4e: der Produktionspfad holt nach einem Fehlschlag bis zu
        // drei weitere Hoster **derselben** Quelle. Der Harness bildet das ab,
        // damit die Quote den Produktpfad zeigt, nicht nur den ersten Hoster.
        const val ALTERNATIVE_ROUNDS = 2
        // Muss die Kette des Resolvers abdecken (Block 4f: ALTERNATIVE_DEADLINE_MS
        // = 60 s, sequenziell). Vorher 45 s — damit haette der Harness die
        // Alternative-Kette abgeschnitten und ihr Ergebnis verworfen, obwohl
        // der Resolver längst einen spielbaren Kandidaten hatte.
        const val ALTERNATIVE_BUDGET_MS = 70_000L
        /** Kandidaten je Alternativeschleife (zusaetzlich zum Fast-Teil). */
        const val ALTERNATIVE_CANDIDATES = 3

        /** Wie viele Interception-Versuche am Ende im Detail stehen. */
        const val DETAILED_INTERCEPTS = 6

        /** Wie viele Resolver-Ereignisse je fehlgeschlagenem Titel protokolliert werden. */
        const val CAUSE_EVENTS = 6
    }
}

private fun String.sanitize(): String = replace(Regex("\\s+"), " ").take(60)

/** Leerzeichen zu Unterstrichen: eine Zeile, ein Token. */
private fun String.compact(): String = replace(Regex("\\s+"), "_").take(160)

/**
 * Zweite, disjunkte Stichprobe (Holdout). Sie wird erst gefahren, wenn die
 * Trainings-Stichprobe fertig ist, und dient nur der Abnahme — nie zum
 * Nachsteuern. Start über die Einstellungen („Titel-Quote Holdout").
 */
@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackQuotaHoldoutActivity : PlaybackQuotaActivity() {
    override fun sampleMode(): String = MODE_HOLDOUT
}