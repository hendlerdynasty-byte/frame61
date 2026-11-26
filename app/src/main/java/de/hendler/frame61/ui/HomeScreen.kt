package de.hendler.frame61.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import de.hendler.frame61.design.FocusRestorer
import de.hendler.frame61.design.Frame61Theme
import de.hendler.frame61.design.LocalSkin
import de.hendler.frame61.design.PosterCard
import de.hendler.frame61.design.ProvideTvFocusRegistry
import de.hendler.frame61.design.tvFocusable
import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.meta.EpisodeInfo
import de.hendler.frame61.ui.home.HomeViewModel
import de.hendler.frame61.ui.home.TvHome
import de.hendler.frame61.ui.info.InfoScreen
import de.hendler.frame61.ui.layout.LayoutBuilderScreen
import de.hendler.frame61.ui.profile.ProfileScreen
import de.hendler.frame61.ui.search.SearchScreen
import de.hendler.frame61.ui.settings.SettingsScreen
import de.hendler.frame61.ui.skins.SkinsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : androidx.appcompat.app.AppCompatActivity() {

    @Inject
    lateinit var autoResolver: de.hendler.frame61.playback.AutoPlaybackResolver

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (de.hendler.frame61.ui.player.PlayerKeyRouter.dispatch(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val content = androidx.compose.ui.platform.ComposeView(this)
        setContentView(content)
        content.setContent {
            Frame61 { navController -> HomeScreen(navController) }
        }
        // Captcha-Hoster still vorwaermen, damit der erste Titel nicht auf eine
        // Challenge wartet. Bewusst verzögert und hoechstens zwei Hosts:
        // mehrere gleichzeitige WebViews auf dem Hauptthread blockieren die
        // Nachrichten-Schleife (ANR bei 25dba40).
        content.postDelayed({
            Thread {
                runCatching { autoResolver.warmUpCaptchaHosts() }
            }.start()
        }, 5_000L)
    }
}

/** App shell: theme, focus registry and the navigation graph. */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun Frame61(
    content: @Composable (NavController) -> Unit
) {
    val skinViewModel: SkinViewModel = hiltViewModel()
    val skin by skinViewModel.skin.collectAsStateWithLifecycle()
    val uiDensity by skinViewModel.uiDensity.collectAsStateWithLifecycle()

    val activity = LocalActivity.current
    val windowSizeClass = activity?.let {
        runCatching { calculateWindowSizeClass(it) }.getOrNull()
    }
    val isTv = remember(activity) {
        activity?.let { ctx ->
            val uiMode = ctx.getSystemService(android.app.UiModeManager::class.java)
            uiMode?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
                ctx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        } == true
    }

    CompositionLocalProvider(
        LocalWindowSizeClass provides windowSizeClass,
        LocalUiDensity provides uiDensity
    ) {
        Frame61Theme(skin = skin) {
            ProvideTvFocusRegistry {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route ?: "home"
                val chrome = navigationChrome(isTv, currentRoute)

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val safeH = maxWidth * 0.05f
                    val safeV = maxHeight * 0.03f
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(
                            start = (if (chrome == NavigationChrome.SIDE) 96.dp else 0.dp) + safeH,
                            end = safeH,
                            top = (if (chrome == NavigationChrome.TOP) 64.dp else 0.dp) + safeV,
                            bottom = safeV
                        )
                    ) {
                        composable("home") { HomeScreen(navController) }
                        composable("films") {
                            BrowseScreen(
                                type = "movie",
                                title = "Filme",
                                onOpenTitle = { id -> navController.navigate("detail/$id") }
                            )
                        }
                        composable("series") {
                            BrowseScreen(
                                type = "series",
                                title = "Serien",
                                onOpenTitle = { id -> navController.navigate("detail/$id") }
                            )
                        }
                        composable("watchlist") {
                            WatchlistScreen(onOpenTitle = { id -> navController.navigate("detail/$id") })
                        }
                        composable("detail/{titleId}") { entry ->
                            DetailScreen(entry.arguments?.getString("titleId").orEmpty(), navController)
                        }
                        composable("source_lab") { SourceLabScreen() }
                        composable(
                            route = "player/{titleId}?episodeId={episodeId}&episodeNumber={episodeNumber}&season={season}",
                            arguments = listOf(
                                navArgument("episodeId") {
                                    type = NavType.LongType
                                    defaultValue = 0L
                                },
                                navArgument("episodeNumber") {
                                    type = NavType.IntType
                                    defaultValue = 0
                                },
                                navArgument("season") {
                                    type = NavType.IntType
                                    defaultValue = 1
                                }
                            )
                        ) { entry ->
                            val titleId = entry.arguments?.getString("titleId").orEmpty()
                            val episodeId = entry.arguments?.getLong("episodeId") ?: 0L
                            val episodeNumber = entry.arguments?.getInt("episodeNumber") ?: 0
                            val season = entry.arguments?.getInt("season") ?: 1
                            PlayerRouteFor(titleId, episodeId, episodeNumber, season)
                        }
                        composable("search") { SearchScreen(onOpenTitle = { id ->
                            navController.navigate("detail/$id")
                        }) }
                        composable("skins") { SkinsScreen() }
                        composable("layout_builder") {
                            LayoutBuilderScreen(onDone = { navController.popBackStack() })
                        }
                        composable("settings") {
                            SettingsScreen(
                                onOpenLayout = { navController.navigate("layout_builder") },
                                onOpenInfo = { navController.navigate("info") },
                                onOpenSources = { navController.navigate("source_lab") }
                            )
                        }
                        composable("info") {
                            InfoScreen(onBack = { navController.popBackStack() })
                        }
                        composable("profiles") {
                            ProfileScreen(onProfileChosen = { navController.popBackStack() })
                        }
                    }

                    val navigate: (String) -> Unit = { route ->
                        if (route == "home") {
                            navController.popBackStack("home", inclusive = false)
                        } else {
                            navController.navigate(route) { popUpTo("home") }
                        }
                    }
                    when (chrome) {
                        NavigationChrome.SIDE -> TvSideNav(
                            currentRoute = currentRoute,
                            onNavigate = navigate,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = safeH, top = safeV, bottom = safeV)
                        )
                        NavigationChrome.TOP -> NavBar(
                            currentRoute = currentRoute,
                            onNavigate = navigate,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(start = safeH, end = safeH, top = safeV)
                        )
                        NavigationChrome.NONE -> Unit
                    }
                }
            }
        }
    }
}

