package de.hendler.frame61.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Test
import java.io.File

/**
 * Loads the shipped hoster packs from `:app`'s assets. The directory is handed
 * over as the system property `frame61.hostersDir` (set in the build file) — no
 * relative parent traversal.
 */
class HosterPackLoaderTest {

    private val hostersDir: File? = System.getProperty("frame61.hostersDir")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.isDirectory }

    private fun requireDir(): File =
        hostersDir ?: throw AssumptionViolatedException(
            "frame61.hostersDir not set or missing (value=${System.getProperty("frame61.hostersDir")})"
        )

    @Test
    fun `every shipped pack validates`() {
        val dir = requireDir()
        val loader = HosterPackLoader(dir)
        val files = loader.files()
        assertTrue("no hoster packs in $dir", files.isNotEmpty())

        val rejected = mutableListOf<String>()
        val loaded = files.mapNotNull { file ->
            val (pack, reason) = loader.loadWithReason(file)
            if (pack == null) rejected += "${file.name} → $reason"
            pack
        }

        println("hoster packs: ${loaded.size}/${files.size} valid, rejected=${rejected.ifEmpty { "-" }}")
        assertTrue("rejected packs:\n  " + rejected.joinToString("\n  "), rejected.isEmpty())
        assertEquals(files.size, loaded.size)
    }

    @Test
    fun `every pack declares domains and a result variable`() {
        val packs = HosterPackLoader(requireDir()).loadAll()
        packs.forEach { pack ->
            assertTrue("${pack.id}: domains fehlen", pack.domains.isNotEmpty())
            assertTrue(
                "${pack.id}: result '${pack.result}' nicht in steps",
                pack.result.isNullOrBlank() || pack.steps.any { it.saveAs == pack.result }
            )
        }
    }

    @Test
    fun `validation rejects a broken pack on its own`() {
        val dir = requireDir()
        val broken = File(dir.parentFile, "broken-hosters")
        broken.mkdirs()
        try {
            File(broken, "kaputt.json").writeText(
                """{"id":"k","name":"K","domains":[],"steps":[{"action":"gibtsnicht"}]}"""
            )
            File(broken, "gut.json").writeText(
                """{"id":"g","name":"G","domains":["g.io"],"result":"f",
                    "steps":[{"action":"get","url":"https://{host}/{path}","saveAs":"f"}]}"""
            )
            val loaded = HosterPackLoader(broken).loadAll()
            assertEquals(listOf("g"), loaded.map { it.id })
        } finally {
            broken.deleteRecursively()
        }
    }
}