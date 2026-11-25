package de.hendler.frame61.playback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.hendler.frame61.resolver.HosterOutcome
import de.hendler.frame61.resolver.HosterPack
import de.hendler.frame61.resolver.HosterPackValidator
import de.hendler.frame61.resolver.HosterResolver
import de.hendler.frame61.resolver.MirrorCandidate
import de.hendler.frame61.resolver.OkHttpBodyFetcher
import de.hendler.frame61.scraper.engine.PackInterpreter
import de.hendler.frame61.scraper.engine.PackLoader
import de.hendler.frame61.scraper.engine.SourcePack
import de.hendler.frame61.scraper.engine.StreamResult
import de.hendler.frame61.ui.player.CatalogPlayback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * End-to-end resolver for the legal-free ARD Mediathek source.
 *
 * Flow: title → ARD search (show grouping) → episodes → stream URL →
 * `HosterResolver` with the `direct` pack (pass-through for HLS URLs).
 *
 * The API is keyless but undocumented; every step is defensive. A failure
 * returns an empty list, so the player shows an honest error instead of
 * showing a black screen. `verified` for the pack is only set by the worker
 * after a recorded/live check.
 */
@Singleton
class ArdPlaybackResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packLoader: PackLoader,
    private val interpreter: PackInterpreter
) {

    private val directPack: HosterPack? by lazy {
        runCatching {
            context.assets.open("hosters/direct.json").bufferedReader().use { it.readText() }
        }.getOrNull()?.let { HosterPackValidator.parse(it) }
    }

    private val hosterResolver: HosterResolver? by lazy {
        directPack?.let { HosterResolver(listOf(it), OkHttpBodyFetcher()) }
    }

    suspend fun resolve(
        title: String,
        episodeNumber: Int = 0,
        season: Int = 1
    ): List<MirrorCandidate> =
        withContext(Dispatchers.IO) {
            val pack = packLoader.loadAllPacks().firstOrNull { it.id == PACK_ID }
                ?: return@withContext emptyList()
            val show = findShow(pack, title) ?: return@withContext emptyList()
            val episodes = runCatching { interpreter.episodes(pack, show.id) }
                .getOrDefault(emptyList())
            // Block 8: zuerst (Staffel, Nummer), dann nur die Nummer — die
            // ARD-Grouping liefert Teaser der ersten Staffel, deren Staffel
            // nicht immer ausgewiesen ist. Ohne Staffel waehlte die Suche bei
            // Mehrstaffel-Serien die Folge aus Staffel 1.
            val episodeId = EpisodeSelection.select(
                episodes.map { EpisodeTarget(it.id, it.season, it.episode) },
                season = season,
                number = episodeNumber
            )?.id

            val streamTarget = episodeId ?: show.id
            val streams = runCatching { interpreter.streams(pack, show.id, streamTarget) }
                .getOrDefault(emptyList())
            if (streams.isEmpty()) return@withContext emptyList()

            streams
                .sortedByDescending { it.quality?.filter(Char::isDigit)?.toIntOrNull() ?: 0 }
                .mapNotNull { stream ->
                    toCandidate(show, episodeId, episodeNumber, stream)
                }
        }

    private fun findShow(pack: SourcePack, title: String): de.hendler.frame61.scraper.engine.SearchResult? {
        val hits = runCatching { interpreter.search(pack, title) }.getOrDefault(emptyList())
            .filter { it.title.isNotBlank() && it.id.isNotBlank() }
        if (hits.isEmpty()) return null
        val ordered = hits.sortedWith(
            compareByDescending<de.hendler.frame61.scraper.engine.SearchResult> {
                it.title.equals(title, ignoreCase = true)
            }.thenByDescending {
                it.title.startsWith(title, ignoreCase = true)
            }.thenByDescending {
                it.title.contains(title, ignoreCase = true)
            }
        )
        // Der Katalog kann einen anderen Titel tragen als der Treffer; dann
        // zählt der beste Treffer, nicht „keine Quelle“.
        return ordered.firstOrNull { it.title.contains(title, ignoreCase = true) }
            ?: ordered.firstOrNull()
    }

    private suspend fun toCandidate(
        show: de.hendler.frame61.scraper.engine.SearchResult,
        episodeId: String?,
        episodeNumber: Int,
        stream: StreamResult
    ): MirrorCandidate? {
        if (!stream.url.startsWith("https://")) return null
        val resolvedUrl = runCatching {
            when (val outcome = hosterResolver?.resolve(stream.url)) {
                is HosterOutcome.Resolved -> outcome.url
                else -> stream.url
            }
        }.getOrDefault(stream.url)

        return MirrorCandidate(
            sourceId = "ard",
            titleId = CatalogPlayback.stableId(show.id),
            episodeId = episodeId?.let { CatalogPlayback.stableId(it) }
                ?: episodeNumber.toLong(),
            url = resolvedUrl,
            lang = stream.lang?.take(2) ?: "de",
            quality = stream.quality ?: "auto",
            hosterId = directPack?.id,
            healthScore = 1.0,
            legalFree = true,
            requestHeaders = mapOf(
                "User-Agent" to HosterResolver.DEFAULT_USER_AGENT,
                "Referer" to stream.url
            ),
            // Gate 1 (1.6.0): Das ARD-API-Item ist die Identitätsangabe.
            identityTitle = show.title.takeIf { it.isNotBlank() },
            identityYear = show.year,
            identityKind = "api"
        )
    }

    companion object {
        const val PACK_ID = "ard-mediathek"
    }
}
