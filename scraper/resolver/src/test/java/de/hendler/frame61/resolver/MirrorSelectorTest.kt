package de.hendler.frame61.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorSelectorTest {

    private fun mirror(
        sourceId: String,
        health: Double,
        lang: String = "de",
        quality: String = "1080p",
        legalFree: Boolean = false
    ) = MirrorCandidate(
        sourceId = sourceId, titleId = 1, url = "https://x/$sourceId",
        lang = lang, quality = quality, healthScore = health, legalFree = legalFree
    )

    @Test
    fun `health score dominates`() {
        val ordered = MirrorSelector.order(
            listOf(
                mirror("weak", 0.4, quality = "2160p"),
                mirror("strong", 0.9, quality = "480p")
            )
        )
        assertEquals(listOf("strong", "weak"), ordered.map { it.sourceId })
    }

    @Test
    fun `language is the second key`() {
        val ordered = MirrorSelector.order(
            listOf(
                mirror("en", 0.8, lang = "en"),
                mirror("de", 0.8, lang = "de")
            )
        )
        assertEquals("de", ordered.first().sourceId)
    }

    @Test
    fun `quality is the third key and compares numerically`() {
        val ordered = MirrorSelector.order(
            listOf(
                mirror("sd", 0.8, quality = "480p"),
                mirror("hd", 0.8, quality = "1080p"),
                mirror("uhd", 0.8, quality = "2160p")
            )
        )
        assertEquals(listOf("uhd", "hd", "sd"), ordered.map { it.sourceId })
        // a naive string sort would put "480p" above "1080p"
        assertTrue(MirrorSelector.qualityRank("1080p") > MirrorSelector.qualityRank("480p"))
    }

    @Test
    fun `legal free wins ties`() {
        val ordered = MirrorSelector.order(
            listOf(
                mirror("gray", 0.7, legalFree = false),
                mirror("free", 0.7, legalFree = true)
            )
        )
        assertEquals("free", ordered.first().sourceId)
    }

    @Test
    fun `legal free does not outrank a healthier mirror`() {
        val ordered = MirrorSelector.order(
            listOf(
                mirror("free", 0.5, legalFree = true),
                mirror("gray", 0.95, legalFree = false)
            )
        )
        assertEquals("gray", ordered.first().sourceId)
    }
}

/** Fake clock so the 8-second boundary is exact. */
private class FixedClock(var value: Long = 0L) : Clock {
    override fun nowMs(): Long = value
}

class PlaybackWatchdogTest {

    @Test
    fun `no frame for 8 seconds is a stall`() {
        val clock = FixedClock(1_000)
        val watchdog = PlaybackWatchdog(timeoutMs = 8_000, clock = clock)
        watchdog.reportFrame(5_000, clock.value)

        clock.value = 8_999
        assertFalse(watchdog.isStalled())
        assertFalse(watchdog.onStall())

        clock.value = 9_000
        assertTrue(watchdog.isStalled())
        assertEquals(8_000, watchdog.msSinceFrame())
    }

    @Test
    fun `a frame resets the watchdog`() {
        val clock = FixedClock(0)
        val watchdog = PlaybackWatchdog(timeoutMs = 8_000, clock = clock)

        clock.value = 7_000
        watchdog.reportFrame(7_000, clock.value)   // frame at 7s
        clock.value = 12_000
        assertFalse("frame at 7s must reset the 8s window", watchdog.isStalled())
        assertEquals(5_000, watchdog.msSinceFrame())
    }

    @Test
    fun `stall records count and position`() {
        val clock = FixedClock(0)
        val watchdog = PlaybackWatchdog(timeoutMs = 8_000, clock = clock)
        watchdog.reportFrame(42_000, 0)

        clock.value = 8_000
        assertTrue(watchdog.onStall())
        assertEquals(42_000, watchdog.positionMs)
        assertEquals(1, watchdog.stalls)

        clock.value = 16_000
        assertTrue(watchdog.onStall())
        assertEquals(2, watchdog.stalls)
    }

    @Test
    fun `rotation hands out mirrors in order and stops at the end`() {
        val watchdog = PlaybackWatchdog(timeoutMs = 8_000, clock = FixedClock(0))
        val rotation = MirrorRotation(
            candidates = listOf(
                MirrorCandidate("a", 1, 1, "https://x/a", healthScore = 0.9),
                MirrorCandidate("b", 1, 1, "https://x/b", healthScore = 0.8),
                MirrorCandidate("c", 1, 1, "https://x/c", healthScore = 0.7)
            ),
            watchdog = watchdog
        )

        assertEquals("a", rotation.current?.sourceId)
        assertEquals(2, rotation.remaining)
        assertEquals("b", rotation.rotate()?.sourceId)
        assertEquals("c", rotation.rotate()?.sourceId)
        assertNull("no mirror left after the third", rotation.rotate())
        assertEquals(2, rotation.switches)
    }

    @Test
    fun `rotation carries the playback position over`() {
        val clock = FixedClock(3_000)
        val watchdog = PlaybackWatchdog(timeoutMs = 8_000, clock = clock)
        watchdog.reportFrame(123_456, clock.value)

        val rotation = MirrorRotation(
            candidates = listOf(
                MirrorCandidate("a", 1, 1, "https://x/a", healthScore = 0.9),
                MirrorCandidate("b", 1, 1, "https://x/b", healthScore = 0.8)
            ),
            watchdog = watchdog
        )
        rotation.rotate()
        assertEquals(123_456, rotation.resumePositionMs())
    }
}