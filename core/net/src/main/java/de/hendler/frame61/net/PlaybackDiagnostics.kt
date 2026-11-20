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
        if (bytes.size >= 12 && bytes.asciiAt(4, "ftyp")) return "MP4"
        if (bytes.startsWith(0x1A, 0x45, 0xDF, 0xA3)) return "MKV"
        if (bytes.size >= 12 && bytes.asciiAt(0, "RIFF") && bytes.asciiAt(8, "AVI ")) return "AVI"
        if (bytes.size >= 12 && bytes.asciiAt(0, "RIFF") && bytes.asciiAt(8, "WAVE")) return "WAV"
        if (text.startsWith("FLV")) return "FLV"
        if (text.startsWith("OggS")) return "OGG"
        if (bytes.startsWith(0x30, 0x26, 0xB2, 0x75)) return "ASF"
        if (bytes.startsWith(0x2E, 0x52, 0x4D, 0x46)) return "REALMEDIA"
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
 * No event is persisted or sent anywhere. Late events from another session are
 * dropped, and textual response bodies are never duplicated into the trace.
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

    @Volatile
    private var active = false

    /** Optional Android log bridge; tests and non-UI callers can leave it null. */
    @Volatile
    var sink: ((PlaybackTraceEvent) -> Unit)? = null

    fun beginSession(title: String, episodeNumber: Int): Long {
        val id = sessionIds.incrementAndGet()
        synchronized(lock) {
            currentSessionId = id
            active = true
            _events.value = emptyList()
        }
        record(
            "SESSION",
            "Wiedergabe gestartet",
            sessionId = id,
            details = mapOf("title" to title, "episode" to "$episodeNumber")
        )
        return id
    }

    fun endSession(sessionId: Long) {
        record("SESSION", "Wiedergabe beendet", sessionId = sessionId)
        val ended = synchronized(lock) {
            if (currentSessionId == sessionId && active) {
                active = false
                true
            } else {
                false
            }
        }
        if (ended) sink = null
    }

    fun isActive(sessionId: Long): Boolean = active && sessionId != 0L && sessionId == currentSessionId

    fun record(
        category: String,
        message: String,
        sessionId: Long = currentSessionId,
        url: String? = null,
        status: Int? = null,
        headers: Map<String, String> = emptyMap(),
        firstBytes: ByteArray? = null,
        details: Map<String, String> = emptyMap()
    ) {
        val safeBytes = firstBytes?.let(::safeBinarySignature)
        val event = PlaybackTraceEvent(
            timestampMs = System.currentTimeMillis(),
            sessionId = sessionId,
            category = category,
            message = safeMessage(message).take(240),
            url = url?.let(::safeUrl),
            status = status,
            headers = safeHeaders(headers),
            bytesHex = safeBytes?.hex,
            bytesPreview = safeBytes?.preview,
            details = details.mapValues { (_, value) -> safeMessage(value).take(240) }
        )
        synchronized(lock) {
            if (!isActive(sessionId)) return
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
                name.equals("Referer", ignoreCase = true) -> safeUrl(value)
                else -> safeMessage(value).take(240)
            }
        }

    fun safeMessage(raw: String): String {
        val withoutUserInfo = USER_INFO.replace(raw) { match -> match.groupValues[1] }
        val withoutRawUrls = URL_PATTERN.replace(withoutUserInfo) { match -> safeUrl(match.value) }
        val withoutBearer = BEARER_PATTERN.replace(withoutRawUrls) { match ->
            "${match.groupValues[1]}sha256:${fingerprint(match.groupValues[2])}"
        }
        return SENSITIVE_ASSIGNMENT.replace(withoutBearer) { match ->
            "${match.groupValues[1]}=sha256:${fingerprint(match.groupValues[2])}"
        }
    }

    fun safeUrl(raw: String): String {
        val withoutUserInfo = USER_INFO.replace(raw) { match -> match.groupValues[1] }
        val queryStart = withoutUserInfo.indexOf('?')
        if (queryStart < 0) return withoutUserInfo.substringBefore('#').take(500)
        val base = withoutUserInfo.substring(0, queryStart)
        val query = withoutUserInfo.substring(queryStart + 1).substringBefore('#')
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

    private data class SafeBytes(val hex: String? = null, val preview: String? = null)

    private fun safeBinarySignature(bytes: ByteArray): SafeBytes = when (ContainerSniffer.detect(bytes)) {
        "HLS" -> SafeBytes(preview = "#EXTM3U [weitere Textbytes redigiert]")
        "MP4", "MKV", "AVI", "MPEG-TS", "MPEG-PS", "FLV", "OGG", "MP3", "ASF", "REALMEDIA" ->
            SafeBytes(hex = bytes.take(32).joinToString("") { "%02X".format(it) })
        else -> SafeBytes()
    }

    private fun fingerprint(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .take(6).joinToString("") { "%02x".format(it) }

    private val URL_PATTERN = Regex("""https?://[^\s<>"']+""")
    private val USER_INFO = Regex("""(?i)(https?://)[^/@\s]+@""")
    private val BEARER_PATTERN = Regex("""(?i)(bearer\s+)([A-Za-z0-9._~+/-]+=?)""")
    private val SENSITIVE_ASSIGNMENT = Regex(
        """(?i)\b(token|auth|signature|sig|jwt|session|cookie|credential|password|key|hdnts|hdnea|policy)=([^\s&;,}]+)"""
    )
    private val SENSITIVE_HEADERS = setOf(
        "Authorization", "Proxy-Authorization", "Set-Cookie", "X-Api-Key", "X-Auth-Token"
    )
    private val SENSITIVE_QUERY_KEYS = setOf(
        "token", "auth", "signature", "sig", "jwt", "session", "cookie", "credential",
        "password", "key", "hdnts", "hdnea", "policy"
    )
}
