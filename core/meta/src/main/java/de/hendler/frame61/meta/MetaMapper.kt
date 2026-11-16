package de.hendler.frame61.meta

/**
 * Metadata type enum
 */
enum class MetaType {
    TMDB, TVDB, JUSTWATCH, TRAKT
}

/**
 * TMDB locale for German metadata
 */
object TmdbLocale {
    val ACCEPT_LANGUAGE = "de-DE"
    val LANGUAGE = "de"
}

/**
 * Meta mapper — converts TMDB → internal models
 */
object MetaMapper {
    fun mapMedia(response: de.hendler.frame61.meta.TmdbMediaResponse): de.hendler.frame61.data.database.entities.Title {
        return de.hendler.frame61.data.database.entities.Title(
            tmdbId = response.id,
            type = if (response.mediaType == "tv") "series" else "movie",
            year = response.releaseDate.substring(0, 4).toIntOrNull() ?: 0,
            runtime = 0
        )
    }
}
