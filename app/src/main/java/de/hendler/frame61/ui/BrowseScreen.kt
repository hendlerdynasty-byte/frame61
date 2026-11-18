package de.hendler.frame61.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.data.ResumeRepository
import de.hendler.frame61.design.FocusRestorer
import de.hendler.frame61.design.LocalSkin
import de.hendler.frame61.design.PosterCard
import de.hendler.frame61.design.tvFocusable
import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.ui.player.CatalogPlayback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BrowseState(
    val titles: List<CatalogItem> = emptyList(),
    val total: Int = 0,
    val genres: List<String> = emptyList(),
    val decades: List<Int> = emptyList(),
    val selectedGenre: String? = null,
    val selectedDecade: Int? = null,
    val progress: Map<String, Float> = emptyMap()
)

/** Catalog grid with genre/decade filters and per-title resume progress. */
@Composable
fun BrowseScreen(
    type: String,
    title: String,
    onOpenTitle: (String) -> Unit
) {
    val viewModel: BrowseViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(type) { viewModel.load(type) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshProgress()
        onPauseOrDispose { }
    }
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, state.titles.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                if (last >= state.titles.lastIndex - 24) viewModel.loadMore()
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        if (state.genres.isNotEmpty()) {
            FilterRow(
                label = "Genre",
                options = state.genres,
                selected = state.selectedGenre,
                allLabel = "Alle Genres",
                onSelect = viewModel::setGenre
            )
        }
        if (state.decades.isNotEmpty()) {
            FilterRow(
                label = "Jahrzehnt",
                options = state.decades.map { "${it}er" },
                selected = state.selectedDecade?.let { "${it}er" },
                allLabel = "Alle Jahre",
                onSelect = { label ->
                    viewModel.setDecade(label?.removeSuffix("er")?.toIntOrNull())
                }
            )
        }
        Text(
            text = "${state.total} Titel",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            state = gridState,
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(state.titles, key = { it.id }) { item ->
                BrowseCard(
                    item = item,
                    progress = state.progress[item.id],
                    onClick = { onOpenTitle(item.id) }
                )
            }
        }
    }

    state.titles.firstOrNull()?.let { FocusRestorer(targetId = "browse-${it.id}") }
}

@Composable
private fun FilterRow(
    label: String,
    options: List<String>,
    selected: String?,
    allLabel: String,
    onSelect: (String?) -> Unit
) {
    val skin = LocalSkin.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf<String?>(null).plus(options).forEach { option ->
            val text = option ?: allLabel
            val active = selected == option
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) skin.colors.canvas else skin.colors.textPrimary,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) skin.colors.focus else skin.colors.surface)
                    .tvFocusable(id = "filter-$label-$text", onClick = { onSelect(option) })
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun BrowseCard(item: CatalogItem, progress: Float?, onClick: () -> Unit) {
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
            onClick = onClick,
            progress = progress
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
class BrowseViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val prefs: ProfileDataStore,
    private val resume: ResumeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    private var base: List<CatalogItem> = emptyList()
    private var loadedType: String? = null
    private var progressByTitle: Map<Long, Float> = emptyMap()
    private var visibleCount = PAGE_SIZE

    fun load(type: String) {
        if (loadedType == type) return
        loadedType = type
        visibleCount = PAGE_SIZE
        viewModelScope.launch(Dispatchers.IO) {
            base = catalog.byType(type)
            publish()
        }
    }

    fun setGenre(genre: String?) {
        _state.value = _state.value.copy(selectedGenre = genre)
        visibleCount = PAGE_SIZE
        publish()
    }

    fun setDecade(decade: Int?) {
        _state.value = _state.value.copy(selectedDecade = decade)
        visibleCount = PAGE_SIZE
        publish()
    }

    fun loadMore() {
        if (visibleCount >= _state.value.total) return
        visibleCount += PAGE_SIZE
        publish()
    }

    fun refreshProgress() {
        viewModelScope.launch(Dispatchers.IO) {
            val profileId = prefs.currentProfileId.first()
            progressByTitle = resume.progressByTitle(profileId)
            publish()
        }
    }

    private fun publish() {
        val current = _state.value
        val filtered = base.filter { item ->
            (current.selectedGenre == null || item.genres.any { it.equals(current.selectedGenre, true) }) &&
                (current.selectedDecade == null || (item.year?.div(10)?.times(10) == current.selectedDecade))
        }
        val progress = base.associate { item ->
            item.id to (progressByTitle[CatalogPlayback.stableId(item.id)] ?: 0f)
        }
        _state.value = current.copy(
            titles = filtered.take(visibleCount),
            total = filtered.size,
            genres = base.flatMap { it.genres }.distinct().sorted(),
            decades = base.mapNotNull { it.year?.div(10)?.times(10) }.distinct().sortedDescending(),
            progress = progress
        )
    }

    private companion object {
        const val PAGE_SIZE = 120
    }
}

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val prefs: ProfileDataStore,
    private val resume: ResumeRepository
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

    suspend fun progress(): Map<String, Float> {
        val profileId = prefs.currentProfileId.first()
        val stored = resume.progressByTitle(profileId)
        return catalog.all().associate { item ->
            item.id to (stored[CatalogPlayback.stableId(item.id)] ?: 0f)
        }
    }
}

@Composable
fun WatchlistScreen(onOpenTitle: (String) -> Unit) {
    val viewModel: WatchlistViewModel = hiltViewModel()
    val titles by viewModel.items.collectAsStateWithLifecycle()
    val progress by produceState(initialValue = emptyMap<String, Float>(), titles) {
        value = withContext(Dispatchers.IO) { viewModel.progress() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(text = "Meine Liste", style = MaterialTheme.typography.headlineSmall)
        if (titles.isEmpty()) {
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
                    BrowseCard(
                        item = item,
                        progress = progress[item.id],
                        onClick = { onOpenTitle(item.id) }
                    )
                }
            }
        }
    }

    titles.firstOrNull()?.let { FocusRestorer(targetId = "browse-${it.id}") }
}
