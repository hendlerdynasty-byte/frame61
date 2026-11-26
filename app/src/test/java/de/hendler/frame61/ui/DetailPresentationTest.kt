package de.hendler.frame61.ui

import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.meta.EpisodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailPresentationTest {

    private val series = CatalogItem(id = "s1", title = "Serie", type = "series")
    private val episodes = listOf(
        EpisodeInfo(id = 11, season = 1, number = 1, name = "Anfang"),
        EpisodeInfo(id = 21, season = 2, number = 1, name = "Weiter"),
        EpisodeInfo(id = 22, season = 2, number = 2, name = "Auswahl")
    )

    @Test
    fun `Auswahl liefert exakt Staffel Folge und Player-Route`() {
        val target = detailPlaybackTarget(series, episodes, selectedSeason = 2, selectedEpisodeId = 22)
        assertEquals(22L, target.episodeId)
        assertEquals(2, target.episodeNumber)
        assertEquals(2, target.season)
        assertEquals(
            "player/s1?episodeId=22&episodeNumber=2&season=2",
            playerRoute(series, target)
        )
    }

    @Test
    fun `fremde Auswahl faellt auf erste Folge der Staffel zurueck`() {
        val target = detailPlaybackTarget(series, episodes, selectedSeason = 2, selectedEpisodeId = 11)
        assertEquals(21L, target.episodeId)
        assertEquals(1, target.episodeNumber)
        assertEquals(2, target.season)
    }

    @Test
    fun `Serie ohne Folgen behaelt den bestehenden Player-Fallback`() {
        val target = detailPlaybackTarget(series, emptyList(), selectedSeason = null, selectedEpisodeId = null)
        assertFalse(target.hasEpisode)
        assertEquals("player/s1", playerRoute(series, target))
    }

    @Test
    fun `Film ignoriert Episodenauswahl`() {
        val movie = series.copy(type = "movie")
        val target = detailPlaybackTarget(movie, episodes, selectedSeason = 2, selectedEpisodeId = 22)
        assertFalse(target.hasEpisode)
        assertEquals("player/s1", playerRoute(movie, target))
    }

    @Test
    fun `aehnliche Titel priorisieren Genres und schliessen sich selbst aus`() {
        val current = series.copy(genres = listOf("Drama"), year = 2020)
        val catalog = listOf(
            current,
            current.copy(id = "doc", title = "Doku", genres = listOf("Dokumentation"), year = 2020),
            current.copy(id = "drama", title = "Drama", genres = listOf("Drama"), year = 2021),
            current.copy(id = "other", title = "Andere", genres = listOf("Komödie"), year = 1990)
        )
        val result = similarTitles(current, catalog)
        assertEquals("drama", result.first().id)
        assertFalse(result.any { it.id == current.id })
    }

    @Test
    fun `Laufzeit und Fortschritt haben lesbare Leerzustaende`() {
        assertEquals("Laufzeit unbekannt", runtimeLabel(null))
        assertEquals("2 Std. 5 Min.", runtimeLabel(125))
        assertEquals("Noch nicht gesehen", episodeProgressLabel(null))
        assertEquals("42 % gesehen", episodeProgressLabel(0.42f))
        assertEquals("Gesehen", episodeProgressLabel(0.95f))
    }

    @Test
    fun `Play-Label nutzt dieselbe Staffel und Folge`() {
        val target = detailPlaybackTarget(series, episodes, selectedSeason = 2, selectedEpisodeId = 22)
        assertTrue(playbackLabel(series, target).contains("S02 E02"))
    }
}
