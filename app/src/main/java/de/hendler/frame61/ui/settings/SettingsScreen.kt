package de.hendler.frame61.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hendler.frame61.design.FocusRestorer
import de.hendler.frame61.design.tvFocusable
import de.hendler.frame61.ui.skins.SkinsViewModel

/** The six selectable showcase skins (Block 4). */
private val SHOWCASE_SKINS = listOf(
    "cinema-red",
    "magic-blue",
    "midnight-gold",
    "arctic-light",
    "neon-grid",
    "kino-noir"
)

/**
 * Settings: autoplay, watched threshold, layout builder, sideload updates.
 *
 * Everything persists through DataStore, so a choice survives restarts.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenLayout: () -> Unit = {},
    onOpenInfo: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val skinsViewModel: SkinsViewModel = hiltViewModel()
    val skinsState by skinsViewModel.state.collectAsStateWithLifecycle()
    val showcase = skinsState.skins.filter { it.id in SHOWCASE_SKINS }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(text = "Einstellungen", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(12.dp))
        Text(text = "Darstellung", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            showcase.forEach { skin ->
                val selected = skin.id == skinsState.activeSkinId
                val onClick = { skinsViewModel.select(skin.id) }
                if (selected) {
                    Button(
                        onClick = onClick,
                        modifier = Modifier.tvFocusable(
                            id = "skin-${skin.id}",
                            onClick = onClick
                        )
                    ) { Text(text = skin.name) }
                } else {
                    OutlinedButton(
                        onClick = onClick,
                        modifier = Modifier.tvFocusable(
                            id = "skin-${skin.id}",
                            onClick = onClick
                        )
                    ) { Text(text = skin.name) }
                }
            }
        }
        Text(
            text = "Aktiv: ${skinsState.activeSkinId} · sofort angewendet, in DataStore gespeichert",
            style = MaterialTheme.typography.labelSmall
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Autoplay der nächsten Folge",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.autoplay,
                onCheckedChange = viewModel::setAutoplay,
                modifier = Modifier.tvFocusable(id = "settings-autoplay", onClick = {
                    viewModel.setAutoplay(!state.autoplay)
                })
            )
        }

        Text(
            text = "„Gesehen“ ab %.0f %%".format(state.watchedPercentage * 100),
            style = MaterialTheme.typography.bodyLarge
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.8f, 0.9f, 0.95f).forEach { value ->
                TextButton(onClick = { viewModel.setWatchedPercentage(value) }) {
                    Text(text = "%.0f %%".format(value * 100))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onOpenLayout,
            modifier = Modifier.tvFocusable(id = "settings-layout", onClick = onOpenLayout)
        ) { Text(text = "Layout bearbeiten") }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onOpenInfo,
            modifier = Modifier.tvFocusable(id = "settings-info", onClick = onOpenInfo)
        ) { Text(text = "Info & Quellen") }

        Spacer(Modifier.height(8.dp))

        Text(text = "Update-Quelle (Manifest-URL)", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = state.updateUrl,
            onValueChange = viewModel::setUpdateUrl,
            label = { Text("https://…/update.json") },
            modifier = Modifier.fillMaxWidth().tvFocusable(id = "settings-update-url", onClick = {})
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::checkUpdate,
                modifier = Modifier.tvFocusable(id = "settings-update-check", onClick = viewModel::checkUpdate)
            ) { Text(text = "Jetzt prüfen") }

            if (state.updateAvailable) {
                Button(
                    onClick = viewModel::installUpdate,
                    modifier = Modifier.tvFocusable(id = "settings-update-install", onClick = viewModel::installUpdate)
                ) { Text(text = "Installieren") }
            }
        }

        if (state.updateStatus.isNotBlank()) {
            Text(text = state.updateStatus, style = MaterialTheme.typography.bodySmall)
        }

        if (state.saved) {
            Text(text = "Gespeichert", style = MaterialTheme.typography.labelSmall)
        }
    }

    FocusRestorer(targetId = "settings-autoplay")
}