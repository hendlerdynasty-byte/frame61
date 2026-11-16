package de.hendler.frame61.scraper.engine

import de.hendler.frame61.net.HealthScore
import de.hendler.frame61.net.JsoupClient
import de.hendler.frame61.net.HttpClientFactory
import org.jsoup.nodes.Document
import java.io.IOException
import kotlinx.coroutines.*

/**
 * Scraper engine — core scraping logic
 */
class ScraperEngine {

    private val jsoupClient = JsoupClient()

    suspend fun scrape(sourceConfig: SourceConfig): Result<List<ScraperResult>> {
        return try {
            val url = buildScrapeUrl(sourceConfig)
            val doc = jsoupClient.fetchWithJsoup(url).getOrThrow()
            val results = parseResults(doc, sourceConfig)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildScrapeUrl(sourceConfig: SourceConfig): String {
        return sourceConfig.search.urlTemplate
    }

    private fun parseResults(doc: Document, sourceConfig: SourceConfig): List<ScraperResult> {
        return doc.select(sourceConfig.search.resultSelector).map { element ->
            ScraperResult(
                titleId = element.attr("data-id").toLongOrNull() ?: 0L,
                title = element.text(),
                sourceId = sourceConfig.id,
                url = element.attr("href"),
                lang = "de",
                quality = "1080p"
            )
        }
    }
}

data class ScraperResult(
    val titleId: Long,
    val title: String,
    val sourceId: String,
    val url: String,
    val lang: String,
    val quality: String
)

/**
 * Health scoring — per source, aggregated
 */
object HealthScoring {

    private val healthScores = mutableMapOf<String, MutableList<HealthScore>>()

    fun updateHealth(sourceId: String, score: HealthScore) {
        healthScores.getOrPut(sourceId) { mutableListOf() }.add(score)
        if (healthScores[sourceId]?.size ?: 0 > 100) {
            healthScores[sourceId] = (healthScores[sourceId] ?: mutableListOf()).takeLast(100).toMutableList()
        }
    }

    fun getAverageHealth(sourceId: String): Double {
        return healthScores[sourceId]?.map { it.score }?.average() ?: 0.0
    }

    fun getOverallHealth(): Map<String, Double> {
        return healthScores.mapValues { (_, scores) ->
            scores.map { it.score }.average()
        }
    }

    fun getHealthySources(threshold: Double = 0.5): Set<String> {
        return healthScores.filterValues { scores ->
            scores.map { it.score }.average() >= threshold
        }.keys.toSet()
    }
}

/**
 * Scraping job — scheduled via WorkManager
 */
class ScrapingJob {
    suspend fun run() {
        // Background scraping job
        for (sourceId in SourceRegistry.ALL) {
            val config = loadSourceConfig(sourceId)
            val result = ScraperEngine().scrape(config)
            result.onSuccess { results ->
                results.forEach { r ->
                    HealthScoring.updateHealth(r.sourceId, HealthScore(
                        sourceId = r.sourceId,
                        score = if (r.quality == "1080p") 0.8 else 0.6,
                        lastChecked = System.currentTimeMillis(),
                        responseTimeMs = 200,
                        statusCode = 200
                    ))
                }
            }
        }
    }

    private suspend fun loadSourceConfig(sourceId: String): SourceConfig {
        return SourceConfig(
            id = sourceId,
            version = "1.0.0",
            minEngine = "2.0",
            legalFree = sourceId in SourceRegistry.TIER_C_LEGAL_FREE,
            search = SourceConfig.SearchConfig("", "q", "a"),
            detail = SourceConfig.DetailConfig("", "", true),
            streams = SourceConfig.StreamsConfig("", emptyMap())
        )
    }
}
