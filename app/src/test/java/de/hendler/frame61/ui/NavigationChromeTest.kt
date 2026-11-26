package de.hendler.frame61.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NavigationChromeTest {

    private val routes = listOf(
        null,
        "home",
        "films",
        "series",
        "search",
        "watchlist",
        "settings",
        "detail/{titleId}",
        "source_lab",
        "layout_builder",
        "info",
        "profiles",
        "player",
        "player/{titleId}?episodeId={episodeId}&episodeNumber={episodeNumber}&season={season}"
    )

    @Test
    fun `TV nutzt ausschließlich SIDE oder im Player NONE`() {
        routes.forEach { route ->
            val expected = if (route?.startsWith("player") == true) {
                NavigationChrome.NONE
            } else {
                NavigationChrome.SIDE
            }
            assertEquals("route=$route", expected, navigationChrome(isTv = true, route = route))
        }
    }

    @Test
    fun `Phone nutzt ausschließlich TOP oder im Player NONE`() {
        routes.forEach { route ->
            val expected = if (route?.startsWith("player") == true) {
                NavigationChrome.NONE
            } else {
                NavigationChrome.TOP
            }
            assertEquals("route=$route", expected, navigationChrome(isTv = false, route = route))
        }
    }

    @Test
    fun `keine TV- oder Phone-Route liefert jemals BOTTOM`() {
        assertFalse(NavigationChrome.entries.any { it.name == "BOTTOM" })
        listOf(false, true).forEach { isTv ->
            routes.forEach { route ->
                assertNotEquals(
                    "isTv=$isTv route=$route",
                    "BOTTOM",
                    navigationChrome(isTv, route).name
                )
            }
        }
    }
}
