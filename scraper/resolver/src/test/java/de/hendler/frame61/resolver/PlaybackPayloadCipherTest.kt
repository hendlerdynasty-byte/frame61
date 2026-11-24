package de.hendler.frame61.resolver

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Belege fuer den Filemoon-Playback-Block (24.11.2026).
 *
 * Der Klartext der echten Aufzeichnung wird hier **nicht** als Klartext
 * mitgeschleppt: der Test entschluesselt den aufgezeichneten Block mit der
 * Rekonstruktion aus dem Player-Bundle der Seite. So faellt jede Abweichung
 * in Schluesselauswahl oder Layout sofort auf.
 *
 * Die Aufzeichnung ist eine echte Live-Antwort; ihre `version` wechselt von
 * Abruf zu Abruf. Die Tests lesen sie deshalb aus der Datei statt sie
 * festzuschreiben.
 */
class PlaybackPayloadCipherTest {

    private val recordedBody: String by lazy {
        val file = File(
            System.getProperty("user.dir"),
            "src/test/resources/fixtures-hosters/filemoon/page.body.html"
        )
        require(file.isFile) { "Fixture fehlt: $file" }
        file.readText()
    }

    private val playback: JsonObject by lazy {
        (Json.parseToJsonElement(recordedBody) as JsonObject)["playback"] as JsonObject
    }

    private val recordedVersion: Int
        get() = (playback["version"] as JsonPrimitive).contentOrNull!!.toInt()

    private fun firstSourceUrl(plain: String): String {
        val sources = Json.parseToJsonElement(plain).jsonObject["sources"]!!.jsonArray
        return sources.first().jsonObject["url"]!!.jsonPrimitive.contentOrNull!!
    }

    @Test
    fun `recorded playback block decrypts to the m3u8 source`() {
        val plain = PlaybackPayloadCipher.decrypt(recordedBody)
        assertNotNull("Playback-Block liess sich nicht entschluesseln", plain)
        val url = firstSourceUrl(plain!!)
        assertTrue("m3u8 fehlt im Klartext: $url", url.startsWith("https://"))
        assertTrue("m3u8-URL endet nicht auf master.m3u8: $url", url.contains("/master.m3u8"))
    }

    @Test
    fun `decrypted url keeps real ampersands instead of json escapes`() {
        val plain = PlaybackPayloadCipher.decrypt(recordedBody)!!
        // Der Klartext escaped die Trennzeichen der URL als \u0026 — der
        // JSON-Parser muss daraus echte '&' machen, sonst ist die m3u8
        // fuer den Player kaputt.
        assertTrue("Klartext sollte die Escape-Sequenz enthalten", plain.contains("\\u0026"))
        val url = firstSourceUrl(plain)
        assertFalse("URL enthaelt noch die Escape-Sequenz: $url", url.contains("\\u0026"))
        assertTrue("URL hat keine echten Trennzeichen: $url", url.contains("&s="))
    }

    @Test
    fun `another valid version selects the wrong key and fails closed`() {
        // Die Auswahl {v, 31-v} ist bijektiv: v und 31-v waehlen dieselben
        // Teile. Gesucht ist deshalb eine Version mit anderem Paar.
        val current = PlaybackPayloadCipher.permutation(recordedVersion, 30).toSet()
        val other = (1..20).first { candidate ->
            PlaybackPayloadCipher.permutation(candidate, 30).toSet() != current
        }
        val body = recordedBody.replace(
            "\"version\":\"$recordedVersion\"",
            "\"version\":\"$other\""
        )
        assertFalse("Version wurde nicht ersetzt", body == recordedBody)
        assertNull("Falsche Version muss scheitern", PlaybackPayloadCipher.decrypt(body))
    }

    @Test
    fun `an unusable version falls back to all parts and still fails closed`() {
        val body = recordedBody.replace("\"version\":\"$recordedVersion\"", "\"version\":\"0\"")
        assertFalse("Version wurde nicht ersetzt", body == recordedBody)
        assertNull("Unbrauchbare Version muss scheitern", PlaybackPayloadCipher.decrypt(body))
    }

    @Test
    fun `tampered payload is rejected by the gcm tag`() {
        val body = recordedBody.replace("\"payload\":\"", "\"payload\":\"A")
        assertNull("Manipulierte Nutzlast muss scheitern", PlaybackPayloadCipher.decrypt(body))
    }

