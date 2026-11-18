package de.hendler.frame61.search

import android.util.Log
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.meta.MetadataSource
import de.hendler.frame61.meta.SearchHit
import de.hendler.frame61.scraper.engine.PackInterpreter
import de.hendler.frame61.scraper.engine.PackLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Search across all active index packs in parallel.
 *
 * A pack hit is mapped onto a bundled catalog title (normalized title match).
 * That keeps the detail route valid while the source layer stays invisible.
 * Failures are logged only; the search keeps the snapshot/catalog results.
 */
@Singleton
class AggregatedIndexSource @Inject constructor(
    private val packLoader: PackLoader,
    private val interpreter: PackInterpreter,
    private val catalog: CatalogRepository,
    private val prefs: ProfileDataStore
) : MetadataSource {

    override val id: String = "indizes"

    override suspend fun search(query: String, limit: Int): List<SearchHit> =
        withContext(Dispatchers.IO) {
            val needle = normalize(query)
            if (needle.isBlank()) return@withContext emptyList()
            val disabled = runCatching { prefs.disabledSources.first() }.getOrDefault(emptySet())
            val packs = packLoader.loadAllPacks().filter { it.id !in disabled }
            val titles = catalog.all()

            val hits = coroutineScope {
                packs.map { pack ->
                    async {
                        val started = System.currentTimeMillis()
                        val result = runCatching {
                            withTimeoutOrNull(TIMEOUT_MS) { interpreter.search(pack, query) }
                                .orEmpty()
                        }.getOrDefault(emptyList())
                        Log.i(
                            TAG,
                            "${pack.id}: ${result.size} hit(s) in " +
                                "${System.currentTimeMillis() - started} ms" +
                                if (result.isEmpty()) " (no result)" else ""
                        )
                        result
                    }
                }.awaitAll().flatten()
            }

            hits.mapNotNull { hit ->
                val match = titles.firstOrNull { item ->
                    val title = normalize(item.title)
                    title == normalize(hit.title) ||
                        title.startsWith(normalize(hit.title)) ||
                        normalize(hit.title).startsWith(title)
                } ?: return@mapNotNull null
                SearchHit(
                    externalId = match.id,
                    title = match.title,
                    year = match.year,
                    type = match.type,
                    poster = match.poster,
                    overview = match.overview,
                    lang = "de",
                    runtime = match.runtime
                )
            }.distinctBy { it.externalId }.take(limit)
        }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9äöüß]+"), " ")
        .trim()

    companion object {
        private const val TAG = "Frame61Search"
        private const val TIMEOUT_MS = 4_000L
    }
}
