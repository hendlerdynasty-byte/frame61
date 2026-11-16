package de.hendler.frame61.scraper.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import java.io.File
import kotlinx.serialization.Serializable

/**
 * Runs a pack's rules against recorded/synthetic bodies and compares the parsed
 * result with an expected JSON file.
 *
 * Layout of a fixture directory:
 * ```
 * fixtures/<packId>/
 *   meta.json                  provenance (recorded | synthetic)
 *   search.body.json           input body
 *   search.expected.json       expected parsed output
 *   streams.body.html
 *   streams.expected.json
 * ```
 *
 * Nothing here touches the network, so a green run proves the *parser* works on
 * the recorded shape — it does not prove the live site still matches.
 */
class FixtureRunner(
    private val interpreter: PackInterpreter = PackInterpreter(),
    private val fixturesDir: File
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        prettyPrint = false
    }

    fun availablePackIds(): List<String> =
        fixturesDir.listFiles { f -> f.isDirectory && !f.name.startsWith("._") }
            ?.sortedBy { it.name }
            ?.map { it.name }
            ?: emptyList()

    fun meta(packId: String): FixtureMeta? {
        val file = File(fixturesDir, "$packId/meta.json")
        if (!file.isFile) return null
        return runCatching { json.decodeFromString<FixtureMeta>(file.readText()) }.getOrNull()
    }

    /** One check per `<name>.body.*` / `<name>.expected.json` pair. */
    fun run(pack: SourcePack, packId: String = pack.id): FixtureReport {
        val dir = File(fixturesDir, "$packId")
        val meta = meta(packId)
        val params = buildMap {
            meta?.id?.let { put("id", it); put("titleId", it) }
        }
        val checks = dir.listFiles { f ->
            f.isFile && f.name.endsWith(".expected.json") && !f.name.startsWith("._")
        }
            ?.sortedBy { it.name }
            ?.mapNotNull { expectedFile ->
                val stem = expectedFile.name.removeSuffix(".expected.json")
                val bodyFile = dir.listFiles { f ->
                    f.isFile && !f.name.startsWith("._") && f.name.startsWith("$stem.body.")
                }?.firstOrNull() ?: return@mapNotNull null
                runCheck(pack, stem, bodyFile, expectedFile, params)
            }
            ?: emptyList()

        return FixtureReport(
            packId = packId,
            checks = checks,
            meta = meta
        )
    }

    private fun runCheck(
        pack: SourcePack,
        name: String,
        bodyFile: File,
        expectedFile: File,
        params: Map<String, String>
    ): FixtureCheck {
        val body = runCatching { bodyFile.readText() }.getOrDefault("")
        val actual: Any? = try {
            when (name) {
                "search" -> interpreter.parseSearch(pack, body)
                "detail" -> interpreter.parseDetail(pack, body)
                "episodes" -> interpreter.parseEpisodes(pack, body)
                "streams" -> interpreter.parseStreams(pack, body, params)
                else -> return FixtureCheck(
                    name = name,
                    passed = false,
                    skipped = true,
                    detail = "unknown check '$name'"
                )
            }
        } catch (error: Throwable) {
            return FixtureCheck(
                name = name,
                passed = false,
                skipped = false,
                detail = "parser threw: ${error.message}"
            )
        }

        val expectedText = runCatching { expectedFile.readText() }.getOrDefault("[]")
        val expectedJson = runCatching { json.parseToJsonElement(expectedText) }.getOrNull()
        val actualJson = toEncodable(actual)
        val passed = expectedJson != null && matches(actualJson, expectedJson)

        return FixtureCheck(
            name = name,
            passed = passed,
            skipped = false,
            detail = if (passed) "match" else diff(actualJson, expectedJson)
        )
    }

    /**
     * Structural comparison. Strings must match exactly (and be strings on both
     * sides), numbers are compared numerically so `1` equals `1.0`, an expected
     * `null` means "do not check", array order and length are significant, and a
     * key present in the expectation but absent in the parsed output fails.
     */
    private fun matches(
        actual: kotlinx.serialization.json.JsonElement?,
        expected: kotlinx.serialization.json.JsonElement
    ): Boolean = when (expected) {
        is kotlinx.serialization.json.JsonNull -> true
        is kotlinx.serialization.json.JsonObject -> {
            val act = actual as? kotlinx.serialization.json.JsonObject ?: return false
            expected.all { (key, expValue) ->
                val actValue = act[key] ?: return false
                matches(actValue, expValue)
            }
        }
        is kotlinx.serialization.json.JsonArray -> {
            val act = actual as? kotlinx.serialization.json.JsonArray ?: return false
            act.size == expected.size &&
                expected.indices.all { matches(act[it], expected[it]) }
        }
        is kotlinx.serialization.json.JsonPrimitive -> {
            val act = actual as? kotlinx.serialization.json.JsonPrimitive ?: return false
            when {
                expected.isString -> act.isString && act.content == expected.content
                else -> {
                    val exp = expected.content.toDoubleOrNull() ?: return false
                    val got = if (act.isString) null else act.content.toDoubleOrNull()
                    got != null && got.compareTo(exp) == 0
                }
            }
        }
        else -> false
    }

    private fun toEncodable(value: Any?): kotlinx.serialization.json.JsonElement = when (value) {
        null -> kotlinx.serialization.json.JsonNull
        is List<*> -> kotlinx.serialization.json.buildJsonArray {
            value.forEach { add(toEncodable(it)) }
        }
        is SearchResult -> kotlinx.serialization.json.buildJsonObject {
            put("id", value.id)
            put("title", value.title)
            value.year?.let { put("year", it) }
            value.type?.let { put("type", it) }
        }
        is DetailResult -> kotlinx.serialization.json.buildJsonObject {
            put("id", value.id)
            put("title", value.title)
            value.year?.let { put("year", it) }
        }
        is EpisodeResult -> kotlinx.serialization.json.buildJsonObject {
            put("id", value.id)
            put("season", value.season)
            put("episode", value.episode)
            put("title", value.title)
        }
        is StreamResult -> kotlinx.serialization.json.buildJsonObject {
            put("url", value.url)
            value.quality?.let { put("quality", it) }
            value.lang?.let { put("lang", it) }
            value.hosterId?.let { put("hosterId", it) }
        }
        else -> kotlinx.serialization.json.JsonNull
    }

    private fun diff(actual: kotlinx.serialization.json.JsonElement?, expected: kotlinx.serialization.json.JsonElement?): String {
        val a = actual?.toString()?.take(220) ?: "null"
        val e = expected?.toString()?.take(220) ?: "null"
        return "expected $e | actual $a"
    }

    data class FixtureCheck(
        val name: String,
        val passed: Boolean,
        val skipped: Boolean,
        val detail: String
    )

    data class FixtureReport(
        val packId: String,
        val checks: List<FixtureCheck>,
        val meta: FixtureMeta?
    ) {
        val skipped: Boolean get() = checks.isEmpty() || checks.all { it.skipped }
        val passed: Boolean get() = checks.isNotEmpty() && checks.all { it.passed }
        val summary: String
            get() = buildString {
                append("[${packId}] ")
                if (checks.isEmpty()) append("NO CHECKS")
                else append(checks.joinToString(", ") { "${it.name}=${when { it.skipped -> "skip"; it.passed -> "pass"; else -> "FAIL" }}" })
                meta?.provenance?.let { append(" (${it})") }
            }
    }
}

@Serializable
data class FixtureMeta(
    val id: String? = null,                 // context id, e.g. the item being inspected
    val provenance: String = "unknown",     // recorded | synthetic
    val capturedAt: String? = null,
    val sourceUrl: String? = null,
    val responseSha256: String? = null,
    val note: String? = null
)