package de.hendler.frame61.net

import okhttp3.*
import okio.buffer
import okio.source
import java.io.IOException

/**
 * OkHttp client factory — shared across all modules
 */
object HttpClientFactory {
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    fun create(): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(Frame61CookieJar)
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(ChainlinkLoggingInterceptor())
            .addNetworkInterceptor(PlaybackDiagnosticsInterceptor())
            .build()
    }

    fun createForWebView(): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(Frame61CookieJar)
            .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Health score for mirror sources
 * Range: 0.0 (dead) — 1.0 (healthy)
 */
data class HealthScore(
    val sourceId: String,
    val score: Double,
    val lastChecked: Long,
    val responseTimeMs: Long,
    val statusCode: Int,
    val isLegalFree: Boolean = false
) {
    val isHealthy: Boolean get() = score >= 0.5

    companion object {
        val DEAD = HealthScore("", 0.0, 0, 0, 0)
        val HEALTHY = HealthScore("", 1.0, System.currentTimeMillis(), 200, 200, true)
    }
}

/**
 * Mirror resolution result
 */
data class MirrorResult(
    val titleId: Long,
    val sourceId: String,
    val streamUrl: String,
    val lang: String,
    val quality: String,
    val healthScore: Double,
    val isLegalFree: Boolean
)
