package de.hendler.frame61.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The manifest parser must never throw and never invent fields. */
class UpdateManifestTest {

    private val valid = """
        {"versionCode": 2, "versionName": "1.1.0",
         "apkUrl": "https://example.invalid/frame61-2.apk",
         "sha256": "abc123", "notes": "Neu"}
    """.trimIndent()

    @Test
    fun `valid manifest parses`() {
        val manifest = UpdateManifest.parse(valid)
        assertEquals(2, manifest?.versionCode)
        assertEquals("1.1.0", manifest?.versionName)
        assertEquals("https://example.invalid/frame61-2.apk", manifest?.apkUrl)
        assertEquals("abc123", manifest?.sha256)
        assertEquals("Neu", manifest?.notes)
    }

    @Test
    fun `broken json is null, not a crash`() {
        assertNull(UpdateManifest.parse("{"))
        assertNull(UpdateManifest.parse(""))
        assertNull(UpdateManifest.parse("garbage"))
    }

    @Test
    fun `missing version code or url is invalid`() {
        assertNull(UpdateManifest.parse("""{"versionName":"1.0","apkUrl":"https://x/y.apk"}"""))
        assertNull(UpdateManifest.parse("""{"versionCode":3}"""))
        assertNull(UpdateManifest.parse("""{"versionCode":3,"apkUrl":""}"""))
    }

    @Test
    fun `non-http apk url is rejected`() {
        assertNull(UpdateManifest.parse("""{"versionCode":3,"apkUrl":"ftp://x/y.apk"}"""))
        assertNull(UpdateManifest.parse("""{"versionCode":3,"apkUrl":"/local.apk"}"""))
    }

    @Test
    fun `optional fields stay absent`() {
        val manifest = UpdateManifest.parse("""{"versionCode":3,"apkUrl":"https://x/y.apk"}""")
        // versionName falls back to the version code, it is never blank
        assertEquals("3", manifest?.versionName)
        assertTrue(manifest?.sha256 == null)
        assertTrue(manifest?.notes == null)
    }
}