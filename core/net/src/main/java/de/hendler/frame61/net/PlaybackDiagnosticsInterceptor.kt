package de.hendler.frame61.net

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import okio.GzipSource
import okio.buffer

/** Records every active playback hop, including redirects, without consuming the body. */
class PlaybackDiagnosticsInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val sessionId = PlaybackDiagnostics.currentSessionId
        if (!PlaybackDiagnostics.isActive(sessionId)) return chain.proceed(request)
        val started = System.nanoTime()
        PlaybackDiagnostics.record(
            category = "HTTP",
            message = "${request.method} ${request.url.host}",
            sessionId = sessionId,
            url = request.url.toString(),
            headers = request.headers.toMap()
        )
        val response = try {
            chain.proceed(request)
        } catch (error: IOException) {
            PlaybackDiagnostics.record(
                category = "HTTP",
                message = "Netzwerkfehler: ${error.javaClass.simpleName}",
                sessionId = sessionId,
                url = request.url.toString()
            )
            throw error
        }
        if (!PlaybackDiagnostics.isActive(sessionId)) return response

        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        val sniff = runCatching { sniffForDiagnostics(response) }
        val firstBytes = sniff.getOrNull()
        PlaybackDiagnostics.record(
            category = "HTTP",
            message = "Antwort ${response.code}",
            sessionId = sessionId,
            url = response.request.url.toString(),
            status = response.code,
            headers = response.request.headers.toMap(),
            firstBytes = firstBytes,
            details = mapOf(
                "elapsedMs" to "$elapsedMs",
                "contentType" to (response.header("Content-Type") ?: "unknown"),
                "contentEncoding" to (response.header("Content-Encoding") ?: "identity"),
                "container" to (firstBytes?.let(ContainerSniffer::detect) ?: "SNIFF_FAILED"),
                "sniffBytes" to (firstBytes?.size?.toString() ?: "0"),
                "sniffError" to (sniff.exceptionOrNull()?.javaClass?.simpleName ?: "-"),
                "redirectFrom" to (response.priorResponse?.request?.url?.toString()?.let(PlaybackDiagnostics::safeUrl) ?: "-")
            )
        )
        return response
    }
}

internal fun sniffForDiagnostics(response: Response): ByteArray {
    val isGzip = response.header("Content-Encoding")?.equals("gzip", ignoreCase = true) == true
    val peekBytes = if (isGzip) MAX_GZIP_BYTES else MAX_SNIFF_BYTES
    val raw = response.peekBody(peekBytes.toLong()).use { it.bytes() }
    return if (isGzip) {
        GzipSource(Buffer().write(raw)).buffer().use { source ->
            source.request(MAX_SNIFF_BYTES.toLong())
            source.readByteArray(minOf(MAX_SNIFF_BYTES.toLong(), source.buffer.size))
        }
    } else {
        raw
    }
}

private const val MAX_SNIFF_BYTES = 377
private const val MAX_GZIP_BYTES = 65_536
