package de.hendler.frame61.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resume rows need a stable, unique id per (profile, title, episode).
 * Before this, every row was id=0 and REPLACE deleted the previous title's
 * position — measured on the A52 as a single resume row.
 */
class ResumeRowIdTest {

    @Test
    fun `id is stable`() {
        assertEquals(
            resumeRowId(profileId = 0, titleId = 42, episodeId = 7),
            resumeRowId(profileId = 0, titleId = 42, episodeId = 7)
        )
    }

    @Test
    fun `episodes do not share a row`() {
        val first = resumeRowId(0, 42, 1)
        val second = resumeRowId(0, 42, 2)
        assertNotEquals(first, second)
    }

    @Test
    fun `titles and profiles do not collide`() {
        val base = resumeRowId(0, 42, 1)
        assertNotEquals(base, resumeRowId(0, 43, 1))
        assertNotEquals(base, resumeRowId(1, 42, 1))
    }

    @Test
    fun `id is positive`() {
        listOf(0L, 1L, Long.MAX_VALUE).forEach { profile ->
            listOf(0L, 7L, 9_999L).forEach { title ->
                listOf(0L, 3L, 123_456L).forEach { episode ->
                    assertTrue(resumeRowId(profile, title, episode) > 0L)
                }
            }
        }
    }
}
