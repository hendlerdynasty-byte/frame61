package de.hendler.frame61.meta

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One episode as shown in the detail screen. */
data class EpisodeInfo(
    val id: Long,
    val season: Int,
    val number: Int,
    val name: String,
    val airDate: String? = null,
    val runtime: Int? = null,
    val still: String? = null
) {
    val label: String get() = "S%02d E%02d · %s".format(season, number, name)
}

/**
 * TVmaze episode lists — free, no API key, no scraping.
 *
 * `GET /singlesearch/shows?q=<title>&embed=episodes` returns the best-matching
 * show with its full episode list (confirmed against https://www.tvmaze.com/api,
 * 2026-11-16). The match is name-based; a wrong first hit is possible for
 * ambiguous titles — the detail screen shows the matched show name so the user
 * sees what was loaded.
 *
 * [parse] is pure and fixture-tested; the network path never throws. Parsing
 * uses safe casts only: `JsonElement.jsonObject` throws for `null` values, and
 * real episodes may carry `"image": null` — one such episode must not wipe the
 * whole list (that bug was caught by the fixture test).
 */
class TvmazeEpisodeSource(
    private val client: okhttp3.OkHttpClient = okhttp3.OkHttpClient()
) {

    suspend fun episodes(title: String): List<EpisodeInfo> = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext emptyList()
        val url = "https://api.tvmaze.com/singlesearch/shows" +
            "?q=${TvmazeMetadataSource.enc(title)}&embed=episodes"
        val body = runCatching { TvmazeMetadataSource.httpGet(client, url) }.getOrNull()
            ?: return@withContext emptyList()
        parse(body)
    }

    /** Matched show name, so the UI can show which show the episodes belong to. */
    fun matchedShowName(body: String): String? = runCatching {
        (json.parseToJsonElement(body) as? JsonObject)?.get("name").stringOrNull()
    }.getOrNull()

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(body: String): List<EpisodeInfo> = runCatching {
            val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
            val episodes = (root["_embedded"] as? JsonObject)
                ?.get("episodes") as? JsonArray ?: return emptyList()
            episodes.mapNotNull { element ->
                val e = element as? JsonObject ?: return@mapNotNull null
                val id = e["id"].longOrNull() ?: return@mapNotNull null
                EpisodeInfo(
                    id = id,
                    season = e["season"].intOrNull() ?: 0,
                    number = e["number"].intOrNull() ?: 0,
                    name = e["name"].stringOrNull() ?: "Folge",
                    airDate = e["airdate"].stringOrNull(),
                    runtime = e["runtime"].intOrNull(),
                    still = (e["image"] as? JsonObject)?.get("medium").stringOrNull()
                )
            }
        }.getOrDefault(emptyList())

        private fun JsonElement?.stringOrNull(): String? =
            (this as? JsonPrimitive)?.contentOrNull

        private fun JsonElement?.intOrNull(): Int? = this.stringOrNull()?.toIntOrNull()

        private fun JsonElement?.longOrNull(): Long? = this.stringOrNull()?.toLongOrNull()
    }
}