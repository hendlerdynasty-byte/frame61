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
    private var copiedAsset: java.io.File? = null
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
                // The surface arrives after the player was created, so this is
                // the normal path that attaches the video output.
                runCatching { mediaPlayer?.setDisplay(holder) }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                runCatching { mediaPlayer?.setDisplay(null) }
            }
        })
    }

    /**
     * The view is attached to the window only after the UI switched to the
     * fallback engine, so the surface usually does not exist yet when [open]
     * runs. Calling `setDisplay` on a released surface throws
     * "IllegalArgumentException: The surface has been released" and killed the
     * whole fallback (measured on the A52). The holder callback attaches the
     * surface as soon as it exists.
     */
    private fun attachSurfaceIfReady(player: MediaPlayer) {
        val holder = surfaceView.holder
        if (holder.surface?.isValid == true) {
            runCatching { player.setDisplay(holder) }
        }
    }

    fun open(url: String, headers: Map<String, String>, startAtMs: Long) {
        release()
        pendingSeekMs = startAtMs.coerceAtLeast(0L)
        lastPositionMs = pendingSeekMs
        val player = MediaPlayer()
        mediaPlayer = player
        attachSurfaceIfReady(player)
        if (url.startsWith("asset:///")) {
            val path = url.removePrefix("asset:///")
            // Android's system player picks its extractor from the path. The
            // extension must survive the temporary copy, otherwise a valid MP4
            // is judged by the artificial ".media" suffix instead of its bytes.
            val extension = Regex("\\.([A-Za-z0-9]{1,8})$").find(path)?.groupValues?.get(1)
                ?: "media"
            val file = java.io.File(
                appContext.cacheDir,
                "fallback-${path.hashCode()}.$extension"
            )
            appContext.assets.open(path).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            copiedAsset = file
            player.setDataSource(file.absolutePath)
        } else {
            player.setDataSource(appContext, Uri.parse(url), headers)
        }
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
        copiedAsset?.delete()
        copiedAsset = null
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
            audioTracks = labels(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO),
            textTracks = labels(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT)
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