/** Home backed by the bundled catalog. */
@Composable
fun HomeScreen(navController: NavController) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.error != null -> Text(text = state.error.orEmpty())
            else -> TvHome(
                content = state.content,
                onOpenTitle = { navController.navigate("detail/${it.id}") },
                onPrefetch = { viewModel.prefetch(it) }
            )
        }
    }
}

@Composable
fun DetailScreen(titleId: String, navController: NavController) {
    val detailViewModel: DetailViewModel = hiltViewModel()
    val catalog: CatalogRepository = detailViewModel.catalog
    val episodeSource = detailViewModel.episodes
    val watchlist by detailViewModel.watchlist.collectAsStateWithLifecycle()
    val profileId by detailViewModel.profileId.collectAsStateWithLifecycle()

    val itemResult by produceState<Result<CatalogItem?>?>(initialValue = null, titleId, catalog) {
        value = runCatching {
            withContext(Dispatchers.IO) { catalog.byId(titleId) }
        }
    }
    val current = itemResult?.getOrNull()

    val episodeResult by produceState<Result<List<EpisodeInfo>>?>(
        initialValue = null,
        current?.id,
        episodeSource
    ) {
        val item = current
        value = if (item != null && item.type.equals("series", ignoreCase = true)) {
            runCatching { withContext(Dispatchers.IO) { episodeSource.episodes(item.id) } }
        } else {
            Result.success(emptyList())
        }
    }
    val episodes = episodeResult?.getOrDefault(emptyList()).orEmpty()

    val similarResult by produceState<Result<List<CatalogItem>>?>(
        initialValue = null,
        current?.id,
        catalog
    ) {
        val item = current
        value = item?.let {
            runCatching { withContext(Dispatchers.IO) { similarTitles(it, catalog.all()) } }
        }
    }

    var progressRefresh by remember(current?.id) { mutableStateOf(0) }
    LifecycleResumeEffect(current?.id) {
        progressRefresh++
        onPauseOrDispose { }
    }
    val progress by produceState<Map<Long, Float>>(
        initialValue = emptyMap(),
        current?.id,
        episodes,
        profileId,
        progressRefresh
    ) {
        val item = current
        value = if (item != null) {
            runCatching { detailViewModel.episodeProgress(item.id, profileId) }
                .getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            itemResult == null -> DetailMessage("Lade …")
            itemResult?.isFailure == true -> DetailMessage("Details konnten nicht geladen werden.")
            current == null -> DetailMessage("Titel nicht gefunden.")
            else -> DetailContent(
                catalogItem = current,
                episodes = episodes,
                episodeResult = episodeResult,
                similarResult = similarResult,
                progress = progress,
                watchlist = watchlist,
                onOpenTitle = { id -> navController.navigate("detail/$id") },
                onSearch = { navController.navigate("search") },
                onToggleWatchlist = { detailViewModel.toggleWatchlist(current.id) },
                onPlay = { target -> navController.navigate(playerRoute(current, target)) },
                onPrefetch = { episode ->
                    detailViewModel.prefetch(current, episode.number, episode.season)
                },
                onPrefetchTitle = { detailViewModel.prefetch(it) },
                onPrefetchPlay = { target ->
                    detailViewModel.prefetch(current, target.episodeNumber, target.season)
                }
            )
        }
    }
}

