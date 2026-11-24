package de.hendler.frame61.playback

import de.hendler.frame61.resolver.MirrorCandidate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Vorwaermlauf auf Fokus (Block 7).
 *
 * Drei Regeln tragen das Feature und werden hier festgeschrieben:
 *  - der Fokus muss **stehen**, bevor etwas passiert (Entprellung),
 *  - der Player uebernimmt den vorbereiteten Kandidaten,
 *  - waehrend der Player laeuft, wird nichts vorgewaermt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchControllerTest {

    private fun candidate(url: String = "https://cdn.test/master.m3u8") = MirrorCandidate(
        sourceId = "s-to",
        titleId = 1L,
        episodeId = null,
        url = url,
        lang = "de",
        quality = "1080",
        hosterId = "filemoon",
        healthScore = 1.0,
        legalFree = false,
        requestHeaders = emptyMap(),
        cookies = null
    )

    @Test
    fun `der fokus entprellt - erst nach 400 ms wird aufgeloest`() = runTest {
        val resolver = mockk<AutoPlaybackResolver>()
        coEvery { resolver.prefetch(any(), any(), any()) } coAnswers { listOf(candidate()) }
        val controller = PrefetchController(resolver, this)

        controller.prefetch("Serie X")
        advanceTimeBy(PrefetchController.DEBOUNCE_MS - 1)
        runCurrent()
        coVerify(exactly = 0) { resolver.prefetch(any(), any(), any()) }

        advanceTimeBy(2)
        advanceUntilIdle()
        coVerify(exactly = 1) { resolver.prefetch("Serie X", 0, 1) }
    }

    @Test
    fun `der vorbereitete kandidat wird vom player uebernommen`() = runTest {
        val resolver = mockk<AutoPlaybackResolver>()
        coEvery { resolver.prefetch(any(), any(), any()) } coAnswers { listOf(candidate()) }
        val controller = PrefetchController(resolver, this)

        controller.prefetch("Serie X")
        advanceUntilIdle()

        val prepared = controller.peek("Serie X")
        assertEquals(1, prepared.size)
        assertEquals("https://cdn.test/master.m3u8", prepared.first().url)
    }

    @Test
    fun `ein fremder titel bekommt keinen kandidaten`() = runTest {
        val resolver = mockk<AutoPlaybackResolver>()
        coEvery { resolver.prefetch(any(), any(), any()) } coAnswers { listOf(candidate()) }
        val controller = PrefetchController(resolver, this)

        controller.prefetch("Serie X")
        advanceUntilIdle()

        assertTrue(controller.peek("Andere Serie").isEmpty())
    }

    @Test
    fun `waehrend der player laeuft wird nichts vorgewaermt`() = runTest {
        val resolver = mockk<AutoPlaybackResolver>()
        coEvery { resolver.prefetch(any(), any(), any()) } coAnswers { listOf(candidate()) }
        val controller = PrefetchController(resolver, this)

        controller.markForegroundBusy(true)
        controller.prefetch("Serie X")
        advanceUntilIdle()

        coVerify(exactly = 0) { resolver.prefetch(any(), any(), any()) }
        assertTrue(controller.peek("Serie X").isEmpty())
    }

    @Test
    fun `der cache schluessel traegt staffel und folge`() {
        // Block 8: ein Kandidat der Staffel 1 darf nicht fuer Staffel 2 gelten.
        assertEquals(
            "Serie X|s2e7",
            PrefetchController.key("Serie X", 7, 2)
        )
        assertTrue(PrefetchController.key("Serie X", 7, 1) != PrefetchController.key("Serie X", 7, 2))
    }

    @Test
    fun `ein kandidat ist 60 sekunden lang gueltig`() {
        assertTrue(PrefetchController.fresh(at = 0L, now = 59_999L))
        assertTrue(!PrefetchController.fresh(at = 0L, now = 60_000L))
    }

    @Test
    fun `bs to wird nicht vorgewaermt weil es die webview braucht`() {
        // VOE laeuft ueber die stille WebView; ein Vorwaermlauf wuerde dem
        // echten Start den Slot wegnehmen.
        assertTrue(PrefetchScope.packsFor("bsto").isEmpty())
        assertTrue(!PrefetchScope.isPrefetchable("bsto"))
    }

    @Test
    fun `jede harvestgruppe zeigt auf ihr pack`() {
        assertEquals(setOf("s-to"), PrefetchScope.packsFor("serienstream"))
        assertEquals(setOf("aniworld"), PrefetchScope.packsFor("aniworld"))
        assertEquals(setOf("filmpalast"), PrefetchScope.packsFor("filmpalast"))
        assertEquals(setOf("moflix"), PrefetchScope.packsFor("moflix"))
        assertEquals(setOf("ard-mediathek"), PrefetchScope.packsFor("ard"))
        assertTrue(PrefetchScope.packsFor(null).isEmpty())
        assertTrue(PrefetchScope.packsFor("unbekannt").isEmpty())
    }
}
