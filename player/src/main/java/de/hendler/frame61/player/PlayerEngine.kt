package de.hendler.frame61.player

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.datasource.okhttp.OkHttpDataSource
import de.hendler.frame61.net.HttpClientFactory
import de.hendler.frame61.net.PlaybackDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the UI needs to render the player, in one immutable snapshot. */
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercent: Int = 0,
    val isReady: Boolean = false,
    val error: String? = null,
    val audioTracks: List<TrackLabel> = emptyList(),
    val textTracks: List<TrackLabel> = emptyList(),
    val selectedAudio: String? = null,
    val selectedText: String? = null,
    val speed: Float = 1.0f,
    val engine: String = "Media3"
) {
    val fraction: Float get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val isNearEnd: Boolean get() = durationMs > 0 && fraction >= 0.9f
}

data class TrackLabel(val id: String, val label: String, val language: String?)

/** Preserve Range and other DataSpec headers while adding resolver context. */
internal fun mergeMediaRequestHeaders(
    mediaHeaders: Map<String, String>,
    dataSpecHeaders: Map<String, String>
): Map<String, String> = mediaHeaders + dataSpecHeaders

internal fun inferMediaMimeType(url: String, declared: String? = null): String? = declared ?: when {
    url.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
    url.contains(".mp4", ignoreCase = true) -> MimeTypes.VIDEO_MP4
    url.contains(".mkv", ignoreCase = true) -> "video/x-matroska"
    url.contains(".avi", ignoreCase = true) -> "video/x-msvideo"
    url.contains(".ts", ignoreCase = true) -> "video/mp2t"
    else -> null
}

/**
 * Media3 wrapper.
 *
 * Every state change is published as [PlayerUiState]. [publish] is the single
 * place that reports playback progress, so the watchdog has one source of truth.
 */
