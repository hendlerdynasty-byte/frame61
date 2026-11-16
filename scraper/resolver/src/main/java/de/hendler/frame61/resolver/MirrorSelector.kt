package de.hendler.frame61.resolver

/**
 * One playable candidate for a title/episode. Mirrors are site copies; identity
 * stays the TMDB id.
 */
data class MirrorCandidate(
    val sourceId: String,
    val titleId: Long,
    val episodeId: Long? = null,
    val url: String,
    val lang: String = "de",
    val quality: String = "unknown",
    val hosterId: String? = null,
    val healthScore: Double = 0.0,
    val legalFree: Boolean = false
)

/**
 * Orders mirrors exactly as specified: health score first, then language, then
 * quality; a legal-free pack wins ties.
 */
object MirrorSelector {

    fun order(candidates: List<MirrorCandidate>, preferredLang: String = "de"): List<MirrorCandidate> =
        candidates.sortedWith(
            compareByDescending<MirrorCandidate> { it.healthScore }
                .thenBy { if (it.lang.equals(preferredLang, ignoreCase = true)) 0 else 1 }
                .thenByDescending { qualityRank(it.quality) }
                .thenByDescending { if (it.legalFree) 1 else 0 }
        )

    /** `1080p`, `1080`, `FHD` → comparable rank; unknown sorts last. */
    fun qualityRank(quality: String): Int {
        val digits = Regex("""\d{3,4}""").find(quality)?.value?.toIntOrNull() ?: return 0
        return digits
    }
}

/** Minimal clock seam so the watchdog is testable without Android. */
fun interface Clock {
    fun nowMs(): Long
}

object SystemClock : Clock {
    override fun nowMs(): Long = System.nanoTime() / 1_000_000L
}

/**
 * 8-second watchdog: as long as frames arrive the mirror stays; after
 * [timeoutMs] without a frame the caller rotates to the next mirror.
 *
 * Rotation keeps the same player instance and restores [positionMs], so the UI
 * does not flicker — the swap is a source change, not a screen change.
 */
class PlaybackWatchdog(
    private val timeoutMs: Long = 8_000,
    private val clock: Clock = SystemClock
) {
    private var lastFrameAt: Long = clock.nowMs()
    private var lastPositionMs: Long = 0L
    private var stallCount: Int = 0

    val positionMs: Long get() = lastPositionMs
    val stalls: Int get() = stallCount

    /** Proof of life from the player. */
    fun reportFrame(positionMs: Long, nowMs: Long = clock.nowMs()) {
        lastFrameAt = nowMs
        lastPositionMs = positionMs
    }

    fun isStalled(nowMs: Long = clock.nowMs()): Boolean = nowMs - lastFrameAt >= timeoutMs

    fun msSinceFrame(nowMs: Long = clock.nowMs()): Long = nowMs - lastFrameAt

    /** Records a stall; returns true when the caller should rotate. */
    fun onStall(nowMs: Long = clock.nowMs()): Boolean {
        if (!isStalled(nowMs)) return false
        stallCount++
        lastFrameAt = nowMs
        return true
    }
}

/**
 * Rotation state for one title/episode. Keeps the current index and hands out
 * the next candidate; `null` means the mirror list is exhausted.
 */
class MirrorRotation(
    candidates: List<MirrorCandidate>,
    private val watchdog: PlaybackWatchdog
) {
    private val mirrors = MirrorSelector.order(candidates)
    private var index = 0
    private var switchCount = 0

    val current: MirrorCandidate? get() = mirrors.getOrNull(index)
    val switches: Int get() = switchCount
    val remaining: Int get() = (mirrors.size - index - 1).coerceAtLeast(0)

    /** Called when a mirror failed; returns the next one, or null. */
    fun rotate(): MirrorCandidate? {
        if (index + 1 >= mirrors.size) return null
        index++
        switchCount++
        return mirrors[index]
    }

    /** Position carried over so the next mirror resumes instead of restarting. */
    fun resumePositionMs(): Long = watchdog.positionMs
}