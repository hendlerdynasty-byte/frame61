package de.hendler.frame61.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hendler.frame61.meta.CatalogItem

/**
 * Connects [PlayerScreen] to [PlayerViewModel].
 *
 * Kept apart from the screen so the screen stays stateless and previewable; the
 * ViewModel decides what is played, resumed and when to rotate mirrors.
 */
@Composable
fun PlayerRoute(
    item: CatalogItem,
    profileId: Long,
    episodeId: Long = 0L,
    onExit: () -> Unit = {}
) {
    val viewModel: PlayerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(item.id, episodeId) {
        viewModel.start(
            profileId = profileId,
            titleId = item.id,
            episodeId = episodeId,
            candidates = CatalogPlayback.candidates(item)
        )
    }

    PlayerScreen(
        state = state.ui,
        player = viewModel.player,
        countdownSeconds = state.countdownSeconds,
        onTogglePlay = viewModel::togglePlay,
        onSeekBack = { viewModel.seekBy(-10_000L) },
        onSeekForward = { viewModel.seekBy(10_000L) },
        onCancelCountdown = viewModel::cancelCountdown,
        onRetry = viewModel::retry,
        onSelectAudio = viewModel::selectAudio,
        onSelectText = viewModel::selectText,
        onCycleSpeed = viewModel::cycleSpeed
    )

    state.fatal?.let { message ->
        Surface {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                Text(text = message)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { onExit() }
    }
}