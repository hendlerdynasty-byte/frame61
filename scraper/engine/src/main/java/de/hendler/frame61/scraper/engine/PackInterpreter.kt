package de.hendler.frame61.scraper.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Executes the search / detail / episodes / streams rules of a [SourcePack].
 *
 * Two dialects are supported:
 *  - `kind = "html"`  → Jsoup CSS selectors, optional `@attr` / `@text` suffix
 *  - `kind = "api"`   → dot-path into a decoded JSON body (`a.b.0.c`)
 *
 * [parseSearch] and friends are pure functions over an already fetched body so
 * the fixture runner can execute them without network; the `fetch` methods only
 * add the HTTP call.
 */
class PackInterpreter(private val http: PackHttpClient = PackHttpClient()) {

    // ------------------------------------------------------------ network --

    fun search(pack: SourcePack, query: String, page: Int = 1): List<SearchResult> {
        val rule = pack.search ?: return emptyList()
        val body = http.get(pack, rule, mapOf("query" to query, "q" to query, "page" to page.toString()))
        return parseSearch(pack, body)
    }

    fun detail(pack: SourcePack, id: String): DetailResult? {
        val rule = pack.detail ?: return null
        val body = http.get(pack, rule, mapOf("titleId" to id, "id" to id))
        return parseDetail(pack, body)
    }

    fun episodes(pack: SourcePack, id: String): List<EpisodeResult> {
        val rule = pack.episodes ?: pack.detail ?: return emptyList()
        val body = http.get(pack, rule, mapOf("titleId" to id, "id" to id))
        return parseEpisodes(pack, body)
    }

    fun streams(pack: SourcePack, id: String, episodeId: String? = null): List<StreamResult> {
        val rule = pack.streams ?: return emptyList()
        val body = http.get(pack, rule, mapOf("titleId" to id, "id" to id, "episodeId" to (episodeId ?: "")))
        return parseStreams(pack, body)
    }

    // -------------------------------------------------------------- html ----

    fun parseSearchHtml(pack: SourcePack, body: String): List<SearchResult> {
        val rule = pack.search ?: return emptyList()
        val doc = Jsoup.parse(body)
        return rule.resultSelector.selectNodes(doc).map { node ->
            val link = rule.linkSelector.selectFirstIn(node) ?: node.selectFirstOrNull("a") ?: node
            val href = link.attrOr(rule.idAttr ?: "href")
            SearchResult(
                id = href.toTitleId(),
                title = rule.titleSelector.textOf(node).ifBlank { link.text() },
                year = rule.yearSelector.intOf(node),
                type = rule.typeSelector.textOf(node).ifBlank { null },
                url = link.absHref(rule.idAttr ?: "href")
            )
        }.filter { it.id.isNotBlank() || it.title.isNotBlank() }
    }

    fun parseDetailHtml(pack: SourcePack, body: String): DetailResult? {
        val rule = pack.detail ?: return null
        val doc = Jsoup.parse(body)
        val root = rule.resultSelector.selectFirstIn(doc) ?: doc.body()
        val title = rule.titleSelector.textOf(root).ifBlank { doc.title() }
        if (title.isBlank()) return null
        return DetailResult(id = "", title = title, year = rule.yearSelector.intOf(root))
    }

    fun parseEpisodesHtml(pack: SourcePack, body: String): List<EpisodeResult> {
        val rule = pack.episodes ?: pack.detail ?: return emptyList()
        val doc = Jsoup.parse(body)
        return rule.episodeSelector.selectNodes(doc).mapIndexed { index, node ->
            val link = rule.episodeLinkSelector.selectFirstIn(node) ?: node.selectFirstOrNull("a") ?: node
            EpisodeResult(
                id = link.attrOr("href").toTitleId(),
                season = rule.seasonSelector.intOf(node) ?: 1,
                episode = rule.episodeIndexSelector.intOf(node) ?: (index + 1),
                title = rule.episodeTitleSelector.textOf(node).ifBlank { link.text() }
            )
        }.filter { it.id.isNotBlank() }
    }

    fun parseStreamsHtml(pack: SourcePack, body: String): List<StreamResult> {
        val rule = pack.streams ?: return emptyList()
        val doc = Jsoup.parse(body)
        var health = 1.0
        return rule.selector.selectNodes(doc).mapNotNull { node ->
            val url = node.attrOr(rule.urlAttr ?: "href")
            if (url.isBlank()) return@mapNotNull null
            rule.healthAttr?.let { attr -> node.attrOrNull(attr)?.toDoubleOrNull()?.let { health = it } }
            if (health < (rule.minHealth ?: 0.0)) return@mapNotNull null
            val rawQuality = rule.qualitySelector.textOf(node)
            val rawLang = rule.langSelector.textOf(node)
            StreamResult(
                url = url,
                quality = rule.qualityMap[rawQuality] ?: rawQuality.ifBlank { null },
                lang = rule.langMap[rawLang] ?: rawLang.ifBlank { null },
                hosterId = rule.hosterSelector.textOf(node).ifBlank { null },
                health = health
            )
        }
    }

