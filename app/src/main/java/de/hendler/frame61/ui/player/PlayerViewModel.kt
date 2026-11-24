package de.hendler.frame61.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.data.ResumeRepository
import de.hendler.frame61.net.PlaybackDiagnostics
import de.hendler.frame61.player.Media3Frame61Player
import de.hendler.frame61.player.NextEpisodeCountdown
import de.hendler.frame61.player.PlayerUiState
import de.hendler.frame61.playback.AutoPlaybackResolver
import de.hendler.frame61.playback.CaptchaBroker
import de.hendler.frame61.playback.CaptchaRequest
import de.hendler.frame61.playback.PrefetchController
import de.hendler.frame61.resolver.MirrorCandidate
import de.hendler.frame61.resolver.PlaybackWatchdog
import de.hendler.frame61.resolver.hostOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class PlayerScreenState(
    val ui: PlayerUiState = PlayerUiState(),
    val loading: Boolean = true,
    val countdownSeconds: Int? = null,
    val fatal: String? = null,
    /** "ARD Mediathek" when the real source resolved, "Demo-Stream" otherwise. */
    val sourceNote: String? = null
)

/**
 * Drives one playback session: source selection, resume, watchdog, countdown.
 *
 * The watchdog is wired here on purpose — before this it was tested but never
 * called from a player, so the 8-second rule existed only on paper.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    private val resumeRepository: ResumeRepository,
    private val autoResolver: AutoPlaybackResolver,
    private val prefetchController: PrefetchController,
    private val captchaBroker: CaptchaBroker,
    prefs: ProfileDataStore
) : AndroidViewModel(application) {

    val captchaRequest: StateFlow<CaptchaRequest?> = captchaBroker.request

    val subtitleScale: StateFlow<Float> = prefs.subtitleScale.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        1.0f
    )

    private val mediaPlayer = Media3Frame61Player(application)
    private val watchdog = PlaybackWatchdog(timeoutMs = WATCHDOG_MS)
    private val countdown = NextEpisodeCountdown(countdownMs = COUNTDOWN_MS)

    private val _state = MutableStateFlow(PlayerScreenState())
    val state: StateFlow<PlayerScreenState> = _state.asStateFlow()

    private var profileId: Long = 0L
    private var titleId: String = ""
    private var titleName: String = ""
    private var episodeId: Long = 0L
    private var episodeNumber: Int = 0
    private var season: Int = 1
    /**
     * Stabiler Resume-Schlüssel der Folge (Block 8).
     *
     * Die Routen-EpisodeId ist die TVmaze-Id — stabil über Quellenwechsel
     * hinweg. Für Katalogtitel **ohne** Metadaten-Snapshot ist sie aber 0,
     * dann teilten sich alle Folgen eines Titels einen Slot. Abgeleitet aus
     * (Titel, Staffel, Nummer) ist der Schlüssel eindeutig und unabhängig
     * davon, über welche Quelle die Folge gerade aufgelöst wurde.
     */
    private var episodeKey: Long = 0L
    private var mirrors: List<MirrorCandidate> = emptyList()
    private var fallbackMirrors: List<MirrorCandidate> = emptyList()
    private var mirrorIndex = 0
    private var lastPositionMs: Long = -1L
    /** Startzeit der Wiedergabe — Bezugspunkt fuer die Zeit bis zum Bild. */
    private var sessionStartedAt: Long = 0L
    /** Wurde der Kandidat aus dem Vorwaermlauf uebernommen? */
    private var startedFromPrefetch: Boolean = false
    /** Erstes spielbares Bild bereits gemeldet? */
    private var firstFrameReported: Boolean = false
    private var tickJob: kotlinx.coroutines.Job? = null
    private var recoveryJob: Job? = null
    /**
     * Der laufende Aufloesungslauf des Starts (Block 7).
     *
     * Eine Fehler-Recovery, die waehrenddessen eintritt, wartet kurz auf
     * diesen Lauf: der Kandidat aus dem Fokus-Vorwaermlauf kann scheitern,
     * obwohl die volle Aufloesung Sekunden spaeter den spielbaren liefert.
     */
    private var resolveJob: Job? = null
    private val failedUrls = mutableSetOf<String>()
    /**
     * Hosts der **Watch-URLs**, die bereits gescheitert sind.
     *
     * Für die Alternative-Kette (Block 4e) ist das der richtige Schlüssel:
     * die aufgelöste Medien-URL sieht anders aus als die Hoster-Seite, die
     * sie erzeugt hat. Gemessen Lauf 7: 12 Titel starben auf
     * `moflix.rpmplay.xyz`, obwohl dieselbe Watch-Seite 3–8 weitere Hoster
     * anbot.
     */
    private val failedHosts = mutableSetOf<String>()

    /** The Media3 instance the [PlayerScreen] renders into. */
    val player: ExoPlayer get() = mediaPlayer.exoPlayer
    val fallbackPlayerView: android.view.SurfaceView get() = mediaPlayer.fallbackView

    fun start(
        profileId: Long,
        titleId: String,
        title: String,
        episodeId: Long,
        episodeNumber: Int,
        season: Int = 1,
        fallbackCandidates: List<MirrorCandidate>
    ) {
        recoveryJob?.cancel()
        tickJob?.cancel()
        resolveJob?.cancel()
        failedUrls.clear()
        failedHosts.clear()
        this.profileId = profileId
        this.titleId = titleId
        this.titleName = title
        this.episodeId = episodeId
        this.episodeNumber = episodeNumber
        this.season = season
        this.episodeKey = if (episodeId > 0) {
            episodeId
        } else {
            CatalogPlayback.stableId("$titleId|s$season|e$episodeNumber")
        }
        this.mirrors = fallbackCandidates
        this.fallbackMirrors = fallbackCandidates
        this.mirrorIndex = 0
        this.sessionStartedAt = System.currentTimeMillis()
        this.firstFrameReported = false
        this.startedFromPrefetch = false
        val diagnosticSessionId = PlaybackDiagnostics.beginSession(title, episodeNumber)

        mediaPlayer.attach()
        mediaPlayer.onFrame = ::onFrame
        mediaPlayer.onError = ::recoverFromPlayback
        // Ab jetzt hat der Player den Vordergrund: keine neuen Vorwaermläufe.
        prefetchController.markForegroundBusy(true)

        viewModelScope.launch {
            val titleKey = CatalogPlayback.stableId(titleId)
            // Block 8: Resume über Quellenwechsel — der Schlüssel hängt nur an
            // der Folge (Titel, Staffel, Nummer), nie an der Quelle. Titel ohne
            // Metadaten hatten bisher episodeId = 0 als Slot; beim Lesen wird
            // deshalb der alte Schlüssel als Fallback mitgeprüft, damit
            // bestehende Positionen nicht verloren gehen.
            val resume = resumeRepository.load(profileId, titleKey, episodeKey)
                ?: resumeRepository.load(profileId, titleKey, 0L)
            val resumeMs = resume?.positionMs ?: 0L

            // Block 7: Kandidat aus dem Fokus-Vorwaermlauf. Liegt einer vor,
            // startet der Player sofort damit — die volle Aufloesung laeuft
            // parallel weiter und liefert nur die Fallback-Kette.
            val prefetched = prefetchController.take(title, episodeNumber, season)
            if (prefetched.isNotEmpty()) {
                mirrors = prefetched
                startedFromPrefetch = true
                _state.update { it.copy(sourceNote = sourceNote(prefetched.first())) }
                PlaybackDiagnostics.record(
                    "RESOLVER",
                    "Start aus dem Vorwaermlauf (${prefetched.size} Kandidat(en))",
                    sessionId = diagnosticSessionId,
                    details = mapOf("sourceId" to prefetched.first().sourceId)
                )
                openMirror(index = 0, startAtMs = resumeMs, sessionId = diagnosticSessionId)
            }

            // Der Ticker laeuft von hier an: er veroeffentlicht den Player-
            // Zustand (Ladeanzeige, Position) — auch waehrend die Aufloesung
            // noch laeuft.
            startTicker()

            // Real sources first; the demo is only a last-resort fallback and is
            // never labelled as a source (source failures are log-only).
            // resolveFast: sobald ein Kandidat steht, startet der Player — der
            // Rest der Quellen darf den Start nicht verzögern.
            resolveJob = launch {
                val resolved = runCatching {
                    autoResolver.resolveFast(
                        title,
                        episodeNumber,
                        season,
                        sessionId = diagnosticSessionId
                    )
                }.getOrDefault(emptyList())
                if (prefetched.isEmpty()) {
                    mirrors = resolved.ifEmpty { fallbackCandidates }
                    _state.update { it.copy(sourceNote = resolved.firstOrNull()?.let(::sourceNote)) }
                    openMirror(index = 0, startAtMs = resumeMs, sessionId = diagnosticSessionId)
                } else {
                    // Der Vorwaerm-Kandidat laeuft schon: die volle Liste nur
                    // als zusaetzliche Fallbacks anhaengen, nicht umschalten.
                    val known = mirrors.map { it.url }.toSet()
                    mirrors = (mirrors + resolved.filter { it.url !in known }).distinctBy { it.url }
                    fallbackMirrors = (resolved + fallbackMirrors).distinctBy { it.url }
                }
            }
        }
    }

    /** Anzeigename der Quelle im Player. */
    private fun sourceNote(candidate: MirrorCandidate): String? =
        when (candidate.sourceId) {
            "ard" -> "ARD Mediathek"
            else -> candidate.sourceId
        }

    private fun openMirror(
        index: Int,
        startAtMs: Long,
        sessionId: Long = PlaybackDiagnostics.currentSessionId
    ) {
        val mirror = mirrors.getOrNull(index)
        if (mirror == null) {
            _state.update { it.copy(loading = false, fatal = "Keine Quelle verfügbar") }
            return
        }
        mirrorIndex = index
        lastPositionMs = startAtMs
        PlaybackDiagnostics.record(
            "RESOLVER",
            "Öffne Kandidat ${index + 1}/${mirrors.size}",
            sessionId = sessionId,
            url = mirror.url,
            details = mapOf(
                "sourceId" to mirror.sourceId,
                "hosterId" to (mirror.hosterId ?: "-"),
                "quality" to mirror.quality,
                "resumeMs" to startAtMs.toString()
            )
        )
        mediaPlayer.open(
            url = mirror.url,
            mimeType = mirror.mimeType,
            startAtMs = startAtMs,
            requestHeaders = mirror.requestHeaders,
            cookies = mirror.cookies
        )
        watchdog.reportFrame(startAtMs)
        _state.update {
            it.copy(
                loading = false,
                fatal = null,
                sourceNote = when (mirror.sourceId) {
                    "ard" -> "ARD Mediathek"
                    "demo" -> "Demo-Stream"
                    else -> mirror.sourceId
                }
            )
        }
    }

    /** Frame tick: resume throttled, watchdog fed, countdown advanced. */
    private fun onFrame(positionMs: Long, durationMs: Long) {
        val current = _state.value
        _state.update { it.copy(ui = mediaPlayer.state.value) }

        // Block 7 (Messkriterium): erstes spielbares Bild seit dem Start der
        // Wiedergabe. Damit ist auf dem Geraet sichtbar, wie lange der Weg
        // vom Tap bis zum Bild gedauert hat — mit und ohne Vorwaermlauf.
        if (!firstFrameReported && mediaPlayer.state.value.isReady) {
            firstFrameReported = true
            PlaybackDiagnostics.record(
                "PLAYER",
                "Erstes Bild nach ${System.currentTimeMillis() - sessionStartedAt} ms",
                details = mapOf(
                    "vorwaermlauf" to if (startedFromPrefetch) "ja" else "nein",
                    "mirrors" to mirrors.size.toString()
                )
            )
        }

        // The watchdog measures *progress*, not the presence of the 1 s ticker:
        // feeding it on every publish made it fire after 8 s even while the
        // stream was fine ("Alle Mirrors ausgefallen", measured on the A52).
        if (positionMs != lastPositionMs) {
            lastPositionMs = positionMs
            watchdog.reportFrame(positionMs)
        }

        viewModelScope.launch {
            resumeRepository.saveThrottled(
                profileId = profileId,
                titleId = CatalogPlayback.stableId(titleId),
                episodeId = episodeKey,
                positionMs = positionMs,
                durationMs = durationMs
            )
        }

        if (watchdog.isStalled() && mediaPlayer.state.value.isReady) {
            recoverFromPlayback()
        }

        val next = countdown.onFrame(positionMs, durationMs)
        _state.update {
            it.copy(
                countdownSeconds = if (next.running) ((next.remainingMs + 999) / 1000).toInt() else null
            )
        }
    }

    /** Player errors and stalls rotate first, then force a fresh resolution. */
    private fun recoverFromPlayback(error: androidx.media3.common.PlaybackException? = null) {
        if (recoveryJob?.isActive == true) return
        recoveryJob = viewModelScope.launch {
            // Block 7: der laufende Aufloesungslauf des Starts kann den
            // spielbaren Kandidaten Sekunden spaeter liefern — kurz warten,
            // bevor auf die naechsten Mirrors rotiert wird.
            runCatching { withTimeoutOrNull(10_000L) { resolveJob?.join() } }
            val resumeMs = mediaPlayer.state.value.positionMs
            val current = mirrors.getOrNull(mirrorIndex)
            current?.url?.let(failedUrls::add)
            val watchHost = current?.streamUrl?.let { hostOf(it) }.orEmpty()
            if (watchHost.isNotEmpty()) failedHosts += watchHost
            PlaybackDiagnostics.record(
                "PLAYER",
                "Automatische Neuauflösung: ${error?.errorCodeName ?: "stall"}",
                details = mapOf(
                    "failedUrls" to failedUrls.size.toString(),
                    "failedHosts" to failedHosts.joinToString(",")
                )
            )
            val nextIndex = mirrorIndex + 1
            if (nextIndex < mirrors.size) {
                openMirror(nextIndex, resumeMs)
                return@launch
            }
            // Nächste Alternative **derselben Quelle** (Block 4e): der Fast-Path
            // löst je Quelle nur den ersten Hoster auf. Ist der aufgelöst, aber
            // nicht abspielbar (ExoPlayer 3003), stehen die anderen Hoster der
            // Watch-Seite noch unberührt da — gemessen Lauf 7: 12 Fehlschläge an
            // einem toten Hoster, obwohl 3–8 Alternativen danebenstanden.
            val sourceId = mirrors.getOrNull(mirrorIndex)?.sourceId
            val alternatives = sourceId?.let { id ->
                runCatching {
                    autoResolver.resolveAlternative(
                        title = titleName,
                        episodeNumber = episodeNumber,
                        season = season,
                        sourceId = id,
                        excludeUrls = failedUrls.toSet(),
                        excludeHosts = failedHosts.toSet()
                    )
                }.getOrDefault(emptyList())
            }.orEmpty()
            if (alternatives.isNotEmpty()) {
                mirrors = alternatives
                openMirror(0, resumeMs)
                return@launch
            }
            val resolved = runCatching {
                autoResolver.resolve(titleName, episodeNumber, season, fresh = true)
            }.getOrDefault(emptyList())
            val remaining = (resolved + fallbackMirrors)
                .distinctBy { it.url }
                .filter { it.url !in failedUrls }
            if (remaining.isEmpty()) {
                _state.update { it.copy(loading = false, fatal = "Alle Mirrors ausgefallen") }
            } else {
                mirrors = remaining
                openMirror(0, resumeMs)
            }
        }
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                mediaPlayer.publish()
            }
        }
    }

    fun togglePlay() {
        if (mediaPlayer.state.value.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
    }

    /** Re-opens the current mirror — the user-visible recovery path. */
    fun retry() {
        openMirror(index = mirrorIndex, startAtMs = mediaPlayer.state.value.positionMs)
    }

    fun cycleSpeed() {
        val current = mediaPlayer.state.value.speed
        val next = when {
            current < 1.0f -> 1.0f
            current < 1.5f -> 1.5f
            else -> 0.75f
        }
        mediaPlayer.setSpeed(next)
    }

    fun selectAudio(trackId: String?) = mediaPlayer.selectAudio(trackId)
    fun selectText(trackId: String?) = mediaPlayer.selectText(trackId)

    fun seekBy(deltaMs: Long) {
        mediaPlayer.seekTo(mediaPlayer.state.value.positionMs + deltaMs)
    }

    fun cancelCountdown() = countdown.cancel()

    fun submitCaptcha(result: de.hendler.frame61.playback.CaptchaResult) {
        captchaBroker.submit(result)
    }

    fun cancelCaptcha() {
        captchaBroker.cancel()
    }

    override fun onCleared() {
        tickJob?.cancel()
        recoveryJob?.cancel()
        resolveJob?.cancel()
        // Der Player gibt den Vordergrund frei: wieder vorwaermen.
        prefetchController.markForegroundBusy(false)
        captchaBroker.cancel()
        // viewModelScope is already cancelled here; a one-shot IO scope keeps the
        // final position. Measured: the old save never ran, so the last seconds
        // were lost on every exit.
        val position = mediaPlayer.state.value.positionMs
        val duration = mediaPlayer.state.value.durationMs
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            resumeRepository.save(
                profileId = profileId,
                titleId = CatalogPlayback.stableId(titleId),
                episodeId = episodeKey,
                positionMs = position,
                durationMs = duration
            )
        }
        mediaPlayer.release()
        super.onCleared()
    }

    companion object {
        const val WATCHDOG_MS = 8_000L
        const val COUNTDOWN_MS = 8_000L
        const val TICK_MS = 1_000L
    }
}