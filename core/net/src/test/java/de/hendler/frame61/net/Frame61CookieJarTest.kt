package de.hendler.frame61.net

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Frame61CookieJarTest {
    @Before
    fun setUp() = Frame61CookieJar.clear()

    @After
    fun tearDown() = Frame61CookieJar.clear()

    @Test
    fun `cookies survive hops with domain path and secure matching`() {
        val root = "https://source.invalid/e/1".toHttpUrl()
        val media = "https://cdn.source.invalid/video/playlist.m3u8".toHttpUrl()
        val otherPath = "https://source.invalid/other/video.ts".toHttpUrl()
        Frame61CookieJar.saveFromResponse(
            root,
            listOf(
                Cookie.Builder().name("cf").value("1").domain("source.invalid").path("/").secure().build(),
                Cookie.Builder().name("embed").value("2").domain("source.invalid").path("/e/").secure().build()
            )
        )

        val names = Frame61CookieJar.loadForRequest(media).map { it.name }
        assertEquals(listOf("cf"), names)
        assertTrue(Frame61CookieJar.loadForRequest(root).map { it.name }.containsAll(listOf("cf", "embed")))
        assertTrue(Frame61CookieJar.loadForRequest(otherPath).none { it.name == "embed" })
        assertTrue(Frame61CookieJar.loadForRequest("http://source.invalid/e/1".toHttpUrl()).isEmpty())
    }
}
