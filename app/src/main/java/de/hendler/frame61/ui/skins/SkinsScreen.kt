package de.hendler.frame61.ui.skins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hendler.frame61.design.SkinJsonParser
import de.hendler.frame61.design.tvFocusable
import de.hendler.frame61.ui.skins.SkinsViewModel

/**
 * Skin picker: shows every parsed skin with its own colours. Selecting one
 * writes it to DataStore; the theme follows immediately.
 */
@Composable
fun SkinsScreen(
    viewModel: SkinsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text(text = "Skins", style = MaterialTheme.typography.headlineSmall)

        state.error?.let { Text(text = it, color = Color.Red) }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            items(state.skins, key = { it.id }) { def ->
                SkinPreviewCard(
                    id = def.id,
                    name = def.name,
                    canvas = def.canvas,
                    accent = def.accents,
                    selected = def.id == state.activeSkinId,
                    onClick = { viewModel.select(def.id) }
                )
            }
        }

        Text(
            text = "Aktiv: ${state.activeSkinId}",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun SkinPreviewCard(
    id: String,
    name: String,
    canvas: String,
    accent: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val canvasColor = SkinJsonParser.color(canvas)
    val accentColor = SkinJsonParser.color(accent)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .size(width = 180.dp, height = 130.dp)
            .tvFocusable(id = "skin-$id", onClick = onClick)
            .semantics { contentDescription = "Skin $name${if (selected) ", aktiv" else ""}" }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(canvasColor)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
            }
            if (selected) {
                Text(text = "Aktiv", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}