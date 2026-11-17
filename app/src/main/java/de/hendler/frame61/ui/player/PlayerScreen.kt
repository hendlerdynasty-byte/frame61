package de.hendler.frame61.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import de.hendler.frame61.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import de.hendler.frame61.design.LocalSkin
import de.hendler.frame61.design.tvFocusable
import de.hendler.frame61.player.PlayerUiState

/**
 * Full-screen player.
 *
 * The `PlayerView` is an AndroidView because Media3 ships no Compose variant.
 * Controls are Compose, so the focus ring and 48dp targets stay consistent.
 *
 * Media3 marks every `PlayerView` API as `@UnstableApi` with no stable
 * alternative; the opt-in uses the AndroidX marker because the lint check
 * recognises it.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    player: ExoPlayer?,
    countdownSeconds: Int? = null,
    sourceNote: String? = null,
    onTogglePlay: () -> Unit = {},
    onSeekBack: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onCancelCountdown: () -> Unit = {},
    onRetry: () -> Unit = {},
    onSelectAudio: (String?) -> Unit = {},
    onSelectText: (String?) -> Unit = {},
    onCycleSpeed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (player != null) {
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> createPlayerView(ctx) },
                update = { view -> view.player = player }
            )
        }

        if (!state.isReady && state.error == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        state.error?.let { message ->
            ErrorPanel(
                message = message,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (countdownSeconds != null) {
            NextEpisodeOverlay(
                seconds = countdownSeconds,
                onCancel = onCancelCountdown,
                modifier = Modifier.align(Alignment.BottomEnd).padding(48.dp)
            )
        }

        TrackPanel(
            state = state,
            onSelectAudio = onSelectAudio,
            onSelectText = onSelectText,
            onCycleSpeed = onCycleSpeed,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        )

        // Visible provenance: "ARD Mediathek" for the real source, "Demo-Stream"
        // when the resolver fell back. Never pretend a demo is the title.
        sourceNote?.let { note ->
            Text(
                text = note,
                color = skin.colors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            )
        }

        // Brand wordmark from the design package (transparent PNG, 3:1). Never
        // stretched: ContentScale.Fit, fixed width.
        Image(
            painter = painterResource(R.drawable.frame61_logo_horizontal),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .width(180.dp)
        )

        PlayerControls(
            state = state,
            onTogglePlay = onTogglePlay,
            onSeekBack = onSeekBack,
            onSeekForward = onSeekForward,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** A failed stream must be visible and recoverable, never a black screen. */
@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    Surface(modifier = modifier, color = skin.colors.surface) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(text = "Wiedergabe fehlgeschlagen", style = MaterialTheme.typography.titleMedium)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = skin.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(text = "Erneut versuchen") }
        }
    }
}

/** Audio/subtitle track chooser and speed — the missing "Text". */
@Composable
private fun TrackPanel(
    state: PlayerUiState,
    onSelectAudio: (String?) -> Unit,
    onSelectText: (String?) -> Unit,
    onCycleSpeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.audioTracks.isEmpty() && state.textTracks.isEmpty()) return

    val skin = LocalSkin.current
    Surface(
        modifier = modifier,
        color = skin.colors.surface.copy(alpha = 0.92f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (state.audioTracks.isNotEmpty()) {
                Text("Audio", style = MaterialTheme.typography.labelLarge)
                state.audioTracks.forEach { track ->
                    val selected = track.id == state.selectedAudio
                    Text(
                        text = ("${if (selected) "▶ " else "  "}${track.label}").trim(),
                        modifier = Modifier.tvFocusable(id = "audio-${track.id}", onClick = {
                            onSelectAudio(if (selected) null else track.id)
                        })
                    )
                }
            }
            if (state.textTracks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Untertitel", style = MaterialTheme.typography.labelLarge)
                state.textTracks.forEach { track ->
                    val selected = track.id == state.selectedText
                    Text(
                        text = ("${if (selected) "▶ " else "  "}${track.label}").trim(),
                        modifier = Modifier.tvFocusable(id = "text-${track.id}", onClick = {
                            onSelectText(if (selected) null else track.id)
                        })
                    )
                }
            }
            Text(
                text = "Tempo: %.2fx".format(state.speed),
                modifier = Modifier.tvFocusable(id = "speed", onClick = onCycleSpeed)
            )
        }
    }
}

@Composable
private fun NextEpisodeOverlay(
    seconds: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    Surface(
        modifier = modifier.tvFocusable(id = "next-episode", onClick = onCancel),
        color = skin.colors.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Nächste Folge in $seconds s", color = skin.colors.textPrimary)
            Text(text = "Abbrechen", color = skin.colors.textSecondary)
        }
    }
}

@Composable
private fun PlayerControls(
    state: PlayerUiState,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    Surface(modifier = modifier.fillMaxWidth(), color = skin.colors.surface.copy(alpha = 0.92f)) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(
                text = "${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}",
                color = skin.colors.textPrimary,
                style = MaterialTheme.typography.labelMedium
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(skin.colors.canvas)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.fraction.coerceIn(0f, 1f))
                        .background(skin.colors.primary)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ControlButton(label = "−10", id = "seek-back", onClick = onSeekBack)
                ControlButton(
                    label = if (state.isPlaying) "Pause" else "Play",
                    id = "toggle-play",
                    onClick = onTogglePlay
                )
                ControlButton(label = "+10", id = "seek-forward", onClick = onSeekForward)
            }
        }
    }
}

@Composable
private fun ControlButton(label: String, id: String, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Surface(
        modifier = Modifier.tvFocusable(id = id, onClick = onClick),
        color = skin.colors.surface
    ) {
        Text(
            text = label,
            color = skin.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** The only place that builds a [PlayerView]; carries the unstable-API opt-in. */
@androidx.annotation.OptIn(UnstableApi::class)
private fun createPlayerView(context: android.content.Context): PlayerView =
    PlayerView(context).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        setShutterBackgroundColor(android.graphics.Color.BLACK)
    }