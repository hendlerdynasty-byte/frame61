package de.hendler.frame61.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import de.hendler.frame61.design.tvFocusable

/**
 * Settings: autoplay and the watched threshold.
 *
 * Both persist through DataStore, so a choice survives restarts.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(text = "Einstellungen", style = MaterialTheme.typography.headlineSmall)

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
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf(0.8f, 0.9f, 0.95f).forEach { value ->
                TextButton(onClick = { viewModel.setWatchedPercentage(value) }) {
                    Text(text = "%.0f %%".format(value * 100))
                }
            }
        }

        if (state.saved) {
            Text(text = "Gespeichert", style = MaterialTheme.typography.labelSmall)
        }
    }
}