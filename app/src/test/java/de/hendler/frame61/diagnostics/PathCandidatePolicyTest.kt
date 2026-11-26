package de.hendler.frame61.diagnostics

import de.hendler.frame61.resolver.MirrorCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathCandidatePolicyTest {
    @Test
    fun `failure records media and watch urls but not the pack host`() {
        val failedUrls = mutableSetOf<String>()
        val failedHosts = mutableSetOf<String>()
        val candidate = mirror(
            url = "https://cdn.example/video/master.m3u8",
            streamUrl = "https://aniworld.to/redirect/123"
        )

        PathCandidatePolicy.recordFailure(
            packBaseUrl = "https://aniworld.to",
            candidate = candidate,
            failedUrls = failedUrls,
            failedHosts = failedHosts
        )

        assertEquals(setOf(candidate.url, candidate.streamUrl), failedUrls)
        assertEquals(setOf("cdn.example"), failedHosts)
    }

    @Test
    fun `foreign watch host is blocked while own pack host is ignored`() {
        val failedHosts = mutableSetOf<String>()
        val candidate = mirror(
            url = "https://aniworld.to/media/123",
            streamUrl = "https://voe.sx/e/abc"
        )

        PathCandidatePolicy.recordFailure(
            packBaseUrl = "https://aniworld.to",
            candidate = candidate,
            failedUrls = mutableSetOf(),
            failedHosts = failedHosts
        )

        assertEquals(setOf("voe.sx"), failedHosts)
    }

    @Test
    fun `only non-excluded aniworld candidates survive`() {
        val keep = mirror("https://cdn.example/keep.m3u8", sourceId = "aniworld")
        val other = mirror("https://cdn.example/other.m3u8", sourceId = "ard")
        val mediaExcluded = mirror("https://cdn.example/dead.m3u8", sourceId = "aniworld")
        val watchExcluded = mirror(
            url = "https://cdn.example/watch-dead.m3u8",
            streamUrl = "https://aniworld.to/redirect/dead",
            sourceId = "aniworld"
        )

        val result = PathCandidatePolicy.aniworldOnly(
            listOf(keep, other, mediaExcluded, watchExcluded),
            setOf(mediaExcluded.url, watchExcluded.streamUrl!!)
        )

        assertEquals(listOf(keep), result)
        assertTrue(result.all { it.sourceId == "aniworld" })
    }

    @Test
    fun `round limits match the production-path contract`() {
        assertEquals(3, PathCandidatePolicy.MAX_CORRECT_PER_ROUND)
        assertEquals(2, PathCandidatePolicy.MAX_ALTERNATIVE_ROUNDS)
        assertTrue(PathCandidatePolicy.canRequestAlternative(attemptedCorrect = 1, alternativeRounds = 0))
        assertTrue(PathCandidatePolicy.canRequestAlternative(attemptedCorrect = 3, alternativeRounds = 1))
        assertFalse(PathCandidatePolicy.canRequestAlternative(attemptedCorrect = 0, alternativeRounds = 0))
        assertFalse(PathCandidatePolicy.canRequestAlternative(attemptedCorrect = 3, alternativeRounds = 2))
    }

    private fun mirror(
        url: String,
        streamUrl: String? = null,
        sourceId: String = "aniworld"
    ) = MirrorCandidate(
        sourceId = sourceId,
        titleId = 1L,
        url = url,
        streamUrl = streamUrl,
        identityTitle = "Title",
        identityKind = "source"
    )
}