class Media3Frame61Player(
    private val context: Context
) {

    private val trackSelector = DefaultTrackSelector(context)
    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .build()
    private val fallback = SystemFallbackPlayer(context)
    private var useFallback = false
    private var fallbackAttempted = false
    private var currentUrl = ""
    private var currentHeaders: Map<String, String> = emptyMap()
    private var primaryError: PlaybackException? = null

    init {
        PlaybackDiagnostics.sink = { event -> Log.d(DIAG_TAG, event.asLogLine()) }
    }

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private var playbackSessionId: Long = 0L

    /**
     * The underlying Media3 player, for views that need the instance itself
     * (`PlayerView.player`). Wrapped by this class otherwise — no caller should
     * bypass it for control, only for rendering.
     */
    val exoPlayer: ExoPlayer get() = player
    val fallbackView: android.view.SurfaceView get() = fallback.surfaceView

    /** Called on every published frame tick — the watchdog consumes this. */
    var onFrame: ((positionMs: Long, durationMs: Long) -> Unit)? = null
    var onError: ((PlaybackException?) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            PlaybackDiagnostics.record(
                "PLAYER",
                "Media3-Zustand $playbackState",
                sessionId = playbackSessionId,
                details = mapOf("state" to stateName(playbackState))
            )
            publish()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()

        override fun onTracksChanged(tracks: Tracks) {
            PlaybackDiagnostics.record(
                "PLAYER",
                "Spuren aktualisiert",
                sessionId = playbackSessionId,
                details = mapOf(
                    "video" to tracks.groups.count { it.type == C.TRACK_TYPE_VIDEO }.toString(),
                    "audio" to tracks.groups.count { it.type == C.TRACK_TYPE_AUDIO }.toString(),
                    "text" to tracks.groups.count { it.type == C.TRACK_TYPE_TEXT }.toString(),
                    "variants" to videoFormats(tracks),
                    "selectedVideo" to (player.videoFormat?.let(::formatSummary) ?: "-")
                )
            )
            publish()
        }

        override fun onRenderedFirstFrame() {
            PlaybackDiagnostics.record("PLAYER", "Erstes Frame gerendert", sessionId = playbackSessionId)
            publish()
        }

        override fun onPlayerError(error: PlaybackException) {
            PlaybackDiagnostics.record(
                "PLAYER",
                "Media3-Fehler ${error.errorCodeName}",
                sessionId = playbackSessionId,
                details = mapOf(
                    "errorCode" to error.errorCode.toString(),
                    "cause" to causeChain(error)
                )
            )
            startFallback(error)
        }
    }

    fun attach() {
        player.addListener(listener)
        publish()
    }

    fun open(
        url: String,
        mimeType: String? = null,
        startAtMs: Long = 0L,
        requestHeaders: Map<String, String> = emptyMap(),
        cookies: String? = null
    ) {
        // A missing media-source class used to kill the whole process
        // (ClassNotFoundException: HlsMediaSource$Factory, A52 15:14:32).
        // The dependency is fixed, but a broken build must never crash the UI:
        // surface it as the normal error panel instead.
        playbackSessionId = PlaybackDiagnostics.currentSessionId
        fallbackAttempted = false
        useFallback = false
        primaryError = null
        currentUrl = url
        fallback.release()
        runCatching {
            val inferred = inferMediaMimeType(url, mimeType)
            PlaybackDiagnostics.record(
                "PLAYER",
                "Media3 öffnet Kandidaten",
                sessionId = playbackSessionId,
                url = url,
                details = mapOf(
                    "mimeType" to (inferred ?: "sniffing"),
                    "startAtMs" to startAtMs.toString(),
                    "headerNames" to requestHeaders.keys.sorted().joinToString(","),
                    "cookies" to (cookies != null).toString(),
                    "engine" to "ExoPlayer/Media3 + OkHttpDataSource"
                )
            )
            val builder = MediaItem.Builder().setUri(url)
            inferred?.let { builder.setMimeType(it) }
            val mediaHeaders = if (cookies.isNullOrBlank()) {
                requestHeaders
            } else {
                requestHeaders + mapOf("Cookie" to cookies)
            }
            currentHeaders = mediaHeaders
            val okHttpFactory = OkHttpDataSource.Factory(HttpClientFactory.create())
            val resolvingFactory = ResolvingDataSource.Factory(
                okHttpFactory,
                ResolvingDataSource.Resolver { dataSpec ->
                    dataSpec.withRequestHeaders(
                        mergeMediaRequestHeaders(mediaHeaders, dataSpec.httpRequestHeaders)
                    )
                }
            )
            val dataSourceFactory = DefaultDataSource(context, resolvingFactory.createDataSource())
            val source = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)
                .createMediaSource(builder.build())
            player.setMediaSource(source, startAtMs.coerceAtLeast(0L))
            player.prepare()
            player.play()
        }.onFailure { error ->
            PlaybackDiagnostics.record(
                "PLAYER",
                "Media3-Setup fehlgeschlagen: ${error.javaClass.simpleName}",
                sessionId = playbackSessionId,
                url = url
            )
            _state.value = _state.value.copy(
                error = error.message ?: error.javaClass.simpleName,
                isPlaying = false
            )
            onError?.invoke(null)
            return
        }
        _state.value = _state.value.copy(
            error = null,
            positionMs = startAtMs,
            durationMs = 0L,
            engine = "Media3"
        )
    }

    fun play() {
        if (useFallback) fallback.play() else player.play()
    }

    fun pause() {
        if (useFallback) fallback.pause() else player.pause()
        publish()
    }

    fun stop() {
        player.stop()
        fallback.stop()
        _state.value = _state.value.copy(isPlaying = false, positionMs = 0L)
    }

    fun release() {
        PlaybackDiagnostics.record("PLAYER", "Media3 freigegeben", sessionId = playbackSessionId)
        player.removeListener(listener)
        fallback.release()
        player.release()
        PlaybackDiagnostics.endSession(playbackSessionId)
    }

    fun seekTo(positionMs: Long) {
        if (useFallback) fallback.seekTo(positionMs) else player.seekTo(positionMs.coerceAtLeast(0L))
        _state.value = _state.value.copy(positionMs = positionMs.coerceAtLeast(0L))
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        if (useFallback) fallback.setSpeed(clamped) else player.setPlaybackSpeed(clamped)
        _state.value = _state.value.copy(speed = clamped)
    }

    fun selectAudio(trackId: String?) = selectTrack(C.TRACK_TYPE_AUDIO, trackId) { copy(selectedAudio = trackId) }
    fun selectText(trackId: String?) = selectTrack(C.TRACK_TYPE_TEXT, trackId) { copy(selectedText = trackId) }

    private fun selectTrack(
        type: Int,
        trackId: String?,
        update: PlayerUiState.() -> PlayerUiState
    ) {
        if (useFallback) {
            fallback.selectTrack(type, trackId)
            _state.value = _state.value.update()
            publish()
            return
        }
        val params = trackSelector.buildUponParameters()
        val group = player.currentTracks.groups.firstOrNull { it.type == type }

        if (trackId == null || group == null) {
            params.clearOverridesOfType(type)
        } else {
            val index = (0 until group.length).firstOrNull { group.getTrackFormat(it).id == trackId }
            if (index == null) {
                params.clearOverridesOfType(type)
            } else {
                params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
            }
        }

        runCatching { trackSelector.setParameters(params.build()) }
        _state.value = _state.value.update()
    }

    /** Reads position/duration/buffer, publishes state and emits a frame tick. */
    fun publish() {
        if (useFallback) {
            val snapshot = fallback.snapshot()
            _state.value = _state.value.copy(
                isPlaying = snapshot.isPlaying,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
                bufferedPercent = snapshot.bufferedPercent,
                isReady = snapshot.isReady,
                audioTracks = snapshot.audioTracks,
                textTracks = snapshot.textTracks,
                engine = "Android MediaPlayer"
            )
            onFrame?.invoke(snapshot.positionMs, snapshot.durationMs)
            return
        }
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = if (player.duration == C.TIME_UNSET) 0L else player.duration.coerceAtLeast(0L)
        val buffered = player.bufferedPosition
        val percent = if (duration > 0) ((buffered * 100L) / duration).toInt().coerceIn(0, 100) else 0

        _state.value = _state.value.copy(
            isPlaying = player.isPlaying,
            positionMs = position,
            durationMs = duration,
            bufferedPercent = percent,
            isReady = player.playbackState == Player.STATE_READY,
            engine = "Media3",
            audioTracks = labels(C.TRACK_TYPE_AUDIO),
            textTracks = labels(C.TRACK_TYPE_TEXT)
        )
        onFrame?.invoke(position, duration)
    }

    private fun labels(type: Int): List<TrackLabel> =
        player.currentTracks.groups.filter { it.type == type }.flatMap { group ->
            (0 until group.length).map { index ->
                val format = group.getTrackFormat(index)
                TrackLabel(
                    id = format.id ?: "track-$index",
                    label = format.label ?: format.language ?: "Spur ${index + 1}",
                    language = format.language
                )
            }
        }

    private fun videoFormats(tracks: Tracks): String =
        tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { group ->
            (0 until group.length).map { index -> formatSummary(group.getTrackFormat(index)) }
        }.joinToString(" | ").ifBlank { "-" }

    private fun formatSummary(format: androidx.media3.common.Format): String = buildString {
        append(format.width).append('x').append(format.height)
        append('@').append(format.bitrate)
        format.codecs?.let { append(" codecs=").append(it) }
        format.colorInfo?.let { append(" colorTransfer=").append(it.colorTransfer) }
    }

    private fun startFallback(error: PlaybackException) {
        primaryError = error
        val container = PlaybackDiagnostics.events.value.lastOrNull { event ->
            event.url == PlaybackDiagnostics.safeUrl(currentUrl) && event.status != null
        }?.details?.get("container")
        if (fallbackAttempted || container in setOf("HTML", "JSON", "EMPTY")) {
            _state.value = _state.value.copy(error = error.errorCodeName, isPlaying = false)
            onError?.invoke(error)
            return
        }
        fallbackAttempted = true
        useFallback = true
        _state.value = _state.value.copy(
            error = null,
            isPlaying = false,
            isReady = false,
            engine = "Android MediaPlayer"
        )
        PlaybackDiagnostics.record(
            "PLAYER",
            "Wechsel zu Android MediaPlayer",
            sessionId = playbackSessionId,
            url = currentUrl,
            details = mapOf("container" to (container ?: "unbekannt"))
        )
        fallback.onFirstFrame = {
            PlaybackDiagnostics.record(
                "PLAYER",
                "Systemplayer erstes Frame",
                sessionId = playbackSessionId
            )
            publish()
        }
        fallback.onPreparedChanged = { publish() }
        fallback.onFailure = { what, extra ->
            PlaybackDiagnostics.record(
                "PLAYER",
                "Systemplayer fehlgeschlagen",
                sessionId = playbackSessionId,
                details = mapOf("what" to "$what", "extra" to "$extra")
            )
            _state.value = _state.value.copy(
                error = primaryError?.errorCodeName ?: "SYSTEM_PLAYER_ERROR",
                isPlaying = false
            )
            onError?.invoke(primaryError)
        }
        runCatching {
            fallback.open(currentUrl, currentHeaders, _state.value.positionMs)
        }.onFailure {
            fallback.onFailure(-1, -1)
        }
    }

    private fun stateName(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN"
    }

    private fun causeChain(error: PlaybackException): String =
        generateSequence(error.cause) { it.cause }
            .take(4)
            .joinToString(" <- ") { it.javaClass.simpleName }
            .ifBlank { "none" }

    private companion object {
        const val DIAG_TAG = "Frame61Diag"
    }
}