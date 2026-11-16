package de.hendler.frame61.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.hendler.frame61.design.FocusRestorer
import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.meta.CatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import de.hendler.frame61.design.Frame61Theme
import de.hendler.frame61.design.ProvideTvFocusRegistry
import de.hendler.frame61.design.tvFocusable
import de.hendler.frame61.ui.home.HomeViewModel
import de.hendler.frame61.ui.layout.LayoutBuilderScreen
import de.hendler.frame61.ui.profile.ProfileScreen
import de.hendler.frame61.ui.search.SearchScreen
import de.hendler.frame61.ui.settings.SettingsScreen
import de.hendler.frame61.ui.skins.SkinsScreen
import de.hendler.frame61.ui.home.TvHome

@AndroidEntryPoint
class MainActivity : androidx.appcompat.app.AppCompatActivity() {

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
@Composable
fun Frame61(
    content: @Composable (NavController) -> Unit
) {
    // The theme is driven by assets/skins/*.json — without this the app renders
    // the hardcoded default skin (measured on the A52: default indigo focus ring,
    // default canvas instead of the brand skin).
    val skinViewModel: SkinViewModel = hiltViewModel()
    val skin by skinViewModel.skin.collectAsStateWithLifecycle()

    Frame61Theme(skin = skin) {
        ProvideTvFocusRegistry {
            val navController = rememberNavController()
            Box(modifier = Modifier.fillMaxSize()) {
                androidx.navigation.compose.NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.padding(bottom = 64.dp)
                ) {
                    composable("home") { HomeScreen(navController) }
                    composable("detail/{titleId}") { entry ->
                        DetailScreen(entry.arguments?.getString("titleId").orEmpty(), navController)
                    }
                    composable("source_lab") { SourceLabScreen() }
                    composable("player/{titleId}") { entry ->
                        val titleId = entry.arguments?.getString("titleId").orEmpty()
                        PlayerRouteFor(titleId, navController)
                    }
                    composable("search") { SearchScreen(onOpenTitle = { id ->
                        navController.navigate("detail/$id")
                    }) }
                    composable("skins") { SkinsScreen() }
                    composable("layout_builder") {
                        LayoutBuilderScreen(onDone = { navController.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(onOpenLayout = { navController.navigate("layout_builder") })
                    }
                    composable("profiles") {
                        ProfileScreen(onProfileChosen = { navController.popBackStack() })
                    }
                }

                val backStack by navController.currentBackStackEntryAsState()
                NavBar(
                    currentRoute = backStack?.destination?.route ?: "home",
                    onNavigate = { route ->
                        if (route != "home") navController.navigate(route) { popUpTo("home") }
                    },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                )
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
    val catalog: CatalogRepository = hiltViewModel<DetailViewModel>().catalog
    val item by produceState<CatalogItem?>(initialValue = null, titleId) {
        value = withContext(Dispatchers.IO) { catalog.byId(titleId) }
    }

    // Der Klick liegt auf dem Button, nicht auf der Karte: eine Card mit
    // leerem onClick schluckt auf dem TV jeden Druck, bevor der Button ihn sieht.
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(text = item?.title ?: "Detail: $titleId")
            item?.let {
                Text(text = buildString {
                    it.year?.let { y -> append(y).append("  ·  ") }
                    append(it.genres.joinToString(", "))
                })
                Spacer(Modifier.height(8.dp))
                Text(text = it.overview)
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.Button(
                    onClick = { navController.navigate("player/${it.id}") },
                    modifier = Modifier.tvFocusable(id = "detail-play", onClick = {
                        navController.navigate("player/${it.id}")
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
            } ?: run {
                Text(text = "Lade …")
            }
        }
    }
    FocusRestorer(targetId = "detail-play")
}
