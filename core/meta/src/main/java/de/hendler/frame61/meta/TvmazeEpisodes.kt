package de.hendler.frame61.meta

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
 * [parse] is pure and fixture-tested; the network path never throws.
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
        json.parseToJsonElement(body).jsonObject["name"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(body: String): List<EpisodeInfo> = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val episodes = root["_embedded"]?.jsonObject
                ?.get("episodes")?.let { it as? JsonArray } ?: return emptyList()
            episodes.mapNotNull { element ->
                val e = element.jsonObject
                val id = e["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    ?: return@mapNotNull null
                val season = e["season"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val number = e["number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val name = e["name"]?.jsonPrimitive?.contentOrNull ?: "Folge"
                EpisodeInfo(
                    id = id,
                    season = season,
                    number = number,
                    name = name,
                    airDate = e["airdate"]?.jsonPrimitive?.contentOrNull,
                    runtime = e["runtime"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                    still = e["image"]?.jsonObject?.get("medium")?.jsonPrimitive?.contentOrNull
                )
            }
        }.getOrDefault(emptyList())
    }
}