@Composable
private fun DetailContent(
    catalogItem: CatalogItem,
    episodes: List<EpisodeInfo>,
    episodeResult: Result<List<EpisodeInfo>>?,
    similarResult: Result<List<CatalogItem>>?,
    progress: Map<Long, Float>,
    watchlist: Set<String>,
    onOpenTitle: (String) -> Unit,
    onSearch: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onPlay: (DetailPlaybackTarget) -> Unit,
    onPrefetch: (EpisodeInfo) -> Unit,
    onPrefetchTitle: (CatalogItem) -> Unit,
    onPrefetchPlay: (DetailPlaybackTarget) -> Unit
) {
    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    var selectedSeason by remember(catalogItem.id) { mutableStateOf<Int?>(null) }
    var selectedEpisodeId by remember(catalogItem.id) { mutableStateOf<Long?>(null) }
    val activeSeason = selectedSeason ?: seasons.firstOrNull() ?: 1
    val filteredEpisodes = remember(episodes, activeSeason) {
        episodes.filter { it.season == activeSeason }
            .sortedWith(compareBy<EpisodeInfo> { it.number }.thenBy { it.id })
    }
    val selectedEpisode = filteredEpisodes.firstOrNull { it.id == selectedEpisodeId }
        ?: filteredEpisodes.firstOrNull()
    val target = remember(catalogItem, episodes, activeSeason, selectedEpisodeId) {
        detailPlaybackTarget(catalogItem, episodes, activeSeason, selectedEpisodeId)
    }
    val similar = similarResult?.getOrNull().orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "hero") {
            DetailHero(
                item = catalogItem,
                target = target,
                onPlay = { onPlay(target) },
                onPrefetchPlay = { onPrefetchPlay(target) }
            )
        }
        item(key = "actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleWatchlist,
                    modifier = Modifier.tvFocusable(
                        id = "detail-watchlist",
                        onClick = onToggleWatchlist
                    )
                ) {
                    Text(if (catalogItem.id in watchlist) "Aus Meine Liste" else "In Meine Liste")
                }
                OutlinedButton(
                    onClick = onSearch,
                    modifier = Modifier.tvFocusable(id = "detail-search", onClick = onSearch)
                ) {
                    Text("Ähnliche suchen")
                }
            }
        }
        item(key = "synopsis") {
            DetailTextSection(
                title = "Synopsis",
                text = catalogItem.overview.ifBlank { "Keine Synopsis vorhanden." }
            )
        }
        item(key = "genres") {
            DetailTextSection(
                title = "Genres",
                text = catalogItem.genres.filter { it.isNotBlank() }.joinToString(", ")
                    .ifBlank { "Keine Genres hinterlegt." }
            )
        }
        item(key = "cast") {
            DetailTextSection(
                title = "Besetzung",
                text = catalogItem.cast.map { it.name }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                    .ifBlank { "Keine Besetzung hinterlegt." }
            )
        }

        if (catalogItem.type.equals("series", ignoreCase = true)) {
            item(key = "episodes-heading") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    SectionTitle("Folgen")
                    if (seasons.isNotEmpty()) {
                        SeasonRow(
                            seasons = seasons,
                            activeSeason = activeSeason,
                            episodes = episodes,
                            onSelect = { season ->
                                selectedSeason = season
                                selectedEpisodeId = null
                            },
                            onPrefetch = onPrefetch
                        )
                    }
                    selectedEpisode?.let { episode ->
                        SelectedEpisodeBar(
                            episode = episode,
                            onPlay = { onPlay(target) },
                            onPrefetchPlay = { onPrefetchPlay(target) }
                        )
                    }
                    when {
                        episodeResult == null -> EmptyLine("Folgen werden geladen …")
                        episodeResult?.isFailure == true -> EmptyLine("Folgen konnten nicht geladen werden.")
                        filteredEpisodes.isEmpty() -> EmptyLine("Keine Folgen für diese Staffel hinterlegt.")
                    }
                }
            }
            items(
                items = filteredEpisodes,
                key = { "${it.season}:${it.number}:${it.id}" }
            ) { episode ->
                EpisodeRow(
                    episode = episode,
                    selected = episode.id == selectedEpisode?.id,
                    progress = progress[episode.id],
                    onSelect = {
                        selectedSeason = episode.season
                        selectedEpisodeId = episode.id
                    },
                    onPrefetch = { onPrefetch(episode) }
                )
            }
        }

        item(key = "similar-heading") {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                SectionTitle("Ähnliche Titel")
            }
        }
        when {
            similarResult == null -> item(key = "similar-status") { EmptyLine("Ähnliche Titel werden geladen …") }
            similarResult?.isFailure == true -> item(key = "similar-status") {
                EmptyLine("Ähnliche Titel konnten nicht geladen werden.")
            }
            similar.isEmpty() -> item(key = "similar-status") {
                EmptyLine("Keine ähnlichen Titel im Katalog.")
            }
            else -> item(key = "similar-row") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(similar, key = { it.id }) { similarItem ->
                        SimilarCard(
                            item = similarItem,
                            onOpen = { onOpenTitle(similarItem.id) },
                            onPrefetch = { onPrefetchTitle(similarItem) }
                        )
                    }
                }
            }
        }
    }
    FocusRestorer(targetId = "detail-play")
}