    @Test
    fun `malformed input never throws`() {
        assertNull(PlaybackPayloadCipher.decrypt(""))
        assertNull(PlaybackPayloadCipher.decrypt("kein json"))
        assertNull(PlaybackPayloadCipher.decrypt("{}"))
        assertNull(PlaybackPayloadCipher.decrypt("{\"playback\":{}}"))
        assertNull(
            PlaybackPayloadCipher.decrypt(
                "{\"playback\":{\"iv\":\"###\",\"payload\":\"###\",\"key_parts\":[\"###\"],\"version\":\"9\"}}"
            )
        )
    }

    @Test
    fun `key selection follows the player bundle`() {
        // version v -> Teile v und 31-v (1-basiert), sonst alle Teile.
        assertEquals(listOf(9, 22), PlaybackPayloadCipher.permutation(9, 30))
        assertEquals(listOf(1, 30), PlaybackPayloadCipher.permutation(1, 30))
        assertEquals(listOf(20, 11), PlaybackPayloadCipher.permutation(20, 30))
        assertTrue(PlaybackPayloadCipher.permutation(0, 30).isEmpty())
        assertTrue(PlaybackPayloadCipher.permutation(21, 30).isEmpty())
        // Teile ausserhalb der Liste -> Rueckfall auf alle Teile.
        assertTrue(PlaybackPayloadCipher.permutation(9, 5).isEmpty())
    }

    @Test
    fun `base64url decoding accepts both alphabets and rejects garbage`() {
        assertEquals("hi", String(PlaybackPayloadCipher.base64UrlDecode("aGk")!!))
        assertEquals("hi", String(PlaybackPayloadCipher.base64UrlDecode("aGk=")!!))
        // '-' und '_' stehen fuer '+' und '/'.
        val bytes = PlaybackPayloadCipher.base64UrlDecode("-_8")!!
        assertEquals(listOf(-5, -1), bytes.map { it.toInt() })
        assertNull(PlaybackPayloadCipher.base64UrlDecode("aGk!"))
    }

    @Test
    fun `key material falls back to all parts when the version does not fit`() {
        val part = "QUFBQUFBQUFBQUFBQUFBQQ" // 16 x 0x41, base64url ohne Padding
        // Ohne passende Version greift der Rueckfall auf alle Teile.
        val all = PlaybackPayloadCipher.keyMaterial(listOf(part, part, part), 0)
        assertNull("Drei Teile ergeben keinen gueltigen Schluessel", all)
        // Zwei Teile ergeben einen gueltigen 32-Byte-Schluessel.
        val two = PlaybackPayloadCipher.keyMaterial(listOf(part, part), 1)
        assertEquals(32, two!!.size)
        // Ein Teil allein ist ein gueltiger 16-Byte-Schluessel.
        assertEquals(16, PlaybackPayloadCipher.keyMaterial(listOf(part), 1)!!.size)
    }

    @Test
    fun `aesgcm pack step reads exactly the recorded fields`() {
        val version = (playback["version"] as JsonPrimitive).contentOrNull
        assertTrue("Version fehlt im Block", !version.isNullOrBlank())
        // Test-vector: der Block traegt genau die Felder, die der Schritt liest.
        val minimal = buildJsonObject {
            put("playback", buildJsonObject {
                put("iv", (playback["iv"] as JsonPrimitive).contentOrNull!!)
                put("payload", (playback["payload"] as JsonPrimitive).contentOrNull!!)
                put("key_parts", playback["key_parts"]!!)
                put("version", version!!)
            })
        }
        assertEquals(
            PlaybackPayloadCipher.decrypt(recordedBody),
            PlaybackPayloadCipher.decrypt(minimal.toString())
        )
    }

    @Test
    fun `playback block exposes the fields the pack needs`() {
        assertTrue(playback.keys.containsAll(listOf("iv", "payload", "key_parts", "version")))
        val parts = playback["key_parts"] as JsonArray
        assertTrue("Zu wenige Schluesselteile: ${parts.size}", parts.size >= 2)
        // Die beiden Teile der Version muessen im Block vorhanden sein.
        val order = PlaybackPayloadCipher.permutation(recordedVersion, parts.size)
        assertTrue("Permutation passt nicht zum Block", order.isNotEmpty())
        order.forEach { index ->
            assertTrue("Teil $index fehlt", parts.getOrNull(index - 1) != null)
        }
    }
}
