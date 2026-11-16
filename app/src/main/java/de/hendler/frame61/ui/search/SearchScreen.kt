package de.hendler.frame61.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.hendler.frame61.design.LocalSkin
import de.hendler.frame61.design.tvFocusable

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onOpenTitle: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val skin = LocalSkin.current

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text(text = "Suche", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.onQueryChange(it) },
            label = { Text("Titel, Genre, Jahr") },
            modifier = Modifier.fillMaxWidth().tvFocusable(id = "search-input", onClick = { })
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Quellen: ${state.sources.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = skin.colors.textSecondary
            )
        }

        if (state.isLoading) {
            CircularProgressIndicator()
        }

        state.error?.let { Text(text = it, color = skin.colors.textSecondary) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.hits, key = { it.externalId }) { hit ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .tvFocusable(id = "hit-${hit.externalId}", onClick = {
                            onOpenTitle(hit.externalId)
                        })
                        .semantics { contentDescription = hit.title }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!hit.poster.isNullOrBlank()) {
                            AsyncImage(
                                model = hit.poster,
                                contentDescription = null,
                                modifier = Modifier.height(88.dp).width(66.dp)
                            )
                        }
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(text = hit.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = buildString {
                                    hit.year?.let { append(it).append("  ·  ") }
                                    hit.type?.let { append(it) }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = skin.colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }

}