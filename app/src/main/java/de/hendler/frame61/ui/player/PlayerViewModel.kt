package de.hendler.frame61.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.data.ResumeRepository
import de.hendler.frame61.player.Media3Frame61Player
import de.hendler.frame61.player.NextEpisodeCountdown
import de.hendler.frame61.player.PlayerUiState
import de.hendler.frame61.playback.AutoPlaybackResolver
import de.hendler.frame61.playback.CaptchaBroker
import de.hendler.frame61.playback.CaptchaRequest
import de.hendler.frame61.resolver.MirrorCandidate
import de.hendler.frame61.resolver.PlaybackWatchdog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private var episodeId: Long = 0L
    private var mirrors: List<MirrorCandidate> = emptyList()
    private var mirrorIndex = 0
    private var lastPositionMs: Long = -1L
    private var tickJob: kotlinx.coroutines.Job? = null

    /** The Media3 instance the [PlayerScreen] renders into. */
    val player: ExoPlayer get() = mediaPlayer.exoPlayer

    fun start(
        profileId: Long,
        titleId: String,
        title: String,
        episodeId: Long,
        episodeNumber: Int,
        fallbackCandidates: List<MirrorCandidate>
    ) {
        this.profileId = profileId
        this.titleId = titleId
        this.episodeId = episodeId
        this.mirrors = fallbackCandidates
        this.mirrorIndex = 0

        mediaPlayer.attach()
        mediaPlayer.onFrame = ::onFrame

        viewModelScope.launch {
            // Real sources first; the demo is only a last-resort fallback and is
            // never labelled as a source (source failures are log-only).
            val resolved = runCatching { autoResolver.resolve(title, episodeNumber) }
                .getOrDefault(emptyList())
            mirrors = resolved.ifEmpty { fallbackCandidates }
            _state.update {
                it.copy(
                    sourceNote = resolved.firstOrNull()?.let { candidate ->
                        if (candidate.sourceId == "ard") "ARD Mediathek"
                        else candidate.sourceId
                    }
                )
            }
            val resume = resumeRepository.load(profileId, CatalogPlayback.stableId(titleId), episodeId)
            openMirror(index = 0, startAtMs = resume?.positionMs ?: 0L)
            startTicker()
        }
    }

    private fun openMirror(index: Int, startAtMs: Long) {
        val mirror = mirrors.getOrNull(index)
        if (mirror == null) {
            _state.update { it.copy(loading = false, fatal = "Keine Quelle verfügbar") }
            return
        }
        mirrorIndex = index
        lastPositionMs = startAtMs
        mediaPlayer.open(mirror.url, startAtMs = startAtMs)
        watchdog.reportFrame(startAtMs)
        _state.update { it.copy(loading = false, fatal = null) }
    }

    /** Frame tick: resume throttled, watchdog fed, countdown advanced. */
    private fun onFrame(positionMs: Long, durationMs: Long) {
        val current = _state.value
        _state.update { it.copy(ui = mediaPlayer.state.value) }

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
                episodeId = episodeId,
                positionMs = positionMs,
                durationMs = durationMs
            )
        }

        if (watchdog.isStalled() && mediaPlayer.state.value.isReady) {
            rotateMirror()
        }

        val next = countdown.onFrame(positionMs, durationMs)
        _state.update {
            it.copy(
                countdownSeconds = if (next.running) ((next.remainingMs + 999) / 1000).toInt() else null
            )
        }
    }

    /** 8 s without a frame → next mirror, position carried over. */
    private fun rotateMirror() {
        val nextIndex = mirrorIndex + 1
        if (nextIndex >= mirrors.size) {
            _state.update { it.copy(fatal = "Alle Mirrors ausgefallen") }
            return
        }
        val resumeMs = watchdog.positionMs
        openMirror(nextIndex, resumeMs)
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
        // viewModelScope is already cancelled here; a one-shot IO scope keeps the
        // final position. Measured: the old save never ran, so the last seconds
        // were lost on every exit.
        val position = mediaPlayer.state.value.positionMs
        val duration = mediaPlayer.state.value.durationMs
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            resumeRepository.save(
                profileId = profileId,
                titleId = CatalogPlayback.stableId(titleId),
                episodeId = episodeId,
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