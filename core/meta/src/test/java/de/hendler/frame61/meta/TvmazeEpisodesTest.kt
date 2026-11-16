package de.hendler.frame61.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Episode parsing must be stable and never throw — it is fixture-tested. */
class TvmazeEpisodesTest {

    private val body = """
        {
          "id": 82, "name": "Stranger Things", "type": "Scripted",
          "_embedded": {
            "episodes": [
              {"id": 1, "season": 1, "number": 1, "name": "Chapter One",
               "airdate": "2016-07-15", "runtime": 47,
               "image": {"medium": "https://example.invalid/s1e1.jpg"}},
              {"id": 2, "season": 1, "number": 2, "name": "Chapter Two",
               "airdate": "2016-07-15", "runtime": 49, "image": null}
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `episodes parse with season and number`() {
        val episodes = TvmazeEpisodeSource.parse(body)
        assertEquals(2, episodes.size)
        assertEquals("S01 E01 · Chapter One", episodes.first().label)
        assertEquals(1, episodes.first().season)
        assertEquals(1, episodes.first().number)
        assertEquals(47, episodes.first().runtime)
        assertEquals("https://example.invalid/s1e1.jpg", episodes.first().still)
        assertNull(episodes[1].still)
    }

    @Test
    fun `missing or broken payload yields an empty list`() {
        assertTrue(TvmazeEpisodeSource.parse("{}").isEmpty())
        assertTrue(TvmazeEpisodeSource.parse("[]").isEmpty())
        assertTrue(TvmazeEpisodeSource.parse("garbage").isEmpty())
        assertTrue(TvmazeEpisodeSource.parse("").isEmpty())
    }

    @Test
    fun `episodes without embedded list are empty`() {
        assertTrue(TvmazeEpisodeSource.parse("""{"id":1,"name":"Ohne Folgen"}""").isEmpty())
    }

    @Test
    fun `matched show name is readable`() {
        val source = TvmazeEpisodeSource()
        assertEquals("Stranger Things", source.matchedShowName(body))
        assertNull(source.matchedShowName("garbage"))
    }
}