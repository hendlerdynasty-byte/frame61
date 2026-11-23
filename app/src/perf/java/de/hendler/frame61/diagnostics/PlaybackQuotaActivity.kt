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
import de.hendler.frame61.player.Media3Frame61Player
import de.hendler.frame61.resolver.MirrorCandidate
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

        sample.forEachIndexed { index, item ->
            val episode = if (item.type == "series") 1 else 0
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

            if (mirrors.isNotEmpty()) {
                for ((candidateIndex, mirror) in mirrors.take(MAX_CANDIDATES).withIndex()) {
                    tried = candidateIndex + 1
                    triedHosts += host(mirror.url)
                    val attemptStarted = SystemClock.elapsedRealtime()
                    val attempt = playCandidate(mirror)
                    if (attempt.firstFrameMs != null) {
                        played = true
                        firstFrameMs = resolveMs + (SystemClock.elapsedRealtime() - attemptStarted)
                        usedHost = host(mirror.url)
                        errorClass = "-"
                        break
                    }
                    errorClass = attempt.errorClass ?: "EMBED_UNRESOLVED"
                }
            } else {
                errorClass = classifyFailure(PlaybackDiagnostics.events.value.drop(traceStart))
            }

            if (played) {
                ok++
                firstFrameTotal += firstFrameMs
                firstFrames += firstFrameMs
                report(
                    "QUOTA $mode idx=${index + 1} id=${item.id} type=${item.type} " +
                        "RESULT=OK class=- resolveMs=$resolveMs playMs=${firstFrameMs - resolveMs} " +
                        "firstFrameMs=$firstFrameMs candidates=${mirrors.size} tried=$tried " +
                        "host=$usedHost"
                )
            } else {
                classes[errorClass] = (classes[errorClass] ?: 0) + 1
                report(
                    "QUOTA $mode idx=${index + 1} id=${item.id} type=${item.type} " +
                        "title=${item.title.sanitize()} RESULT=FAIL class=$errorClass " +
                        "resolveMs=$resolveMs candidates=${mirrors.size} tried=$tried " +
                        "hosts=[${triedHosts.joinToString(",")}]"
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
            CatalogItem(id = entry.optString("id"), title = title, type = entry.optString("type"))
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

    private data class CatalogItem(val id: String, val title: String, val type: String)

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
        const val RESOLVE_BUDGET_MS = 30_000L
    }
}

private fun String.sanitize(): String = replace(Regex("\\s+"), " ").take(60)

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