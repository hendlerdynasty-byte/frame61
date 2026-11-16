package de.hendler.frame61.scraper.engine

import kotlinx.serialization.Serializable

/**
 * Source configuration from assets/sources/<id>.json
 */
@Serializable
data class SourceConfig(
    val id: String,
    val version: String,
    val minEngine: String,
    val legalFree: Boolean,
    val search: SearchConfig,
    val detail: DetailConfig,
    val streams: StreamsConfig
) {
    @Serializable
    data class SearchConfig(
        val urlTemplate: String,
        val queryParam: String,
        val resultSelector: String
    )

    @Serializable
    data class DetailConfig(
        val urlTemplate: String,
        val selector: String,
        val parseStreamLinks: Boolean
    )

    @Serializable
    data class StreamsConfig(
        val extractor: String,
        val headers: Map<String, String>
    )
}

/**
 * Source tier classification
 */
enum class SourceTier {
    TIER_A, TIER_B, TIER_C
}

object SourceRegistry {

    val TIER_A = listOf(
        "streamkiste_sx",
        "filmpalast_sx",
        "cine_to",
        "s_to",
        "aniworld_to",
        "kinokiste_cloud",
        "megakino_de",
        "dokujunkies_org"
    )

    val TIER_B = listOf(
        "movie4k_to",
        "hdfilme",
        "kinox_to",
        "filmfans_org",
        "bs_to",
        "anime_loads_org"
    )

    val TIER_C_LEGAL_FREE = listOf(
        "ARD", "ZDF", "Arte", "3sat", "KiKa",
        "Pluto_TV_DE", "Netzkino", "Rakuten_Free",
        "Plex_Free", "YouTube", "Archive.org"
    )

    val ALL = TIER_A + TIER_B + TIER_C_LEGAL_FREE

    fun getTier(sourceId: String): SourceTier {
        return when {
            sourceId in TIER_A -> SourceTier.TIER_A
            sourceId in TIER_B -> SourceTier.TIER_B
            sourceId in TIER_C_LEGAL_FREE -> SourceTier.TIER_C
            else -> SourceTier.TIER_B
        }
    }
}
