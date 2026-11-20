package de.hendler.frame61.net

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One bounded, local-only event in the current playback session. */
data class PlaybackTraceEvent(
    val timestampMs: Long,
    val sessionId: Long,
    val category: String,
    val message: String,
    val url: String? = null,
    val status: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val bytesHex: String? = null,
    val bytesPreview: String? = null,
    val details: Map<String, String> = emptyMap()
) {
    fun asLogLine(): String = buildString {
        append("session=").append(sessionId)
        append(' ').append(category).append(": ").append(message)
        url?.let { append(" url=").append(it) }
        status?.let { append(" status=").append(it) }
        if (headers.isNotEmpty()) append(" headers=").append(headers)
        bytesHex?.let { append(" firstBytes=").append(it) }
        bytesPreview?.let { append(" preview='").append(it).append('\'') }
        if (details.isNotEmpty()) append(" details=").append(details)
    }
}

/** Byte-level identification; response sniffing never consumes the media body. */
object ContainerSniffer {
    fun detect(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "EMPTY"
        val text = bytes.decodeToString(0, minOf(bytes.size, 512), throwOnInvalidSequence = false)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        val lower = text.take(512).lowercase()
        if (text.startsWith("#EXTM3U")) return "HLS"
        if (lower.startsWith("<!doctype html") || lower.startsWith("<html") ||
            lower.startsWith("<head") || lower.startsWith("<body")
        ) return "HTML"
        if (lower.startsWith("{") || lower.startsWith("[")) return "JSON"
        if (bytes.size >= 12 && bytes[4].toInt().toChar() == 'f' &&
            bytes[5].toInt().toChar() == 't' && bytes[6].toInt().toChar() == 'y' &&
            bytes[7].toInt().toChar() == 'p'
        ) return "MP4"
        if (bytes.startsWith(0x1A, 0x45, 0xDF, 0xA3)) return "MKV"
        if (bytes.size >= 12 && bytes.asciiAt(0, "RIFF") && bytes.asciiAt(8, "AVI ")) {
            return "AVI"
        }
        if (text.startsWith("FLV")) return "FLV"
        if (text.startsWith("OggS")) return "OGG"
        if (bytes.startsWith(0x00, 0x00, 0x01, 0xBA)) return "MPEG-PS"
        if (bytes.startsWith(0x47) && bytes.size > 188 && bytes[188].toInt() == 0x47) {
            return "MPEG-TS"
        }
        if (text.startsWith("ID3")) return "MP3"
        return "UNKNOWN"
    }

    private fun ByteArray.startsWith(vararg values: Int): Boolean =
        size >= values.size && values.indices.all { (this[it].toInt() and 0xFF) == values[it] }

    private fun ByteArray.asciiAt(offset: Int, expected: String): Boolean =
        expected.indices.all { this[offset + it].toInt() == expected[it].code }
}

/**
 * Process-local diagnostics shared by resolver, network and player modules.
 * No event is persisted or sent anywhere. Query tokens and cookie values are
 * represented by a short SHA-256 fingerprint so hops remain comparable.
 */
object PlaybackDiagnostics {
    private const val MAX_EVENTS = 500
    private val lock = Any()
    private val sessionIds = AtomicLong(0L)
    private val _events = MutableStateFlow<List<PlaybackTraceEvent>>(emptyList())
    val events: StateFlow<List<PlaybackTraceEvent>> = _events.asStateFlow()

    @Volatile
    var currentSessionId: Long = 0L
        private set

    /** Optional Android log bridge; tests and non-UI callers can leave it null. */
    @Volatile
    var sink: ((PlaybackTraceEvent) -> Unit)? = null

    fun beginSession(title: String, episodeNumber: Int): Long {
        val id = sessionIds.incrementAndGet()
        currentSessionId = id
        synchronized(lock) { _events.value = emptyList() }
        record("SESSION", "Wiedergabe gestartet", details = mapOf("title" to title, "episode" to "$episodeNumber"))
        return id
    }

    fun record(
        category: String,
        message: String,
        url: String? = null,
        status: Int? = null,
        headers: Map<String, String> = emptyMap(),
        firstBytes: ByteArray? = null,
        details: Map<String, String> = emptyMap()
    ) {
        val event = PlaybackTraceEvent(
            timestampMs = System.currentTimeMillis(),
            sessionId = currentSessionId,
            category = category,
            message = message.take(240),
            url = url?.let(::safeUrl),
            status = status,
            headers = safeHeaders(headers),
            bytesHex = firstBytes?.take(32)?.joinToString("") { "%02X".format(it) },
            bytesPreview = firstBytes?.let(::safePreview),
            details = details.mapValues { (_, value) -> value.take(240) }
        )
        synchronized(lock) {
            _events.value = (_events.value + event).takeLast(MAX_EVENTS)
        }
        runCatching { sink?.invoke(event) }
    }

    fun snapshotText(maxEvents: Int = 100): String = synchronized(lock) {
        _events.value.takeLast(maxEvents).joinToString("\n") { it.asLogLine() }
    }

    fun clear() {
        synchronized(lock) { _events.value = emptyList() }
    }

    fun safeHeaders(headers: Map<String, String>): Map<String, String> =
        headers.mapValues { (name, value) ->
            when {
                name.equals("Cookie", ignoreCase = true) -> {
                    val names = value.split(';').mapNotNull {
                        it.substringBefore('=').trim().takeIf(String::isNotBlank)
                    }.sorted()
                    "names=${names.joinToString(",")};sha256=${fingerprint(value)}"
                }
                SENSITIVE_HEADERS.any { name.equals(it, ignoreCase = true) } ->
                    "sha256=${fingerprint(value)}"
                else -> value.take(240)
            }
        }

    fun safeUrl(raw: String): String {
        val queryStart = raw.indexOf('?')
        if (queryStart < 0) return raw.substringBefore('#').take(500)
        val base = raw.substring(0, queryStart)
        val query = raw.substring(queryStart + 1).substringBefore('#')
        val safeQuery = query.split('&').joinToString("&") { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            if (value.isEmpty()) key
            else if (SENSITIVE_QUERY_KEYS.any { key.contains(it, ignoreCase = true) }) {
                "$key=sha256:${fingerprint(value)}"
            } else {
                "$key=${value.take(120)}"
            }
        }
        return "$base?$safeQuery".take(500)
    }

    private fun safePreview(bytes: ByteArray): String = bytes.take(64).joinToString("") { value ->
        val code = value.toInt() and 0xFF
        when {
            code in 32..126 -> code.toChar().toString()
            code == 10 || code == 13 || code == 9 -> " "
            else -> "."
        }
    }

    private fun fingerprint(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .take(6).joinToString("") { "%02x".format(it) }

    private val SENSITIVE_HEADERS = setOf(
        "Authorization", "Proxy-Authorization", "Set-Cookie", "X-Api-Key", "X-Auth-Token"
    )
    private val SENSITIVE_QUERY_KEYS = setOf(
        "token", "auth", "signature", "sig", "jwt", "session", "cookie", "credential",
        "password", "key", "hdnts", "hdnea", "policy"
    )
}
