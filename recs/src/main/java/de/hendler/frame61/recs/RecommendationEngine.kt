package de.hendler.frame61.recs

import de.hendler.frame61.data.database.entities.RecVector
import de.hendler.frame61.meta.MetaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recommendation engine
 */
interface RecommendationEngine {
    suspend fun getRecommendations(profileId: Long, limit: Int = 20): List<RecRecommendation>
    suspend fun addVector(profileId: Long, tag: String, weight: Double)
    suspend fun removeVector(profileId: Long, tag: String)
    suspend fun clearVectors(profileId: Long)
}

data class RecRecommendation(
    val titleId: Long,
    val reason: String,
    val weight: Double,
    val metaType: MetaType
)

/**
 * BecauseYouWatched — collaborative filter
 */
class BecauseYouWatched : RecommendationEngine {
    override suspend fun getRecommendations(profileId: Long, limit: Int): List<RecRecommendation> {
        return emptyList()
    }

    override suspend fun addVector(profileId: Long, tag: String, weight: Double) {}
    override suspend fun removeVector(profileId: Long, tag: String) {}
    override suspend fun clearVectors(profileId: Long) {}
}

/**
 * Top10 — trending based on rec vectors
 */
class Top10Engine : RecommendationEngine {
    override suspend fun getRecommendations(profileId: Long, limit: Int): List<RecRecommendation> {
        return emptyList()
    }

    override suspend fun addVector(profileId: Long, tag: String, weight: Double) {}
    override suspend fun removeVector(profileId: Long, tag: String) {}
    override suspend fun clearVectors(profileId: Long) {}
}

/**
 * Rec vector repository — persists to Room
 */
interface RecVectorRepository {
    suspend fun getVectors(profileId: Long): List<RecVector>
    suspend fun addVector(recVector: RecVector)
    suspend fun removeTag(profileId: Long, tag: String)
    suspend fun clearProfile(profileId: Long)
}

class DefaultRecVectorRepository : RecVectorRepository {
    override suspend fun getVectors(profileId: Long): List<RecVector> = emptyList()
    override suspend fun addVector(recVector: RecVector) {}
    override suspend fun removeTag(profileId: Long, tag: String) {}
    override suspend fun clearProfile(profileId: Long) {}
}
