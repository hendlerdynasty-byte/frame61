package de.hendler.frame61.data

import de.hendler.frame61.data.database.StreamEvidenceDao
import de.hendler.frame61.data.database.entities.StreamEvidence
import de.hendler.frame61.data.database.entities.StreamEvidenceOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StreamEvidenceRepositoryTest {

    private val dao = mockk<StreamEvidenceDao>(relaxed = true)
    private val repository = StreamEvidenceRepository(dao)

    @Test
    fun `upsert uses stable id and stores only a normalized hash`() = runTest {
        coEvery { dao.upsert(any()) } returns Unit

        val first = repository.record(sample())
        val second = repository.record(sample(checkedAt = 2_000L))

        assertEquals(first.id, second.id)
        assertEquals(HASH, first.redactedUrlHash)
        coVerify(exactly = 2) { dao.upsert(any()) }
    }

    @Test
    fun `episode and source are part of row identity`() {
        val base = streamEvidenceId("aw:title", 1, 1, "aniworld", "embed-1")
        assertNotEquals(base, streamEvidenceId("aw:title", 1, 2, "aniworld", "embed-1"))
        assertNotEquals(base, streamEvidenceId("aw:title", 2, 1, "aniworld", "embed-1"))
        assertNotEquals(base, streamEvidenceId("aw:title", 1, 1, "other", "embed-1"))
    }

    @Test
    fun `raw URL is rejected instead of being persisted`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            runTest {
                repository.record(sample(redactedUrlHash = "https://cdn.example/video?token=secret"))
            }
        }
        org.junit.Assert.assertTrue(error.message.orEmpty().contains("SHA-256"))
    }

    @Test
    fun `outcome filter delegates the exact episode and outcome`() = runTest {
        coEvery {
            dao.getByEpisodeAndOutcome("aw:title", 1, 2, StreamEvidenceOutcome.CORRECT)
        } returns listOf(sample(episode = 2))

        val rows = repository.getByEpisodeAndOutcome(
            "aw:title", 1, 2, StreamEvidenceOutcome.CORRECT
        )

        assertEquals(1, rows.size)
        assertEquals(2, rows.single().episode)
        coVerify(exactly = 1) {
            dao.getByEpisodeAndOutcome("aw:title", 1, 2, StreamEvidenceOutcome.CORRECT)
        }
    }

    private fun sample(
        episode: Int = 1,
        checkedAt: Long = 1_000L,
        redactedUrlHash: String? = HASH.uppercase()
    ) = StreamEvidence(
        catalogId = "aw:title",
        season = 1,
        episode = episode,
        sourceId = "aniworld",
        sourceKey = "embed-1",
        checkedAt = checkedAt,
        outcome = StreamEvidenceOutcome.CORRECT,
        identityScore = 0.98,
        httpStatus = 200,
        container = "hls",
        redactedUrlHash = redactedUrlHash,
        reason = "identity confirmed"
    )

    private companion object {
        const val HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
