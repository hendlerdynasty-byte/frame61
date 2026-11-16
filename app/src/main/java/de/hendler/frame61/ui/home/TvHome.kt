package de.hendler.frame61.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import de.hendler.frame61.design.LocalFrame61Typography
import de.hendler.frame61.design.FocusRestorer
import de.hendler.frame61.design.LocalSkin
import de.hendler.frame61.design.tvFocusable
import de.hendler.frame61.meta.CatalogItem

/**
 * One rail of the home screen. [RailKind] mirrors the 12 row types from the
 * layout builder spec; the order comes from the profile's `layoutId`.
 */
enum class RailKind(val key: String, val title: String) {
    BILLBOARD("billboard", "Im Fokus"),
    CONTINUE_WATCHING("continue_watching", "Weiter schauen"),
    BECAUSE_YOU_WATCHED("because_you_watched", "Weil du … geschaut hast"),
    TOP10("top10", "Top 10"),
    NEW_EPISODES("new_episodes", "Neue Folgen"),
    GENRE_RAIL("genre_rail", "Genres"),
    COLLECTION("collection", "Sammlungen"),
    FREE_LEGAL("free_legal", "Kostenlos & legal"),
    AIRING_TODAY("airing_today", "Heute neu"),
    RANDOM("random", "Zufällig für dich"),
    SOURCE_RAIL("source_rail", "Quellen"),
    MARATHON("marathon", "Marathon")
}

/** Everything the home screen renders, precomputed so the composables stay dumb. */
data class HomeContent(
    val billboard: CatalogItem?,
    val rails: List<HomeRail>
)

data class HomeRail(
    val kind: RailKind,
    val items: List<CatalogItem>
)

/**
 * TV home: billboard plus rails.
 *
 * Uses the Compose foundation lazy lists. `androidx.tv:foundation:1.0.0` ships
 * no lazy list classes at all (verified: the AAR contains 3 classes), so
 * `TvLazyColumn` does not exist in this version; TV focus comes from
 * `Modifier.focusGroup()` on each rail plus focusable cards.
 *
 * Every card is focusable; the ring is drawn inside the card bounds and
 * therefore cannot be clipped by the scrolling rail.
 */
@Composable
fun TvHome(
    content: HomeContent,
    onOpenTitle: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = skin.layout.gutterDp,
            end = skin.layout.gutterDp,
            bottom = skin.layout.gutterDp * 2
        ),
        verticalArrangement = Arrangement.spacedBy(skin.layout.gutterDp)
    ) {
        content.billboard?.let { billboard ->
            item(key = "billboard") {
                Billboard(item = billboard, onClick = { onOpenTitle(billboard) })
            }
        }

        content.rails.forEach { rail ->
            if (rail.items.isEmpty()) return@forEach
            item(key = rail.kind.key) {
                RailHeader(title = rail.kind.title)
                TvRail(rail = rail, onOpenTitle = onOpenTitle)
            }
        }
    }

    // First focusable element: the billboard when present, else the first card.
    // Without this the screen starts with no focus at all — no ring, D-pad lost.
    FocusRestorer(targetId = firstFocusableId(content))
}

/** Stable id of the element that should hold focus when the screen appears. */
fun firstFocusableId(content: HomeContent): String {
    content.billboard?.let { return "billboard-${it.id}" }
    val first = content.rails.firstOrNull { it.items.isNotEmpty() }?.items?.firstOrNull()
    return first?.let { "card-${it.id}" } ?: "home-root"
}

