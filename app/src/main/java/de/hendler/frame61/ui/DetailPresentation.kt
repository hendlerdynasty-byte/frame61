package de.hendler.frame61.ui

import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.meta.EpisodeInfo
import kotlin.math.abs
import kotlin.math.roundToInt

/** Exakte Adresse, die Abspielen und Fokus-Prefetch gemeinsam verwenden. */
data class DetailPlaybackTarget(
    val episodeId: Long = 0L,
    val episodeNumber: Int = 0,
    val season: Int = 1
) {
    val hasEpisode: Boolean get() = episodeId != 0L || episodeNumber > 0
}

/** Wählt die explizit markierte Folge oder die erste Folge der gewählten Staffel. */
fun detailPlaybackTarget(
    item: CatalogItem,
    episodes: List<EpisodeInfo>,
    selectedSeason: Int?,
    selectedEpisodeId: Long?
): DetailPlaybackTarget {
    if (!item.type.equals("series", ignoreCase = true)) return DetailPlaybackTarget()
    val season = selectedSeason ?: episodes.minOfOrNull { it.season } ?: 1
    val inSeason = episodes.filter { it.season == season }.ifEmpty { episodes }
    val selected = inSeason.firstOrNull { it.id == selectedEpisodeId }
        ?: inSeason.minWithOrNull(compareBy<EpisodeInfo> { it.number }.thenBy { it.id })
        ?: return DetailPlaybackTarget()
    return DetailPlaybackTarget(selected.id, selected.number, selected.season)
}

/** Player-Route aus genau denselben Werten wie [DetailPlaybackTarget]. */
fun playerRoute(item: CatalogItem, target: DetailPlaybackTarget): String {
    if (!item.type.equals("series", ignoreCase = true) || !target.hasEpisode) {
        return "player/${item.id}"
    }
    return "player/${item.id}?episodeId=${target.episodeId}" +
        "&episodeNumber=${target.episodeNumber}&season=${target.season}"
}

fun playbackLabel(item: CatalogItem, target: DetailPlaybackTarget): String =
    if (item.type.equals("series", ignoreCase = true) && target.hasEpisode) {
        "S%02d E%02d abspielen".format(target.season, target.episodeNumber)
    } else {
        "Abspielen"
    }

fun runtimeLabel(minutes: Int?): String = when {
    minutes == null || minutes <= 0 -> "Laufzeit unbekannt"
    minutes < 60 -> "$minutes Min."
    minutes % 60 == 0 -> "${minutes / 60} Std."
    else -> "${minutes / 60} Std. ${minutes % 60} Min."
}

fun episodeProgressLabel(progress: Float?): String = when {
    progress == null -> "Noch nicht gesehen"
    progress.coerceIn(0f, 1f) >= 0.9f -> "Gesehen"
    else -> "${(progress.coerceIn(0f, 1f) * 100).roundToInt()} % gesehen"
}

private data class ScoredTitle(val item: CatalogItem, val score: Int, val yearDistance: Int)

/** Stabile lokale Empfehlung: Genres, Typ, Jahresnähe, Bewertung, Titel. */
fun similarTitles(
    current: CatalogItem,
    catalog: List<CatalogItem>,
    limit: Int = 12
): List<CatalogItem> {
    if (limit <= 0) return emptyList()
    val genres = current.genres.map { it.trim().lowercase() }.toSet()
    return catalog.asSequence()
        .filter { it.id != current.id }
        .map { candidate ->
            val shared = genres.intersect(candidate.genres.map { it.trim().lowercase() }.toSet()).size
            val currentYear = current.year
            val candidateYear = candidate.year
            val yearDistance = if (currentYear != null && candidateYear != null) {
                abs(currentYear - candidateYear)
            } else {
                Int.MAX_VALUE
            }
            val score = shared * 10 +
                (if (current.type.equals(candidate.type, ignoreCase = true)) 2 else 0) +
                (if (yearDistance <= 5) 1 else 0)
            ScoredTitle(candidate, score, yearDistance)
        }
        .filter { it.score > 0 }
        .sortedWith(
            compareByDescending<ScoredTitle> { it.score }
                .thenBy { it.yearDistance }
                .thenByDescending { it.item.rating ?: 0.0 }
                .thenBy { it.item.title.lowercase() }
        )
        .take(limit)
        .map { it.item }
        .toList()
}
