package de.hendler.frame61.diagnostics

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
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
import de.hendler.frame61.scraper.engine.PackInterpreter
import de.hendler.frame61.scraper.engine.PackLoader
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Perf-only proof of the real source chain on the device.
 *
 * For every index page of the delivery list the activity runs the production
 * chain — pack search, stream lookup, hoster resolution — and then plays the
 * resolved URL with the production engine. A source only counts as verified
 * when the player actually advanced on the device; everything else is reported
 * as BLOCKED with the live cause (HTTP status, timeout, captcha, dead host).
 */
@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class SourceCoverageActivity : ComponentActivity() {
    @Inject lateinit var packLoader: PackLoader
    @Inject lateinit var interpreter: PackInterpreter
    @Inject lateinit var jsEvaluator: WebViewJsEvaluator

    private lateinit var engine: Media3Frame61Player
    private lateinit var playerView: PlayerView
    private lateinit var playerContainer: FrameLayout
    private lateinit var statusView: TextView
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
            textSize = 14f
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
        setContentView(root)
        de.hendler.frame61.net.PlaybackDiagnostics.beginSession("source-coverage", 0)
        engine.attach()
        coverageJob = lifecycleScope.launch { runCoverage() }
    }

    private suspend fun runCoverage() {
        val packs = packLoader.loadAllPacks()
        val hosterResolver = HosterResolver(hosterPacks(), OkHttpBodyFetcher(), jsEvaluator)
        var verified = 0
        var blocked = 0
        for (target in TARGETS) {
            val pack = packs.firstOrNull { it.id == target.id }
            if (pack == null) {
                blocked++
                report(target, "BLOCKED", "kein Pack im Bundle (Dokumentationsbeleg noetig)")
                continue
            }
            val hits = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                guarded { interpreter.search(pack, target.query) }
            }
            if (hits == null) {
                blocked++
                report(target, "BLOCKED", "Suche nach ${SEARCH_TIMEOUT_MS} ms ohne Antwort")
                continue
            }
            val searchFailure = hits.exceptionOrNull()
            if (searchFailure != null) {
                blocked++
                report(target, "BLOCKED", "Suche: ${searchFailure.message ?: searchFailure.javaClass.simpleName}")
                continue
            }
            val hit = hits.getOrThrow().firstOrNull()
            if (hit == null) {
                blocked++
                report(target, "BLOCKED", "Suche '${target.query}' lieferte keine Treffer")
                continue
            }
            val titleId = hit.playbackId ?: hit.id
            val streams = withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                guarded { interpreter.streams(pack, titleId, "1") }
            }
            if (streams == null) {
                blocked++
                report(target, "BLOCKED", "Streamabruf nach ${STREAM_TIMEOUT_MS} ms ohne Antwort")
                continue
            }
            val streamFailure = streams.exceptionOrNull()
            if (streamFailure != null) {
                blocked++
                report(
                    target,
                    "BLOCKED",
                    "Streamabruf: ${streamFailure.message ?: streamFailure.javaClass.simpleName}"
                )
                continue
            }
            val streamList = streams.getOrDefault(emptyList())
            if (streamList.isEmpty()) {
                blocked++
                report(
                    target,
                    "BLOCKED",
                    "Treffer '${hit.title}' ohne Stream (Regel fehlt oder Seite liefert nichts)"
                )
                continue
            }
            var done = false
            var lastReason = "keine Hoster-Aufloesung"
            for (stream in streamList.take(MAX_STREAMS)) {
                val outcome = withTimeoutOrNull(HOSTER_TIMEOUT_MS) {
                    guarded { hosterResolver.resolve(stream.url) }
                }
                val failure = outcome?.exceptionOrNull()
                if (failure != null) {
                    lastReason = "Hoster ${stream.hosterId ?: "?"}: " +
                        (failure.message ?: failure.javaClass.simpleName)
                    continue
                }
                val url: String
                val headers: Map<String, String>
                val cookies: String?
                val resolved = outcome?.getOrNull()
                when (resolved) {
                    is HosterOutcome.Resolved -> {
                        url = resolved.url
                        headers = resolved.requestHeaders
                        cookies = resolved.cookies
                    }
                    is HosterOutcome.Unknown -> {
                        url = resolved.url
                        headers = resolved.requestHeaders
                        cookies = resolved.cookies
                    }
                    is HosterOutcome.Failed -> {
                        lastReason = "Hoster ${stream.hosterId ?: "?"}: ${resolved.reason}"
                        continue
                    }
                    is HosterOutcome.CaptchaRequired -> {
                        lastReason = "Captcha erforderlich (${resolved.kind})"
                        continue
                    }
                    null -> {
                        lastReason = "Hoster-Zeitueberschreitung nach $HOSTER_TIMEOUT_MS ms"
                        continue
                    }
                }
                Log.i(
                    TAG,
                    "SOURCE ${target.id} RESOLVED hoster=${stream.hosterId ?: "-"} " +
                        "url=$url headers=${headers.keys.sorted()}"
                )
                val played = play(url, headers, cookies)
                if (played != null) {
                    verified++
                    report(target, "VERIFIED_PLAYBACK", played)
                    done = true
                    break
                }
                lastReason = "Wiedergabe fehlgeschlagen: $played"
            }
            if (!done) {
                blocked++
                report(target, "BLOCKED", lastReason)
            }
        }
        val summary = "COVERAGE SUMMARY verified=$verified blocked=$blocked"
        Log.i(TAG, summary)
        status("$summary\n${results.joinToString("\n")}")
    }

    /**
     * Runs the blocking pack calls off the main thread and keeps the failure
     * reason, but never swallows the cancellation of the enclosing timeout.
     */
    private suspend fun <T> guarded(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /**
     * Plays [url] with the production engine and returns a short proof line, or
     * null when the player did not advance.
     */
    private suspend fun play(
        url: String,
        headers: Map<String, String>,
        cookies: String?
    ): String? {
        status("Spiele $url")
        engine.open(url, requestHeaders = headers, cookies = cookies)
        repeat(PLAYBACK_POLLS) {
            delay(PLAYBACK_POLL_MS)
            val state = engine.state.value
            renderEngine(state.engine)
            status(
                "Spiele $url\n${state.engine} ${state.positionMs}/${state.durationMs} ms " +
                    (state.error ?: "kein Fehler")
            )
            if (state.error != null) {
                return "Fehler ${state.error} (primary=${engine.lastPrimaryErrorName ?: "-"})"
            }
            if (state.isReady && state.positionMs > 0L) {
                return "lief ${state.positionMs} ms (engine=${state.engine})"
            }
        }
        return null
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

    private fun report(target: Target, verdict: String, detail: String) {
        val line = "SOURCE ${target.id} VERDICT=$verdict DETAIL=$detail"
        results += line
        Log.i(TAG, line)
        status("${results.size}/${TARGETS.size} $line")
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

    private data class Target(val id: String, val query: String)

    private companion object {
        const val TAG = "Frame61Coverage"
        const val SEARCH_TIMEOUT_MS = 10_000L
        const val STREAM_TIMEOUT_MS = 10_000L
        const val HOSTER_TIMEOUT_MS = 10_000L
        const val PLAYBACK_POLL_MS = 1_000L
        const val PLAYBACK_POLLS = 12
        const val MAX_STREAMS = 3

        /**
         * The 14 index pages of the delivery list. The query is the one the
         * fetch worker uses for the same page, so the device run and the
         * recorded fixtures talk about the same title.
         */
        val TARGETS = listOf(
            Target("aniworld", "naruto"),
            Target("bs-to", "naruto"),
            Target("filmpalast", "inception"),
            Target("filmstarts", "inception"),
            Target("kinokiste", "inception"),
            Target("kinox", "inception"),
            Target("megakino", "inception"),
            Target("moflix", "inception"),
            Target("movie2k", "inception"),
            Target("s-to", "black doves"),
            Target("serienstream", "black doves"),
            Target("streamcloud", "inception"),
            Target("filmo", "inception"),
            Target("cine", "inception")
        )
    }
}
