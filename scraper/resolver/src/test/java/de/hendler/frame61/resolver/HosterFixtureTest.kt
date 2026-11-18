package de.hendler.frame61.resolver

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Fuehrt jede echte Hoster-Aufzeichnung aus `fixtures-hosters` durch dieselbe
 * Schrittkette wie die App. Der Netzaufruf ist durch die aufgezeichnete Antwort
 * ersetzt; `responseSha256` bindet die Datei an die damalige Serverantwort.
 */
class HosterFixtureTest {

    private val fixturesDir = File(System.getProperty("user.dir"), "src/test/resources/fixtures-hosters")
    private val hostersDir: File? = System.getProperty("frame61.hostersDir")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.isDirectory }

    private class FixtureFetcher(private val bodies: List<String>) : BodyFetcher {
        private var position = 0
        override suspend fun get(url: String, headers: Map<String, String>): String {
            val index = position.coerceAtMost(bodies.lastIndex)
            position += 1
            return bodies[index]
        }

        override suspend fun post(url: String, body: String, headers: Map<String, String>): String =
            get(url, headers)
    }

    @Test
    fun `recorded hoster fixtures resolve to the recorded url`() = runTest {
        if (!fixturesDir.isDirectory) {
            throw AssumptionViolatedException("no fixtures at $fixturesDir")
        }
        val hosters = hostersDir
            ?: throw AssumptionViolatedException("frame61.hostersDir not set")
        val packs = HosterPackLoader(hosters).loadAll().associateBy { it.id }
        val directories = fixturesDir.listFiles { f -> f.isDirectory && !f.name.startsWith("._") }
            ?.sortedBy { it.name }
            ?: emptyList()
        if (directories.isEmpty()) {
            throw AssumptionViolatedException("no hoster fixture directories")
        }

        val reports = mutableListOf<String>()
        directories.forEach { dir ->
            val metaFile = File(dir, "meta.json")
            if (!metaFile.isFile) return@forEach
            val meta = HosterPackValidator.json.parseToJsonElement(metaFile.readText()).jsonObject
            val packId = requireNotNull(meta["id"]) { "meta.id fehlt" }.jsonPrimitive.content
            val sourceUrl = requireNotNull(meta["sourceUrl"]) { "meta.sourceUrl fehlt" }.jsonPrimitive.content
            val inputUrl = meta["inputUrl"]?.jsonPrimitive?.content ?: sourceUrl
            val expected = requireNotNull(
                HosterPackValidator.json
                    .parseToJsonElement(File(dir, "result.expected.json").readText())
                    .jsonObject["url"]
            ) { "result.expected.url fehlt" }.jsonPrimitive.content
            val pack = packs[packId] ?: throw AssertionError("$packId: kein Hoster-Pack")
            val bodies = dir.listFiles { f -> f.name.contains(".body.") }
                ?.sortedBy { it.name }
                ?.map { it.readText() }
                ?: emptyList()
            assertTrue("$packId: keine aufgezeichnete Antwort", bodies.isNotEmpty())

            val recordedHash = requireNotNull(meta["responseSha256"]) {
                "meta.responseSha256 fehlt"
            }.jsonPrimitive.content
            val actualHash = MessageDigest.getInstance("SHA-256")
                .digest(bodies.first().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            assertEquals("$packId: Fixture weicht vom Hash ab", recordedHash, actualHash)

            val outcome = HosterResolver(listOf(pack), FixtureFetcher(bodies))
                .resolve(inputUrl, cookies = "fixture=1")
            assertTrue("$packId: $outcome", outcome is HosterOutcome.Resolved)
            assertEquals("$packId: falsche Ergebnis-URL", expected, (outcome as HosterOutcome.Resolved).url)
            reports += "$packId -> ${outcome.url}"
        }

        reports.forEach(::println)
        assertTrue("kein Hoster-Fixture geprueft", reports.isNotEmpty())
    }
}
