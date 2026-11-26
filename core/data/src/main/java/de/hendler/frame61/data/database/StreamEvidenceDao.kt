package de.hendler.frame61.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import de.hendler.frame61.data.database.entities.StreamEvidence
import de.hendler.frame61.data.database.entities.StreamEvidenceOutcome

@Dao
interface StreamEvidenceDao {

    @Upsert
    suspend fun upsert(evidence: StreamEvidence)

    @Query("SELECT * FROM stream_evidence WHERE id = :id")
    suspend fun getById(id: Long): StreamEvidence?

    @Query("SELECT * FROM stream_evidence WHERE catalogId = :catalogId ORDER BY checkedAt DESC, id DESC")
    suspend fun getByCatalogId(catalogId: String): List<StreamEvidence>

    @Query(
        "SELECT * FROM stream_evidence " +
            "WHERE catalogId = :catalogId AND season = :season AND episode = :episode " +
            "ORDER BY checkedAt DESC, id DESC"
    )
    suspend fun getByEpisode(
        catalogId: String,
        season: Int,
        episode: Int
    ): List<StreamEvidence>

    @Query(
        "SELECT * FROM stream_evidence " +
            "WHERE catalogId = :catalogId AND season = :season AND episode = :episode " +
            "AND outcome = :outcome ORDER BY checkedAt DESC, id DESC"
    )
    suspend fun getByEpisodeAndOutcome(
        catalogId: String,
        season: Int,
        episode: Int,
        outcome: StreamEvidenceOutcome
    ): List<StreamEvidence>

    @Query("SELECT * FROM stream_evidence WHERE outcome = :outcome ORDER BY checkedAt DESC, id DESC")
    suspend fun getByOutcome(outcome: StreamEvidenceOutcome): List<StreamEvidence>
}
