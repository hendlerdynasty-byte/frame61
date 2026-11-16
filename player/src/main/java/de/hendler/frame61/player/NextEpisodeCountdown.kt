package de.hendler.frame61.player

/**
 * Next-episode countdown.
 *
 * Pure state machine so it is testable without a player or a device:
 * it starts at 90 % of the runtime, runs for [countdownMs] and then reports
 * that the next episode should start.
 */
data class CountdownState(
    val running: Boolean = false,
    val remainingMs: Long = 0L,
    val finished: Boolean = false
)

class NextEpisodeCountdown(
    private val countdownMs: Long = 8_000L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var startedAt: Long = 0L
    private var armed: Boolean = false
    private var cancelled: Boolean = false

    /** Call on every frame tick. Returns the current countdown state. */
    fun onFrame(positionMs: Long, durationMs: Long): CountdownState {
        if (durationMs <= 0L) return CountdownState()
        // A cancelled countdown stays cancelled for this playback; otherwise the
        // next tick at >=90 % would re-arm it immediately.
        if (cancelled) return CountdownState()

        if (!armed) {
            if (ResumeThresholds.reached(positionMs, durationMs)) {
                armed = true
                startedAt = clock()
                return CountdownState(running = true, remainingMs = countdownMs)
            }
            return CountdownState()
        }

        val elapsed = clock() - startedAt
        val remaining = (countdownMs - elapsed).coerceAtLeast(0L)
        return if (remaining == 0L) {
            CountdownState(running = false, remainingMs = 0L, finished = true)
        } else {
            CountdownState(running = true, remainingMs = remaining)
        }
    }

    /** Called when the user cancels (e.g. picks something else). */
    fun cancel() {
        armed = false
        startedAt = 0L
        cancelled = true
    }

    /** Starts over — used when a new episode begins. */
    fun reset() {
        armed = false
        startedAt = 0L
        cancelled = false
    }

    val isArmed: Boolean get() = armed
    val isCancelled: Boolean get() = cancelled
}

object ResumeThresholds {
    const val WATCHED = 0.9

    /** 90 % of the runtime marks the title as watched. */
    fun isWatched(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs.toDouble() / durationMs >= WATCHED

    fun reached(positionMs: Long, durationMs: Long): Boolean = isWatched(positionMs, durationMs)
}

/**
 * Decides what happens when playback stops: next episode, or nothing.
 * Kept pure so the decision is testable apart from the player.
 */
object PlaybackOutcome {

    fun nextEpisode(
        positionMs: Long,
        durationMs: Long,
        hasNextEpisode: Boolean,
        autoplayEnabled: Boolean
    ): Boolean = autoplayEnabled && hasNextEpisode && ResumeThresholds.isWatched(positionMs, durationMs)
}