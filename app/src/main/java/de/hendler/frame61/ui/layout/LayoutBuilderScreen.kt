package de.hendler.frame61.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hendler.frame61.design.LocalSkin
import de.hendler.frame61.design.tvFocusable
import de.hendler.frame61.ui.home.RailKind

/**
 * Layout builder: which rails the home shows, in which order.
 *
 * The list is the 12 row types from the layout spec minus the billboard (that
 * one is always the first row). Switches are D-pad reachable; saving writes the
 * profile's layoutId, restoring the default is one button away.
 */
@Composable
fun LayoutBuilderScreen(
    viewModel: LayoutBuilderViewModel = hiltViewModel(),
    onDone: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val skin = LocalSkin.current

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(text = "Layout", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Reihenfolge und Auswahl der Startseite",
            style = MaterialTheme.typography.bodySmall,
            color = skin.colors.textSecondary
        )

        LazyColumn(
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(viewModel.selectable, key = { it.key }) { kind ->
                RailRow(
                    title = kind.title,
                    checked = kind in state.selected,
                    onToggle = { viewModel.toggle(kind) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    viewModel.save()
                    onDone()
                },
                modifier = Modifier.tvFocusable(id = "layout-save", onClick = {
                    viewModel.save()
                    onDone()
                })
            ) { Text(text = "Speichern") }

            OutlinedButton(
                onClick = { viewModel.resetDefault() },
                modifier = Modifier.tvFocusable(id = "layout-default", onClick = { viewModel.resetDefault() })
            ) { Text(text = "Standard") }

            OutlinedButton(
                onClick = { viewModel.selectAll() },
                modifier = Modifier.tvFocusable(id = "layout-all", onClick = { viewModel.selectAll() })
            ) { Text(text = "Alle") }
        }

        if (state.message.isNotBlank()) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.labelMedium,
                color = skin.colors.textSecondary
            )
        }
    }
}

@Composable
private fun RailRow(title: String, checked: Boolean, onToggle: () -> Unit) {
    val skin = LocalSkin.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(id = "rail-$title", onClick = onToggle),
        color = skin.colors.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}