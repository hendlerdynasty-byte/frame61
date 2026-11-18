package de.hendler.frame61.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.hendler.frame61.design.LocalSkin
import de.hendler.frame61.design.tvFocusable

/** The six TV destinations. Technical tools live inside Settings. */
enum class NavDestination(val route: String, val label: String) {
    HOME("home", "Start"),
    FILMS("films", "Filme"),
    SERIES("series", "Serien"),
    SEARCH("search", "Suche"),
    WATCHLIST("watchlist", "Meine Liste"),
    SETTINGS("settings", "Einstellungen")
}

/**
 * Bottom bar with the app's tools.
 *
 * Before this existed the Source Lab route was compiled but unreachable — the
 * user had no tools at all. Every entry is a focusable TV target.
 */
@Composable
fun NavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavDestination.entries.forEach { dest ->
            val selected = currentRoute == dest.route
            Text(
                text = dest.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                color = if (selected) skin.colors.primary else skin.colors.textSecondary,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .semantics { contentDescription = dest.label }
                    .tvFocusable(id = "nav-${dest.route}", onClick = { onNavigate(dest.route) })
            )
        }
    }
}

/** True when [route] is one of the tool destinations. */
fun isToolRoute(route: String?): Boolean =
    NavDestination.entries.any { it.route == route }