    // --------------------------------------------------------------- api ----

    fun parseSearchApi(pack: SourcePack, body: String): List<SearchResult> {
        val rule = pack.search ?: return emptyList()
        val items = rule.jsonPath.arrayIn(body) ?: return emptyList()
        return items.mapNotNull { item ->
            val id = rule.idPath.stringOn(item).orEmpty().toTitleId()
            val title = rule.titlePath.stringOn(item).orEmpty()
            if (id.isBlank() && title.isBlank()) return@mapNotNull null
            SearchResult(
                id = id,
                title = title,
                year = rule.yearPath.stringOn(item)?.toIntOrNull(),
                type = rule.typePath.stringOn(item)
            )
        }
    }

    fun parseStreamsApi(pack: SourcePack, body: String, params: Map<String, String> = emptyMap()): List<StreamResult> {
        val rule = pack.streams ?: return emptyList()
        val items = rule.streamPath.arrayIn(body) ?: rule.jsonPath.arrayIn(body) ?: return emptyList()
        return items.mapNotNull { item ->
            val template = rule.streamUrlTemplate
            val url = if (template != null) {
                val name = rule.streamNamePath.stringOn(item) ?: return@mapNotNull null
                PackHttpClient.render(template, params + mapOf("name" to name))
            } else {
                item.urlValue() ?: return@mapNotNull null
            }
            StreamResult(
                url = url,
                quality = rule.streamQualityPath.stringOn(item),
                lang = rule.streamLangPath.stringOn(item),
                hosterId = null,
                health = 1.0
            )
        }
    }

    // ----------------------------------------------------------- dispatch ---

    fun parseDetailApi(pack: SourcePack, body: String): DetailResult? {
        val rule = pack.detail ?: return null
        val root = runCatching { bodyJson.parseToJsonElement(body) }.getOrNull() ?: return null
        val title = rule.titlePath.stringOn(root).orEmpty()
        if (title.isBlank()) return null
        val year = rule.yearPath.stringOn(root)?.toIntOrNull()
        return DetailResult(id = "", title = title, year = year)
    }

    /**
     * API episodes: the rule's `jsonPath` points at the episode array; the
     * season/number are read from `episodeSeasonPath`/`episodeNumberPath` when
     * present, otherwise from a title like "Folge 1 · Staffel 2 … (S02/E01)".
     * The fallback is the array index (1-based), never a silent zero.
     */
    fun parseEpisodesApi(pack: SourcePack, body: String): List<EpisodeResult> {
        val rule = pack.episodes ?: return emptyList()
        val items = rule.jsonPath.arrayIn(body) ?: return emptyList()
        return items.mapIndexedNotNull { index, item ->
            val id = rule.idPath.stringOn(item) ?: return@mapIndexedNotNull null
            val title = rule.titlePath.stringOn(item).orEmpty().ifBlank { "Folge ${index + 1}" }
            val seasonFromTitle = Regex("""\(\s*S(\d+)\s*/\s*E(\d+)\s*\)""").find(title)
            val season = rule.episodeSeasonPath.stringOn(item)?.toIntOrNull()
                ?: seasonFromTitle?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("""Staffel\s+(\d+)""").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: 1
            val number = rule.episodeNumberPath.stringOn(item)?.toIntOrNull()
                ?: seasonFromTitle?.groupValues?.getOrNull(2)?.toIntOrNull()
                ?: (index + 1)
            EpisodeResult(id = id, season = season, episode = number, title = title)
        }
    }

    fun parseSearch(pack: SourcePack, body: String): List<SearchResult> =
        if (pack.isApi()) parseSearchApi(pack, body) else parseSearchHtml(pack, body)

    fun parseDetail(pack: SourcePack, body: String): DetailResult? =
        if (pack.isApi()) parseDetailApi(pack, body) else parseDetailHtml(pack, body)

    fun parseEpisodes(pack: SourcePack, body: String): List<EpisodeResult> =
        if (pack.isApi()) parseEpisodesApi(pack, body) else parseEpisodesHtml(pack, body)

    fun parseStreams(pack: SourcePack, body: String, params: Map<String, String> = emptyMap()): List<StreamResult> =
        if (pack.isApi()) parseStreamsApi(pack, body, params) else parseStreamsHtml(pack, body)
}

