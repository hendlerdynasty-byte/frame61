package de.hendler.frame61.resolver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A hoster pack describes how to turn a hoster page URL into a playable stream
 * URL. It lives in `assets/hosters/<id>.json`.
 *
 * The step machine is deliberately small and data-driven: a pack is a list of
 * steps over a string variable store. Unknown actions are rejected when the
 * pack is loaded, so a broken pack can never reach the resolver.
 */
@Serializable
data class HosterPack(
    val id: String,
    val name: String,
    val version: Int = 1,
    val verified: Boolean = false,
    /** Domain suffixes, matched longest-first, subdomains included. */
    val domains: List<String> = emptyList(),
    /** `webview` marks a hoster that needs a visible WebView frame. */
    val captcha: String? = null,
    /** Name of the variable holding the final stream URL. */
    val result: String? = null,
    val steps: List<HosterStep> = emptyList()
)

@Serializable
data class HosterStep(
    val action: String,
    val url: String? = null,
    val method: String = "GET",
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val input: String? = null,
    val pattern: String? = null,
    val group: Int = 1,
    val path: String? = null,
    val ms: Long = 0L,
    val saveAs: String? = null
)

/** Validation and file loading without an Android Context. */
object HosterPackValidator {

    val ACTIONS = setOf("get", "regex", "json", "post", "sleep", "copy")

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parse(content: String): HosterPack? =
        runCatching { json.decodeFromString<HosterPack>(content) }.getOrNull()?.takeIf { isValid(it) }

    fun isValid(pack: HosterPack): Boolean = reason(pack).isEmpty()

    /** Empty string means valid; otherwise the first problem found. */
    fun reason(pack: HosterPack): String {
        if (pack.id.isBlank()) return "id leer"
        if (pack.name.isBlank()) return "name leer"
        if (pack.domains.isEmpty()) return "keine domains"
        if (pack.steps.isEmpty()) return "keine steps"
        pack.steps.forEachIndexed { index, step ->
            if (step.action !in ACTIONS) return "step $index: action '${step.action}' unbekannt"
            when (step.action) {
                "get", "post" -> if (step.url.isNullOrBlank()) return "step $index: url fehlt"
                "regex" -> {
                    if (step.input.isNullOrBlank()) return "step $index: input fehlt"
                    if (step.pattern.isNullOrBlank()) return "step $index: pattern fehlt"
                    runCatching { Regex(step.pattern) }
                        .onFailure { return "step $index: pattern ungültig (${it.message})" }
                }
                "json" -> {
                    if (step.input.isNullOrBlank()) return "step $index: input fehlt"
                    if (step.path.isNullOrBlank()) return "step $index: path fehlt"
                }
                "sleep" -> if (step.ms < 0) return "step $index: negative sleep"
                "copy" -> if (step.url.isNullOrBlank()) return "step $index: url fehlt"
            }
        }
        if (!pack.result.isNullOrBlank() && pack.result !in pack.steps.mapNotNull { it.saveAs }) {
            return "result '$pack.result' wird von keinem step erzeugt"
        }
        return ""
    }
}

/** Loads hoster packs from a directory. A broken pack is dropped on its own. */
class HosterPackLoader(private val dir: File) {

    fun files(): List<File> =
        dir.listFiles { f -> f.isFile && f.extension == "json" && !f.name.startsWith("._") }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun loadAll(): List<HosterPack> = files().mapNotNull { load(it) }

    fun load(file: File): HosterPack? = loadWithReason(file).first

    fun loadWithReason(file: File): Pair<HosterPack?, String?> {
        val text = runCatching { file.readText() }
            .getOrElse { return null to "read failed: ${it.message}" }
        val pack = runCatching {
            HosterPackValidator.json.decodeFromString<HosterPack>(text)
        }.getOrElse { return null to "decode failed: ${it.message}" }
        val reason = HosterPackValidator.reason(pack)
        return if (reason.isEmpty()) pack to null else null to "invalid: $reason"
    }
}