package de.hendler.frame61.playback

import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.resolver.ContentIdentity
import de.hendler.frame61.scraper.engine.EpisodeResult
import de.hendler.frame61.scraper.engine.PackHttpClient
import de.hendler.frame61.scraper.engine.PackInterpreter
import de.hendler.frame61.scraper.engine.SearchResult
import de.hendler.frame61.scraper.engine.SourcePack
import de.hendler.frame61.scraper.engine.StreamResult
import java.net.URI

/** Aufgelöster, quellenbelegter aniworld-Zielpunkt. */
internal data class AniworldResolution(
    val hit: SearchResult,
    val episode: EpisodeResult,
    val watchUrl: String,
    val streams: List<StreamResult>
)

/** Austauschbarer HTTP-Zugang, damit die Zielregel ohne Netzwerk testbar ist. */
internal interface AniworldEpisodeAccess {
    suspend fun search(pack: SourcePack, title: String, cookies: String?): List<SearchResult>
    suspend fun episodes(pack: SourcePack, titleId: String, cookies: String?): List<EpisodeResult>
    suspend fun streams(
        pack: SourcePack,
        titleId: String,
        episodeId: String,
        cookies: String?,
        season: Int
    ): List<StreamResult>
}

private class InterpreterAniworldAccess(private val interpreter: PackInterpreter) : AniworldEpisodeAccess {
    override suspend fun search(pack: SourcePack, title: String, cookies: String?): List<SearchResult> =
        interpreter.search(pack, title, cookies = cookies)

    override suspend fun episodes(pack: SourcePack, titleId: String, cookies: String?): List<EpisodeResult> =
        interpreter.episodes(pack, titleId, cookies)

    override suspend fun streams(
        pack: SourcePack,
        titleId: String,
        episodeId: String,
        cookies: String?,
        season: Int
    ): List<StreamResult> = interpreter.streams(pack, titleId, episodeId, cookies, season)
}

/**
 * Gemeinsamer strenger Zielpfad für aniworld in Normalauflösung, Prefetch und
 * Alternativkette. Ein Suchtreffer allein ist keine Episodenadresse: Erst die
 * Episodenliste liefert die interne ID und die tatsächliche Staffel.
 */
internal class AniworldEpisodeTargetResolver(
    private val access: AniworldEpisodeAccess
) {
    constructor(interpreter: PackInterpreter) : this(InterpreterAniworldAccess(interpreter))
    suspend fun resolve(
        pack: SourcePack,
        item: CatalogItem?,
        season: Int,
        number: Int,
        cookies: String? = null
    ): AniworldResolution? {
        if (pack.id != ANIWORLD_PACK_ID || season <= 0 || number <= 0) return null
        val title = item?.title?.trim().orEmpty()
        if (title.isBlank()) return null
        val sourceKey = item?.takeIf { it.group == ANIWORLD_GROUP }?.sourceKey
        val hit = selectHit(access.search(pack, title, cookies), title, sourceKey, item?.year)
            ?: return null
        val titleId = hit.id.trim()
        if (titleId.isBlank()) return null
        val episodes = access.episodes(pack, titleId, cookies)
        val selected = EpisodeSelection.selectExact(
            episodes.map { EpisodeTarget(it.id, it.season, it.episode) },
            season,
            number
        ) ?: return null
        val sourceEpisode = episodes.singleOrNull {
            it.id == selected.id && it.season == selected.season && it.episode == selected.number
        } ?: return null
        val internalId = sourceEpisode.id.removePrefix("episode-").trim()
        if (internalId.isBlank() || internalId.startsWith("episode-")) return null
        val watchUrl = sourceEpisode.watchUrl?.trim().orEmpty()
        if (!matchesStreamTarget(pack, titleId, internalId, season, watchUrl)) return null

        val streams = access.streams(pack, titleId, internalId, cookies, season)
        return AniworldResolution(
            hit = hit,
            episode = sourceEpisode.copy(id = internalId),
            watchUrl = watchUrl,
            streams = streams
        )
    }

    private fun selectHit(
        hits: List<SearchResult>,
        title: String,
        sourceKey: String?,
        catalogYear: Int?
    ): SearchResult? {
        val usable = hits.filter { it.id.isNotBlank() }
            .filter { catalogYear == null || it.year == null || catalogYear == it.year }
        if (!sourceKey.isNullOrBlank()) {
            val wantedKey = keyOf(sourceKey)
            return usable.singleOrNull { hit ->
                keyOf(hit.id) == wantedKey || hit.url?.let(::keyOf) == wantedKey
            }
        }
        val wantedTitle = ContentIdentity.normalize(title)
        return usable.singleOrNull { hit ->
            if (wantedTitle.isBlank()) {
                hit.title.trim().equals(title.trim(), ignoreCase = true)
            } else {
                ContentIdentity.normalize(hit.title) == wantedTitle
            }
        }
    }

    private fun keyOf(value: String): String =
        value.trim().trimEnd('/').substringAfterLast('/').substringBefore('?').substringBefore('#')

    /** Der Episodenlink muss genau zum Stream-Endpunkt aus ID und Staffel passen. */
    private fun matchesStreamTarget(
        pack: SourcePack,
        titleId: String,
        episodeId: String,
        season: Int,
        watchUrl: String
    ): Boolean {
        val rule = pack.streams ?: return false
        if (rule.endpoint.isNullOrBlank() || watchUrl.isBlank()) return false
        val expected = PackHttpClient.buildUrl(
            pack,
            rule,
            mapOf("titleId" to titleId, "episodeId" to episodeId, "season" to season.toString())
        )
        val expectedUri = runCatching { URI(expected) }.getOrNull() ?: return false
        val actualUri = runCatching { URI(watchUrl) }.getOrNull() ?: return false
        return expectedUri.scheme.equals(actualUri.scheme, ignoreCase = true) &&
            expectedUri.host.equals(actualUri.host, ignoreCase = true) &&
            expectedUri.port == actualUri.port &&
            expectedUri.path.trimEnd('/') == actualUri.path.trimEnd('/')
    }

    internal companion object {
        const val ANIWORLD_PACK_ID = "aniworld"
        const val ANIWORLD_GROUP = "aniworld"
    }
}
