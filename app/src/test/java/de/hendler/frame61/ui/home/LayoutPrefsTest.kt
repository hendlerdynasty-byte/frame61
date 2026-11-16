package de.hendler.frame61.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layout id is the persistence contract between the layout builder and the
 * home screen. These tests pin it without a device.
 */
class LayoutPrefsTest {

    @Test
    fun `default layout is encoded as default`() {
        // profileLayoutId collapses the built-in rail set to "default";
        // encode() itself is the explicit list in canonical RailKind order
        // (BECAUSE_YOU_WATCHED precedes TOP10 in the enum).
        assertEquals(LayoutPrefs.DEFAULT_ID, LayoutPrefs.profileLayoutId(HomeContentBuilder.DEFAULT_LAYOUT))
        assertEquals(
            "continue_watching,because_you_watched,top10,new_episodes," +
                "genre_rail,free_legal,random,marathon",
            LayoutPrefs.encode(HomeContentBuilder.DEFAULT_LAYOUT)
        )
    }

    @Test
    fun `selection round-trips`() {
        val selected = setOf(RailKind.TOP10, RailKind.FREE_LEGAL, RailKind.MARATHON)
        val id = LayoutPrefs.encode(selected)
        assertEquals(selected, LayoutPrefs.decode(id))
    }

    @Test
    fun `canonical order is stable regardless of input order`() {
        val a = LayoutPrefs.encode(linkedSetOf(RailKind.MARATHON, RailKind.TOP10))
        val b = LayoutPrefs.encode(linkedSetOf(RailKind.TOP10, RailKind.MARATHON))
        assertEquals(a, b)
        assertEquals("top10,marathon", a)
    }

    @Test
    fun `unknown keys are ignored`() {
        assertEquals(
            setOf(RailKind.TOP10),
            LayoutPrefs.decode("top10,nicht_vorhanden")
        )
    }

    @Test
    fun `blank or default falls back to the built-in rail set`() {
        assertEquals(HomeContentBuilder.DEFAULT_LAYOUT, LayoutPrefs.decode(""))
        assertEquals(HomeContentBuilder.DEFAULT_LAYOUT, LayoutPrefs.decode(null))
        assertEquals(HomeContentBuilder.DEFAULT_LAYOUT, LayoutPrefs.decode(LayoutPrefs.DEFAULT_ID))
    }

    @Test
    fun `billboard is never part of a stored layout`() {
        assertTrue(LayoutPrefs.encode(setOf(RailKind.BILLBOARD, RailKind.TOP10)) == "top10")
        assertTrue(RailKind.BILLBOARD !in LayoutPrefs.decode("top10"))
    }

    @Test
    fun `empty selection falls back to default`() {
        assertEquals(HomeContentBuilder.DEFAULT_LAYOUT, LayoutPrefs.decode(LayoutPrefs.encode(emptySet())))
    }
}