@Composable
private fun RailHeader(title: String) {
    val typography = LocalFrame61Typography.current
    androidx.compose.material3.Text(
        text = title,
        style = TextStyle(fontSize = typography.rowTitle),
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun TvRail(
    rail: HomeRail,
    onOpenTitle: (CatalogItem) -> Unit
) {
    val skin = LocalSkin.current
    LazyRow(
        modifier = Modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(skin.layout.gutterDp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(rail.items, key = { it.id }) { item ->
            PosterCard(item = item, onClick = { onOpenTitle(item) })
        }
    }
}

@Composable
private fun PosterCard(
    item: CatalogItem,
    onClick: () -> Unit
) {
    val skin = LocalSkin.current
    val typography = LocalFrame61Typography.current
    val width = skin.layout.rowHeightDp * skin.layout.cardAspectRatio

    // The wrapper owns focus AND click. The Card must not be clickable itself:
    // The surface is deliberately not clickable: a clickable surface is a
    // focus target of its own and used to steal focus from this box, so no ring
    // was ever drawn (verified on the A52).
    Box(
        modifier = Modifier
            .width(width)
            .height(skin.layout.rowHeightDp)
            .tvFocusable(
                id = "card-${item.id}",
                focusedColor = skin.colors.focus,
                onClick = onClick
            )
            .semantics { contentDescription = "${item.title} ${item.year ?: ""}".trim() }
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Artwork(item = item, modifier = Modifier.fillMaxSize())
                androidx.compose.material3.Text(
                    text = item.title,
                    style = TextStyle(fontSize = typography.cardTitle),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun Billboard(
    item: CatalogItem,
    onClick: () -> Unit
) {
    val skin = LocalSkin.current
    val typography = LocalFrame61Typography.current

    // Ring on the wrapper; the surface stays unclickable so it cannot take focus.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(skin.layout.billboardHeightDp)
            .tvFocusable(
                id = "billboard-${item.id}",
                focusedColor = skin.colors.focus,
                onClick = onClick
            )
            .semantics { contentDescription = item.title }
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Artwork(item = item, modifier = Modifier.fillMaxSize())
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                ) {
                    androidx.compose.material3.Text(
                        text = item.title,
                        style = TextStyle(fontSize = typography.billboardTitle)
                    )
                    androidx.compose.material3.Text(
                        text = buildString {
                            item.year?.let { append(it).append("  ·  ") }
                            if (item.genres.isNotEmpty()) append(item.genres.take(2).joinToString(", "))
                        },
                        style = TextStyle(
                            fontSize = typography.caption,
                            color = skin.colors.textSecondary
                        )
                    )
                }
            }
        }
    }
}

/** Real artwork, or a gradient placeholder — never a broken image. */
@Composable
private fun Artwork(item: CatalogItem, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.layout.cardRadius)
    if (item.poster.isNotBlank()) {
        AsyncImage(
            model = item.poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape)
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(skin.colors.primary.copy(alpha = 0.35f), skin.colors.surface)
                    )
                )
        )
    }
}

/** Builds the home content from the catalog. Pure function — unit testable. */
object HomeContentBuilder {

    val DEFAULT_LAYOUT: Set<RailKind> = linkedSetOf(
        RailKind.CONTINUE_WATCHING,
        RailKind.TOP10,
        RailKind.NEW_EPISODES,
        RailKind.BECAUSE_YOU_WATCHED,
        RailKind.GENRE_RAIL,
        RailKind.FREE_LEGAL,
        RailKind.RANDOM,
        RailKind.MARATHON
    )

    fun build(
        catalog: List<CatalogItem>,
        layout: Set<RailKind> = DEFAULT_LAYOUT,
        seed: Int = 0,
        continueWatching: List<CatalogItem> = emptyList()
    ): HomeContent {
        val byId = catalog.associateBy { it.id }

        fun rail(kind: RailKind): List<CatalogItem> = when (kind) {
            RailKind.BILLBOARD -> emptyList()
            RailKind.CONTINUE_WATCHING -> continueWatching.mapNotNull { byId[it.id] ?: it }
            RailKind.TOP10 -> catalog
                .sortedWith(
                    compareByDescending<CatalogItem> { it.rating ?: 0.0 }
                        .thenByDescending { it.year ?: 0 }
                ).take(10)
            RailKind.NEW_EPISODES -> catalog.filter { it.type == "series" }
                .sortedByDescending { it.year ?: 0 }.take(12)
            RailKind.BECAUSE_YOU_WATCHED -> catalog.filter { it.type == "movie" }.take(12)
            RailKind.GENRE_RAIL -> catalog.take(12)
            RailKind.FREE_LEGAL -> catalog.filter { it.type == "movie" }.take(12)
            RailKind.COLLECTION -> catalog.shuffled(kotlin.random.Random(seed.toLong())).take(12)
            RailKind.AIRING_TODAY -> catalog.sortedByDescending { it.year ?: 0 }.take(12)
            RailKind.RANDOM -> catalog.shuffled(kotlin.random.Random(seed.toLong())).take(12)
            RailKind.SOURCE_RAIL -> catalog.take(12)
            RailKind.MARATHON -> catalog.filter { it.type == "series" }.take(20)
        }

        val rails = layout
            .filter { it != RailKind.BILLBOARD }
            .map { HomeRail(it, rail(it)) }
            .filter { it.items.isNotEmpty() }

        return HomeContent(billboard = catalog.firstOrNull(), rails = rails)
    }
}