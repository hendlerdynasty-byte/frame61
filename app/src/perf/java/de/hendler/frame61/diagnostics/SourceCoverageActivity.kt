package de.hendler.frame61.diagnostics

import android.graphics.Color
import android.os.Bundle
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
import de.hendler.frame61.playback.WebViewJsEvaluator
import de.hendler.frame61.player.Media3Frame61Player
import de.hendler.frame61.resolver.HosterOutcome
import de.hendler.frame61.resolver.HosterPack
import de.hendler.frame61.resolver.HosterPackValidator
import de.hendler.frame61.resolver.HosterResolver
import de.hendler.frame61.resolver.OkHttpBodyFetcher
import de.hendler.frame61.scraper.engine.PackHttpClient
import de.hendler.frame61.scraper.engine.PackInterpreter
import de.hendler.frame61.scraper.engine.PackLoader
import de.hendler.frame61.scraper.engine.PackRule
import de.hendler.frame61.scraper.engine.StreamResult
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Perf-only proof of the real source chain on the device.
 *
 * For every index page of the delivery list the activity runs the production
 * chain — pack search, stream lookup, hoster resolution — and then plays the
 * resolved URL with the production engine. A source only counts as verified
 * when the player actually advanced on the device; everything else is reported
 * as BLOCKED with the live cause (HTTP status, timeout, captcha, dead host).
 *
 * Every line of the result is written to the screen *and* to the log, so the
 * run can be read back from a screenshot, a UI dump and logcat.
 */
