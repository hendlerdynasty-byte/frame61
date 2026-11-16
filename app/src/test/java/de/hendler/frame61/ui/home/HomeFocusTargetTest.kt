package de.hendler.frame61.ui.home

import de.hendler.frame61.meta.CatalogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home screen must always have a first focus target. These tests pin the
 * selector logic without a device: the ids must match what `TvFocusable` cards
 * and the billboard register under.
 */
class HomeFocusTargetTest {

    private fun item(id: String, title: String = id, type: String = "movie") = CatalogItem(
        id = id, title = title, type = type, year = 2024, poster = "https://example.invalid/p.jpg"
    )

    @Test
    fun `billboard wins as first focus target`() {
        val content = HomeContent(
            billboard = item("tt1"),
            rails = listOf(HomeRail(RailKind.TOP10, listOf(item("tt2"))))
        )
        assertEquals("billboard-tt1", firstFocusableId(content))
    }

    @Test
    fun `without a billboard the first card is used`() {
        val content = HomeContent(
            billboard = null,
            rails = listOf(
                HomeRail(RailKind.TOP10, emptyList()),
                HomeRail(RailKind.NEW_EPISODES, listOf(item("s1"), item("s2")))
            )
        )
        assertEquals("card-s1", firstFocusableId(content))
    }

    @Test
    fun `an empty home still returns a stable id`() {
        assertEquals("home-root", firstFocusableId(HomeContent(null, emptyList())))
    }

    @Test
    fun `continue watching rail is skipped when empty`() {
        // Regression: the old build restored focus to "continue-0" while the rail
        // was empty, so no card ever received focus.
        val content = HomeContent(
            billboard = item("tt9"),
            rails = listOf(HomeRail(RailKind.CONTINUE_WATCHING, emptyList()))
        )
        assertEquals("billboard-tt9", firstFocusableId(content))
    }

    @Test
    fun `builder never emits an empty rail`() {
        val catalog = listOf(item("a"), item("b"), item("c", type = "series"))
        val content = HomeContentBuilder.build(catalog)
        assertTrue(content.rails.isNotEmpty())
        assertTrue("empty rail emitted", content.rails.all { it.items.isNotEmpty() })
        assertTrue("billboard missing", content.billboard != null)
    }

    @Test
    fun `continue watching prefers existing titles`() {
        val catalog = listOf(item("a"), item("b"))
        val content = HomeContentBuilder.build(
            catalog = catalog,
            layout = setOf(RailKind.CONTINUE_WATCHING),
            continueWatching = listOf(item("a"))
        )
        assertEquals(1, content.rails.size)
        assertEquals(listOf("a"), content.rails.first().items.map { it.id })
    }
}