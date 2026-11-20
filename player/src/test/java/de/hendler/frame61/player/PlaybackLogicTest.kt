package de.hendler.frame61.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLogicTest {

    // ---------------------------------------------------------- 90 % rule --

    @Test
    fun `ninety percent counts as watched`() {
        assertTrue(ResumeThresholds.isWatched(90_000, 100_000))
        assertTrue(ResumeThresholds.isWatched(100_000, 100_000))
    }

    @Test
    fun `below ninety percent is not watched`() {
        assertFalse(ResumeThresholds.isWatched(89_999, 100_000))
        assertFalse(ResumeThresholds.isWatched(0, 100_000))
    }

    @Test
    fun `zero duration is never watched`() {
        assertFalse(ResumeThresholds.isWatched(0, 0))
        assertFalse(ResumeThresholds.isWatched(50_000, 0))
    }

    // ------------------------------------------------- next-episode decision --

    @Test
    fun `autoplay needs both conditions`() {
        assertTrue(PlaybackOutcome.nextEpisode(95_000, 100_000, hasNextEpisode = true, autoplayEnabled = true))
        assertFalse("no next episode", PlaybackOutcome.nextEpisode(95_000, 100_000, false, true))
        assertFalse("autoplay off", PlaybackOutcome.nextEpisode(95_000, 100_000, true, false))
        assertFalse("not at the end", PlaybackOutcome.nextEpisode(10_000, 100_000, true, true))
    }

    // ------------------------------------------------------- 8s countdown --

    @Test
    fun `countdown starts at ninety percent and runs eight seconds`() {
        var now = 0L
        val countdown = NextEpisodeCountdown(countdownMs = 8_000, clock = { now })

        assertEquals(CountdownState(), countdown.onFrame(10_000, 100_000))

        val armed = countdown.onFrame(90_000, 100_000)
        assertTrue(armed.running)
        assertEquals(8_000, armed.remainingMs)
        assertTrue(countdown.isArmed)

        now = 3_000
        val middle = countdown.onFrame(90_000, 100_000)
        assertTrue(middle.running)
        assertEquals(5_000, middle.remainingMs)

        now = 8_000
        val done = countdown.onFrame(90_000, 100_000)
        assertFalse(done.running)
        assertTrue(done.finished)
        assertEquals(0L, done.remainingMs)
    }

    @Test
    fun `cancel disarms the countdown`() {
        var now = 0L
        val countdown = NextEpisodeCountdown(countdownMs = 8_000, clock = { now })
        countdown.onFrame(90_000, 100_000)
        assertTrue(countdown.isArmed)

        countdown.cancel()
        assertFalse(countdown.isArmed)
        assertTrue("cancel must stick", countdown.isCancelled)
        // Regression: without the cancelled flag the next tick at >=90 % re-armed
        assertEquals(CountdownState(), countdown.onFrame(95_000, 100_000))

        // A new episode clears the cancellation
        countdown.reset()
        assertFalse(countdown.isCancelled)
        assertTrue(countdown.onFrame(95_000, 100_000).running)
    }

    @Test
    fun `countdown does not start without duration`() {
        val countdown = NextEpisodeCountdown(clock = { 0L })
        assertEquals(CountdownState(), countdown.onFrame(0, 0))
        assertFalse(countdown.isArmed)
    }

    @Test
    fun `media headers preserve range and carry resolver context`() {
        val merged = mergeMediaRequestHeaders(
            mapOf("User-Agent" to "Frame61", "Referer" to "https://source.invalid/e/1", "Cookie" to "cf=1"),
            mapOf("Range" to "bytes=0-377")
        )
        assertEquals("bytes=0-377", merged["Range"])
        assertEquals("Frame61", merged["User-Agent"])
        assertEquals("https://source.invalid/e/1", merged["Referer"])
        assertEquals("cf=1", merged["Cookie"])
    }

    @Test
    fun `state snapshot math is consistent`() {
        val state = PlayerUiState(positionMs = 25_000, durationMs = 100_000)
        assertEquals(0.25f, state.fraction, 0.0001f)
        assertFalse(state.isNearEnd)

        val near = PlayerUiState(positionMs = 95_000, durationMs = 100_000)
        assertTrue(near.isNearEnd)
    }
}