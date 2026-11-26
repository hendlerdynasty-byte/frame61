package de.hendler.frame61.diagnostics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathCatalogValidatorTest {
    private val repoRoot: File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `committed manifest is exactly twenty known aniworld ids`() {
        val report = validateCommittedManifest()

        assertTrue(report.issues.joinToString("\n"), report.isValid)
        assertEquals(20, report.ids.size)
        assertEquals(20, report.ids.toSet().size)
        assertTrue(report.ids.all { it.startsWith("aw:") })
        assertTrue("aw:slam-dunk" in report.ids)
        assertFalse("aw:death-note" in report.ids)
    }

    @Test
    fun `wrong count duplicate prefix and missing catalog id fail`() {
        val manifest = manifest(ids = listOf("aw:known", "aw:known", "aw:missing", "ss:wrong"))
        val catalog = catalog("""[{"id":"aw:known","title":"Known","group":"aniworld","sourceKey":"known"}]""")

        val report = PathCatalogValidator.validate(manifest, catalog)
        val codes = report.issues.map { it.code }.toSet()

        assertFalse(report.isValid)
        assertTrue("SAMPLE_COUNT" in codes)
        assertTrue("ID_DUPLICATE" in codes)
        assertTrue("ID_FORMAT" in codes)
        assertTrue("CATALOG_MISSING" in codes)
    }

    @Test
    fun `catalog group and source key must match the aniworld id`() {
        val ids = List(20) { "aw:known-$it" }
        val manifest = manifest(ids = ids)
        val items = ids.joinToString(",", "[", "]") { id ->
            """{"id":"$id","title":"Known","group":"other","sourceKey":"different"}"""
        }

        val report = PathCatalogValidator.validate(manifest, catalog(items))

        assertTrue(report.issues.any { it.code == "CATALOG_GROUP" })
        assertTrue(report.issues.any { it.code == "CATALOG_SOURCE_KEY" })
    }

    @Test
    fun `malformed json is reported without throwing`() {
        val report = PathCatalogValidator.validate("{", "not-json")

        assertFalse(report.isValid)
        assertEquals(setOf("MANIFEST_JSON", "CATALOG_JSON"), report.issues.map { it.code }.toSet())
    }

    private fun validateCommittedManifest(): PathCatalogValidator.Report = PathCatalogValidator.validate(
        File(repoRoot, "app/src/perf/assets/catalog/aniworld-path.json").readText(),
        File(repoRoot, "app/src/main/assets/catalog/catalog.json").readText()
    )

    private fun manifest(ids: List<String>): String = """
        {"schema":1,"sourceId":"aniworld","expectedCount":20,"ids":[${ids.joinToString(",") { "\"$it\"" }}]}
    """.trimIndent()

    private fun catalog(items: String): String = """{"items":$items}"""
}
