package de.hendler.frame61.scraper.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Source pack as stored in assets/sources/<id>.json.
 *
 * Schema is additive: a pack may carry `search`, `detail`, `episodes` and
 * `streams` rules; only the identity fields are mandatory. Rules understand
 * both an HTML dialect (Jsoup CSS selectors, `@attr` / `@text` suffix) and an
 * API dialect (dot-path into a JSON body).
 */
@Serializable
data class SourcePack(
    val id: String,
    val name: String,
    val baseUrl: String,
    val version: Int = 1,
    val minEngine: String? = null,
    val tier: String? = null,
    val legalFree: Boolean = false,
    val kind: String = "html",
    val verified: Boolean = false,
    val lastCheck: String? = null,
    val check: PackCheck? = null,
    val search: PackRule? = null,
    val detail: PackRule? = null,
    val episodes: PackRule? = null,
    val streams: PackRule? = null
)

/** Worker result stored in the pack (additive, optional). */
@Serializable
data class PackCheck(
    val status: String? = null,
    val reason: String? = null,
    val checked: List<String> = emptyList(),
    val misses: List<String> = emptyList(),
    @SerialName("http_status") val httpStatus: Int? = null
)

@Serializable
data class PackRule(
    // request
    val endpoint: String? = null,
    val path: String? = null,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val queryParam: String? = null,
    val pageLimit: Int = 20,
    /** Optional per-rule dialect: `html` or `api`; overrides the pack's `kind`. */
    val format: String? = null,
    // html: search
    val resultSelector: String? = null,
    val titleSelector: String? = null,
    val yearSelector: String? = null,
    val typeSelector: String? = null,
    val linkSelector: String? = null,
    val idAttr: String? = null,
    // html: episodes
    val episodeSelector: String? = null,
    val seasonSelector: String? = null,
    val episodeTitleSelector: String? = null,
    val episodeLinkSelector: String? = null,
    val episodeIndexSelector: String? = null,
    // api: season/number paths inside an episode entry
    val episodeSeasonPath: String? = null,
    val episodeNumberPath: String? = null,
    // html: streams
    val selector: String? = null,
    val urlAttr: String? = null,
    val qualitySelector: String? = null,
    val langSelector: String? = null,
    val hosterSelector: String? = null,
    val healthAttr: String? = null,
    val minHealth: Double? = null,
    val qualityMap: Map<String, String> = emptyMap(),
    val langMap: Map<String, String> = emptyMap(),
    val referrers: List<Referrer>? = null,
    // api: dot-paths
    val jsonPath: String? = null,
    val idPath: String? = null,
    val titlePath: String? = null,
    val yearPath: String? = null,
    val typePath: String? = null,
    val streamPath: String? = null,
    val streamQualityPath: String? = null,
    val streamLangPath: String? = null,
    val streamNamePath: String? = null,
    val streamUrlTemplate: String? = null
)

@Serializable
data class Referrer(
    val domain: String,
    val accept: Boolean = true
)

/** Loads and validates source packs. A broken pack is dropped on its own. */
class PackLoader(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    suspend fun loadPacks(): List<SourcePack> = withContext(Dispatchers.IO) {
        val files = context.assets.list("sources")?.filter { it.endsWith(".json") } ?: emptyList()
        files.mapNotNull { loadPackFromAssets(it) }
    }

    /** Internal directory for hot-loaded packs (Source Lab import). */
    fun internalSourcesDir(): File = File(context.filesDir, "sources")

    /**
     * Every pack the app knows: bundled assets plus the internal directory.
     *
     * A broken pack is dropped on its own in both places; the app keeps running
     * with whatever validates. Duplicate ids keep the first (bundled) version.
     */
    suspend fun loadAllPacks(): List<SourcePack> = withContext(Dispatchers.IO) {
        val internal = FilePackLoader(internalSourcesDir()).loadAll()
        (loadPacks() + internal).distinctBy { it.id }
    }

    suspend fun loadPackFromAssets(file: String): SourcePack? = withContext(Dispatchers.IO) {
        runCatching {
            val content = context.assets.open("sources/$file").bufferedReader().use { it.readText() }
            parsePack(content)
        }.getOrNull()
    }

    fun parsePack(content: String): SourcePack? = PackLoaderValidator.parse(content)
}

/** File-system backed loader, used by the fixture runner and the Source Lab. */
class FilePackLoader(private val dir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun loadAll(): List<SourcePack> =
        packFiles().mapNotNull { file -> load(file) }

    /** Skips AppleDouble sidecars (`._foo.json`) and anything not ending in .json. */
    fun packFiles(): List<File> =
        dir.listFiles { f -> f.isFile && f.extension == "json" && !f.name.startsWith("._") }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun load(file: File): SourcePack? = loadWithReason(file).first

    /** Returns the pack plus, on rejection, the reason — silent drops hide bugs. */
    fun loadWithReason(file: File): Pair<SourcePack?, String?> {
        val text = runCatching { file.readText() }
            .getOrElse { return null to "read failed: ${it.message}" }
        val pack = runCatching { json.decodeFromString<SourcePack>(text) }
            .getOrElse { return null to "decode failed: ${it.message}" }
        if (!PackLoaderValidator.isValid(pack)) {
            return null to "invalid: ${PackLoaderValidator.reason(pack)}"
        }
        return pack to null
    }
}

/** Validation without an Android Context, so unit tests can reuse it. */
object PackLoaderValidator {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun isValid(pack: SourcePack): Boolean {
        if (pack.id.isBlank() || pack.name.isBlank() || pack.baseUrl.isBlank()) return false
        if (pack.kind !in setOf("html", "api")) return false
        val rules = listOfNotNull(pack.search, pack.detail, pack.episodes, pack.streams)
        if (rules.isEmpty()) return false
        if (rules.any { it.format != null && it.format !in setOf("html", "api") }) return false
        // A rule may be parse-only (e.g. streams scraped from the detail page);
        // the pack as a whole needs at least one addressable target.
        return rules.any { it.endpoint.orEmpty().isNotBlank() || it.path.orEmpty().isNotBlank() }
    }

    fun parse(content: String): SourcePack? =
        runCatching { json.decodeFromString<SourcePack>(content) }.getOrNull()?.takeIf(::isValid)

    fun reason(pack: SourcePack): String {
        if (pack.id.isBlank()) return "id leer"
        if (pack.name.isBlank()) return "name leer"
        if (pack.baseUrl.isBlank()) return "baseUrl leer"
        if (pack.kind !in setOf("html", "api")) return "kind='${pack.kind}'"
        val rules = listOfNotNull(pack.search, pack.detail, pack.episodes, pack.streams)
        if (rules.isEmpty()) return "keine Regel"
        rules.firstOrNull { it.format != null && it.format !in setOf("html", "api") }?.let {
            return "format='${it.format}'"
        }
        val addressable = rules.count {
            it.endpoint.orEmpty().isNotBlank() || it.path.orEmpty().isNotBlank()
        }
        return if (addressable > 0) "" else "keine Regel mit endpoint/path"
    }
}