package de.hendler.frame61.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.data.ResumeRepository
import de.hendler.frame61.data.StreamEvidenceRepository
import de.hendler.frame61.data.database.entities.StreamEvidence
import de.hendler.frame61.data.database.entities.StreamEvidenceOutcome
import de.hendler.frame61.net.PlaybackDiagnostics
import de.hendler.frame61.player.Media3Frame61Player
import de.hendler.frame61.player.NextEpisodeCountdown
import de.hendler.frame61.player.PlayerUiState
import de.hendler.frame61.playback.AutoPlaybackResolver
import de.hendler.frame61.playback.CaptchaBroker
import de.hendler.frame61.playback.CaptchaRequest
import de.hendler.frame61.playback.EpisodeSelection
import de.hendler.frame61.playback.PrefetchController
import de.hendler.frame61.playback.EpisodeSelection
import de.hendler.frame61.resolver.ContentIdentityGate
import de.hendler.frame61.resolver.IdentityRequest
import de.hendler.frame61.resolver.IdentityKind
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
import java.security.MessageDigest
import javax.inject.Inject

data class PlayerScreenState(
    val ui: PlayerUiState = PlayerUiState(),
    val loading: Boolean = true,
    val countdownSeconds: Int? = null,
    val fatal: String? = null,
    /** "ARD Mediathek" when the real source resolved, null otherwise. */
    val sourceNote: String? = null,
    /** Sichtbarer Hinweis, wenn nur ein Trailer/unbestätigter Kandidat läuft. */
    val identityHint: String? = null,
    /** Dauerhaft im Overlay: angefragter Titel (Gate 1, 1.6.0). */
    val titleLabel: String = "",
    /** Dauerhaft im Overlay: Laufzeit aus dem Katalog, wenn bekannt. */
    val runtimeLabel: String? = null
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
    private val streamEvidenceRepository: StreamEvidenceRepository,
    prefs: ProfileDataStore
) : AndroidViewModel(application) {

    val captchaRequest: StateFlow<CaptchaRequest?> = captchaBroker.request

    val subtitleScale: StateFlow<Float> = prefs.subtitleScale.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        1.0f
    )

    /**
     * Gate 1 (1.6.0): kein Kandidat wird abgespielt, ohne dass seine
     * Identität geprüft ist. Seitengrößen sind klein; der Titel wird je URL
     * einmal geholt und im Prozess behalten.
     */
    private val identityGate = ContentIdentityGate()
    private var identityRequest: IdentityRequest? = null
    private val mediaPlayer = Media3Frame61Player(application)
    private val watchdog = PlaybackWatchdog(timeoutMs = WATCHDOG_MS)
    private val countdown = NextEpisodeCountdown(countdownMs = COUNTDOWN_MS)

    private val _state = MutableStateFlow(PlayerScreenState())
    val state: StateFlow<PlayerScreenState> = _state.asStateFlow()

    private var profileId: Long = 0L
    private var titleId: String = ""
    private var titleName: String = ""
    private var titleType: String = "movie"
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
    private val identityScores = mutableMapOf<String, Double>()
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
        type: String = "movie",
        runtimeMin: Int? = null,
        year: Int? = null
    ) {
        recoveryJob?.cancel()
        tickJob?.cancel()
        resolveJob?.cancel()
        failedUrls.clear()
        failedHosts.clear()
        identityScores.clear()
        this.profileId = profileId
        this.titleId = titleId
        this.titleName = title
        this.titleType = type
        this.episodeId = episodeId
        this.episodeNumber = episodeNumber
        this.season = season
        this.episodeKey = if (episodeId > 0) {
            episodeId
        } else {
            CatalogPlayback.stableId("$titleId|s$season|e$episodeNumber")
        }
        // Gate 1: es gibt keinen Demo-/Platzhalter-Kandidaten mehr. Ohne
        // bestätigte Quelle bleibt der Player bei einer Fehlermeldung stehen.
        this.mirrors = emptyList()
        this.fallbackMirrors = emptyList()
        this.mirrorIndex = 0
        this.identityRequest = IdentityRequest(
            title = title,
            year = year,
            type = type,
            season = season,
            episode = EpisodeSelection.effectiveNumber(type, episodeNumber),
            runtimeMin = runtimeMin
        )
        _state.update {
            it.copy(
                titleLabel = title,
                runtimeLabel = runtimeMin?.let { min -> "${min} Min." },
                identityHint = null,
                fatal = null
            )
        }
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
            // Gate 1: auch der Vorwaermkandidat wird erst geprüft. Ein
            // Vorwaermlauf ohne Identitätsangabe wird verworfen, nicht gespielt.
            val request = identityRequest
            val prefetchedRaw = prefetchController.take(title, episodeNumber, season)
            val prefetched = if (request != null && prefetchedRaw.isNotEmpty()) {
                identityGate.verify(request, prefetchedRaw)
                    .filter { it.playable }
                    .map { it.candidate }
            } else {
                emptyList()
            }
            if (prefetchedRaw.isNotEmpty() && prefetched.isEmpty()) {
                PlaybackDiagnostics.record(
                    "IDENTITY",
                    "Vorwaermlauf verworfen: keine bestätigte Identität (${prefetchedRaw.size})",
                    sessionId = diagnosticSessionId
                )
            }
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

            // Gate 1: Es gibt keinen Demo-/Platzhalter-Fallback mehr. Nur
            // Kandidaten mit bestätigter Identität werden geöffnet; sonst bleibt
            // eine verständliche Fehlermeldung stehen.
            // resolveFast: sobald ein Kandidat steht, startet der Player — der
            // Rest der Quellen darf den Start nicht verzögern.
            resolveJob = launch {
                var resolved = runCatching {
                    autoResolver.resolveFast(
                        title,
                        episodeNumber,
                        season,
                        sessionId = diagnosticSessionId
                    )
                }.getOrDefault(emptyList())
                if (resolved.isEmpty()) {
                    // Anbieter rotieren innerhalb kurzer Zeit. Ein frischer
                    // zweiter Versuch ist billiger als eine endlose Warteschleife
                    // und verhindert, dass ein transientes 404/Timeout sofort
                    // als endgültiger Fehler erscheint.
                    delay(1_500)
                    resolved = runCatching {
                        autoResolver.resolveFast(
                            title,
                            episodeNumber,
                            season,
                            sessionId = diagnosticSessionId,
                            fresh = true
                        )
                    }.getOrDefault(emptyList())
                }
                val verified = if (request != null) {
                    identityGate.verify(request, resolved)
                } else {
                    emptyList()
                }
                verified.forEach { v ->
                    identityScores[v.candidate.url] = v.verdict.score
                    PlaybackDiagnostics.record(
                        "IDENTITY",
                        "${v.verdict.kind.name} ${v.candidate.sourceId} ${v.verdict.matchedTitle ?: "-"}",
                        sessionId = diagnosticSessionId,
                        details = mapOf(
                            "score" to String.format(java.util.Locale.ROOT, "%.2f", v.verdict.score),
                            "evidenz" to v.evidenceSource,
                            "grund" to v.verdict.reason
                        )
                    )
                }
                val playable = verified.filter { it.playable }.map { it.candidate }
                val lastResort = verified.firstOrNull { it.lastResort }?.candidate
                if (prefetched.isEmpty()) {
                    when {
                        playable.isNotEmpty() -> {
                            mirrors = playable
                            _state.update {
                                it.copy(
                                    sourceNote = sourceNote(playable.first()),
                                    identityHint = null
                                )
                            }
                            openMirror(index = 0, startAtMs = resumeMs, sessionId = diagnosticSessionId)
                        }
                        lastResort != null -> {
                            // Trailer nur als letzter Ausweg — sichtbar markiert.
                            mirrors = listOf(lastResort)
                            _state.update {
                                it.copy(
                                    sourceNote = sourceNote(lastResort),
                                    identityHint = "Vorschau: nur Trailer/Material, nicht der ganze Titel"
                                )
                            }
                            openMirror(index = 0, startAtMs = resumeMs, sessionId = diagnosticSessionId)
                        }
                        else -> {
                            _state.update {
                                it.copy(
                                    loading = false,
                                    fatal = if (resolved.isEmpty()) {
                                        "Keine Quelle für diesen Titel gefunden"
                                    } else {
                                        "Keine Quelle mit bestätigtem Inhalt gefunden"
                                    }
                                )
                            }
                            PlaybackDiagnostics.record(
                                "IDENTITY",
                                "kein Kandidat mit bestätigter Identität (${resolved.size} geprüft)",
                                sessionId = diagnosticSessionId
                            )
                        }
                    }
                } else {
                    // Der Vorwaerm-Kandidat laeuft schon: die volle Liste nur
                    // als zusaetzliche, geprüfte Fallbacks anhaengen.
                    val known = mirrors.map { it.url }.toSet()
                    mirrors = (mirrors + playable.filter { it.url !in known }).distinctBy { it.url }
                    fallbackMirrors = (playable + fallbackMirrors).distinctBy { it.url }
                }
            }
        }
    }

    /** Anzeigename der Quelle im Player. */
    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

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
                    // Es gibt keinen Demo-Kandidaten mehr; der Zweig bleibt nur für
            // alte Resume-/Cache-Einträge, die noch auf ihn zeigen.
            "demo" -> null
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
            val candidate = mirrors.getOrNull(mirrorIndex)
            if (candidate != null) {
                val outcome = if (current.identityHint != null) {
                    StreamEvidenceOutcome.TRAILER_ONLY
                } else {
                    StreamEvidenceOutcome.CORRECT
                }
                viewModelScope.launch {
                    streamEvidenceRepository.record(
                        StreamEvidence(
                            catalogId = titleId,
                            season = if (titleType.equals("movie", ignoreCase = true)) 0 else season,
                            episode = if (titleType.equals("movie", ignoreCase = true)) {
                                0
                            } else {
                                EpisodeSelection.effectiveNumber(titleType, episodeNumber)
                            },
                            sourceId = candidate.sourceId,
                            sourceKey = candidate.sourceId,
                            checkedAt = System.currentTimeMillis(),
                            outcome = outcome,
                            identityScore = identityScores[candidate.url] ?: 0.0,
                            httpStatus = null,
                            container = null,
                            redactedUrlHash = sha256(candidate.url),
                            reason = "first frame"
                        )
                    )
                }
            }
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
            current?.streamUrl?.let(failedUrls::add)
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
            val request = identityRequest
            val verifiedAlternatives = if (request != null && alternatives.isNotEmpty()) {
                identityGate.verify(request, alternatives).filter { it.playable }.map { it.candidate }
            } else {
                emptyList()
            }
            if (verifiedAlternatives.isNotEmpty()) {
                mirrors = verifiedAlternatives
                openMirror(0, resumeMs)
                return@launch
            }
            val resolved = runCatching {
                autoResolver.resolve(titleName, episodeNumber, season, fresh = true)
            }.getOrDefault(emptyList())
            val verifiedResolved = if (request != null) {
                identityGate.verify(request, resolved).filter { it.playable }.map { it.candidate }
            } else {
                emptyList()
            }
            val remaining = (verifiedResolved + fallbackMirrors)
                .distinctBy { it.url }
                .filter { it.url !in failedUrls }
            if (remaining.isEmpty()) {
                _state.update {
                    it.copy(
                        loading = false,
                        fatal = "Alle Quellen ausgefallen — keine bestätigte Wiedergabe"
                    )
                }
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