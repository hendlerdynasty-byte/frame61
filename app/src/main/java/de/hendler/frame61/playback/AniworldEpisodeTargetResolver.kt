package de.hendler.frame61.playback

import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.net.PlaybackDiagnostics
import de.hendler.frame61.resolver.ContentIdentity
import de.hendler.frame61.scraper.engine.EpisodePage
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
    suspend fun episodePage(pack: SourcePack, titleId: String, cookies: String?): EpisodePage?
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

    override suspend fun episodePage(pack: SourcePack, titleId: String, cookies: String?): EpisodePage? =
        interpreter.episodesWithTitle(pack, titleId, cookies)

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
 * Alternativkette. Ein gültiger Katalog-sourceKey adressiert die Serie direkt;
 * die Episodenseite liefert Titel, interne IDs und tatsächliche Staffel in einem
 * Abruf. Nur ohne gültigen sourceKey wird exakt gesucht.
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
        if (pack.id != ANIWORLD_PACK_ID || season <= 0 || number <= 0) {
            return fail("Pack oder Episodenadresse ungültig")
        }
        val directTitleId = item
            ?.takeIf { it.group == ANIWORLD_GROUP }
            ?.sourceKey
            ?.let(::validTitleId)
        val title = item?.title?.trim().orEmpty()
        val selectedHit = if (directTitleId == null) {
            if (title.isBlank()) return fail("Titel fehlt")
            selectHit(access.search(pack, title, cookies), title, item?.year)
                ?: return fail("kein exakter Suchtreffer")
        } else {
            null
        }
        val titleId = directTitleId ?: selectedHit?.id?.trim().orEmpty()
        if (titleId.isBlank()) return fail("sourceKey fehlt")
        val page = access.episodePage(pack, titleId, cookies) ?: return fail("Episodenseite nicht lesbar")
        val sourceTitle = page.title?.trim().orEmpty()
        if (sourceTitle.isBlank()) return fail("Quellentitel fehlt")
        val hit = SearchResult(
            id = titleId,
            title = sourceTitle,
            // Der Seitentitel ist die Quellenangabe; nur das Jahr darf aus dem
            // Katalog ergänzt werden, wenn die Quelle keines nennt.
            year = page.year ?: selectedHit?.year ?: item?.year
        )
        val selected = EpisodeSelection.selectExact(
            page.episodes.map { EpisodeTarget(it.id, it.season, it.episode) },
            season,
            number
        ) ?: return fail("Staffel/Folge nicht gefunden")
        val sourceEpisode = page.episodes.singleOrNull {
            it.id == selected.id && it.season == selected.season && it.episode == selected.number
        } ?: return fail("Episodenzeile nicht eindeutig")
        val internalId = sourceEpisode.id.removePrefix("episode-").trim()
        if (internalId.isBlank() || internalId.startsWith("episode-")) return fail("interne ID ungültig")
        val watchUrl = sourceEpisode.watchUrl?.trim().orEmpty()
        if (!matchesStreamTarget(pack, titleId, internalId, season, watchUrl)) {
            return fail("Watch-Adresse passt nicht zum Stream-Ziel")
        }

        val streams = access.streams(pack, titleId, internalId, cookies, season)
        PlaybackDiagnostics.record(
            "RESOLVER",
            "aniworld Ziel bestätigt",
            details = mapOf(
                "sourceKey" to titleId,
                "staffel" to season.toString(),
                "folge" to number.toString(),
                "interneId" to internalId,
                "streams" to streams.size.toString()
            )
        )
        return AniworldResolution(
            hit = hit,
            episode = sourceEpisode.copy(id = internalId),
            watchUrl = watchUrl,
            streams = streams
        )
    }

    private fun fail(reason: String): AniworldResolution? {
        PlaybackDiagnostics.record("RESOLVER", "aniworld Ziel abgelehnt: $reason")
        return null
    }

    private fun selectHit(
        hits: List<SearchResult>,
        title: String,
        catalogYear: Int?
    ): SearchResult? {
        val usable = hits.filter { it.id.isNotBlank() }
            .filter { catalogYear == null || it.year == null || catalogYear == it.year }
        val wantedTitle = ContentIdentity.normalize(title)
        return usable.singleOrNull { hit ->
            if (wantedTitle.isBlank()) {
                hit.title.trim().equals(title.trim(), ignoreCase = true)
            } else {
                ContentIdentity.normalize(hit.title) == wantedTitle
            }
        }
    }

    /** Sicherer einzelner titleId-Pfadsegment; keine URL, Query oder Traversal. */
    private fun validTitleId(value: String): String? {
        val id = value.trim()
        if (id.isBlank() || id == "." || id == "..") return null
        return id.takeUnless { candidate ->
            candidate.any { it.isWhitespace() || it in "/\\?#" }
        }
    }

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
