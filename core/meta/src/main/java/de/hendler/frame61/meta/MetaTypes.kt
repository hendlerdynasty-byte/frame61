package de.hendler.frame61.meta

import kotlinx.serialization.Serializable

/**
 * TMDB metadata response types
 */
@Serializable
data class TmdbMediaResponse(
    val id: Long,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val releaseDate: String,
    val genreIds: List<Long>,
    val mediaType: String,        // "movie" | "tv"
    val voteAverage: Double,
    val voteCount: Int,
    val popularity: Double
)

@Serializable
data class TmdbCreditsResponse(
    val cast: List<TmdbCast>,
    val crew: List<TmdbCrew>
)

@Serializable
data class TmdbCast(
    val id: Long,
    val name: String,
    val character: String,
    val order: Int
)

@Serializable
data class TmdbCrew(
    val id: Long,
    val name: String,
    val job: String
)

@Serializable
data class TmdbSeason(
    val id: Long,
    val seasonNumber: Int,
    val episodeCount: Int,
    val episodes: List<TmdbEpisode>
)

@Serializable
data class TmdbEpisode(
    val id: Long,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val name: String,
    val overview: String,
    val runtime: Int,
    val stillPath: String
)

/**
 * TVDB badge type
 */
enum class BadgeType {
    TMDB, TVDB, JUSTWATCH
}

@Serializable
data class MetaBadge(
    val type: BadgeType,
    val id: String,
    val url: String
)
