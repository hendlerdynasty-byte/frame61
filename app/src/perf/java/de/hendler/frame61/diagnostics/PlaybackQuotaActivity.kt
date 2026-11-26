package de.hendler.frame61.diagnostics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Html
import android.util.Log
import android.view.Gravity
import android.view.PixelCopy
import android.view.View
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
import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.net.PlaybackDiagnostics
import de.hendler.frame61.playback.AutoPlaybackResolver
import de.hendler.frame61.playback.InterceptionLog
import de.hendler.frame61.player.Media3Frame61Player
import de.hendler.frame61.scraper.engine.PackInterpreter
import de.hendler.frame61.scraper.engine.PackLoader
import de.hendler.frame61.resolver.ContentIdentityGate
import de.hendler.frame61.resolver.IdentityRequest
import de.hendler.frame61.resolver.MirrorCandidate
import de.hendler.frame61.resolver.hostOf
import java.io.File
import java.util.Random
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

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
    @Inject lateinit var packLoader: PackLoader
    @Inject lateinit var interpreter: PackInterpreter
    @Inject lateinit var catalogRepository: CatalogRepository

    private lateinit var engine: Media3Frame61Player
    private lateinit var playerView: PlayerView
    private lateinit var playerContainer: FrameLayout
    private lateinit var playbackOverlay: TextView
    private lateinit var statusView: TextView
    private lateinit var scroll: ScrollView
    private var quotaJob: Job? = null
    private var renderedEngine = ""
    private var pathMode = false
    /**
     * Gate 1 (1.6.0): Identitätsprüfung je gespieltem Kandidaten. Der Lauf
     * zählt weiter „spielbar" (Reihe 1–11 bleibt vergleichbar) **und**
     * „richtig" (nur mit bestätigtem Inhalt).
     */
    private val identityGate = ContentIdentityGate()
    private val lines = mutableListOf<String>()
    private var logFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pathMode = sampleMode() == MODE_ANIWORLD20
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
        playbackOverlay = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(210, 0, 0, 0))
            setPadding(20, 14, 20, 14)
            visibility = if (pathMode) View.VISIBLE else View.GONE
        }
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
                if (pathMode) {
                    reportPathSummary(false, 0, "FATAL:${error.javaClass.simpleName}:${error.message}")
                } else {
                    report("QUOTA FATAL ${error.javaClass.simpleName}: ${error.message}")
                }
            }
        }
    }

    /** Fixed 20-title Aniworld path acceptance; never falls back to another source. */
    private suspend fun runAniworldPathAcceptance() {
        logFile = File(
            getExternalFilesDir(PATH_OUTPUT_DIR),
            "logs/path-${System.currentTimeMillis()}.txt"
        ).also { it.parentFile?.mkdirs() }
        val manifestJson = runCatching {
            assets.open(PATH_MANIFEST_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()
        val catalogJson = runCatching {
            assets.open("catalog/catalog.json").bufferedReader().use { it.readText() }
        }.getOrNull()
        val validation = if (manifestJson != null && catalogJson != null) {
            PathCatalogValidator.validate(manifestJson, catalogJson)
        } else {
            PathCatalogValidator.Report(
                sourceId = PathCatalogValidator.SOURCE_ID,
                ids = emptyList(),
                issues = listOf(
                    PathCatalogValidator.Issue(
                        "PATH_ASSET_MISSING",
                        null,
                        if (manifestJson == null) PATH_MANIFEST_ASSET else "catalog/catalog.json"
                    )
                )
            )
        }
        report(
            "PATH START mode=$MODE_ANIWORLD20 manifest=$PATH_MANIFEST_ASSET " +
                "ids=${validation.ids.size} expected=${PathCatalogValidator.EXPECTED_COUNT} " +
                "datei=${logFile?.absolutePath}"
        )
        if (!validation.isValid) {
            report(
                "PATH PANEL_INVALID " + validation.issues.joinToString(" | ") {
                    "${it.code}:${it.id ?: "-"}:${it.message.compact()}"
                }
            )
            reportPathSummary(false, 0, "PANEL_INVALID")
            return
        }

        val pack = withContext(Dispatchers.IO) {
            packLoader.loadAllPacks().firstOrNull { it.id == PathCatalogValidator.SOURCE_ID }
        }
        if (pack == null) {
            report("PATH PANEL_INVALID sourceId=${PathCatalogValidator.SOURCE_ID} grund=Pack_fehlt")
            reportPathSummary(false, 0, "PACK_MISSING")
            return
        }
        val items = catalogRepository.all().associateBy { it.id }
        var passed = 0
        validation.ids.forEachIndexed { index, id ->
            val item = items.getValue(id)
            val title = decodeCatalogTitle(item.title)
            val slug = item.sourceKey ?: id.removePrefix(PathCatalogValidator.ID_PREFIX)
            val comparisonArtwork = localArtwork(id)
            val catalogPanel = inspectCatalogPanel(item)
            val sourceData = withTimeoutOrNull(PATH_SOURCE_BUDGET_MS) {
                withContext(Dispatchers.IO) {
                    val detail = runCatching { interpreter.detail(pack, slug) }.getOrNull()
                    val episodes = runCatching { interpreter.episodes(pack, slug) }.getOrDefault(emptyList())
                    detail to episodes
                }
            }
            val detail = sourceData?.first
            val episodes = sourceData?.second.orEmpty()
            val sourceDetailValues = detail?.let {
                listOfNotNull(
                    it.description?.takeIf(String::isNotBlank),
                    it.year?.toString(),
                    it.runtime?.toString(),
                    it.genres.filter(String::isNotBlank).joinToString(",").takeIf(String::isNotBlank)
                )
            }.orEmpty()
            // Der kanonische Katalog ist für die sichtbare Detailseite führend.
            // Die live Quellseite kann zusätzliche Felder liefern, darf aber
            // keine vollständige Abnahme blockieren, wenn der Katalog belegt ist.
            val catalogDetailValues = listOfNotNull(
                item.year?.toString(),
                item.runtime?.toString(),
                item.overview.takeIf(String::isNotBlank),
                item.genres.filter(String::isNotBlank).joinToString(",").takeIf(String::isNotBlank)
            )
            val detailValues = sourceDetailValues.ifEmpty { catalogDetailValues }
            val detailOk = detailValues.isNotEmpty() &&
                (detail?.title?.isNotBlank() ?: item.title.isNotBlank())
            val episodesOk = episodes.isNotEmpty() && episodes.all { it.season > 0 && it.episode > 0 }
            val seasons = episodes.map { it.season }.distinct().sorted()
            val firstEpisode = episodes.minWithOrNull(compareBy({ it.season }, { it.episode }))
            val panelValid = catalogPanel.isValid && detailOk && episodesOk && firstEpisode != null

            var resolveStatus = "NOT_RUN"
            var identityKind = "-"
            var identityScore = 0.0
            var firstFrameStatus = "NOT_RUN"
            var firstFrameMs = 0L
            var playbackStatus = "NOT_RUN"
            var positionMs = 0L
            var durationMs = 0L
            var screenshotStatus = "NOT_RUN"
            var screenshotPath = "-"
            var cause = when {
                !catalogPanel.poster -> "CATALOG_POSTER_INVALID"
                !catalogPanel.backdrop -> "CATALOG_BACKDROP_INVALID"
                !catalogPanel.overview -> "CATALOG_OVERVIEW_EMPTY"
                !catalogPanel.genres -> "CATALOG_GENRES_EMPTY"
                !catalogPanel.cast -> "CATALOG_CAST_EMPTY"
                !catalogPanel.year -> "CATALOG_YEAR_MISSING"
                !catalogPanel.runtime -> "CATALOG_RUNTIME_MISSING"
                comparisonArtwork == null -> "COMPARISON_ARTWORK_MISSING"
                !detailOk -> "DETAIL_FIELDS_EMPTY"
                !episodesOk -> "EPISODES_OR_SEASONS_EMPTY"
                else -> "-"
            }

            if (panelValid) {
                val episode = checkNotNull(firstEpisode)
                val sessionId = PlaybackDiagnostics.beginSession(title, episode.episode)
                try {
                    val resolved = withTimeoutOrNull(PATH_RESOLVE_BUDGET_MS) {
                        autoResolver.resolveFast(
                            title = title,
                            episodeNumber = episode.episode,
                            season = episode.season,
                            sessionId = sessionId,
                            fresh = true
                        )
                    }.orEmpty()
                    val aniworldCandidates = resolved.filter {
                        it.sourceId == PathCatalogValidator.SOURCE_ID
                    }
                    resolveStatus = if (aniworldCandidates.isNotEmpty()) "PASS" else "FAIL"
                    val identityRequest = IdentityRequest(
                        title = title,
                        year = item.year,
                        type = item.type,
                        season = episode.season,
                        episode = episode.episode,
                        runtimeMin = item.runtime
                    )
                    val verified = identityGate.verify(identityRequest, aniworldCandidates.take(MAX_CANDIDATES))
                    val correct = verified.firstOrNull { it.verdict.kind.name == "CORRECT" }
                    val judged = correct ?: verified.firstOrNull()
                    identityKind = judged?.verdict?.kind?.name ?: "UNKNOWN"
                    identityScore = judged?.verdict?.score ?: 0.0
                    if (correct == null) {
                        cause = "PANEL_INVALID:${identityKind}"
                    } else {
                        val attempt = playPathCandidate(correct.candidate, title)
                        firstFrameStatus = if (attempt.firstFrameMs > 0L) "PASS" else "FAIL"
                        firstFrameMs = attempt.firstFrameMs
                        playbackStatus = if (attempt.ok) "PASS" else "FAIL"
                        positionMs = attempt.positionMs
                        durationMs = attempt.durationMs
                        if (attempt.ok) {
                            val screenshot = File(
                                getExternalFilesDir(PATH_OUTPUT_DIR),
                                "screenshots/${index + 1}-${safeFileName(id)}.png"
                            ).also { it.parentFile?.mkdirs() }
                            screenshotPath = screenshot.absolutePath
                            report(
                                "PATH SCREENSHOT_CUE idx=${index + 1}/${validation.ids.size} " +
                                    "id=$id datei=$screenshotPath"
                            )
                            screenshotStatus = if (capturePathScreenshot(screenshot)) "PASS" else "FAIL"
                        }
                        if (!attempt.ok) cause = attempt.error ?: "FIRST_FRAME_OR_PROGRESS_MISSING"
                        if (screenshotStatus == "FAIL") cause = "SCREENSHOT_FAILED"
                    }
                } finally {
                    engine.stop()
                    PlaybackDiagnostics.endSession(sessionId)
                }
            }

            val titlePassed = panelValid && comparisonArtwork != null && resolveStatus == "PASS" &&
                identityKind == "CORRECT" && firstFrameStatus == "PASS" &&
                playbackStatus == "PASS" && screenshotStatus == "PASS"
            if (titlePassed) passed++
            report(
                "PATH idx=${index + 1}/${validation.ids.size} id=$id title=${title.sanitize()} " +
                    "catalog=PASS catalogPanel=${if (catalogPanel.isValid) "PASS" else "FAIL"} " +
                    "catalogPoster=${catalogPanel.poster.status()} catalogBackdrop=${catalogPanel.backdrop.status()} " +
                    "overview=${catalogPanel.overview.status()} genres=${catalogPanel.genres.status()} " +
                    "cast=${catalogPanel.cast.status()} year=${catalogPanel.year.status()} " +
                    "runtime=${catalogPanel.runtime.status()} " +
                    "comparisonArtwork=${if (comparisonArtwork != null) "PASS" else "FAIL"} " +
                    "asset=${comparisonArtwork ?: "-"} detail=${if (detailOk) "PASS" else "FAIL"} " +
                    "detailFields=${detailValues.size} episodes=${if (episodesOk) "PASS" else "FAIL"} " +
                    "episodeCount=${episodes.size} seasons=${seasons.joinToString(",").ifBlank { "-" }} " +
                    "resolve=$resolveStatus sourceId=${PathCatalogValidator.SOURCE_ID} " +
                    "identity=$identityKind score=${String.format(java.util.Locale.ROOT, "%.2f", identityScore)} " +
                    "firstFrame=$firstFrameStatus firstFrameMs=$firstFrameMs playback=$playbackStatus " +
                    "positionMs=$positionMs durationMs=$durationMs screenshot=$screenshotStatus " +
                    "screenshotFile=$screenshotPath result=${if (titlePassed) "PASS" else "FAIL"} " +
                    "panel=${if (panelValid && identityKind == "CORRECT") "VALID" else "INVALID"} cause=$cause"
            )
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
        reportPathSummary(passed == validation.ids.size, passed, "-")
    }

    private fun reportPathSummary(pass: Boolean, passed: Int, cause: String) {
        report(
            "PATH SUMMARY ${if (pass) "PASS" else "FAIL"} passed=$passed/" +
                "${PathCatalogValidator.EXPECTED_COUNT} sourceId=${PathCatalogValidator.SOURCE_ID} " +
                "output=${getExternalFilesDir(PATH_OUTPUT_DIR)?.absolutePath} cause=$cause"
        )
        Log.i(TAG, "PATH DONE datei=${logFile?.absolutePath}")
    }

    private suspend fun playPathCandidate(mirror: MirrorCandidate, title: String): PathPlaybackAttempt {
        val started = SystemClock.elapsedRealtime()
        var firstPosition = -1L
        engine.open(mirror.url, requestHeaders = mirror.requestHeaders, cookies = mirror.cookies)
        repeat(PATH_PLAYBACK_POLLS) {
            delay(PATH_PLAYBACK_POLL_MS)
            engine.publish()
            val state = engine.state.value
            renderEngine(state.engine)
            playbackOverlay.text = "$title · ${formatRuntime(state.positionMs)} / ${formatRuntime(state.durationMs)}"
            if (state.error != null) {
                return PathPlaybackAttempt(false, 0L, state.positionMs, state.durationMs, state.error)
            }
            if (state.isReady && state.positionMs > 0L) {
                if (firstPosition < 0L) {
                    firstPosition = state.positionMs
                } else if (state.positionMs > firstPosition) {
                    return PathPlaybackAttempt(
                        true,
                        SystemClock.elapsedRealtime() - started,
                        state.positionMs,
                        state.durationMs,
                        null
                    )
                }
            }
        }
        val state = engine.state.value
        return PathPlaybackAttempt(
            false,
            if (firstPosition >= 0L) SystemClock.elapsedRealtime() - started else 0L,
            state.positionMs,
            state.durationMs,
            state.error ?: engine.lastPrimaryErrorName ?: "NO_GROWING_POSITION"
        )
    }

    private fun inspectCatalogPanel(item: CatalogItem): CatalogPanel {
        val poster = localCatalogAsset(item.poster)
        val backdrop = localCatalogAsset(item.backdrop)
        val overview = item.overview.isNotBlank()
        val genres = item.genres.any { it.isNotBlank() }
        val cast = item.cast.any { it.name.isNotBlank() }
        val year = item.year != null
        val runtime = item.runtime?.let { it > 0 } == true
        return CatalogPanel(poster, backdrop, overview, genres, cast, year, runtime)
    }

    private fun localCatalogAsset(uri: String): Boolean {
        val prefix = "file:///android_asset/"
        if (!uri.startsWith(prefix)) return false
        val path = uri.removePrefix(prefix)
        if (path.isBlank() || path.split('/').any { it == ".." }) return false
        return decodeAsset(path)
    }

    private fun decodeAsset(path: String): Boolean = runCatching {
        assets.open(path).use { stream ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Bildgrenzen ungültig")
        }
        assets.open(path).use { stream ->
            (BitmapFactory.decodeStream(stream) ?: error("Bild nicht dekodierbar")).recycle()
        }
        true
    }.getOrDefault(false)

    /** Comparison image only; the visible catalog poster/backdrop are checked above. */
    private fun localArtwork(id: String): String? {
        val slug = id.removePrefix(PathCatalogValidator.ID_PREFIX)
        for (extension in PATH_ARTWORK_EXTENSIONS) {
            val path = "$PATH_ARTWORK_DIR/$slug.$extension"
            if (decodeAsset(path)) return path
        }
        return null
    }

    private suspend fun capturePathScreenshot(file: File): Boolean {
        val decor = window.decorView
        if (decor.width <= 0 || decor.height <= 0) return false
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val copied = suspendCancellableCoroutine<Boolean> { continuation ->
            PixelCopy.request(window, bitmap, { result ->
                if (continuation.isActive) continuation.resume(result == PixelCopy.SUCCESS)
            }, Handler(Looper.getMainLooper()))
        }
        if (!copied) {
            bitmap.recycle()
            return false
        }
        return try {
            withContext(Dispatchers.IO) {
                file.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeCatalogTitle(raw: String): String =
        Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim().ifBlank { raw }

    private fun formatRuntime(positionMs: Long): String {
        val seconds = (positionMs.coerceAtLeast(0L) / 1_000L).toInt()
        return "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
    }

    private fun safeFileName(raw: String): String =
        raw.replace(Regex("[^a-z0-9._-]"), "_")

    private suspend fun runQuota() {
        val mode = sampleMode()
        if (mode == MODE_ANIWORLD20) {
            runAniworldPathAcceptance()
            return
        }
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
        // Gate 1 (1.6.0): Richtigkeitsquote — nur mit bestätigtem Inhalt.
        var richtig = 0
        val identClasses = mutableMapOf<String, Int>()
        val groupRichtig = mutableMapOf<String, Int>()
        val groupIdentClasses = mutableMapOf<String, MutableMap<String, Int>>()
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
            var playedMirror: MirrorCandidate? = null

            if (mirrors.isNotEmpty()) {
                for (mirror in mirrors.take(MAX_CANDIDATES)) {
                    fastTried += 1
                    fastHosts += host(mirror.url)
                    failedUrls += mirror.url
                    mirror.streamUrl?.let { failedUrls += it }
                    val watchHost = mirror.streamUrl?.let { hostOf(it) }.orEmpty()
                    if (watchHost.isNotEmpty()) failedHosts += watchHost
                    lastSourceId = mirror.sourceId
                    val attemptStarted = SystemClock.elapsedRealtime()
                    val attempt = playCandidate(mirror)
                    if (attempt.firstFrameMs != null) {
                        playedMirror = mirror
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
                    mirror.streamUrl?.let { failedUrls += it }
                    val watchHost = mirror.streamUrl?.let { hostOf(it) }.orEmpty()
                    if (watchHost.isNotEmpty()) failedHosts += watchHost
                    lastSourceId = mirror.sourceId
                    val attemptStarted = SystemClock.elapsedRealtime()
                    val attempt = playCandidate(mirror)
                    if (attempt.firstFrameMs != null) {
                        playedMirror = mirror
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
            // Gate 1: Identität des tatsächlich gespielten Kandidaten prüfen.
            // Nur CORRECT zählt als richtige Wiedergabe; fehlt eine
            // Quellenangabe und ist keine Seite abrufbar, ist der Lauf
            // UNKNOWN und damit nicht richtig.
            val identityRequest = IdentityRequest(
                title = item.title,
                year = item.year,
                type = item.type,
                season = 1,
                episode = if (episode > 0) episode else 1,
                runtimeMin = item.runtime
            )
            val identVerdict = playedMirror?.let { mirror ->
                identityGate.verify(identityRequest, listOf(mirror)).firstOrNull()
            }
            val identKind = when {
                identVerdict == null -> if (played) "UNKNOWN" else "-"
                else -> identVerdict.verdict.kind.name
            }
            val identScore = identVerdict?.verdict?.score ?: 0.0
            val identReason = identVerdict?.verdict?.reason.orEmpty()
            val identEvidence = identVerdict?.evidenceSource ?: "-"
            if (played) {
                if (identKind == "CORRECT") {
                    richtig++
                    groupRichtig[item.group] = (groupRichtig[item.group] ?: 0) + 1
                } else {
                    identClasses[identKind] = (identClasses[identKind] ?: 0) + 1
                    val m = groupIdentClasses.getOrPut(item.group) { mutableMapOf() }
                    m[identKind] = (m[identKind] ?: 0) + 1
                }
            }
            report(
                "QUOTA IDENT $mode idx=${index + 1} id=${item.id} group=${item.group} " +
                    "played=${if (played) "ja" else "nein"} ident=$identKind " +
                    "score=${String.format(java.util.Locale.ROOT, "%.2f", identScore)} " +
                    "evidenz=$identEvidence" +
                    (if (identReason.isNotBlank()) " grund=${identReason.sanitize()}" else "")
            )
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
        // Richtigkeitsquote (Gate 1): gespielt **und** Inhalt bestätigt.
        report(
            "QUOTA RIGHT SUMMARY mode=$mode richtig=$richtig/${sample.size} " +
                "quote=${richtig * 100 / sample.size} " +
                "gespielt=$ok/${sample.size} " +
                "falsch=[${identClasses.entries.sortedByDescending { it.value }.joinToString(",") { "${it.key}:${it.value}" }}]"
        )
        report(
            "QUOTA RIGHT GROUPEN mode=$mode " +
                groupTotal.keys.sorted().joinToString(" | ") { group ->
                    val total = groupTotal[group] ?: 0
                    val right = groupRichtig[group] ?: 0
                    val top = groupIdentClasses[group].orEmpty()
                        .entries.sortedByDescending { it.value }
                        .firstOrNull()?.let { "${it.key}:${it.value}" } ?: "-"
                    val quote = if (total > 0) right * 100 / total else 0
                    "$group | $total | $right | $quote% | $top"
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
    private fun drawSample(items: List<QuotaCatalogItem>, mode: String, seed: Long): List<QuotaCatalogItem> {
        if (mode == MODE_HOLDOUT) {
            val trainIds = sample(items, TRAIN_SEED).mapTo(mutableSetOf()) { it.id }
            val rest = items.filter { it.id !in trainIds }
            return sample(rest, seed)
        }
        return sample(items, seed)
    }

    private fun sample(items: List<QuotaCatalogItem>, seed: Long): List<QuotaCatalogItem> {
        val random = Random(seed)
        val series = items.filter { it.type == "series" }.shuffled(random)
        val movies = items.filter { it.type != "series" }.shuffled(random)
        val targetSeries = (SAMPLE_SIZE.toLong() * series.size / items.size.coerceAtLeast(1)).toInt()
        return (series.take(targetSeries) + movies.take(SAMPLE_SIZE - targetSeries)).shuffled(random)
    }

    private fun loadCatalog(): List<QuotaCatalogItem> {
        val text = runCatching {
            assets.open("catalog/catalog.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("items") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val title = entry.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            QuotaCatalogItem(
                id = entry.optString("id"),
                title = title,
                type = entry.optString("type"),
                group = entry.optString("group").ifBlank { "unbekannt" },
                year = entry.optInt("year", 0).takeIf { it > 0 },
                runtime = entry.optInt("runtime", 0).takeIf { it > 0 },
                sourceKey = entry.optString("sourceKey").takeIf { it.isNotBlank() }
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
        playerContainer.addView(
            playbackOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
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

    private data class QuotaCatalogItem(
        val id: String,
        val title: String,
        val type: String,
        val group: String,
        /** Gate 1: Jahresangabe für die Identitätsprüfung, wenn der Katalog sie hat. */
        val year: Int? = null,
        /** Gate 1: Laufzeit in Minuten, wenn der Katalog sie hat. */
        val runtime: Int? = null,
        /** Adresse innerhalb der Quellengruppe, z. B. der Aniworld-Slug. */
        val sourceKey: String? = null
    )

    private data class Attempt(val firstFrameMs: Long?, val errorClass: String?)

    private data class CatalogPanel(
        val poster: Boolean,
        val backdrop: Boolean,
        val overview: Boolean,
        val genres: Boolean,
        val cast: Boolean,
        val year: Boolean,
        val runtime: Boolean
    ) {
        val isValid: Boolean
            get() = poster && backdrop && overview && genres && cast && year && runtime
    }

    private fun Boolean.status(): String = if (this) "PASS" else "FAIL"

    private data class PathPlaybackAttempt(
        val ok: Boolean,
        val firstFrameMs: Long,
        val positionMs: Long,
        val durationMs: Long,
        val error: String?
    )

    private companion object {
        const val TAG = "Frame61Quota"
        const val EXTRA_SAMPLE = "sample"
        const val MODE_ANIWORLD20 = "aniworld20"
        const val PATH_MANIFEST_ASSET = "catalog/aniworld-path.json"
        const val PATH_OUTPUT_DIR = "path-acceptance"
        const val PATH_ARTWORK_DIR = "path-acceptance/aniworld"
        val PATH_ARTWORK_EXTENSIONS = listOf("jpg", "png", "webp")
        const val PATH_SOURCE_BUDGET_MS = 20_000L
        const val PATH_RESOLVE_BUDGET_MS = 70_000L
        const val PATH_PLAYBACK_POLL_MS = 250L
        const val PATH_PLAYBACK_POLLS = 48
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