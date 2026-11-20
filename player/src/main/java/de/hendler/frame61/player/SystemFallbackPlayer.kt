package de.hendler.frame61.player

import android.content.Context
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Second, independent playback engine used only after a Media3 failure. */
class SystemFallbackPlayer(context: Context) {
    val surfaceView = SurfaceView(context)

    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private var prepared = false
    private var pendingSeekMs = 0L
    private var bufferedPercent = 0
    private var lastPositionMs = 0L

    var onFirstFrame: () -> Unit = {}
    var onPreparedChanged: () -> Unit = {}
    var onFailure: (what: Int, extra: Int) -> Unit = { _, _ -> }

    init {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                mediaPlayer?.setDisplay(holder)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                mediaPlayer?.setDisplay(null)
            }
        })
    }

    fun open(url: String, headers: Map<String, String>, startAtMs: Long) {
        release()
        pendingSeekMs = startAtMs.coerceAtLeast(0L)
        lastPositionMs = pendingSeekMs
        val player = MediaPlayer()
        mediaPlayer = player
        player.setDisplay(surfaceView.holder)
        player.setDataSource(appContext, Uri.parse(url), headers)
        player.setOnPreparedListener {
            prepared = true
            if (pendingSeekMs > 0L) player.seekTo(pendingSeekMs.toInt())
            player.start()
            onPreparedChanged()
        }
        player.setOnErrorListener { _, what, extra ->
            prepared = false
            onFailure(what, extra)
            true
        }
        player.setOnInfoListener { _, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onFirstFrame()
            false
        }
        player.setOnBufferingUpdateListener { _, percent -> bufferedPercent = percent }
        player.setOnCompletionListener { onPreparedChanged() }
        player.prepareAsync()
    }

    fun play() {
        if (prepared) mediaPlayer?.start()
    }

    fun pause() {
        if (prepared) mediaPlayer?.pause()
        onPreparedChanged()
    }

    fun stop() {
        runCatching { mediaPlayer?.stop() }
        prepared = false
        lastPositionMs = 0L
        pendingSeekMs = 0L
    }

    fun release() {
        runCatching { mediaPlayer?.setDisplay(null) }
        runCatching { mediaPlayer?.reset() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        prepared = false
        pendingSeekMs = 0L
        lastPositionMs = 0L
        bufferedPercent = 0
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        lastPositionMs = target
        if (prepared) mediaPlayer?.seekTo(target.toInt())
        else pendingSeekMs = target
    }

    fun setSpeed(speed: Float) {
        if (prepared) runCatching {
            mediaPlayer?.playbackParams = PlaybackParams().setSpeed(speed)
        }
    }

    fun selectTrack(type: Int, trackId: String?) {
        val player = mediaPlayer ?: return
        runCatching {
            if (trackId == null) {
                val selected = player.getSelectedTrack(type)
                if (selected >= 0) player.deselectTrack(selected)
            } else {
                player.selectTrack(trackId.removePrefix("system-$type-").toInt())
            }
        }
    }

    fun snapshot(): FallbackSnapshot {
        val player = mediaPlayer
        if (prepared && player != null) {
            lastPositionMs = runCatching { player.currentPosition.toLong() }.getOrDefault(lastPositionMs)
        }
        val duration = if (prepared && player != null) {
            runCatching { player.duration.toLong() }.getOrDefault(0L)
        } else {
            0L
        }
        return FallbackSnapshot(
            isPlaying = prepared && runCatching { player?.isPlaying == true }.getOrDefault(false),
            positionMs = lastPositionMs,
            durationMs = duration,
            bufferedPercent = bufferedPercent,
            isReady = prepared,
            audioTracks = labels(MediaPlayer.TrackInfo.TRACK_TYPE_AUDIO),
            textTracks = labels(MediaPlayer.TrackInfo.TRACK_TYPE_TIMEDTEXT)
        )
    }

    private fun labels(type: Int): List<TrackLabel> {
        val player = mediaPlayer ?: return emptyList()
        return runCatching {
            player.trackInfo.mapIndexedNotNull { index, info ->
                if (info.trackType != type) return@mapIndexedNotNull null
                val mime = runCatching { info.format.getString(MediaFormat.KEY_MIME) }.getOrNull()
                TrackLabel(
                    id = "system-$type-$index",
                    label = mime ?: info.language ?: "Spur ${index + 1}",
                    language = info.language
                )
            }
        }.getOrDefault(emptyList())
    }
}

data class FallbackSnapshot(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val bufferedPercent: Int,
    val isReady: Boolean,
    val audioTracks: List<TrackLabel>,
    val textTracks: List<TrackLabel>
)
