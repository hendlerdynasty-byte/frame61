package de.hendler.frame61.data.database

import androidx.room.*
import de.hendler.frame61.data.database.entities.Mirror

@Dao
interface MirrorDao {

    @Query("SELECT * FROM mirrors WHERE titleId = :titleId ORDER BY healthScore DESC, lang ASC, quality DESC")
    suspend fun getMirrorsByTitle(titleId: Long): List<Mirror>

    @Query("SELECT * FROM mirrors WHERE sourceId = :sourceId AND healthScore > :threshold")
    suspend fun getHealthyMirrors(sourceId: String, threshold: Double): List<Mirror>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mirror: Mirror)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(mirrors: List<Mirror>)

    @Query("DELETE FROM mirrors WHERE titleId = :titleId AND sourceId = :sourceId")
    suspend fun removeMirror(titleId: Long, sourceId: String)

    @Query("UPDATE mirrors SET healthScore = :score, lastVerified = :now WHERE id = :id")
    suspend fun updateHealth(id: Long, score: Double, now: Long)

    @Query("SELECT sourceId, AVG(healthScore) as avgScore FROM mirrors GROUP BY sourceId")
    suspend fun getAverageHealthBySource(): List<SourceHealth>
}

data class SourceHealth(
    val sourceId: String,
    val avgScore: Double
)
