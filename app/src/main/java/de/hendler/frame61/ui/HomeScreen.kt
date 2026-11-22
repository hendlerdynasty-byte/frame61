package de.hendler.frame61.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import de.hendler.frame61.design.FocusRestorer
import de.hendler.frame61.design.PosterCard
import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.meta.EpisodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import de.hendler.frame61.design.Frame61Theme
import de.hendler.frame61.design.ProvideTvFocusRegistry
import de.hendler.frame61.design.tvFocusable
import de.hendler.frame61.ui.home.HomeViewModel
import de.hendler.frame61.ui.info.InfoScreen
import de.hendler.frame61.ui.layout.LayoutBuilderScreen
import de.hendler.frame61.ui.profile.ProfileScreen
import de.hendler.frame61.ui.search.SearchScreen
import de.hendler.frame61.ui.settings.SettingsScreen
import de.hendler.frame61.ui.skins.SkinsScreen
import de.hendler.frame61.ui.home.TvHome

@AndroidEntryPoint
class MainActivity : androidx.appcompat.app.AppCompatActivity() {

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
    }
}

/** App shell: theme, focus registry and the navigation graph. */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun Frame61(
    content: @Composable (NavController) -> Unit
) {
    // The theme is driven by assets/skins/*.json — without this the app renders
    // the hardcoded default skin (measured on the A52: default indigo focus ring,
    // default canvas instead of the brand skin).
    val skinViewModel: SkinViewModel = hiltViewModel()
    val skin by skinViewModel.skin.collectAsStateWithLifecycle()
    val uiDensity by skinViewModel.uiDensity.collectAsStateWithLifecycle()

    val activity = LocalActivity.current
    val windowSizeClass = activity?.let {
        runCatching { calculateWindowSizeClass(it) }.getOrNull()
    }
    // TV gets a fixed side rail; phones keep the bottom bar. Leanback OR the TV
    // UI mode covers both real TV devices and the Android TV emulator.
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
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // Overscan safe area: 5 % horizontally, 3 % vertically.
                    val safeH = maxWidth * 0.05f
                    val safeV = maxHeight * 0.03f
                    androidx.navigation.compose.NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(
                            start = (if (isTv) 96.dp else 0.dp) + safeH,
                            end = safeH,
                            top = safeV,
                            bottom = (if (isTv) 0.dp else 64.dp) + safeV
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
                        route = "player/{titleId}?episodeId={episodeId}&episodeNumber={episodeNumber}",
                        arguments = listOf(
                            navArgument("episodeId") {
                                type = NavType.LongType
                                defaultValue = 0L
                            },
                            navArgument("episodeNumber") {
                                type = NavType.IntType
                                defaultValue = 0
                            }
                        )
                    ) { entry ->
                        val titleId = entry.arguments?.getString("titleId").orEmpty()
                        val episodeId = entry.arguments?.getLong("episodeId") ?: 0L
                        val episodeNumber = entry.arguments?.getInt("episodeNumber") ?: 0
                        PlayerRouteFor(titleId, episodeId, episodeNumber)
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

                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route ?: "home"
                // The player is full-screen: no permanent navigation HUD.
                if (!currentRoute.startsWith("player")) {
                    val navigate: (String) -> Unit = { route ->
                        if (route == "home") {
                            // "Start" used to be a no-op: the old code skipped
                            // the navigation for the home route, so the tab did
                            // nothing from any other screen (measured on A52).
                            navController.popBackStack("home", inclusive = false)
                        } else {
                            navController.navigate(route) { popUpTo("home") }
                        }
                    }
                    if (isTv) {
                        TvSideNav(
                            currentRoute = currentRoute,
                            onNavigate = navigate,
                            modifier = Modifier
                                .align(androidx.compose.ui.Alignment.CenterStart)
                                .padding(start = safeH, top = safeV, bottom = safeV)
                        )
                    } else {
                        NavBar(
                            currentRoute = currentRoute,
                            onNavigate = navigate,
                            modifier = Modifier
                                .align(androidx.compose.ui.Alignment.BottomCenter)
                                .padding(start = safeH, end = safeH, bottom = safeV)
                        )
                    }
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
                onOpenTitle = { navController.navigate("detail/${it.id}") }
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

    val item by produceState<CatalogItem?>(initialValue = null, titleId, catalog) {
        value = withContext(Dispatchers.IO) { catalog.byId(titleId) }
    }

    // Episoden kommen aus dem lokalen Snapshot (assets/meta/<id>.json) —
    // kein Live-Call im Client, kein Namens-Matching.
    val episodes by produceState<List<EpisodeInfo>>(
        initialValue = emptyList(),
        item?.id,
        episodeSource
    ) {
        val current = item
        value = if (current != null && current.type == "series") {
            withContext(Dispatchers.IO) { episodeSource.episodes(current.id) }
        } else {
            emptyList()
        }
    }

    // Der Klick liegt auf dem Button, nicht auf der Karte: eine Card mit
    // leerem onClick schluckt auf dem TV jeden Druck, bevor der Button ihn sieht.
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (item == null) Text(text = "Detail: $titleId")
            item?.let { current ->
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.Top
                ) {
                    PosterCard(
                        posterUrl = current.poster,
                        modifier = Modifier
                            .width(120.dp)
                            .height(180.dp),
                        cornerRadius = 12.dp
                    )
                    Spacer(Modifier.width(16.dp))
                    androidx.compose.foundation.layout.Column {
                        Text(
                            text = current.title,
                            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
                        )
                        Text(text = buildString {
                            current.year?.let { y -> append(y).append("  ·  ") }
                            append(current.genres.joinToString(", "))
                        })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(text = current.overview)
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.Button(
                    onClick = { navController.navigate("player/${current.id}") },
                    modifier = Modifier.tvFocusable(id = "detail-play", onClick = {
                        navController.navigate("player/${current.id}")
                    })
                ) {
                    Text(text = "Abspielen")
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = { navController.navigate("search") },
                    modifier = Modifier.tvFocusable(id = "detail-search", onClick = {
                        navController.navigate("search")
                    })
                ) {
                    Text(text = "Ähnliche suchen")
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = { detailViewModel.toggleWatchlist(current.id) },
                    modifier = Modifier.tvFocusable(id = "detail-watchlist", onClick = {
                        detailViewModel.toggleWatchlist(current.id)
                    })
                ) {
                    Text(
                        text = if (current.id in watchlist) {
                            "Aus Meine Liste"
                        } else {
                            "In Meine Liste"
                        }
                    )
                }

                if (current.type == "series" && episodes.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(text = "Folgen (${episodes.size})")
                    episodes.take(60).forEach { ep ->
                        Text(
                            text = ep.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .tvFocusable(id = "episode-${ep.id}", onClick = {
                                    navController.navigate(
                                        "player/${current.id}?episodeId=${ep.id}" +
                                            "&episodeNumber=${ep.number}"
                                    )
                                })
                        )
                    }
                }
            } ?: run {
                Text(text = "Lade …")
            }
        }
    }
    FocusRestorer(targetId = "detail-play")
}
