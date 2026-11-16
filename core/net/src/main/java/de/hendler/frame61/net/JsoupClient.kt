package de.hendler.frame61.net

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

/**
 * Jsoup HTTP client for HTML-based source scraping
 */
class JsoupClient {

    private val httpClient = HttpClientFactory.create()

    suspend fun fetch(url: String): Result<Document> = try {
        val request = okhttp3.Request.Builder()
            .url(url)
            .get()
            .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
            .build()

        val response = httpClient.newCall(request).execute()
        if (response.isSuccessful) {
            Result.success(Jsoup.parse(response.body?.string() ?: ""))
        } else {
            Result.failure(IOException("HTTP ${response.code}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun fetchWithJsoup(url: String): Result<Document> = try {
        Result.success(Jsoup.connect(url)
            .userAgent("Frame61/1.0")
            .timeout(8000)
            .get())
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun parseStreamLinks(document: Document): List<String> {
        return document.select("a[href^=\"http\"], a[href^=\"//\"]").mapNotNull { el ->
            el.attr("href").takeIf { it.isNotEmpty() }
        }
    }

    fun parseVideoElements(document: Document): List<Element> {
        return document.select("video, iframe[src*=\"voe\"], iframe[src*=\"streamtape\"], iframe[src*=\"filemoon\"]")
            .toList()
    }
}

/**
 * Health checker for sources
 */
class HealthChecker {

    suspend fun checkSource(url: String): HealthScore {
        val start = System.currentTimeMillis()
        val result = JsoupClient().fetch(url)
        val elapsed = System.currentTimeMillis() - start

        return HealthScore(
            sourceId = extractSourceId(url),
            score = if (result.isSuccess) {
                minOf(1.0, elapsed.toDouble() / 2000.0)
            } else 0.0,
            lastChecked = System.currentTimeMillis(),
            responseTimeMs = elapsed,
            statusCode = if (result.isSuccess) 200 else 0,
            isLegalFree = false
        )
    }

    private fun extractSourceId(url: String): String {
        return url.substringAfter("//").substringBefore("/")
    }

    /**
     * Score computation: healthScore → lang → quality
     * Primary sort: healthScore desc
     * Secondary: preferred language (de > en > others)
     * Tertiary: quality (4K > 1080p > 720p > 480p)
     */
    fun scoreMirror(mirror: de.hendler.frame61.data.database.entities.Mirror): Double {
        val langScore = when (mirror.lang) {
            "de" -> 1.0
            "en" -> 0.8
            else -> 0.6
        }
        val qualityScore = when (mirror.quality) {
            "4K" -> 1.0
            "1080p" -> 0.9
            "720p" -> 0.7
            else -> 0.5
        }
        return mirror.healthScore * 0.5 + langScore * 0.25 + qualityScore * 0.25
    }
}
