package de.hendler.frame61.meta

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** One curated title in the bundled catalog. */
@Serializable
data class CatalogItem(
    val id: String,
    val title: String,
    val type: String,              // "movie" | "series"
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val runtime: Int? = null,
    val overview: String = "",
    val poster: String = "",
    /**
     * Quellengruppe, aus der der Titel geerntet wurde (`serienstream`,
     * `filmpalast`, `moflix`, `aniworld`, `ard`, `bsto`, `streamcloud`).
     * Die Quote wird je Gruppe ausgewiesen — der Mischwert allein sagt nicht,
     * ob eine Gruppe oder die ganze Bibliothek trägt.
     */
    val group: String? = null,
    /**
     * Adressierung **innerhalb** der Quelle (Slug, API-Id, CRID). Nur die
     * Quelle selbst kennt ihre Schlüssel; der Titel reicht nicht, weil
     * mehrere Quellen denselben Titel führen.
     */
    val sourceKey: String? = null
)

@Serializable
data class Catalog(
    val version: Int = 1,
    val generatedAt: String? = null,
    val provenance: Map<String, String> = emptyMap(),
    val items: List<CatalogItem> = emptyList()
)

/**
 * Reads `assets/catalog/catalog.json`.
 *
 * The catalog is bundled on purpose: the app stays fully usable offline and does
 * not depend on any API key. Live sources ([MetadataSource]) are additive.
 */
class CatalogRepository private constructor(
    private val loader: () -> Catalog
) {

    private var cache: Catalog? = null

    fun all(): List<CatalogItem> = load().items

    fun byId(id: String): CatalogItem? = load().items.firstOrNull { it.id == id }

    fun byType(type: String): List<CatalogItem> = load().items.filter { it.type == type }

    fun byGenre(genre: String): List<CatalogItem> =
        load().items.filter { it.genres.any { g -> g.equals(genre, ignoreCase = true) } }

    fun genres(): List<String> = load().items.flatMap { it.genres }.distinct().sorted()

    /** Top titles by rating, then by year. */
    fun topRated(limit: Int = 10): List<CatalogItem> =
        load().items.sortedWith(
            compareByDescending<CatalogItem> { it.rating ?: 0.0 }
                .thenByDescending { it.year ?: 0 }
        ).take(limit)

    /** Newest first — the "new episodes" rail uses this. */
    fun newest(limit: Int = 10): List<CatalogItem> =
        load().items.sortedByDescending { it.year ?: 0 }.take(limit)

    /**
     * Stable pseudo-random order, so the "random" rail does not jump on every
     * recomposition. Not security relevant — this only shuffles UI rows, no
     * tokens, ids or secrets are drawn from it.
     */
    fun shuffled(seed: Int): List<CatalogItem> =
        load().items.shuffled(kotlin.random.Random(seed.toLong()))

    @Synchronized
    private fun load(): Catalog {
        cache?.let { return it }
        // The bundled catalog carries TVmaze-style markup ("<b>Breaking Bad</b>
        // follows …"). Compose would render it literally (measured on the A52),
        // so strip it once at load instead of in every screen.
        return loader()
            .let { raw ->
                raw.copy(
                    items = raw.items
                        .distinctBy { "${it.title.trim().lowercase()}|${it.year ?: 0}" }
                        .map { it.copy(overview = stripHtml(it.overview)) }
                )
            }
            .also { cache = it }
    }

    companion object {
        fun fromAsset(context: Context): CatalogRepository {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            return CatalogRepository {
                val text = context.assets.open("catalog/catalog.json").bufferedReader().use { it.readText() }
                json.decodeFromString<Catalog>(text)
            }
        }

        fun fromFile(file: File): CatalogRepository {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            return CatalogRepository { json.decodeFromString<Catalog>(file.readText()) }
        }

        fun fromJson(content: String): CatalogRepository {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            return CatalogRepository { json.decodeFromString<Catalog>(content) }
        }
    }
}

/** Removes the simple markup/entities the bundled catalog carries. */
internal fun stripHtml(text: String): String = text
    .replace(Regex("<[^>]*>"), "")
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&nbsp;", " ")
    .trim()