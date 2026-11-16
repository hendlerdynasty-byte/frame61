package de.hendler.frame61.net

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp interceptor that logs request/response chains.
 * Named "Chainlink" to match the Frame61 branding.
 */
class ChainlinkLoggingInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startNs = System.nanoTime()
        val response = chain.proceed(request)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000

        val status = response.code
        val isOk = status in 200..299

        val protocol = response.protocol?.toString() ?: "unknown"
        val bodySize = response.body?.contentLength() ?: -1

        // Log format: [Chainlink] method url → status (elapsed ms, protocol, body)
        android.util.Log.d(
            "Chainlink",
            "$request ${response.code} ${elapsedMs}ms ${protocol} ${
                if (bodySize >= 0) "${bodySize}B" else "streaming"
            }"
        )

        return response
    }
}
