package de.hendler.frame61.meta

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * A searchable metadata source.
 *
 * Implementations must never throw: [search] returns an empty list on any error,
 * so the UI always falls back to the bundled catalog.
 */
interface MetadataSource {
    val id: String
    suspend fun search(query: String, limit: Int = 20): List<SearchHit>
}

data class SearchHit(
    val externalId: String,
    val title: String,
    val year: Int? = null,
    val type: String? = null,       // "movie" | "series"
    val poster: String? = null,
    val overview: String? = null
)

/**
 * TVmaze — free, no API key, real posters and ratings.
 *
 * Verified against `https://api.tvmaze.com/singlesearch/shows?q=…` and
 * `…/search/shows?q=…` (HTTP 200, `original_untouched` poster images).
 */
class TvmazeMetadataSource(
    private val client: OkHttpClient = OkHttpClient()
) : MetadataSource {

    override val id: String = "tvmaze"

    override suspend fun search(query: String, limit: Int): List<SearchHit> =
        withContext(Dispatchers.IO) {
            val url = "https://api.tvmaze.com/search/shows?q=${enc(query)}"
            val body = runCatching { httpGet(client, url) }.getOrNull() ?: return@withContext emptyList()
            val array = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull()
                ?: return@withContext emptyList()
            array.take(limit).mapNotNull { element ->
                val show = element.jsonObject["show"]?.jsonObject ?: return@mapNotNull null
                SearchHit(
                    externalId = show["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    title = show["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    year = show["premiered"]?.jsonPrimitive?.contentOrNull?.take(4)?.toIntOrNull(),
                    type = "series",
                    poster = show["image"]?.jsonObject?.get("original")?.jsonPrimitive?.contentOrNull,
                    overview = show["summary"]?.jsonPrimitive?.contentOrNull?.let { stripTags(it) }
                )
            }
        }

    companion object {
        internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

        internal fun httpGet(client: OkHttpClient, url: String): String {
            val request = Request.Builder().url(url)
                .header("User-Agent", "frame61/1.0 (metadata)")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} für $url")
                return body
            }
        }

        internal fun stripTags(html: String): String =
            html.replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()

        internal fun enc(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8")
    }
}

/** Wikipedia (en) — free, no API key, posters for many films. */
class WikipediaMetadataSource(
    private val client: OkHttpClient = OkHttpClient()
) : MetadataSource {

    override val id: String = "wikipedia"

    override suspend fun search(query: String, limit: Int): List<SearchHit> =
        withContext(Dispatchers.IO) {
            val url = "https://en.wikipedia.org/w/api.php?action=query&format=json" +
                "&list=search&srsearch=${TvmazeMetadataSource.enc(query)}&srlimit=$limit"
            val body = runCatching { TvmazeMetadataSource.httpGet(client, url) }.getOrNull()
                ?: return@withContext emptyList()
            val root = runCatching { TvmazeMetadataSource.json.parseToJsonElement(body).jsonObject }
                .getOrNull() ?: return@withContext emptyList()
            val hits = root["query"]?.jsonObject?.get("search") as? JsonArray
                ?: return@withContext emptyList()
            hits.mapNotNull { element ->
                val page = element.jsonObject
                val title = page["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                SearchHit(
                    externalId = page["pageid"]?.jsonPrimitive?.contentOrNull ?: title,
                    title = title,
                    type = null,
                    poster = null,
                    overview = page["snippet"]?.jsonPrimitive?.contentOrNull
                        ?.let { TvmazeMetadataSource.stripTags(it) }
                )
            }
        }
}

/**
 * Bundled catalog as a [MetadataSource], so search always returns something even
 * without network.
 */
class BundledCatalogSource(
    private val repository: CatalogRepository
) : MetadataSource {

    override val id: String = "catalog"

    override suspend fun search(query: String, limit: Int): List<SearchHit> =
        withContext(Dispatchers.IO) {
            val needle = query.trim().lowercase()
            val pool = if (needle.isEmpty()) repository.topRated(limit) else repository.all()
            pool.filter { needle.isEmpty() || it.title.lowercase().contains(needle) }
                .take(limit)
                .map {
                    SearchHit(
                        externalId = it.id,
                        title = it.title,
                        year = it.year,
                        type = it.type,
                        poster = it.poster,
                        overview = it.overview
                    )
                }
        }
}

/**
 * Chains sources: the bundled catalog always answers, live sources add hits.
 * Duplicate titles are collapsed by (title, year).
 */
class ChainedMetadataSource(
    private val sources: List<MetadataSource>
) : MetadataSource {

    override val id: String = "chained"

    override suspend fun search(query: String, limit: Int): List<SearchHit> {
        val merged = LinkedHashMap<String, SearchHit>()
        for (source in sources) {
            val hits = runCatching { source.search(query, limit) }.getOrDefault(emptyList())
            for (hit in hits) {
                val key = "${hit.title.trim().lowercase()}|${hit.year ?: 0}"
                merged.putIfAbsent(key, hit)
            }
        }
        return merged.values.take(limit).toList()
    }
}