package de.hendler.frame61.net

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/** Records every network hop, including redirects, without consuming the body. */
class PlaybackDiagnosticsInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val started = System.nanoTime()
        PlaybackDiagnostics.record(
            category = "HTTP",
            message = "${request.method} ${request.url.host}",
            url = request.url.toString(),
            headers = request.headers.toMap()
        )
        val response = try {
            chain.proceed(request)
        } catch (error: IOException) {
            PlaybackDiagnostics.record(
                category = "HTTP",
                message = "Netzwerkfehler: ${error.message ?: error.javaClass.simpleName}",
                url = request.url.toString()
            )
            throw error
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        val firstBytes = runCatching {
            response.peekBody(MAX_SNIFF_BYTES.toLong()).use { it.bytes() }
        }.getOrElse { ByteArray(0) }
        PlaybackDiagnostics.record(
            category = "HTTP",
            message = "Antwort ${response.code}",
            url = response.request.url.toString(),
            status = response.code,
            headers = response.request.headers.toMap(),
            firstBytes = firstBytes,
            details = mapOf(
                "elapsedMs" to "$elapsedMs",
                "contentType" to (response.header("Content-Type") ?: "unknown"),
                "container" to ContainerSniffer.detect(firstBytes),
                "redirectFrom" to (response.priorResponse?.request?.url?.toString()?.let(PlaybackDiagnostics::safeUrl) ?: "-")
            )
        )
        return response
    }

    private companion object {
        const val MAX_SNIFF_BYTES = 377
    }
}
