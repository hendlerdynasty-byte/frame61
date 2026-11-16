package de.hendler.frame61.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.ui.player.PlayerRoute

/**
 * Resolves the catalog item for [titleId] and hands it to the player.
 *
 * A title that cannot be resolved produces a visible message rather than a crash.
 */
@Composable
fun PlayerRouteFor(titleId: String, navController: NavController) {
    val catalog: CatalogRepository = hiltViewModel<DetailViewModel>().catalog

    val item by produceState<CatalogItem?>(initialValue = null, titleId, catalog) {
        value = catalog.byId(titleId)
    }

    val resolved = item
    if (resolved != null) {
        PlayerRoute(item = resolved, profileId = 0L, onExit = { navController.popBackStack() })
    } else {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(text = "Titel nicht gefunden: $titleId")
            }
        }
    }
}