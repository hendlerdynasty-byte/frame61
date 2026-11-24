package de.hendler.frame61.resolver

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Liest den verschlüsselten `playback`-Block einer Videoseite und gibt den
 * Klartext (JSON mit `sources[]`) zurück.
 *
 * **Beleg 24.11.2026 (Live-Abruf + Player-Bundle der Seite):**
 * `GET https://filemoon.to/api/videos/{code}` antwortet mit
 * ```json
 * {"playback":{"algorithm":"AES-256-GCM","iv":"…","payload":"…",
 *              "key_parts":["…","…"],"version":"7","expires_at":"…"}}
 * ```
 * Der Schlüssel ist **nicht** der mitgelieferte Text: das Player-Bundle wählt
 * genau **zwei** Teile aus — `key_parts[version-1]` und
 * `key_parts[30-version]` — und erst deren zusammengefügte Bytes ergeben den
 * 32-Byte-Schlüssel. Der Bundle-Automat nennt diese Auswahl
 * `permutation(version, anzahl)`; ohne gültige Version (1…20) greift er auf
 * **alle** Teile zurück. Das ist hier 1:1 nachgebaut.
 *
 * `payload` ist Base64url und enthält — wie bei WebCrypto — Chiffretext **und**
 * GCM-Tag (16 Byte) hintereinander; `iv` ist Base64url mit 12 Byte.
 */
object PlaybackPayloadCipher {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /**
     * Entschlüsselt den `playback`-Block eines Video-JSON.
     * `null` heißt: kein Block, kein Schlüssel oder kein gültiger Klartext.
     */
    fun decrypt(videoJson: String): String? {
        val root = runCatching { json.parseToJsonElement(videoJson) }.getOrNull() as? JsonObject
            ?: return null
        val playback = root["playback"] as? JsonObject ?: return null
        val iv = (playback["iv"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val payload = (playback["payload"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val version = (playback["version"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val parts = (playback["key_parts"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (iv.isEmpty() || payload.isEmpty() || parts.isEmpty()) return null
        val key = keyMaterial(parts, version.toIntOrNull() ?: 0) ?: return null
        val ivBytes = base64UrlDecode(iv) ?: return null
        val payloadBytes = base64UrlDecode(payload) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, ivBytes)
            )
            String(cipher.doFinal(payloadBytes), Charsets.UTF_8)
        }.getOrNull()
    }

    /**
     * Die Teile, aus denen der Schlüssel besteht — 1:1 die Auswahl des
     * Player-Bundles: bei gültiger Version zwei Teile, sonst alle.
     */
    internal fun keyMaterial(parts: List<String>, version: Int): ByteArray? {
        val order = permutation(version, parts.size)
        val picked = if (order.isEmpty()) {
            parts
        } else {
            order.mapNotNull { index -> parts.getOrNull(index - 1) }
                .filter { it.isNotEmpty() }
                .ifEmpty { parts }
        }
        val bytes = picked.mapNotNull { base64UrlDecode(it) }
        if (bytes.isEmpty()) return null
        val key = bytes.fold(ByteArray(0)) { acc, part -> acc + part }
        return key.takeIf { it.size == 16 || it.size == 24 || it.size == 32 }
    }

    /** `version` -> `[version, 31 - version]`, 1-basiert; leer bei Unbrauchbarkeit. */
    internal fun permutation(version: Int, count: Int): List<Int> {
        if (version !in 1..20) return emptyList()
        val first = version
        val second = 31 - version
        if (first < 1 || second < 1 || first > count || second > count) return emptyList()
        return listOf(first, second)
    }

    /**
     * Base64url ohne Abhängigkeit von `java.util.Base64` (erst ab API 26;
     * die App steht bei minSdk 23) — daher von Hand, aber streng: unbekannte
     * Zeichen machen die ganze Dekodierung ungültig.
     */
    internal fun base64UrlDecode(text: String): ByteArray? {
        val clean = text.trim().replace('-', '+').replace('_', '/')
        if (clean.any { it !in ALPHABET && it != '=' }) return null
        val padding = (4 - clean.length % 4) % 4
        val padded = clean + "=".repeat(padding)
        if (padded.length % 4 != 0) return null
        val out = java.io.ByteArrayOutputStream(padded.length / 4 * 3)
        var index = 0
        while (index < padded.length) {
            val chunk = padded.substring(index, index + 4)
            index += 4
            val a = ALPHABET.indexOf(chunk[0])
            val b = ALPHABET.indexOf(chunk[1])
            val c = if (chunk[2] == '=') -1 else ALPHABET.indexOf(chunk[2])
            val d = if (chunk[3] == '=') -1 else ALPHABET.indexOf(chunk[3])
            if (a < 0 || b < 0 || c == -2 || d == -2) return null
            out.write((a shl 2) or (b shr 4))
            if (c >= 0) {
                out.write(((b and 0x0f) shl 4) or (c shr 2))
                if (d >= 0) out.write(((c and 0x03) shl 6) or d)
            }
        }
        return out.toByteArray()
    }

    private const val TAG_BITS = 128
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
}
