package de.hendler.frame61.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.design.FocusRestorer
import de.hendler.frame61.design.LocalSkin
import de.hendler.frame61.design.PosterCard
import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.meta.CatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Filme/Serien: a simple 2:3 grid over the bundled catalog.
 *
 * The grid is adaptive, so a phone shows fewer columns and a TV more — same
 * colors, same PosterCard, only the column count changes.
 */
@Composable
fun BrowseScreen(
    type: String,
    title: String,
    onOpenTitle: (String) -> Unit
) {
    val viewModel: DetailViewModel = hiltViewModel()
    val titles by produceState(initialValue = emptyList<CatalogItem>(), type, viewModel) {
        value = withContext(Dispatchers.IO) { viewModel.catalog.byType(type) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(titles, key = { it.id }) { item ->
                BrowseCard(item = item, onClick = { onOpenTitle(item.id) })
            }
        }
    }

    titles.firstOrNull()?.let { FocusRestorer(targetId = "browse-${it.id}") }
}

@Composable
private fun BrowseCard(item: CatalogItem, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Column(modifier = Modifier.fillMaxWidth()) {
        PosterCard(
            posterUrl = item.poster,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            cornerRadius = skin.layout.cardRadius,
            contentDescription = "${item.title} ${item.year ?: ""}".trim(),
            focusId = "browse-${item.id}",
            onClick = onClick
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    prefs: ProfileDataStore
) : ViewModel() {

    private val _items = MutableStateFlow<List<CatalogItem>>(emptyList())
    val items: StateFlow<List<CatalogItem>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.watchlist.collect { ids ->
                _items.value = catalog.all().filter { it.id in ids }
            }
        }
    }
}

@Composable
fun WatchlistScreen(onOpenTitle: (String) -> Unit) {
    val viewModel: WatchlistViewModel = hiltViewModel()
    val titles by viewModel.items.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(text = "Meine Liste", style = MaterialTheme.typography.headlineSmall)
        if (items.isEmpty()) {
            Text(
                text = "Meine Liste ist leer. Auf einer Detailseite „In Meine Liste“ wählen.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(titles, key = { it.id }) { item ->
                    BrowseCard(item = item, onClick = { onOpenTitle(item.id) })
                }
            }
        }
    }

    titles.firstOrNull()?.let { FocusRestorer(targetId = "browse-${it.id}") }
}
