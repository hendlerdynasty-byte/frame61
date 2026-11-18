package de.hendler.frame61.scraper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Test
import java.io.File

/**
 * Fixture runner for every pack in the app's `assets/sources`.
 *
 * The pack directory is handed over as the system property `frame61.packsDir`
 * (set in this module's build file) — no relative parent traversal.
 *
 * A pack without a fixture directory is *skipped*, never failed: a missing
 * fixture means "no evidence yet", not "broken". A fixture that exists and does
 * not match the parser is a real failure.
 */
class PackFixtureTest {

    private val packsDir: File? = System.getProperty("frame61.packsDir")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.isDirectory }

    private val fixturesDir: File = File(System.getProperty("user.dir"), "src/test/resources/fixtures")

    private val loader: FilePackLoader? = packsDir?.let(::FilePackLoader)
    private val interpreter = PackInterpreter()

    private fun requirePacks(): FilePackLoader {
        return loader ?: throw AssumptionViolatedException(
            "frame61.packsDir not set or missing (value=${System.getProperty("frame61.packsDir")})"
        )
    }

    @Test
    fun `all packs load and validate`() {
        val activeLoader = requirePacks()
        val files: List<File> = requireNotNull(packsDir) { "frame61.packsDir missing" }
            .listFiles { f ->
                f.isFile && f.extension == "json" && !f.name.startsWith("._")
            }
            ?.toList() ?: emptyList()
        assertTrue("no packs found in $packsDir", files.isNotEmpty())

        val rejected = mutableListOf<String>()
        val loaded = files.mapNotNull { file ->
            val (pack, reason) = activeLoader.loadWithReason(file)
            if (pack == null) rejected += "${file.name} → $reason"
            pack
        }

        println("packs: ${loaded.size}/${files.size} valid, rejected=${rejected.ifEmpty { "-" }}")
        assertTrue("packs rejected by validator:\n  " + rejected.joinToString("\n  "), rejected.isEmpty())
        assertEquals(files.size, loaded.size)
    }

    @Test
    fun `packs declare legalFree only for free sources`() {
        val packs = requirePacks().loadAll()
        val legalFree = packs.filter { it.legalFree }.map { it.id }.sorted()
        println("legalFree packs (${legalFree.size}): $legalFree")
        assertTrue(
            "expected at least 6 legal-free packs, got ${legalFree.size}",
            legalFree.size >= 6
        )
    }

    @Test
    fun `fixture runner executes every available fixture`() {
        if (!fixturesDir.isDirectory) {
            throw AssumptionViolatedException("no fixtures directory at $fixturesDir")
        }

        val activeLoader = requirePacks()
        val runner = FixtureRunner(interpreter, fixturesDir)
        val ids = runner.availablePackIds()
        if (ids.isEmpty()) {
            throw AssumptionViolatedException("no fixture directories under $fixturesDir")
        }

        val packs = activeLoader.loadAll().associateBy { it.id }
        val reports = ids.map { id ->
            val pack = packs[id]
                ?: throw AssumptionViolatedException("pack '$id' has a fixture but no source pack")
            runner.run(pack, id)
        }

        reports.forEach { println(it.summary) }

        val failed = reports.filterNot { it.passed }
        assertTrue(
            "fixture failures:\n" + failed.joinToString("\n") { report ->
                report.checks.filterNot { it.passed }
                    .joinToString("\n") { "  ${report.packId}/${it.name}: ${it.detail}" }
            },
            failed.isEmpty()
        )

        val withChecks = reports.count { it.checks.isNotEmpty() }
        assertTrue("expected at least 6 green fixture packs, got $withChecks", withChecks >= 6)
    }

    @Test
    fun `broken pack is dropped alone`() {
        val broken = """
            {"id": "", "name": "kaputt", "baseUrl": "https://x.invalid", "search": {"endpoint": "/s"}}
        """.trimIndent()
        assertEquals(null, PackLoaderValidator.parse(broken))

        val noRule = """
            {"id": "ok", "name": "ohne Regel", "baseUrl": "https://x.invalid"}
        """.trimIndent()
        assertEquals(null, PackLoaderValidator.parse(noRule))

        val good = """
            {"id": "gut", "name": "Gut", "baseUrl": "https://x.invalid", "streams": {"endpoint": "/s"}}
        """.trimIndent()
        assertEquals("gut", PackLoaderValidator.parse(good)?.id)
    }

    @Test
    fun `dot path and selector helpers behave`() {
        val html = "<div class='item'><h3><a href='/sendung/abc-1'>  Titel A </a></h3>" +
            "<span class='d'>2019</span></div>"
        val pack = PackLoaderValidator.parse(
            """
            {"id":"t","name":"T","baseUrl":"https://x.invalid",
             "search":{"endpoint":"/s","resultSelector":".item","titleSelector":"h3 a@text",
                       "yearSelector":".d@text","linkSelector":"h3 a","idAttr":"href"}}
            """.trimIndent()
        ) ?: error("pack must parse")
        val results = interpreter.parseSearchHtml(pack, html)
        assertEquals(1, results.size)
        assertEquals("abc-1", results.first().id)
        assertEquals("Titel A", results.first().title)
        assertEquals(2019, results.first().year)

        val apiPack = PackLoaderValidator.parse(
            """
            {"id":"a","name":"A","baseUrl":"https://x.invalid","kind":"api",
             "search":{"endpoint":"/s","jsonPath":"response.docs",
                       "idPath":"identifier","titlePath":"title","yearPath":"year"}}
            """.trimIndent()
        ) ?: error("api pack must parse")
        val body = """{"response":{"docs":[{"identifier":"i1","title":"Film","year":1999}]}}"""
        val api = interpreter.parseSearchApi(apiPack, body)
        assertEquals(1, api.size)
        assertEquals("i1", api.first().id)
        assertEquals("Film", api.first().title)
        assertEquals(1999, api.first().year)
    }

    @Test
    fun `idFromTitle derives the id from the hit text`() {
        val pack = PackLoaderValidator.parse(
            """
            {"id":"bs","name":"BS","baseUrl":"https://x.invalid",
             "search":{"endpoint":"/search","resultSelector":"option[value]",
                       "idFromTitle":true,"idRegex":"^(.+)$"}}
            """.trimIndent()
        ) ?: error("idFromTitle pack must parse")
        val results = interpreter.parseSearchHtml(pack, """<option value="134">Naruto</option>""")
        assertEquals(1, results.size)
        assertEquals("Naruto", results.first().id)
        assertEquals("Naruto", results.first().title)
    }

    @Test
    fun `api search strips html and mixed rules use their format`() {
        val pack = PackLoaderValidator.parse(
            """
            {"id":"m","name":"M","baseUrl":"https://x.invalid","kind":"api",
             "search":{"endpoint":"/s","jsonPath":".","idPath":"link","titlePath":"title"},
             "detail":{"endpoint":"/d","format":"html","resultSelector":"body","titleSelector":"h1"}}
            """.trimIndent()
        ) ?: error("mixed pack must parse")

        val search = interpreter.parseSearch(
            pack,
            """[{"link":"/anime/stream/naruto","title":"<em>Naruto</em>"}]"""
        )
        assertEquals(1, search.size)
        assertEquals("naruto", search.first().id)
        assertEquals("Naruto", search.first().title)

        val detail = interpreter.parseDetail(pack, "<html><body><h1>Naruto</h1></body></html>")
        assertEquals("Naruto", detail?.title)
    }

    @Test
    fun `api detail and episodes use the detail rule`() {
        val pack = PackLoaderValidator.parse(
            """
            {"id":"a","name":"A","baseUrl":"https://x.invalid","kind":"api",
             "detail":{"endpoint":"/d","jsonPath":"widgets","titlePath":"title","yearPath":"year"},
             "episodes":{"endpoint":"/d","jsonPath":"widgets.0.teasers",
                         "idPath":"id","titlePath":"longTitle"}}
            """.trimIndent()
        ) ?: error("api pack must parse")

        val detail = """{"title":"Babylon Berlin","year":2017,"widgets":[]}"""
        assertEquals("Babylon Berlin", interpreter.parseDetailApi(pack, detail)?.title)
        assertEquals(2017, interpreter.parseDetailApi(pack, detail)?.year)

        val episodes = """
            {"widgets":[{"teasers":[
              {"id":"e1","longTitle":"Folge 1 · Staffel 1 | Babylon Berlin (S01/E01)"},
              {"id":"e2","longTitle":"Folge 2 · Staffel 1 | Babylon Berlin (S01/E02)"}
            ]}]}
        """.trimIndent()
        val parsed = interpreter.parseEpisodesApi(pack, episodes)
        assertEquals(2, parsed.size)
        assertEquals(1, parsed[0].season)
        assertEquals(1, parsed[0].episode)
        assertEquals(2, parsed[1].episode)
        assertEquals("e2", parsed[1].id)
    }
}