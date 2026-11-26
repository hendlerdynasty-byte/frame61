package de.hendler.frame61.diagnostics

import de.hendler.frame61.resolver.MirrorCandidate
import de.hendler.frame61.resolver.hostOf

/** Pure candidate-round policy used by the Aniworld path-acceptance harness. */
object PathCandidatePolicy {
    const val MAX_CORRECT_PER_ROUND = 3
    const val MAX_ALTERNATIVE_ROUNDS = 2

    fun aniworldOnly(
        candidates: List<MirrorCandidate>,
        failedUrls: Set<String> = emptySet()
    ): List<MirrorCandidate> = candidates.filter { candidate ->
        candidate.sourceId == PathCatalogValidator.SOURCE_ID &&
            candidate.url !in failedUrls &&
            (candidate.streamUrl == null || candidate.streamUrl !in failedUrls)
    }

    fun canRequestAlternative(attemptedCorrect: Int, alternativeRounds: Int): Boolean =
        attemptedCorrect > 0 && alternativeRounds < MAX_ALTERNATIVE_ROUNDS

    fun recordFailure(
        packBaseUrl: String,
        candidate: MirrorCandidate,
        failedUrls: MutableSet<String>,
        failedHosts: MutableSet<String>
    ) {
        failedUrls += candidate.url
        candidate.streamUrl?.let { failedUrls += it }
        val sourceHost = hostOf(packBaseUrl)
        listOfNotNull(candidate.url, candidate.streamUrl)
            .map(::hostOf)
            .filter { it.isNotBlank() && it != sourceHost }
            .forEach { failedHosts += it }
    }
}