private fun SourcePack.isApi(): Boolean = kind.equals("api", ignoreCase = true)

// --------------------------------------------------------------- Jsoup -----

/**
 * `h3 a@href` → attribute value of `h3 a`; `h3 a` / `h3 a@text` → text.
 */
private fun String?.textOf(node: Element): String {
    if (this.isNullOrBlank()) return ""
    val (selector, attr) = splitAttr(this)
    val target = selector.selectFirstIn(node) ?: return ""
    return when (attr) {
        null, "text" -> target.text().trim()
        "html" -> target.html().trim()
        else -> target.attr(attr).trim()
    }
}

private fun String?.intOf(node: Element): Int? =
    Regex("""\d{1,4}""").findAll(this.textOf(node)).firstOrNull()?.value?.toIntOrNull()

private fun String?.selectNodes(doc: Document): List<Element> {
    if (this.isNullOrBlank()) return emptyList()
    return runCatching { doc.select(this) }.getOrDefault(emptyList())
}

private fun String?.selectNodes(node: Element): List<Element> {
    if (this.isNullOrBlank()) return emptyList()
    return runCatching { node.select(this) }.getOrDefault(emptyList())
}

private fun String?.selectFirstIn(node: Element): Element? {
    if (this.isNullOrBlank()) return null
    return runCatching { node.selectFirst(this) }.getOrNull()
}

private fun String?.selectFirstIn(doc: Document): Element? {
    if (this.isNullOrBlank()) return null
    return runCatching { doc.selectFirst(this) }.getOrNull()
}

private fun Element.selectFirstOrNull(css: String): Element? =
    runCatching { selectFirst(css) }.getOrNull()

private fun Element.attrOr(name: String): String = attr(name).trim()

private fun Element.attrOrNull(name: String): String? = attr(name).trim().ifBlank { null }

/** Native Jsoup absolute-URL resolution. */
private fun Element.absHref(attribute: String): String =
    runCatching { attr("abs:$attribute") }.getOrDefault("")

private fun splitAttr(raw: String): Pair<String, String?> {
    val at = raw.lastIndexOf('@')
    if (at <= 0 || at == raw.lastIndex) return raw to null
    return raw.substring(0, at) to raw.substring(at + 1)
}

/** `/sendung/abc-123` or `?id=42` → `abc-123` / `42`. */
internal fun String.toTitleId(): String {
    val trimmed = trim().trimEnd('/')
    if (trimmed.isBlank()) return ""
    Regex("[?&](?:id|titleId|videoId|episodeId)=").find(trimmed)?.let { match ->
        val value = trimmed.substring(match.range.last + 1).substringBefore('&')
        if (value.isNotBlank()) return value
    }
    return trimmed.substringAfterLast('/').substringBefore('?').substringBefore('#')
}

// -------------------------------------------------------------- dot-path ---

private val bodyJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

/** `a.b.0.c` → element, walking objects and arrays. */
private fun JsonElement.walk(path: String): JsonElement? {
    var current: JsonElement? = this
    for (segment in path.split('.')) {
        if (segment.isBlank()) continue
        current = when (current) {
            is JsonObject -> current[segment]
            is JsonArray -> current.getOrNull(segment.toIntOrNull() ?: -1)
            else -> null
        } ?: return null
    }
    return current
}

private fun String?.arrayIn(body: String): List<JsonElement>? {
    if (this.isNullOrBlank()) return null
    val root = runCatching { bodyJson.parseToJsonElement(body) }.getOrNull() ?: return null
    return when (val value = root.walk(this)) {
        is JsonArray -> value
        is JsonObject -> listOf(value)
        else -> null
    }
}

private fun String?.stringOn(item: JsonElement): String? {
    if (this.isNullOrBlank()) return null
    val value = item.walk(this) as? JsonPrimitive ?: return null
    return value.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonElement.urlValue(): String? = when (this) {
    is JsonObject -> listOf("url", "src", "file", "href", "stream", "download")
        .firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }
    is JsonPrimitive -> contentOrNull?.takeIf { it.isNotBlank() }
    else -> null
}

// ---------------------------------------------------------------- models ---

data class SearchResult(
    val id: String,
    val title: String,
    val year: Int? = null,
    val type: String? = null,
    val url: String? = null
)

data class DetailResult(
    val id: String,
    val title: String,
    val description: String? = null,
    val year: Int? = null,
    val runtime: Int? = null,
    val genres: List<String> = emptyList()
)

data class EpisodeResult(
    val id: String,
    val season: Int,
    val episode: Int,
    val title: String
)

data class StreamResult(
    val url: String,
    val quality: String? = null,
    val lang: String? = null,
    val hosterId: String? = null,
    val health: Double = 1.0
)