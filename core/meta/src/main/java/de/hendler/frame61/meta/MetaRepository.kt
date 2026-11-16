package de.hendler.frame61.meta

import de.hendler.frame61.meta.BadgeType
import de.hendler.frame61.meta.MetaBadge
import de.hendler.frame61.meta.MetaType
import de.hendler.frame61.meta.TmdbMediaResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MetaRepository {
    suspend fun getMediaById(tmdbId: Long): TmdbMediaResponse
    suspend fun getBadges(tmdbId: Long): List<MetaBadge>
    suspend fun search(query: String, type: MetaType): List<TmdbMediaResponse>
}

interface TmdbRepository : MetaRepository {
    suspend fun getDetails(tmdbId: Long): TmdbMediaResponse
    suspend fun getCredits(tmdbId: Long): de.hendler.frame61.meta.TmdbCreditsResponse
    suspend fun getSeasons(tmdbId: Long): de.hendler.frame61.meta.TmdbSeason
    suspend fun getRecommendations(tmdbId: Long): List<TmdbMediaResponse>
    suspend fun getTrending(type: MetaType, page: Int): List<TmdbMediaResponse>
}

interface TvdbRepository : MetaRepository {
    suspend fun getEpisodeInfo(tmdbId: Long, season: Int, episode: Int): de.hendler.frame61.meta.TmdbEpisode
}

interface JustWatchRepository : MetaRepository {
    suspend fun getStreamingInfo(tmdbId: Long): de.hendler.frame61.meta.MetaBadge
}

class TmdbRepositoryImpl(
    private val baseUrl: String = "https://api.themoviedb.org/3"
) : TmdbRepository {

    override suspend fun getMediaById(tmdbId: Long): TmdbMediaResponse {
        return TmdbMediaResponse(
            id = tmdbId,
            title = "Title",
            originalTitle = "Title",
            overview = "",
            releaseDate = "2024-01-01",
            genreIds = emptyList(),
            mediaType = "movie",
            voteAverage = 0.0,
            voteCount = 0,
            popularity = 0.0
        )
    }

    override suspend fun getBadges(tmdbId: Long): List<MetaBadge> {
        return listOf(
            MetaBadge(BadgeType.TMDB, tmdbId.toString(), "https://www.themoviedb.org/movie/$tmdbId"),
            MetaBadge(BadgeType.TVDB, tmdbId.toString(), "https://www.thetvdb.com/?id=$tmdbId"),
            MetaBadge(BadgeType.JUSTWATCH, tmdbId.toString(), "https://www.justwatch.com/de/movie/$tmdbId")
        )
    }

    override suspend fun search(query: String, type: MetaType): List<TmdbMediaResponse> {
        return emptyList()
    }

    override suspend fun getDetails(tmdbId: Long): TmdbMediaResponse {
        return getMediaById(tmdbId)
    }

    override suspend fun getCredits(tmdbId: Long): de.hendler.frame61.meta.TmdbCreditsResponse {
        return de.hendler.frame61.meta.TmdbCreditsResponse(emptyList(), emptyList())
    }

    override suspend fun getSeasons(tmdbId: Long): de.hendler.frame61.meta.TmdbSeason {
        return de.hendler.frame61.meta.TmdbSeason(
            id = tmdbId,
            seasonNumber = 1,
            episodeCount = 10,
            episodes = emptyList()
        )
    }

    override suspend fun getRecommendations(tmdbId: Long): List<TmdbMediaResponse> {
        return emptyList()
    }

    override suspend fun getTrending(type: MetaType, page: Int): List<TmdbMediaResponse> {
        return emptyList()
    }
}