@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class SourceCoverageActivity : ComponentActivity() {
    @Inject lateinit var packLoader: PackLoader
    @Inject lateinit var interpreter: PackInterpreter
    @Inject lateinit var jsEvaluator: WebViewJsEvaluator

    private val http = PackHttpClient()
    private lateinit var engine: Media3Frame61Player
    private lateinit var playerView: PlayerView
    private lateinit var playerContainer: FrameLayout
    private lateinit var statusView: TextView
    private lateinit var scroll: ScrollView
    private var coverageJob: Job? = null
    private var renderedEngine = ""
    private val results = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = Media3Frame61Player(this)
        playerView = PlayerView(this).apply {
            useController = false
            player = engine.exoPlayer
        }
        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(20, 20, 20, 20)
        }
        playerContainer = FrameLayout(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(statusView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(playerContainer, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        }
        scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        de.hendler.frame61.net.PlaybackDiagnostics.beginSession("source-coverage", 0)
        engine.attach()
        coverageJob = lifecycleScope.launch { runCoverage() }
    }

    private suspend fun runCoverage() {
        val packs = packLoader.loadAllPacks().associateBy { it.id }
        val hosterResolver = HosterResolver(hosterPacks(), OkHttpBodyFetcher(), jsEvaluator)
        val hosterTargetList = hosterTargets()
        var indexVerified = 0
        var playbackVerified = 0

        for (target in INDEX_TARGETS) {
            val pack = packs[target.packId]
            if (pack == null) {
                report("INDEX ${target.packId}", "BLOCKED", target.reason)
                continue
            }
            val searchRule = pack.search
            if (searchRule == null) {
                report("INDEX ${target.packId}", "BLOCKED", "keine Suchregel im Pack")
                continue
            }
            val response = withTimeoutOrNull(HTTP_TIMEOUT_MS) {
                guarded { http.fetch(pack, searchRule, searchParams(searchRule, target.query)) }
            }
            if (response == null) {
                report(
                    "INDEX ${target.packId}",
                    "BLOCKED",
                    "HTTP-Zeitüberschreitung nach $HTTP_TIMEOUT_MS ms (${target.query})",
                    target.reason
                )
                continue
            }
            val failure = response.exceptionOrNull()
            if (failure != null) {
                report(
                    "INDEX ${target.packId}",
                    "BLOCKED",
                    "HTTP/Netzwerk: ${failure.message ?: failure.javaClass.simpleName}",
                    target.reason
                )
                continue
            }
            val answer = response.getOrThrow()
            if (answer.status !in 200..299) {
                report(
                    "INDEX ${target.packId}",
                    "BLOCKED",
                    "HTTP ${answer.status} ${answer.url}",
                    target.reason
                )
                continue
            }
            val hits = interpreter.parseSearch(pack, answer.body)
            if (hits.isEmpty()) {
                report(
                    "INDEX ${target.packId}",
                    "BLOCKED",
                    "HTTP ${answer.status}, aber 0 Treffer aus der Antwort " +
                        "(${answer.contentType ?: "ohne Content-Type"}, ${answer.body.length} Bytes, " +
                        "Anfang: ${answer.body.take(120).sanitize()})",
                    target.reason
                )
                continue
            }
            indexVerified++
            // Die Produktion waehlt den Treffer, dessen Titel die Suchanfrage
            // enthaelt — dieselbe Regel gilt hier.
            val hit = hits.firstOrNull { it.title.contains(target.query, ignoreCase = true) }
                ?: hits.first()
            report(
                "INDEX ${target.packId}",
                "VERIFIED_INDEX",
                "HTTP ${answer.status}, ${hits.size} Treffer, erster '${hit.title}' " +
                    "(${hit.playbackId ?: hit.id})"
            )

            val streamRule = pack.streams
            if (streamRule == null) {
                report(
                    "PLAY ${target.packId}",
                    "BLOCKED",
                    "Indexseite erreichbar, aber das Pack hat keine Stream-Regel"
                )
                continue
            }
            if (streamRule.endpoint.isNullOrBlank() && streamRule.path.isNullOrBlank()) {
                report(
                    "PLAY ${target.packId}",
                    "BLOCKED",
                    "Stream-Regel ohne endpoint/path — kein adressierbares Streamziel"
                )
                continue
            }
            val titleId = hit.playbackId ?: hit.id
            val streamParams = mapOf(
                "titleId" to titleId,
                "id" to titleId,
                "episodeId" to "1"
            )
            val streamResult = withTimeoutOrNull(HTTP_TIMEOUT_MS) {
                guarded { http.fetch(pack, streamRule, streamParams) }
            }
            if (streamResult == null) {
                report(
                    "PLAY ${target.packId}",
                    "BLOCKED",
                    "Streamabruf: Zeitüberschreitung nach $HTTP_TIMEOUT_MS ms"
                )
                continue
            }
            val streamFailure = streamResult.exceptionOrNull()
            if (streamFailure != null) {
                report(
                    "PLAY ${target.packId}",
                    "BLOCKED",
                    "Streamabruf: ${streamFailure.message ?: streamFailure.javaClass.simpleName}"
                )
                continue
            }
            val streamsAnswer = streamResult.getOrThrow()
            if (streamsAnswer.status !in 200..299) {
                report(
                    "PLAY ${target.packId}",
                    "BLOCKED",
                    "Streamabruf HTTP ${streamsAnswer.status} ${streamsAnswer.url}"
                )
                continue
            }
            val streams = interpreter.parseStreams(pack, streamsAnswer.body)
            if (streams.isEmpty()) {
                report(
                    "PLAY ${target.packId}",
                    "BLOCKED",
                    "HTTP ${streamsAnswer.status}, aber keine Stream-Einträge in der Antwort"
                )
                continue
            }
            val played = playStreams(streams.take(MAX_STREAMS), hosterResolver)
            if (played.ok) {
                playbackVerified++
                report("PLAY ${target.packId}", "VERIFIED_PLAYBACK", played.detail)
            } else {
                report("PLAY ${target.packId}", "BLOCKED", played.detail)
            }
        }

        // Hoster-Kette: jeder Hoster mit belegtem Ziel wird auf dem Gerät
        // aufgeloest und abgespielt; Hoster ohne belegtes Ziel bleiben mit dem
        // dokumentierten Grund blockiert.
        var hosterVerified = 0
        val hosterPackList = hosterPacks().associateBy { it.id }
        for (target in hosterTargetList) {
            if (hosterPackList[target.hosterId] == null) {
                report("HOSTER ${target.hosterId}", "BLOCKED", "kein Hoster-Pack im Bundle")
                continue
            }
            val url = target.url
            if (url.isNullOrBlank()) {
                report(
                    "HOSTER ${target.hosterId}",
                    "BLOCKED",
                    target.reason.ifBlank { "kein belegtes Ziel" }
                )
                continue
            }
            val outcome = withTimeoutOrNull(HOSTER_TIMEOUT_MS) {
                guarded { hosterResolver.resolve(url) }
            }
            if (outcome == null) {
                report(
                    "HOSTER ${target.hosterId}",
                    "BLOCKED",
                    "Hoster-Zeitüberschreitung nach $HOSTER_TIMEOUT_MS ms"
                )
                continue
            }
            val failure = outcome.exceptionOrNull()
            if (failure != null) {
                report(
                    "HOSTER ${target.hosterId}",
                    "BLOCKED",
                    "Hoster: ${failure.message ?: failure.javaClass.simpleName}"
                )
                continue
            }
            when (val resolved = outcome.getOrNull()) {
                is HosterOutcome.Resolved -> {
                    val played = play(resolved.url, resolved.requestHeaders, resolved.cookies)
                    if (played.ok) {
                        hosterVerified++
                        report("HOSTER ${target.hosterId}", "VERIFIED_PLAYBACK", played.detail)
                    } else {
                        report(
                            "HOSTER ${target.hosterId}",
                            "BLOCKED",
                            "aufgelöst, aber nicht abgespielt: ${played.detail}"
                        )
                    }
                }
                is HosterOutcome.Unknown -> {
                    val played = play(resolved.url, resolved.requestHeaders, resolved.cookies)
                    if (played.ok) {
                        hosterVerified++
                        report("HOSTER ${target.hosterId}", "VERIFIED_PLAYBACK", played.detail)
                    } else {
                        report(
                            "HOSTER ${target.hosterId}",
                            "BLOCKED",
                            "unbekannter Hoster, nicht abgespielt: ${played.detail}"
                        )
                    }
                }
                is HosterOutcome.Failed ->
                    report("HOSTER ${target.hosterId}", "BLOCKED", "Hoster: ${resolved.reason}")
                is HosterOutcome.CaptchaRequired ->
                    report(
                        "HOSTER ${target.hosterId}",
                        "BLOCKED",
                        "Captcha erforderlich (${resolved.kind}) — WebView-Lösung nötig"
                    )
                null -> report("HOSTER ${target.hosterId}", "BLOCKED", "kein Ergebnis")
            }
        }

        val summary = "COVERAGE SUMMARY index=$indexVerified/${INDEX_TARGETS.size} " +
            "playback=$playbackVerified/${INDEX_TARGETS.size} " +
            "hoster=$hosterVerified/${hosterTargetList.size}"
        Log.i(TAG, summary)
        results += summary
        status(results.joinToString("\n"))
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    /**
     * Runs the blocking pack calls off the main thread and keeps the failure
     * reason, but never swallows the cancellation of the enclosing timeout.
     */
    private suspend fun <T> guarded(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /**
     * The query reaches the request under the rule's own parameter name, the
     * same way [PackInterpreter.search] does it.
     */
    private fun searchParams(rule: PackRule, query: String): Map<String, String> {
        val params = linkedMapOf("query" to query, "q" to query, "page" to "1")
        val name = rule.queryParam
        if (!name.isNullOrBlank() && name !in params) params[name] = query
        return params
    }

    /** Resolves the first stream that plays; returns the proof line or null. */
    private suspend fun playStreams(
        streams: List<StreamResult>,
        hosterResolver: HosterResolver
    ): PlayOutcome {
        var lastReason = "keine Hoster-Auflösung"
        for (stream in streams) {
            if (!stream.url.startsWith("https://")) {
                lastReason = "Nicht-HTTPS-Ziel verworfen: ${stream.url}"
                continue
            }
            val outcome = withTimeoutOrNull(HOSTER_TIMEOUT_MS) {
                guarded { hosterResolver.resolve(stream.url) }
            }
            if (outcome == null) {
                lastReason = "Hoster ${stream.hosterId ?: "-"}: Zeitüberschreitung nach " +
                    "$HOSTER_TIMEOUT_MS ms"
                continue
            }
            val failure = outcome.exceptionOrNull()
            if (failure != null) {
                lastReason = "Hoster ${stream.hosterId ?: "-"}: " +
                    (failure.message ?: failure.javaClass.simpleName)
                continue
            }
            when (val resolved = outcome.getOrNull()) {
                is HosterOutcome.Resolved -> {
                    Log.i(
                        TAG,
                        "SOURCE ${stream.hosterId ?: "-"} RESOLVED url=${resolved.url} " +
                            "headers=${resolved.requestHeaders.keys.sorted()}"
                    )
                    val played = play(resolved.url, resolved.requestHeaders, resolved.cookies)
                    if (played.ok) return played
                    lastReason = "Wiedergabe fehlgeschlagen: ${played.detail}"
                }
                is HosterOutcome.Unknown -> {
                    val played = play(resolved.url, resolved.requestHeaders, resolved.cookies)
                    if (played.ok) return played
                    lastReason = "Wiedergabe fehlgeschlagen: ${played.detail}"
                }
                is HosterOutcome.Failed -> lastReason = "Hoster: ${resolved.reason}"
                is HosterOutcome.CaptchaRequired ->
                    lastReason = "Captcha erforderlich (${resolved.kind})"
                null -> lastReason = "kein Hoster-Ergebnis"
            }
        }
        Log.w(TAG, "PLAY STREAMS FAILED $lastReason")
        return PlayOutcome(false, lastReason)
    }

    /**
     * Plays [url] with the production engine.
     *
     * The poll keeps running after a primary error: the player may switch to
     * the Android system player, and only the final state decides whether the
     * source played.
     */
    private suspend fun play(
        url: String,
        headers: Map<String, String>,
        cookies: String?
    ): PlayOutcome {
        status("Spiele $url")
        engine.open(url, requestHeaders = headers, cookies = cookies)
        var seenError: String? = null
        repeat(PLAYBACK_POLLS) {
            delay(PLAYBACK_POLL_MS)
            val state = engine.state.value
            renderEngine(state.engine)
            if (state.error != null) {
                // Der Primaerfehler wird festgehalten, aber der Lauf geht weiter:
                // der Systemplayer kann noch uebernehmen.
                seenError = state.error
            } else if (state.isReady && state.positionMs > 0L) {
                return PlayOutcome(
                    true,
                    "lief ${state.positionMs}/${state.durationMs} ms " +
                        "(engine=${state.engine}, primary=${engine.lastPrimaryErrorName ?: "-"}, " +
                        "Fehler=${seenError ?: "-"})"
                )
            }
        }
        val state = engine.state.value
        engine.stop()
        val detail = "kein Fortschritt (${state.engine}, ${state.positionMs} ms, " +
            "Fehler=${state.error ?: "-"}, primary=${engine.lastPrimaryErrorName ?: "-"})"
        Log.w(TAG, "PLAY FAILED url=$url $detail")
        return PlayOutcome(false, detail)
    }

    private fun hosterPacks(): List<HosterPack> {
        val assets = applicationContext.assets
        return (assets.list("hosters") ?: emptyArray())
            .filter { it.endsWith(".json") && !it.startsWith("._") }
            .mapNotNull { file ->
                runCatching {
                    assets.open("hosters/$file").bufferedReader().use { reader ->
                        HosterPackValidator.parse(reader.readText())
                    }
                }.getOrNull()
            }
    }

    /** The ten hosters of the delivery list plus the ARD direct stream. */
    private fun hosterTargets(): List<HosterTarget> {
        val assets = applicationContext.assets
        val file = runCatching {
            assets.open("diagnostics/hoster_targets.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()
        val root = runCatching { JSONObject(file) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("targets") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            HosterTarget(
                hosterId = entry.optString("hoster"),
                url = entry.optString("url").takeIf { it.isNotBlank() },
                reason = entry.optString("reason")
            )
        }
    }

    private fun report(scope: String, verdict: String, detail: String, documented: String = "") {
        val suffix = if (verdict == "BLOCKED" && documented.isNotBlank()) " | Doku: $documented" else ""
        val line = "$scope VERDICT=$verdict DETAIL=$detail$suffix"
        results += line
        Log.i(TAG, line)
        status("${results.size} $line")
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

    private fun status(text: String) {
        statusView.text = text
    }

    override fun onDestroy() {
        coverageJob?.cancel()
        engine.release()
        super.onDestroy()
    }

    private data class PlayOutcome(val ok: Boolean, val detail: String)

    private data class Target(val packId: String, val query: String, val reason: String = "")

    private data class HosterTarget(val hosterId: String, val url: String?, val reason: String)

    private companion object {
        const val TAG = "Frame61Coverage"
        const val HTTP_TIMEOUT_MS = 15_000L
        const val HOSTER_TIMEOUT_MS = 15_000L
        const val PLAYBACK_POLL_MS = 1_000L
        const val PLAYBACK_POLLS = 12
        const val MAX_STREAMS = 3

        /**
         * The 14 index pages of the delivery list. The query is the one the
         * fetch worker uses for the same page, so the device run and the
         * recorded fixtures talk about the same title. The reason is the
         * documented cause from docs/INDEX.md for pages that stay blocked.
         */
        val INDEX_TARGETS = listOf(
            Target("aniworld", "naruto"),
            Target("bs-to", "naruto"),
            Target("filmpalast", "inception"),
            Target("filmstarts", "inception"),
            Target(
                "kinokiste",
                "inception",
                "kinokiste.cloud: JWT-/Bot-Gate (HTTP 401), Chrome-Headless 40 B"
            ),
            Target("kinox", "inception", "kinox.to: 6 Rotationsdomains, HTTP 404 auf /suche"),
            Target("megakino", "inception", "megakino.de: DNS/Platzhalter, HTTP 200 ohne Treffer"),
            Target("moflix", "inception"),
            Target("movie2k", "inception", "movie2k: kein Pack — .to/.ch Platzhalter, kein Server"),
            Target("s-to", "black doves"),
            Target("serienstream", "black doves"),
            Target("streamcloud", "inception"),
            Target("filmo", "inception"),
            Target("cine", "inception")
        )
    }
}

/** Keeps the answer snippet readable and free of control characters. */
private fun String.sanitize(): String =
    replace(Regex("\\s+"), " ").take(120)