@Composable
private fun DetailHero(
    item: CatalogItem,
    target: DetailPlaybackTarget,
    onPlay: () -> Unit,
    onPrefetchPlay: () -> Unit
) {
    val skin = LocalSkin.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
    ) {
        val posterWidth = if (maxWidth >= 640.dp) 150.dp else 108.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(skin.colors.primary.copy(alpha = 0.45f), skin.colors.surface)
                    )
                )
        ) {
            item.backdrop?.takeIf { it.isNotBlank() }?.let { backdrop ->
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                skin.colors.canvas.copy(alpha = 0.55f),
                                skin.colors.canvas
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PosterCard(
                    posterUrl = item.poster,
                    modifier = Modifier
                        .width(posterWidth)
                        .aspectRatio(2f / 3f),
                    contentDescription = "Poster zu ${item.title}"
                )
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildList {
                            add(if (item.type.equals("series", ignoreCase = true)) "Serie" else "Film")
                            item.year?.let { add(it.toString()) }
                            item.rating?.let { add("★ ${String.format(Locale.ROOT, "%.1f", it)}") }
                            add(runtimeLabel(item.runtime))
                        }.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = onPlay,
                        modifier = Modifier.tvFocusable(
                            id = "detail-play",
                            onClick = onPlay,
                            onFocusChanged = { focused -> if (focused) onPrefetchPlay() }
                        )
                    ) {
                        Text(playbackLabel(item, target))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTextSection(title: String, text: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionTitle(title)
        Spacer(Modifier.height(6.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun SeasonRow(
    seasons: List<Int>,
    activeSeason: Int,
    episodes: List<EpisodeInfo>,
    onSelect: (Int) -> Unit,
    onPrefetch: (EpisodeInfo) -> Unit
) {
    val skin = LocalSkin.current
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(seasons, key = { it }) { season ->
            val active = season == activeSeason
            val firstEpisode = episodes.firstOrNull { it.season == season }
            Text(
                text = "Staffel $season",
                style = MaterialTheme.typography.labelLarge,
                color = if (active) skin.colors.canvas else skin.colors.textPrimary,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) skin.colors.focus else skin.colors.surface)
                    .tvFocusable(
                        id = "detail-season-$season",
                        onClick = { onSelect(season) },
                        onFocusChanged = { focused ->
                            if (focused) firstEpisode?.let(onPrefetch)
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun SelectedEpisodeBar(
    episode: EpisodeInfo,
    onPlay: () -> Unit,
    onPrefetchPlay: () -> Unit
) {
    val skin = LocalSkin.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(skin.colors.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Ausgewählt", style = MaterialTheme.typography.labelMedium)
            Text(
                text = episode.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = onPlay,
            modifier = Modifier.tvFocusable(
                id = "detail-play-selected",
                onClick = onPlay,
                onFocusChanged = { focused -> if (focused) onPrefetchPlay() }
            )
        ) {
            Text("Abspielen")
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeInfo,
    selected: Boolean,
    progress: Float?,
    onSelect: () -> Unit,
    onPrefetch: () -> Unit
) {
    val skin = LocalSkin.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) skin.colors.primary.copy(alpha = 0.18f) else skin.colors.surface
            )
            .tvFocusable(
                id = "episode-${episode.id}",
                onClick = onSelect,
                onFocusChanged = { focused -> if (focused) onPrefetch() }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EpisodeThumbnail(episode)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = episode.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "S%02d E%02d".format(episode.season, episode.number),
                style = MaterialTheme.typography.labelMedium
            )
            val details = listOfNotNull(
                episode.airDate?.takeIf { it.isNotBlank() },
                episode.runtime?.takeIf { it > 0 }?.let { "$it Min." }
            ).joinToString("  ·  ")
            if (details.isNotBlank()) {
                Text(text = details, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
            EpisodeProgress(progress)
            Spacer(Modifier.height(4.dp))
            Text(
                text = episodeProgressLabel(progress),
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(text = "✓", color = skin.colors.focus)
        }
    }
}

@Composable
private fun EpisodeThumbnail(episode: EpisodeInfo) {
    val skin = LocalSkin.current
    Box(
        modifier = Modifier
            .width(96.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(skin.colors.surface)
    ) {
        episode.still?.takeIf { it.isNotBlank() }?.let { still ->
            AsyncImage(
                model = still,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } ?: Text(
            text = "S%02d E%02d".format(episode.season, episode.number),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun EpisodeProgress(progress: Float?) {
    val skin = LocalSkin.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(skin.colors.textPrimary.copy(alpha = 0.25f))
    ) {
        val fraction = progress?.coerceIn(0f, 1f) ?: 0f
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(skin.colors.focus)
            )
        }
    }
}

@Composable
private fun SimilarCard(
    item: CatalogItem,
    onOpen: () -> Unit,
    onPrefetch: () -> Unit
) {
    Column(Modifier.width(120.dp)) {
        PosterCard(
            posterUrl = item.poster,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(2f / 3f),
            contentDescription = "${item.title} ${item.year ?: ""}".trim(),
            focusId = "similar-${item.id}",
            onClick = onOpen,
            onFocus = onPrefetch
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun DetailMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, textAlign = TextAlign.Center)
    }
}
