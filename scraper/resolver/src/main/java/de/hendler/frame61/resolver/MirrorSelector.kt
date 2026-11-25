package de.hendler.frame61.resolver

import java.net.URI

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
    val legalFree: Boolean = false,
    /** Propagated to every media request, including playlists, keys and segments. */
    val requestHeaders: Map<String, String> = emptyMap(),
    val cookies: String? = null,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    /**
     * Die **Watch-URL**, aus der dieser Kandidat aufgelöst wurde (Hoster-Seite).
     *
     * Für die Alternative-Kette (Block 4e) ist das der einzige stabile
     * Schlüssel: die aufgelöste Medien-URL (HLS) sieht anders aus als die
     * Hoster-Seite, die sie erzeugt hat. Nur mit der Watch-URL lässt sich
     * sagen, welcher Hoster bereits gescheitert ist — bei moflix trägt jedes
     * Alternativziel einen eigenen Host.
     */
    val streamUrl: String? = null,
    /**
     * Identitätsangabe der Quelle für **diesen** Kandidaten (Gate 1, 1.6.0):
     * Suchtreffer-Titel, API-Titel oder Seitentitel. `null` = die Quelle
     * liefert keine Angabe — der Kandidat darf dann nicht abgespielt werden.
     */
    val identityTitle: String? = null,
    val identityYear: Int? = null,
    /** Laufzeit in Sekunden, wenn die Quelle sie nennt (ARD-API, JSON-LD). */
    val identityRuntimeSec: Int? = null,
    /** Woher die Angabe stammt: `source` (Suchtreffer), `api` (ARD), `page` (HTML). */
    val identityKind: String? = null
)

/**
 * Host einer URL, kleingeschrieben und ohne Port — "" bei unparsbarer URL.
 *
 * Der Host ist der Schlüssel für die Alternative-Kette: die aufgelöste
 * Medien-URL (HLS) sieht anders aus als die Hoster-Seite, die sie erzeugt
 * hat. Nur der Host der **Watch-URL** verrät, welcher Hoster bereits
 * gescheitert ist (gemessen Lauf 7: 12 Titel starben auf `moflix.rpmplay.xyz`,
 * obwohl dieselbe Watch-Seite 3–8 weitere Hoster anbot).
 */
fun hostOf(url: String): String =
    runCatching { URI(url).host?.lowercase() }.getOrNull().orEmpty()

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
                .thenByDescending { it.height ?: 0 }
                .thenByDescending { it.bitrate ?: 0 }
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