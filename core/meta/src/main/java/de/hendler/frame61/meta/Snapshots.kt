package de.hendler.frame61.meta

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** One episode inside a metadata snapshot. */
@Serializable
data class MetaEpisode(
    val id: Long = 0,
    val season: Int = 0,
    val number: Int = 0,
    val name: String = "Folge",
    val airdate: String? = null,
    val runtime: Int? = null,
    val image: String? = null
)

/**
 * Offline metadata snapshot for one catalog id.
 *
 * The client never calls TVmaze/Wikipedia directly; it reads
 * `assets/meta/<id>.json`. `live = false` means the file carries the bundled
 * catalog fallback, not a fetched answer.
 */
@Serializable
data class MetaSnapshot(
    val schema: Int = 1,
    val id: String = "",
    val source: String = "catalog",
    val sourceId: String? = null,
    val fetchedAt: String? = null,
    val live: Boolean = false,
    val attribution: String = "",
    val title: String = "",
    val year: Int? = null,
    val type: String = "movie",
    val overview: String = "",
    val poster: String = "",
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val runtime: Int? = null,
    val episodes: List<MetaEpisode> = emptyList(),
    val backdrop: String = "",
    val cast: List<CastMember> = emptyList()
)

@Serializable
data class MetaIndexEntry(
    val id: String,
    val title: String = "",
    val year: Int? = null,
    val type: String = "movie",
    val poster: String = ""
)

@Serializable
data class MetaIndex(
    val schema: Int = 1,
    val generatedAt: String? = null,
    val entries: List<MetaIndexEntry> = emptyList()
)

/**
 * Reads the bundled metadata snapshots.
 *
 * Every catalog id has one file. A missing/broken file yields null for that id
 * only; search then falls back to the bundled catalog through
 * [SnapshotMetadataSource]. All lookups are cached after first use.
 */
class SnapshotRepository private constructor(
    private val indexLoader: () -> MetaIndex,
    private val snapshotLoader: (String) -> MetaSnapshot?
) {

    private var cachedIndex: MetaIndex? = null
    private val cachedSnapshots = HashMap<String, MetaSnapshot?>()
    private var allCache: List<MetaSnapshot>? = null

    fun index(): MetaIndex = cachedIndex ?: indexLoader().also { cachedIndex = it }

    fun generatedAt(): String? = index().generatedAt

    fun byId(id: String): MetaSnapshot? =
        cachedSnapshots.getOrPut(id) { snapshotLoader(id) }

    /** All readable snapshots, sorted by title (stable for the search index). */
    fun all(): List<MetaSnapshot> {
        allCache?.let { return it }
        val loaded = index().entries.mapNotNull { byId(it.id) }
            .sortedWith(compareBy({ it.title.lowercase() }, { it.year ?: 0 }))
        allCache = loaded
        return loaded
    }

    /**
     * Local search index over the snapshots: title, genres and overview.
     * A blank query returns the best-rated entries instead of nothing.
     */
    fun search(query: String, limit: Int = 20): List<MetaSnapshot> {
        val needle = query.trim().lowercase()
        val pool = all()
        val hits = if (needle.isEmpty()) {
            pool.sortedByDescending { it.rating ?: 0.0 }
        } else {
            pool.filter { snapshot ->
                snapshot.title.lowercase().contains(needle) ||
                    snapshot.genres.any { it.lowercase().contains(needle) } ||
                    snapshot.overview.lowercase().contains(needle)
            }.sortedWith(
                compareByDescending<MetaSnapshot> { it.title.lowercase().startsWith(needle) }
                    .thenByDescending { it.rating ?: 0.0 }
                    .thenBy { it.title.lowercase() }
            )
        }
        return hits.take(limit)
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        fun fromAssets(context: Context): SnapshotRepository {
            fun read(name: String): String? = runCatching {
                context.assets.open("meta/$name").bufferedReader().use { it.readText() }
            }.getOrNull()

            return SnapshotRepository(
                indexLoader = {
                    read("index.json")?.let { text ->
                        runCatching { json.decodeFromString<MetaIndex>(text) }.getOrNull()
                    } ?: MetaIndex()
                },
                snapshotLoader = { id ->
                    read("$id.json")?.let { text ->
                        runCatching { json.decodeFromString<MetaSnapshot>(text) }.getOrNull()
                    }
                }
            )
        }

        /** Test/CLI entry point: `meta/` directory with index.json and files. */
        fun fromDirectory(dir: File): SnapshotRepository {
            fun read(name: String): String? = runCatching {
                File(dir, name).readText()
            }.getOrNull()

            return SnapshotRepository(
                indexLoader = {
                    read("index.json")?.let { text ->
                        runCatching { json.decodeFromString<MetaIndex>(text) }.getOrNull()
                    } ?: MetaIndex()
                },
                snapshotLoader = { id ->
                    read("$id.json")?.let { text ->
                        runCatching { json.decodeFromString<MetaSnapshot>(text) }.getOrNull()
                    }
                }
            )
        }

        fun fromJson(indexJson: String, snapshots: Map<String, String>): SnapshotRepository =
            SnapshotRepository(
                indexLoader = {
                    runCatching { json.decodeFromString<MetaIndex>(indexJson) }
                        .getOrDefault(MetaIndex())
                },
                snapshotLoader = { id ->
                    snapshots[id]?.let { text ->
                        runCatching { json.decodeFromString<MetaSnapshot>(text) }.getOrNull()
                    }
                }
            )
    }
}

/**
 * Search over the snapshots. The bundled catalog is the fallback for ids whose
 * snapshot file is missing — the app never loses search because an asset broke.
 */
class SnapshotMetadataSource(
    private val snapshots: SnapshotRepository,
    private val catalog: CatalogRepository? = null
) : MetadataSource {

    override val id: String = "snapshots"

    override suspend fun search(query: String, limit: Int): List<SearchHit> =
        withContext(Dispatchers.IO) {
            val fromSnapshots = snapshots.search(query, limit).map { snapshot ->
                SearchHit(
                    externalId = snapshot.id,
                    title = snapshot.title,
                    year = snapshot.year,
                    type = snapshot.type,
                    poster = snapshot.poster.ifBlank { null },
                    overview = snapshot.overview.ifBlank { null }
                )
            }
            if (fromSnapshots.size >= limit || catalog == null) return@withContext fromSnapshots

            val known = fromSnapshots.map { it.externalId }.toMutableSet()
            val fallback = BundledCatalogSource(catalog).search(query, limit)
                .filter { known.add(it.externalId) }
            (fromSnapshots + fallback).take(limit)
        }
}

/** Episodes from the snapshot file; no network, no name matching. */
class SnapshotEpisodeSource(private val snapshots: SnapshotRepository) {

    suspend fun episodes(titleId: String): List<EpisodeInfo> = withContext(Dispatchers.IO) {
        snapshots.byId(titleId)?.episodes.orEmpty().map { episode ->
            EpisodeInfo(
                id = episode.id,
                season = episode.season,
                number = episode.number,
                name = episode.name,
                airDate = episode.airdate,
                runtime = episode.runtime,
                still = episode.image
            )
        }
    }

    /** The title the snapshot actually belongs to, so the UI can show it. */
    fun matchedShowName(titleId: String): String? = snapshots.byId(titleId)?.title
}
