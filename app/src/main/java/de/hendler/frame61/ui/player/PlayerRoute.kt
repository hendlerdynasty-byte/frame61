package de.hendler.frame61.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.net.PlaybackDiagnostics
import de.hendler.frame61.ui.CaptchaWebViewFrame

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
    episodeNumber: Int = 0
) {
    val viewModel: PlayerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subtitleScale by viewModel.subtitleScale.collectAsStateWithLifecycle()
    val captcha by viewModel.captchaRequest.collectAsStateWithLifecycle()
    val diagnosticEvents by PlaybackDiagnostics.events.collectAsStateWithLifecycle()

    LaunchedEffect(item.id, episodeId, episodeNumber) {
        viewModel.start(
            profileId = profileId,
            titleId = item.id,
            title = item.title,
            episodeId = episodeId,
            episodeNumber = episodeNumber,
            fallbackCandidates = CatalogPlayback.candidates(item)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PlayerScreen(
            state = state.ui,
            player = viewModel.player,
            countdownSeconds = state.countdownSeconds,
            sourceNote = state.sourceNote,
            subtitleScale = subtitleScale,
            diagnosticEvents = diagnosticEvents,
            onTogglePlay = viewModel::togglePlay,
            onSeekBack = { viewModel.seekBy(-10_000L) },
            onSeekForward = { viewModel.seekBy(10_000L) },
            onCancelCountdown = viewModel::cancelCountdown,
            onRetry = viewModel::retry,
            onSelectAudio = viewModel::selectAudio,
            onSelectText = viewModel::selectText,
            onCycleSpeed = viewModel::cycleSpeed
        )

        captcha?.let { request ->
            CaptchaWebViewFrame(
                url = request.url,
                hoster = request.hoster,
                onResult = viewModel::submitCaptcha,
                onCancel = viewModel::cancelCaptcha
            )
        }
    }

    state.fatal?.let { message ->
        Surface {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                Text(text = message)
            }
        }
    }

    // No DisposableEffect here: disposing the route happens on a normal back
    // press too, and the old onDispose popped the *detail* screen as well
    // (measured on the A52: back from the player landed on home).
}