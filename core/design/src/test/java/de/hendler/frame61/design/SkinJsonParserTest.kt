package de.hendler.frame61.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Test
import java.io.File

/**
 * Proves the 11 skin files are actually parsed into different tokens.
 *
 * The pack directory is handed over as the system property `frame61.skinsDir`
 * (set in this module's build file) — no relative parent traversal.
 */
class SkinJsonParserTest {

    private val skinsDir: File? = System.getProperty("frame61.skinsDir")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.isDirectory }

    private fun requireDir(): File =
        skinsDir ?: throw AssumptionViolatedException(
            "frame61.skinsDir not set or missing (value=${System.getProperty("frame61.skinsDir")})"
        )

    private val parser = SkinJsonParser()

    @Test
    fun `all skins parse`() {
        val dir = requireDir()
        val files = dir.listFiles { f -> f.extension == "json" && !f.name.startsWith("._") }
            ?.sortedBy { it.name }
            ?: emptyList()
        assertTrue("no skin files in $dir", files.size >= 11)

        val skins = files.mapNotNull { f -> parser.parse(f.readText()) }
        assertEquals("not every skin file parsed", files.size, skins.size)
        assertTrue("expected at least 12 skins, got ${skins.size}", skins.size >= 12)
    }

    @Test
    fun `skins differ in real tokens not just in id`() {
        val skins = FileSkinManager(requireDir()).loadAll()
        assertTrue(skins.size >= 12)

        val canvases = skins.map { it.colors.canvas.toHex() }.toSet()
        val accents = skins.map { it.colors.primary.toHex() }.toSet()
        assertTrue("expected distinct canvases, got $canvases", canvases.size >= 8)
        assertTrue("expected distinct accents, got $accents", accents.size >= 8)

        // the brand skin must not silently be the same object as a named skin
        val frame61 = skins.first { it.id == "frame61" }
        assertNotEquals(
            "frame61 must carry its own accent",
            skins.first { it.id == "netflix" }.colors.primary,
            frame61.colors.primary
        )
        assertEquals("#060913", frame61.colors.canvas.toHex())
        assertEquals("#FF6B35", frame61.colors.primary.toHex())

        // the indigo variant stays an equal alternative, not a copy
        val indigo = skins.first { it.id == "frame61-indigo" }
        assertEquals("#0D0E12", indigo.colors.canvas.toHex())
        assertEquals("#5B6AFF", indigo.colors.primary.toHex())
        assertNotEquals(frame61.colors.primary, indigo.colors.primary)
    }

    @Test
    fun `layout and motion tokens are mapped`() {
        val skins = FileSkinManager(requireDir()).loadAll()
        val frame61 = skins.first { it.id == "frame61" }

        assertEquals(420, frame61.layout.billboardHeight)
        assertEquals(180, frame61.layout.rowHeight)
        assertEquals(12, frame61.layout.gutter)
        assertEquals(0.6f, frame61.layout.cardAspectRatio)
        assertEquals("vertical", frame61.layout.detailLayout)
        assertEquals("dark", frame61.layout.playerChrome)
        assertEquals(MotionLevel.HIGH, frame61.motionLevel)
        assertEquals(1.0f, frame61.typography.typeScale)
        assertEquals("Frame61", frame61.name)
    }

    @Test
    fun `motion level and hex parsing are tolerant`() {
        assertEquals(MotionLevel.NONE, SkinJsonParser.parseMotionLevel("off"))
        assertEquals(MotionLevel.HIGH, SkinJsonParser.parseMotionLevel("high"))
        assertEquals(MotionLevel.MEDIUM, SkinJsonParser.parseMotionLevel("unbekannt"))

        val color = SkinJsonParser.color("#FF6B35")
        assertEquals(1f, color.red, 0.001f)
        assertEquals(0x6B / 255f, color.green, 0.01f)
        assertEquals(0x35 / 255f, color.blue, 0.01f)

        // short and broken input must not throw
        assertNotNull(SkinJsonParser.color("abc"))
        assertNotNull(SkinJsonParser.color(""))
    }

    @Test
    fun `a broken skin is dropped on its own`() {
        val dir = requireDir()
        val broken = File(dir.parentFile, "broken-skins")
        broken.mkdirs()
        try {
            File(broken, "kaputt.json").writeText("""{"id":"", "canvas":"#000000"}""")
            File(broken, "gut.json").writeText(
                """{"id":"gut","canvas":"#101010","accents":"#FF0000"}"""
            )
            val skins = FileSkinManager(broken).loadAll()
            assertEquals(listOf("gut"), skins.map { it.id })
        } finally {
            broken.deleteRecursively()
        }
    }
}