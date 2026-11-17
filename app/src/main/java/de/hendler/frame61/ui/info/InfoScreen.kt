package de.hendler.frame61.ui.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.BuildConfig
import de.hendler.frame61.design.LocalSkin
import de.hendler.frame61.design.tvFocusable
import de.hendler.frame61.meta.SnapshotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InfoState(
    val generatedAt: String? = null,
    val snapshotCount: Int = 0,
    val liveCount: Int = 0
)

/**
 * Attribution and data provenance.
 *
 * TVmaze requires attribution for its data (CC BY-SA 4.0). The app reads only
 * bundled snapshots, so this screen also states when they were generated.
 * Loading happens off the main thread: 46 files are small, but disk IO on the
 * main thread is still an ANR risk.
 */
@HiltViewModel
class InfoViewModel @Inject constructor(
    private val snapshots: SnapshotRepository
) : ViewModel() {

    private val _state = MutableStateFlow(InfoState())
    val state: StateFlow<InfoState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = InfoState(
                generatedAt = snapshots.generatedAt(),
                snapshotCount = snapshots.index().entries.size,
                liveCount = snapshots.all().count { it.live }
            )
        }
    }
}

@Composable
fun InfoScreen(
    onBack: () -> Unit = {},
    viewModel: InfoViewModel = hiltViewModel()
) {
    val skin = LocalSkin.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(text = "Info & Quellen", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Frame61 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Metadaten: TVmaze (CC BY-SA 4.0) — https://www.tvmaze.com/api",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Ergänzend: Wikipedia (CC BY-SA 4.0) — https://www.wikipedia.org",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Katalog und Poster: eigene Zusammenstellung, offline mitgeliefert.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Snapshots: ${state.snapshotCount} Titel, davon " +
                "${state.liveCount} live abgerufen. Stand: " +
                (state.generatedAt ?: "unbekannt"),
            style = MaterialTheme.typography.bodyMedium,
            color = skin.colors.textSecondary
        )
        Text(
            text = "Der Client ruft keine Metadaten-API auf. Die Snapshots werden " +
                "vorab von tools/fetch/tvmaze_snapshots.py erzeugt.",
            style = MaterialTheme.typography.bodyMedium,
            color = skin.colors.textSecondary
        )
        Spacer(Modifier.height(24.dp))

        TextButton(
            onClick = onBack,
            modifier = Modifier.tvFocusable(id = "info-back", onClick = onBack)
        ) {
            Text(text = "Zurück")
        }
    }
}
