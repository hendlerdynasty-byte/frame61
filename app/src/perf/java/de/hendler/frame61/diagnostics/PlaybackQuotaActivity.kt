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

/**
 * Messharness fuer die Titel-Erfolgsquote (Phase 1.4.0).
 *
 * Es wird eine feste Stichprobe von 100 Katalogtiteln ueber die
 * **Produktionskette** aufgeloest (AutoPlaybackResolver wie im Player) und
 * abgespielt. Jeder Titel bekommt bis zu drei Kandidaten; ein Titel zaehlt nur
 * als Erfolg, wenn die Wiedergabe auf dem Geraet wirklich fortschreitet.
 *
 * Pro Titel wird eine Zeile geschrieben — in den Logcat, in eine Datei unter
 * `Android/data/<paket>/files/quota/` und in den Statusbereich, damit der Lauf
 * auch ohne Dateizugriff aus einem UI-Dump ausgelesen werden kann.
 */
@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackQuotaActivity : ComponentActivity() {

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
        quotaJob = lifecycleScope.launch { runQuota() }
    }

    private suspend fun runQuota() {
        val items = loadCatalog()
        if (items.isEmpty()) {
            report("QUOTA FATAL Katalog nicht lesbar")
            return
        }
        val sample = sample(items, SAMPLE_SIZE)
        logFile = File(getExternalFilesDir(null), "quota/playlog-${System.currentTimeMillis()}.txt")
            .also { it.parentFile?.mkdirs() }
        report(
            "QUOTA START n=${sample.size} seed=$SEED " +
                "katalog=${items.size} datei=${logFile?.absolutePath}"
        )

        var ok = 0
        var totalFirstFrame = 0L
        val firstFrames = mutableListOf<Long>()
        var lastCandidateCount = 0
        val failures = mutableMapOf<String, Int>()

        sample.forEachIndexed { index, item ->
            val episode = if (item.type == "series") 1 else 0
            val started = SystemClock.elapsedRealtime()
            var mirrors = emptyList<MirrorCandidate>()
            val resolved = withTimeoutOrNull(RESOLVE_BUDGET_MS) {
                withContext(Dispatchers.IO) {
                    autoResolver.resolve(item.title, episode, fresh = true)
                }
            }
            if (resolved != null) mirrors = resolved
            val resolveMs = SystemClock.elapsedRealtime() - started
            lastCandidateCount = mirrors.size

            var played = false
            var firstFrameMs = 0L
            var tried = 0
            var usedIndex = -1
            var lastError = if (mirrors.isEmpty()) "NO_CANDIDATE" else "NO_PLAY"
            var usedHost = "-"

            for ((candidateIndex, mirror) in mirrors.take(MAX_CANDIDATES).withIndex()) {
                tried = candidateIndex + 1
                val attempt = playCandidate(mirror)
                if (attempt.firstFrameMs != null) {
                    played = true
                    firstFrameMs = resolveMs + attempt.firstFrameMs
                    usedIndex = candidateIndex
                    usedHost = host(mirror.url)
                    lastError = "-"
                    break
                }
                lastError = attempt.errorClass ?: "NO_PROGRESS"
            }

            if (played) {
                ok++
                totalFirstFrame += firstFrameMs
                firstFrames += firstFrameMs
                report(
                    "QUOTA idx=${index + 1} id=${item.id} type=${item.type} " +
                        "RESULT=OK firstFrameMs=$firstFrameMs (resolve=$resolveMs) " +
                        "candidates=${mirrors.size} tried=$tried used=$usedIndex host=$usedHost"
                )
            } else {
                failures[lastError] = (failures[lastError] ?: 0) + 1
                report(
                    "QUOTA idx=${index + 1} id=${item.id} type=${item.type} " +
                        "title=${item.title.sanitize()} RESULT=FAIL " +
                        "candidates=${mirrors.size} tried=$tried resolveMs=$resolveMs " +
                        "err=$lastError"
                )
            }
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }

        val sorted = firstFrames.sorted()
        val p50 = sorted.getOrNull(sorted.size * 50 / 100.coerceAtLeast(1)) ?: 0L
        val p95 = sorted.getOrNull(((sorted.size - 1) * 95 / 100).coerceAtLeast(0)) ?: 0L
        val failText = failures.entries.sortedByDescending { it.value }
            .joinToString(",") { "${it.key}:${it.value}" }
        report(
            "QUOTA SUMMARY ok=$ok/${sample.size} quote=" +
                "${ok * 100 / sample.size} p50Ms=$p50 p95Ms=$p95 " +
                "avgMs=${if (firstFrames.isEmpty()) 0 else totalFirstFrame / firstFrames.size} " +
                "failures=[$failText]"
        )
        Log.i(TAG, "QUOTA DONE datei=${logFile?.absolutePath}")
    }

    /**
     * Ahmt den Produktionsaufruf nach: erst auflösen, dann höchstens drei
     * Kandidaten abspielen. Ein Kandidat gilt als Erfolg, sobald die Position
     * auf dem Gerät fortschreitet.
     */
    private suspend fun playCandidate(mirror: MirrorCandidate): Attempt {
        val started = SystemClock.elapsedRealtime()
        engine.open(mirror.url, requestHeaders = mirror.requestHeaders, cookies = mirror.cookies)
        var lastError: String? = "NO_PROGRESS"
        repeat(PLAYBACK_POLLS) {
            delay(PLAYBACK_POLL_MS)
            // Ereignisgesteuerter Zustand: ohne publish() bleibt die Position
            // auf dem Stand des letzten Player-Ereignisses (Fallstrick Phase B).
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
        Log.w(
            TAG,
            "QUOTA ATTEMPT FAILED ${mirror.url.take(90)} error=$lastError " +
                "engine=${state.engine} position=${state.positionMs}"
        )
        engine.stop()
        return Attempt(null, lastError)
    }

    private fun sample(items: List<CatalogItem>, count: Int): List<CatalogItem> {
        val random = Random(SEED)
        val series = items.filter { it.type == "series" }.shuffled(random)
        val movies = items.filter { it.type != "series" }.shuffled(random)
        val targetSeries = (count.toLong() * series.size / items.size.coerceAtLeast(1)).toInt()
        val picked = series.take(targetSeries) + movies.take(count - targetSeries)
        return picked.shuffled(random)
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
                type = entry.optString("type")
            )
        }
    }

    private fun report(line: String) {
        lines += line
        Log.i(TAG, line)
        statusView.text = lines.joinToString("\n")
        logFile?.appendText("$line\n")
    }

    private fun host(url: String): String {
        val after = url.substringAfter("://", "")
        return after.substringBefore('/').ifBlank { "-" }
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

    override fun onDestroy() {
        quotaJob?.cancel()
        engine.release()
        super.onDestroy()
    }

    private data class CatalogItem(val id: String, val title: String, val type: String)

    private data class Attempt(val firstFrameMs: Long?, val errorClass: String?)

    private companion object {
        const val TAG = "Frame61Quota"
        const val SAMPLE_SIZE = 100
        const val SEED = 20261122L
        const val MAX_CANDIDATES = 3
        const val PLAYBACK_POLL_MS = 250L
        const val PLAYBACK_POLLS = 28
        const val RESOLVE_BUDGET_MS = 30_000L
    }
}

private fun String.sanitize(): String =
    replace(Regex("\\s+"), " ").take(60)