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
    onOpenInfo: () -> Unit = {},
    onOpenSources: () -> Unit = {}
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

        Spacer(Modifier.height(12.dp))
        ChoiceRow(
            label = "Dichte",
            options = listOf("0" to "Kompakt", "1" to "Standard", "2" to "Geräumig"),
            selected = state.uiDensity.toString(),
            onSelect = { viewModel.setUiDensity(it.toInt()) }
        )
        ChoiceRow(
            label = "Untertitel-Optik",
            options = listOf("0.85" to "Klein", "1.0" to "Normal", "1.2" to "Groß"),
            selected = state.subtitleScale.toString(),
            onSelect = { viewModel.setSubtitleScale(it.toFloat()) }
        )

        Spacer(Modifier.height(16.dp))
        Text(text = "Wiedergabe", style = MaterialTheme.typography.titleMedium)
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

        ChoiceRow(
            label = "Hoster-Präferenz",
            options = listOf(
                "" to "Automatisch",
                "voe" to "Voe",
                "streamtape" to "Streamtape",
                "vidmoly" to "Vidmoly",
                "vidoza" to "Vidoza",
                "mixdrop" to "Mixdrop",
                "luluvdo" to "Luluvdo",
                "loadx" to "Loadx",
                "streamhide" to "Streamhide",
                "filemoon" to "Filemoon",
                "doodstream" to "Doodstream"
            ),
            selected = state.hosterPriority,
            onSelect = viewModel::setHosterPriority
        )
        ChoiceRow(
            label = "Qualität",
            options = listOf(
                "auto" to "Auto",
                "1080" to "1080p",
                "720" to "720p",
                "480" to "480p"
            ),
            selected = state.preferredQuality,
            onSelect = viewModel::setPreferredQuality
        )

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

        Spacer(Modifier.height(16.dp))
        Text(text = "Erweitert", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Aktive Quellen (${state.sources.count { it.id !in state.disabledSources }} " +
                "von ${state.sources.size})",
            style = MaterialTheme.typography.bodyLarge
        )
        state.sources.forEach { source ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = when {
                        source.verified -> "verifiziert"
                        source.status != null -> source.status
                        else -> "unverifiziert"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
                Switch(
                    checked = source.id !in state.disabledSources,
                    onCheckedChange = { viewModel.setSourceEnabled(source.id, it) },
                    modifier = Modifier.tvFocusable(
                        id = "source-${source.id}",
                        onClick = {
                            viewModel.setSourceEnabled(
                                source.id,
                                source.id in state.disabledSources
                            )
                        }
                    )
                )
            }
        }
        Text(
            text = "Fetch-Status",
            style = MaterialTheme.typography.bodyLarge
        )
        state.sources.filter { it.status != null }.forEach { source ->
            Text(
                text = "${source.name}: ${source.status} · ${source.lastCheck ?: "–"}" +
                    (source.reason?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall
            )
        }
        OutlinedButton(
            onClick = onOpenSources,
            modifier = Modifier.tvFocusable(id = "settings-sources", onClick = onOpenSources)
        ) { Text(text = "Source Lab öffnen") }

        Spacer(Modifier.height(8.dp))
        Text(text = "Info", style = MaterialTheme.typography.titleMedium)

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

/** One labelled, persisted choice row (density, subtitle size, hoster, quality). */
@Composable
private fun ChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Text(text = label, style = MaterialTheme.typography.bodyLarge)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, text) ->
            val active = value == selected
            val onClick = { onSelect(value) }
            val focusId = "choice-$label-$value"
            if (active) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.tvFocusable(id = focusId, onClick = onClick)
                ) { Text(text = text) }
            } else {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.tvFocusable(id = focusId, onClick = onClick)
                ) { Text(text = text) }
            }
        }
    }
}